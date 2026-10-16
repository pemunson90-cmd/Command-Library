package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/** Explicit captured Commander assessment, with source eligibility separate from construction. */
public class LocalBasicDeckValidationEngine(
    private val facts: CatalogDeckFactsLookup,
    private val legalities: CatalogFormatLegalityLookup,
    private val status: CatalogStatus,
) : LocalBasicDeckValidation {
    override suspend fun analyze(request: BasicDeckValidationRequest, cancel: CancelToken): Outcome<BasicDeckValidationResult> {
        val context = currentCoroutineContext()
        val poll = { context.ensureActive(); cancel.throwIfCancelled() }
        val guarded = object : CancelToken {
            override val isCancelled: Boolean get() = !context.isActive || cancel.isCancelled
            override fun throwIfCancelled() = poll()
        }
        poll()
        return try {
            validationValue(DeckSnapshotValidator.validate(request.deck, guarded))
            preflight(request, poll)
            val budget = BasicValidationBounds(poll)
            val provenance = active(validationValue(status.status(guarded)), request.snapshot, budget, poll)
            val cards = HashMap<Pair<OracleId, CardId?>, CatalogDeckFacts?>()
            val eligibility = linkedMapOf<OracleId, CatalogFormatLegality?>()
            val evidence = ArrayList<BasicEntryEvidence>()
            for (entry in request.deck.entries) {
                poll()
                if (entry.board == BoardRole.MAYBEBOARD) continue
                val oracle = entry.identity.oracleId.validationKnown()
                val printing = entry.identity.printingId.validationKnown()
                var card: CatalogDeckFacts? = null
                var legality: CatalogFormatLegality? = null
                if (oracle != null && oracle.source == request.snapshot.source) {
                    if (printing == null || printing.source == request.snapshot.source) {
                        val key = oracle to printing
                        if (!cards.containsKey(key)) {
                            cards[key] = missingOrValue(facts.facts(oracle, printing, request.snapshot, guarded))?.also {
                                poll(); budget.facts(it)
                                validateCard(it, oracle, printing, request.snapshot, provenance, poll)
                            }
                        }
                        card = cards[key]
                    }
                    if (!eligibility.containsKey(oracle)) {
                        eligibility[oracle] = missingOrValue(legalities.legality(oracle, COMMANDER_FORMAT, request.snapshot, guarded))?.also {
                            poll(); budget.legality(it)
                            validationRequire(it.oracleId == oracle && it.formatId == COMMANDER_FORMAT && it.snapshot == request.snapshot &&
                                it.provenance.provider == request.snapshot.source && it.provenance.generation == request.snapshot.generation &&
                                it.provenance.version == provenance.version, message = "Source eligibility attribution mismatch")
                            validationRequire(it.status is Knowledge.Unknown || it.status.validationKnown() in setOf(
                                LegalityStatus.LEGAL, LegalityStatus.BANNED, LegalityStatus.RESTRICTED, LegalityStatus.NOT_LEGAL),
                                message = "Invalid source eligibility status")
                        }
                    }
                    legality = eligibility[oracle]
                }
                evidence.add(BasicEntryEvidence(entry, card, legality))
            }
            val result = BasicCommanderChecks.assess(request, evidence, poll)
            val after = active(validationValue(status.status(guarded)), request.snapshot, budget, poll)
            validationRequire(after.version == provenance.version, FailureCode.STALE_GENERATION, "Captured source version changed")
            poll()
            Outcome.Success(result)
        } catch (rejection: ValidationRejection) { rejection.outcome }
    }
    private fun preflight(request: BasicDeckValidationRequest, poll: () -> Unit) {
        validationRequire(request.deck.entries.size <= DeckStatisticsLimits.MAX_ENTRIES, FailureCode.INVALID_INPUT, "Validation entry budget exceeded")
        val snapshot = request.snapshot
        validationRequire(snapshot.source == ProviderId("scryfall") && snapshot.generation != null && snapshot.revision == null &&
            snapshot.language == LanguageTag.ENGLISH, FailureCode.INVALID_INPUT, "Exact English Scryfall snapshot is required")
        val input = BasicValidationBounds(poll, FailureCode.INVALID_INPUT)
        input.id(snapshot.source.value); input.id(snapshot.generation!!.value)
        for (entry in request.deck.entries) {
            poll()
            entry.identity.oracleId.validationKnown()?.let { input.id(it.source.value); input.id(it.raw) }
            entry.identity.printingId.validationKnown()?.let { input.id(it.source.value); input.id(it.raw) }
        }
    }
    private fun active(value: LocalCatalogStatus, snapshot: SnapshotReference, budget: BasicValidationBounds, poll: () -> Unit): Provenance {
        poll()
        budget.catalogStatus(value)
        validationRequire(value.activeGenerations.size <= DeckStatisticsLimits.MAX_ENTRIES &&
            value.installedLanguages.size <= DeckStatisticsLimits.MAX_ENTRIES, message = "Invalid catalog status")
        if (!value.initialized || value.activeGenerations.isEmpty() || LanguageTag.ENGLISH !in value.installedLanguages)
            throw ValidationRejection(Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "No installed English catalog"))
        val generations = value.activeGenerations.filter { poll(); it.status == GenerationStatus.ACTIVE && LanguageTag.ENGLISH in it.languages }
        validationRequire(value.source.validationKnown() == snapshot.source && generations.size == 1 &&
            generations.single().id == snapshot.generation && generations.single().provenance.provider == snapshot.source,
            FailureCode.STALE_GENERATION, "Captured catalog generation changed")
        val generation = generations.single()
        validationRequire(generation.provenance.generation == snapshot.generation, message = "Conflicting catalog generation provenance")
        return generation.provenance
    }
    private fun validateCard(value: CatalogDeckFacts, oracle: OracleId, printing: CardId?, snapshot: SnapshotReference,
        provenance: Provenance, poll: () -> Unit) {
        val card = value.card
        validationRequire(value.snapshot == snapshot && value.printingId == printing && card.oracleId == oracle &&
            card.language == LanguageTag.ENGLISH && card.provenance.provider == snapshot.source &&
            card.provenance.generation == snapshot.generation && card.provenance.version == provenance.version && card.revision == null,
            message = "Validation card attribution mismatch")
        validationRequire(card.name.isNotBlank(), message = "Invalid canonical English name")
        card.colorIdentity.validationKnown()?.let {
            validationRequire(it.size <= Color.entries.size && it.toSet().size == it.size, message = "Invalid source color identity")
        }
        val indices = HashSet<Int>()
        for (face in card.faces) {
            poll()
            validationRequire(face.identity.oracleId == oracle && indices.add(face.identity.index), message = "Validation face attribution mismatch")
        }
        for (identity in card.providerIdentities) {
            poll()
            if (identity.provider == snapshot.source && identity.kind == "oracle")
                validationRequire(identity.rawId == oracle.raw, message = "Validation Oracle mapping mismatch")
        }
    }
    private fun <T> missingOrValue(result: Outcome<T>): T? = when (result) {
        is Outcome.Unavailable -> if (result.reason in setOf(UnavailableReason.NOT_FOUND, UnavailableReason.NOT_INSTALLED,
            UnavailableReason.SOURCE_UNAVAILABLE)) null else validationValue(result)
        else -> validationValue(result)
    }
    private companion object {
        val COMMANDER_FORMAT = FormatId(ProviderId("scryfall"), "commander")
    }
}

