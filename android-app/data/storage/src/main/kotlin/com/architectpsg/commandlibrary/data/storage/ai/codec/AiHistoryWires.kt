package com.architectpsg.commandlibrary.data.storage.ai.codec

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal

internal val entityWire = wire<EntityId>({ string(it.value) }, { EntityId(string()) })
private val providerWire = wire<ProviderId>({ string(it.value) }, { ProviderId(string()) })
private val revisionWire = wire<Revision>({ long(it.value) }, { Revision(long()) })
private val timestampWire = wire<UtcTimestamp>({ long(it.epochSeconds); int(it.nanoAdjustment) }, { UtcTimestamp(long(), int()) })
private val uriWire = wire<ResourceUri>({ string(it.value) }, { ResourceUri(string()) })
private val hashWire = wire<ContentHash>({ checkHash(it); string(it.algorithm); string(it.hex) }, {
    ContentHash(string(), string()).also(::checkHash)
})
private fun checkHash(value: ContentHash) {
    require(value.algorithm == "SHA-256" && value.hex.length == 64 && value.hex.all { it in "0123456789abcdef" })
}
private val evidenceWire = wire<Evidence>({ string(it.description); v(nullable(uriWire), it.source); v(nullable(timestampWire), it.recordedAt) },
    { Evidence(string(), v(nullable(uriWire)), v(nullable(timestampWire))) })
private fun <T> knowledge(codec: AiWire<T>): AiWire<Knowledge<T>> = wire({ value ->
    when (value) {
        is Knowledge.Known -> { byte(1); v(codec, value.value) }
        is Knowledge.Absent -> { byte(2); string(value.reason) }
        is Knowledge.Unknown -> { byte(3); string(value.reason); v(list(evidenceWire), value.evidence) }
    }
}, {
    when (byte()) {
        1 -> Knowledge.Known(v(codec))
        2 -> Knowledge.Absent(string())
        3 -> Knowledge.Unknown(string(), v(list(evidenceWire)))
        else -> badTag()
    }
})
private val countWire = wire<Long>({ require(it >= 0); long(it) }, { long().also { require(it >= 0) } })
private val unitWire = wire<DecimalUnit>({ value ->
    when (value) {
        is DecimalUnit.Fiat -> { byte(1); string(value.currencyCode) }
        DecimalUnit.Tix -> byte(2)
        is DecimalUnit.Native -> { byte(3); v(providerWire, value.provider); string(value.symbol) }
    }
}, {
    when (byte()) {
        1 -> DecimalUnit.Fiat(string())
        2 -> DecimalUnit.Tix
        3 -> DecimalUnit.Native(v(providerWire), string())
        else -> badTag()
    }
})
private fun checkAmount(value: DecimalAmount) {
    require(value.value.signum() >= 0 && value.scale in 0..1024 && value.precision in 1..1024)
}
private val amountWire = wire<DecimalAmount>({
    checkAmount(it)
    string(it.value.toString()); v(unitWire, it.unit); int(it.scale); int(it.precision)
}, {
    val text = string()
    // Bounded decimal text prevents unbounded parsing; scale/precision precede use.
    // BigDecimal retains the exact representation without floating point conversion.
    val value = BigDecimal(text)
    require(value.scale() in 0..1024 && value.precision() in 1..1024)
    DecimalAmount(value, v(unitWire), int(), int()).also(::checkAmount)
})
private val usageWire = wire<Usage>({
    v(knowledge(countWire), it.inputTokens); v(knowledge(countWire), it.outputTokens)
    v(knowledge(countWire), it.cachedInputTokens); v(knowledge(amountWire), it.billedAmount)
    v(knowledge(textWire), it.providerRequestId)
}, { Usage(v(knowledge(countWire)), v(knowledge(countWire)), v(knowledge(countWire)), v(knowledge(amountWire)), v(knowledge(textWire))) })
private val snapshotWire = wire<SnapshotReference>({
    v(providerWire, it.source); v(nullable(textWire), it.generation?.value); v(nullable(revisionWire), it.revision)
    v(nullable(textWire), it.language?.value)
}, { SnapshotReference(v(providerWire), v(nullable(textWire))?.let(::GenerationId), v(nullable(revisionWire)), v(nullable(textWire))?.let(::LanguageTag)) })
private val provenanceWire = wire<HistoryPromptProvenance>({
    v(hashWire, it.effectiveTemplateHash); bool(it.overrideUsed); v(nullable(snapshotWire), it.snapshot)
}, { HistoryPromptProvenance(v(hashWire), bool(), v(nullable(snapshotWire))) })
private val bindingWire = wire<PromptBinding>({ string(it.name); string(it.value, AiHistoryLimits.MAX_PROMPT_BYTES, binding = true) },
    { PromptBinding(string(), string(AiHistoryLimits.MAX_PROMPT_BYTES, binding = true)) })
private val promptWire = wire<PromptArtifact>({
    require(it.templateVersion > 0)
    v(entityWire, it.templateId); int(it.templateVersion); v(providerWire, it.deckId.source); string(it.deckId.raw)
    v(revisionWire, it.deckRevision); string(it.rendered, AiHistoryLimits.MAX_PROMPT_BYTES)
    v(list(bindingWire, AiHistoryLimits.MAX_BINDINGS), it.bindings); v(hashWire, it.contextHash); v(timestampWire, it.createdAt)
}, {
    val template = v(entityWire); val version = int().also { require(it > 0) }
    PromptArtifact(template, version, DeckId(v(providerWire), string()), v(revisionWire), string(AiHistoryLimits.MAX_PROMPT_BYTES),
        v(list(bindingWire, AiHistoryLimits.MAX_BINDINGS)), v(hashWire), v(timestampWire))
})

/** Only immutable identity and content; SQL-owned state is supplied explicitly at decode. */
internal data class AiHistoryContent(
    val id: EntityId, val conversationId: EntityId, val source: HistoryTurnSource,
    val prompt: PromptArtifact, val provenance: HistoryPromptProvenance, val mode: OfflineLabMode,
    val providerProfileId: EntityId?, val modelId: String?, val retryOf: EntityId?,
    val rawContent: String, val usage: Knowledge<Usage>,
)
internal val contentWire = wire<AiHistoryContent>({
    v(entityWire, it.id); v(entityWire, it.conversationId); v(enumeration<HistoryTurnSource>(), it.source)
    v(promptWire, it.prompt); v(provenanceWire, it.provenance); v(enumeration<OfflineLabMode>(), it.mode)
    v(nullable(entityWire), it.providerProfileId); v(nullable(textWire), it.modelId); v(nullable(entityWire), it.retryOf)
    string(it.rawContent, AiHistoryLimits.MAX_REPLY_BYTES); v(knowledge(usageWire), it.usage)
}, {
    AiHistoryContent(v(entityWire), v(entityWire), v(enumeration<HistoryTurnSource>()), v(promptWire), v(provenanceWire),
        v(enumeration<OfflineLabMode>()), v(nullable(entityWire)), v(nullable(textWire)), v(nullable(entityWire)),
        string(AiHistoryLimits.MAX_REPLY_BYTES), v(knowledge(usageWire)))
})
