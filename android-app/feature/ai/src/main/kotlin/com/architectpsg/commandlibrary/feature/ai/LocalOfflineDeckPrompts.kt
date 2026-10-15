package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CancellationException

/** Provider-free read-only prompts and exact-name review using the actual injected catalog port. */
public class LocalOfflineDeckPrompts(
    query: LocalCardQuery,
    private val clock: Clock,
    private val validate: (DeckSnapshot, CancelToken) -> Outcome<Unit>,
) : OfflineDeckPrompts {
    private val references = OfflineReplyReferences(query)
    private val shipped: SnapshotList<PromptTemplate> by lazy { OfflinePromptText.templates() }

    public override fun templates(): SnapshotList<PromptTemplate> = shipped

    public override suspend fun render(request: OfflinePromptRequest, cancel: CancelToken): Outcome<OfflinePromptResult> = guarded(cancel) {
        OfflinePromptText.utf8Size(request.question, OfflinePromptText.USER_BYTES, cancel)
        OfflinePromptText.utf8Size(request.constraints, OfflinePromptText.USER_BYTES, cancel)
        request.overrideBody?.let { OfflinePromptText.utf8Size(it, OfflinePromptText.USER_BYTES, cancel) }
        OfflineReplyReferences.preflight(request.snapshot, cancel)
        when (val valid = validateDeck(request.deck, cancel)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return@guarded valid
            is Outcome.Unavailable -> return@guarded valid
            is Outcome.Unknown -> return@guarded valid
        }
        val template = shipped.single { it.id == request.mode.templateId && it.shippedVersion == request.mode.shippedVersion }
        val body = request.overrideBody ?: template.body
        // Validate the complete template before optional I/O. This pass substitutes empty data only.
        OfflinePromptText.bind(body, mapOf("deck" to "", "constraints" to "", "request" to ""), cancel)
        val mandatory = OfflineDeckContext.mandatory(request, cancel)
        val bodyHash = OfflinePromptText.hash(body)
        val overrideHash = if (request.overrideBody == null) null else bodyHash.hex
        val base = OfflinePromptText.Json(OfflinePromptText.DECK_BYTES, cancel).apply {
            raw("{\"deck\":").raw(mandatory)
            raw(",\"mode\":").string(request.mode.name).raw(",\"intensity\":").string(request.intensity.name)
            raw(",\"includePrivateNotes\":${request.includePrivateNotes},\"request\":").string(request.question)
            raw(",\"constraints\":").string(request.constraints).raw(",\"snapshot\":").snapshot(request.snapshot)
            raw(",\"templateId\":").string(template.id.value).raw(",\"shippedVersion\":${template.shippedVersion},\"bodySHA256\":").string(bodyHash.hex)
            raw(",\"overrideBodySHA256\":"); if (overrideHash == null) raw("null") else string(overrideHash)
            raw("}")
        }.toString()
        val enriched = OfflineDeckContext.enrich(request, references, cancel)
        val context = OfflinePromptText.Json(OfflinePromptText.DECK_BYTES + OfflinePromptText.OPTIONAL_BYTES + 8192, cancel).apply {
            raw("{\"mandatory\":").raw(base).raw(",\"originalOracleContexts\":").raw(enriched.text)
            raw(",\"oracleContextsIncluded\":${enriched.included},\"oracleContextsOmitted\":${enriched.omitted}")
            raw(",\"omissionDisclosure\":").string("Each deck entry absent from originalOracleContexts by entryId has omitted optional Oracle context; its complete mandatory identity and quantity remain above.")
            raw(",\"checks\":{\"formatLegality\":\"unknown\",\"colorIdentity\":\"unknown\",\"budget\":\"unknown\",\"ownership\":\"unknown\",\"acquisitionCost\":\"unknown\"}}")
        }.toString()
        val hash = OfflinePromptText.hash(context)
        val metadata = OfflinePromptText.Json(16384, cancel).apply {
            raw("{\"mode\":").string(request.mode.name).raw(",\"templateId\":").string(template.id.value)
            raw(",\"shippedVersion\":${template.shippedVersion},\"overrideBodySHA256\":")
            if (overrideHash == null) raw("null") else string(overrideHash)
            raw(",\"deckId\":{\"source\":").string(request.deck.id.source.value).raw(",\"raw\":").string(request.deck.id.raw)
            raw("},\"deckRevision\":${request.deck.revision.value},\"snapshot\":").snapshot(request.snapshot)
            raw(",\"oracleContextsIncluded\":${enriched.included},\"oracleContextsOmitted\":${enriched.omitted},\"contextSHA256\":").string(hash.hex).raw("}")
        }.toString()
        val bindings = linkedMapOf("deck" to context, "constraints" to OfflinePromptText.json(request.constraints, cancel),
            "request" to OfflinePromptText.json(request.question, cancel))
        val text = "Offline deck prompt metadata:\n$metadata\n\n" + OfflinePromptText.bind(body, bindings, cancel)
        offlineCancellation(cancel)
        Outcome.Success(OfflinePromptResult(PromptArtifact(template.id, template.shippedVersion, request.deck.id, request.deck.revision,
            text, SnapshotList.copyOf(bindings.map { PromptBinding(it.key, it.value) }), hash, clock.utcNow()), request.mode,
            template, request.overrideBody != null, request.snapshot, enriched.included, enriched.omitted))
    }

    public override suspend fun review(deck: DeckSnapshot, snapshot: SnapshotReference?, reply: String, cancel: CancelToken): Outcome<OfflineReplyReview> = guarded(cancel) {
        // All syntax and length validation precedes any catalog call.
        val names = references.parse(reply, cancel)
        OfflineReplyReferences.preflight(snapshot, cancel)
        when (val valid = validateDeck(deck, cancel)) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> return@guarded valid
            is Outcome.Unavailable -> return@guarded valid
            is Outcome.Unknown -> return@guarded valid
        }
        val cache = LinkedHashMap<String, OfflineCardReference>()
        val reviewed = ArrayList<OfflineCardReference>(names.size)
        for (name in names) {
            offlineCancellation(cancel)
            val reference = cache[name] ?: references.lookup(name, snapshot, cancel).let { lookup ->
                OfflineCardReference(name, lookup.status, snapshot, SnapshotList.copyOf(lookup.cards.map { it.oracleId }),
                    if (lookup.status == OfflineReferenceStatus.FOUND) Knowledge.Known(lookup.cards.single().name)
                    else Knowledge.Unknown("No unique verified original card", SnapshotList.empty()))
            }.also { cache[name] = it }
            reviewed.add(reference)
        }
        offlineCancellation(cancel)
        val unknown = Knowledge.Unknown("No named validation, inventory or pricing producer is connected", SnapshotList.empty())
        Outcome.Success(OfflineReplyReview(deck.id, deck.revision, snapshot, reply, SnapshotList.copyOf(reviewed), references.angle(reply),
            LegalityStatus.UNKNOWN, unknown, unknown, unknown, unknown))
    }

    public override suspend fun card(reference: OfflineCardReference, snapshot: SnapshotReference, cancel: CancelToken): Outcome<CardRecord> = guarded(cancel) {
        // Compare the originating frame before lookup, including callers that forge a reference.
        OfflineReplyReferences.preflight(snapshot, cancel)
        OfflineReplyReferences.preflight(reference.snapshot, cancel)
        if (reference.snapshot != snapshot) return@guarded failed(FailureCode.STALE_GENERATION, "Card reference belongs to a different snapshot")
        if (reference.status != OfflineReferenceStatus.FOUND || reference.candidates.size != 1 || !OfflineReplyReferences.supported(snapshot))
            return@guarded unavailable()
        OfflinePromptText.utf8Size(reference.rawName, 512, cancel)
        OfflinePromptText.utf8Size(reference.candidates.single().raw, 4096, cancel)
        OfflinePromptText.utf8Size(reference.candidates.single().source.value, 128, cancel)
        if (reference.candidates.single().source != snapshot.source) return@guarded unavailable()
        val lookup = references.lookup(reference.rawName, snapshot, cancel)
        val card = lookup.cards.singleOrNull()
        if (lookup.status != OfflineReferenceStatus.FOUND || card == null || card.oracleId != reference.candidates.single()) return@guarded unavailable()
        offlineCancellation(cancel)
        Outcome.Success(card)
    }

    private fun validateDeck(deck: DeckSnapshot, cancel: CancelToken): Outcome<Unit> {
        if (deck.entries.size > 10000) throw OfflineInputException()
        return when (val result = validate(deck, cancel)) {
            is Outcome.Success -> result
            is Outcome.Failure -> failed(result.error.code, "Deck structural validation failed")
            is Outcome.Unavailable -> Outcome.Unavailable(result.reason, "Deck structural validation unavailable")
            is Outcome.Unknown -> Outcome.Unknown("Deck structural validation unknown", SnapshotList.empty())
        }
    }

    private suspend fun <T> guarded(cancel: CancelToken, action: suspend () -> Outcome<T>): Outcome<T> {
        offlineCancellation(cancel)
        return try {
            val result = action()
            offlineCancellation(cancel)
            result
        } catch (error: CancellationException) { throw error }
        catch (_: OfflineInputException) { offlineCancellation(cancel); failed(FailureCode.INVALID_INPUT, "Offline prompt input exceeds limits or is malformed") }
        catch (_: Exception) { offlineCancellation(cancel); failed(FailureCode.INTERNAL, "Offline prompt operation failed") }
    }

    private fun failed(code: FailureCode, message: String): Outcome.Failure = Outcome.Failure(DomainFailure(code, message, SnapshotList.empty()))
    private fun unavailable(): Outcome.Unavailable = Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Original card cannot be verified at the selected snapshot")
}