internal class ValidationRejection(val outcome: Outcome<Nothing>) : RuntimeException()
internal fun validationReject(code: FailureCode, message: String): Nothing =
    throw ValidationRejection(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
internal fun validationRequire(condition: Boolean, code: FailureCode = FailureCode.CORRUPT_DATA, message: String = "Invalid validation facts") {
    if (!condition) validationReject(code, message)
}
internal fun <T> validationValue(outcome: Outcome<T>): T = when (outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw ValidationRejection(outcome)
    is Outcome.Unavailable -> throw ValidationRejection(outcome)
    is Outcome.Unknown -> throw ValidationRejection(outcome)
}
internal fun <T> Knowledge<T>.validationKnown(): T? = (this as? Knowledge.Known)?.value
internal fun validationAdd(a: Long, b: Long): Long {
    validationRequire(a >= 0 && b >= 0 && b <= Long.MAX_VALUE - a, message = "Validation quantity overflow")
    return a + b
}

/** Visits all retained fields, including unknown reasons/evidence and unused face metadata.
 * Charges UTF-8 text, list slots and scalar/object overhead before retaining facts or reducing them.
 * No decimal stringification and no encoded copies are needed for this cumulative admission. */
internal class BasicValidationBounds(private val poll: () -> Unit, private val failureCode: FailureCode = FailureCode.CORRUPT_DATA) {
    private fun requireFact(condition: Boolean, message: String) = validationRequire(condition, failureCode, message)
    private var bytes = 0L
    fun text(value: String, maximum: Int = DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES) {
        poll()
        requireFact(value.length <= maximum && value.length.toLong() + 16 <= DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES - bytes,
            message = "Validation text budget exceeded")
        var index = 0
        var size = 0
        var nextCheck = 0
        while (index < value.length) {
            // A surrogate pair advances by two UTF-16 positions and may skip an exact modulus.
            if (index >= nextCheck) {
                poll()
                nextCheck = index + 1_024
            }
            val char = value[index++]
            size += when {
                char.isHighSurrogate() -> {
                    requireFact(index < value.length && value[index].isLowSurrogate(), message = "Invalid validation Unicode")
                    index++; 4
                }
                char.isLowSurrogate() -> validationReject(failureCode, "Invalid validation Unicode")
                char.code < 128 -> 1
                char.code < 2048 -> 2
                else -> 3
            }
            requireFact(size <= maximum, message = "Validation text budget exceeded")
        }
        charge(size.toLong() + 16)
    }
    fun id(value: String) {
        text(value, 128)
        requireFact(value.isNotBlank() && value.none { it.isISOControl() }, "Invalid source identifier")
    }
    fun catalogStatus(value: LocalCatalogStatus) {
        charge(); text(value.defaultLanguage.value, 128)
        knowledge(value.source) { id(it.value) }; knowledge(value.sourceDate) { charge() }; knowledge(value.fetchedAt) { charge() }
        list(value.installedLanguages, DeckStatisticsLimits.MAX_ENTRIES) { text(it.value, 128) }
        list(value.activeGenerations, DeckStatisticsLimits.MAX_ENTRIES) { generation ->
            charge(); id(generation.id.value); provenance(generation.provenance); text(generation.format)
            list(generation.languages, DeckStatisticsLimits.MAX_ENTRIES) { text(it.value, 128) }
        }
    }
    fun legality(value: CatalogFormatLegality) {
        charge(); oracle(value.oracleId); id(value.formatId.source.value)
        text(value.formatId.raw, 64)
        requireFact(value.formatId.raw.isNotEmpty() && value.formatId.raw.all { it in 'a'..'z' || it in '0'..'9' || it == '_' }, "Invalid format token")
        id(value.snapshot.source.value); value.snapshot.generation?.let { id(it.value) }
        value.snapshot.language?.let { text(it.value) }
        knowledge(value.status) { charge(4) }; provenance(value.provenance)
    }
    private fun charge(size: Long = 32) {
        poll()
        requireFact(size >= 0 && size <= DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES - bytes,
            message = "Validation aggregate facts budget exceeded")
        bytes += size
    }
    private fun <T> list(values: List<T>, maximum: Int = DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES / 8, visit: (T) -> Unit) {
        requireFact(values.size <= maximum, message = "Validation collection budget exceeded")
        charge(16L + values.size.toLong() * 8)
        for (value in values) { poll(); visit(value) }
    }
    private fun <T> knowledge(value: Knowledge<T>, visit: (T) -> Unit) {
        charge()
        when (value) {
            is Knowledge.Known -> visit(value.value)
            is Knowledge.Absent -> text(value.reason)
            is Knowledge.Unknown -> { text(value.reason); list(value.evidence, visit = ::evidence) }
        }
    }
    private fun evidence(value: Evidence) {
        charge(); text(value.description); value.source?.let { text(it.value) }; charge()
    }
    private fun oracle(value: OracleId) { charge(); id(value.source.value); id(value.raw) }
    private fun printing(value: CardId) { charge(); id(value.source.value); id(value.raw) }
    private fun faceIdentity(value: FaceIdentity) {
        charge(); oracle(value.oracleId); knowledge(value.providerFaceId) { id(it) }
    }
    fun provenance(value: Provenance) {
        charge(); id(value.provider.value); value.source?.let { text(it.value) }
        knowledge(value.version) { text(it) }; knowledge(value.sourceDate) { charge() }
        knowledge(value.effectiveAt) { charge() }; value.generation?.let { id(it.value) }
        value.checksum?.let { charge(); text(it.algorithm); text(it.hex) }
        value.notice?.let { notice ->
            charge(); id(notice.provider.value); text(notice.title); knowledge(notice.license) { text(it) }
            text(notice.attribution); notice.uri?.let { text(it.value) }
        }
    }
    private fun types(value: TypeLine) {
        charge(); text(value.original)
        list(value.supertypes, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        list(value.types, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        list(value.subtypes, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        requireFact(value.supertypes.size + value.types.size + value.subtypes.size <= DeckStatisticsLimits.MAX_TYPE_TOKENS,
            message = "Validation type token budget exceeded")
        requireFact(value.types.size == value.types.toSet().size, message = "Duplicate source card type")
    }
    private fun stats(value: CardStats) {
        charge(); knowledge(value.power) { text(it) }; knowledge(value.toughness) { text(it) }
        knowledge(value.loyalty) { text(it) }; knowledge(value.defense) { text(it) }
    }
    fun mana(value: java.math.BigDecimal) {
        poll()
        requireFact(value.signum() >= 0 && value.precision() <= DeckStatisticsLimits.MAX_MANA_PRECISION &&
            value.scale() in 0..DeckStatisticsLimits.MAX_MANA_SCALE, message = "Invalid source mana value")
        charge(64)
    }
    fun facts(value: CatalogDeckFacts) {
        charge()
        val card = value.card
        charge(); oracle(card.oracleId); text(card.name, 512); knowledge(card.originalText) { text(it) }
        knowledge(card.manaCost) { text(it, DeckStatisticsLimits.MAX_COST_BYTES) }; knowledge(card.manaValue, ::mana)
        knowledge(card.colors) { list(it) { charge(4) } }; knowledge(card.colorIdentity) { list(it) { charge(4) } }
        knowledge(card.types, ::types); stats(card.stats); knowledge(card.keywords) { list(it) { text(it) } }
        knowledge(card.layout) { text(it) }; knowledge(card.reserved) { charge(4) }
        text(card.normalizedSearchText); text(card.language.value)
        list(card.faces, DeckStatisticsLimits.MAX_FACES) { face ->
            charge(); faceIdentity(face.identity); text(face.name); knowledge(face.text) { text(it) }
            knowledge(face.manaCost) { text(it, DeckStatisticsLimits.MAX_COST_BYTES) }
            knowledge(face.types, ::types); stats(face.stats)
            list(face.relationships) { relationship ->
                charge(); knowledge(relationship.relatedOracle, ::oracle)
                knowledge(relationship.relatedFace, ::faceIdentity); list(relationship.evidence, visit = ::evidence)
            }
        }
        list(card.providerIdentities) { identity -> charge(); id(identity.provider.value); text(identity.kind, 128); id(identity.rawId) }
        provenance(card.provenance); charge()
        id(value.snapshot.source.value); value.snapshot.generation?.let { id(it.value) }
        value.snapshot.language?.let { text(it.value) }; value.printingId?.let(::printing)
        knowledge(value.reportedProduction) { list(it) { text(it) } }
        knowledge(value.reportedParts) { parts -> list(parts, DeckStatisticsLimits.MAX_RELATED_PARTS) { part ->
            charge(); printing(part.printingId); text(part.component)
            knowledge(part.name) { text(it) }; knowledge(part.typeLine) { text(it) }
        } }
    }
}
