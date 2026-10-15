package com.architectpsg.commandlibrary.feature.links

import com.architectpsg.commandlibrary.core.model.*

/** Pure supplied-link and explicit-template resolution. No vendor routes are inferred. */
public class LocalCardLinks : CardLinks {
    public override fun template(pattern: String, values: LinkTokenValues): Outcome<ResourceUri> =
        LinkTemplateRenderer.render(pattern, values)

    public override fun resolve(
        card: CardRecord,
        printing: PrintingRecord?,
        preferences: LinkPreferences,
    ): Outcome<SnapshotList<ResolvedLink>> {
        if (preferences.destinations.size > MAX_TARGETS || preferences.templates.size > MAX_TARGETS) return invalid("Too many link targets or templates")
        val ids = preferences.destinations.map { it.id }
        val templateIds = preferences.templates.map { it.destinationId }
        if (ids.distinct().size != ids.size || templateIds.distinct().size != templateIds.size || templateIds.any { it !in ids }) {
            return invalid("Duplicate or orphan link configuration")
        }
        for (configured in preferences.templates) {
            when (val status = LinkTemplateRenderer.validate(configured.pattern, configured.allowedTokens)) {
                is Outcome.Success -> Unit
                is Outcome.Failure -> return status
                is Outcome.Unavailable -> return status
                is Outcome.Unknown -> return status
            }
        }
        val targets = preferences.destinations.filter { it.enabled }
        if (targets.isEmpty()) return Outcome.Success(SnapshotList.empty())
        for (target in targets) {
            if (target.allowedOrigins.isEmpty() || target.allowedOrigins.any { !LinkTemplateRenderer.validOrigin(it) }) {
                return invalid("Target requires explicit valid origins")
            }
        }
        if (printing != null) {
            when (val relation = printing.oracleId) {
                is Knowledge.Known -> if (relation.value != card.oracleId) return Outcome.Failure(
                    DomainFailure(FailureCode.SOURCE_MISMATCH, "Selected printing belongs to a different Oracle identity", SnapshotList.empty()),
                )
                is Knowledge.Unknown -> return Outcome.Unknown("Selected printing's whole-card Oracle relation is unproven", relation.evidence)
                is Knowledge.Absent -> return Outcome.Unknown("Selected printing's whole-card Oracle relation is absent", SnapshotList.empty())
            }
        }
        val values = LinkTokenValues(
            card.name, Knowledge.Known(card.oracleId),
            printing?.let { Knowledge.Known(it.id) } ?: Knowledge.Absent("No selected printing"),
            printing?.let { Knowledge.Known(it.setCode) } ?: Knowledge.Absent("No selected printing"),
            printing?.let { Knowledge.Known(it.collectorNumber) } ?: Knowledge.Absent("No selected printing"),
            printing?.language ?: card.language, printing?.vendorIds ?: SnapshotList.empty(),
        )
        val templates = preferences.templates.associateBy { it.destinationId }
        val resolved = mutableListOf<ResolvedLink>()
        for (target in targets) {
            val configured = templates[target.id]
            val supplied = suppliedKeys[target.id.value]?.let { key -> printing?.suppliedLinks?.filter { it.destination == key } }.orEmpty().distinct()
            if (supplied.size > 1) return invalid("Conflicting supplied links for ${safeId(target)}")
            val actual = supplied.singleOrNull()
            // Source URLs are validated even when a preferred template would otherwise hide them.
            if (actual != null) {
                when (val origin = LinkTemplateRenderer.origin(actual.uri.value)) {
                    is Outcome.Success -> if (origin.value !in target.allowedOrigins) return invalid("Supplied URL origin is not allowed for ${safeId(target)}")
                    is Outcome.Failure -> return origin
                    is Outcome.Unavailable -> return origin
                    is Outcome.Unknown -> return origin
                }
            }
            val useTemplate = configured != null && (!preferences.preferSelectedPrinting || actual == null)
            if (useTemplate) {
                when (val rendered = LinkTemplateRenderer.render(configured!!.pattern, values, configured.allowedTokens)) {
                    is Outcome.Success -> {
                        val origin = LinkTemplateRenderer.origin(rendered.value.value)
                        if (origin !is Outcome.Success || origin.value !in target.allowedOrigins) return invalid("Template URL origin is not allowed for ${safeId(target)}")
                        resolved.add(ResolvedLink(target, rendered.value, LinkResolutionKind.TEMPLATE, null, null,
                            Knowledge.Known("Destination matching is user-configured. This template is not an exact seller or selected-printing link.")))
                    }
                    is Outcome.Unavailable -> return Outcome.Unavailable(rendered.reason, "Target ${safeId(target)}: ${rendered.detail}")
                    is Outcome.Failure -> return rendered
                    is Outcome.Unknown -> return rendered
                }
            } else if (actual != null) {
                val warning = if (target.id.value == "cardmarket") {
                    "Source-supplied Cardmarket URL may be inexact; seller, finish, inventory and availability are not verified."
                } else {
                    "Source-supplied URL; destination, seller, finish, inventory and availability are not independently verified."
                }
                resolved.add(ResolvedLink(target, actual.uri, LinkResolutionKind.SUPPLIED, printing!!.id, actual.provenance, Knowledge.Known(warning)))
            } else {
                return Outcome.Unavailable(UnavailableReason.NOT_FOUND,
                    "Target ${safeId(target)}: missing selected-printing supplied URL or explicit template; no verified fallback is configured")
            }
        }
        return Outcome.Success(SnapshotList.copyOf(resolved))
    }

    private fun invalid(message: String): Outcome.Failure =
        Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, message, SnapshotList.empty()))

    private fun safeId(target: LinkDestination): String = target.id.value.takeIf {
        it.length <= 128 && it.all { character -> character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9' || character in "._-" }
    } ?: "[configured target]"

    private companion object {
        const val MAX_TARGETS: Int = 128
        val suppliedKeys: Map<String, String> = mapOf(
            "scryfall" to "scryfall_uri",
            "gatherer" to "related_uris.gatherer",
            "edhrec" to "related_uris.edhrec",
            "tcgplayer" to "purchase_uris.tcgplayer",
            "cardmarket" to "purchase_uris.cardmarket",
            "cardhoarder" to "purchase_uris.cardhoarder",
        )
    }
}
