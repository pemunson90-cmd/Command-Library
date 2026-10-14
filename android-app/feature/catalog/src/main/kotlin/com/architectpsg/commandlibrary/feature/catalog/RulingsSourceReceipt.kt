package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import java.security.MessageDigest

/** Validated data only. Live wire authority still requires verified durable issuance/publication. */
internal data class RulingsSourceReceipt(
    val id: EntityId,
    val metadataPlan: DownloadPlan,
    val metadataArtifact: VerifiedArtifact,
    val metadataConsentId: EntityId,
    val issuedAt: UtcTimestamp,
    val metadataBody: String,
) {
    val candidate: ScryfallRulingsCandidate
    init {
        TrustedRulingsProfiles.identity(id.value)
        BoundedRulingsJson.bytes(metadataPlan.jobId.value, 128)
        require('\u0000' !in metadataPlan.jobId.value)
        require(metadataConsentId.value.isNotBlank() && '\u0000' !in metadataConsentId.value && metadataConsentId.value.toByteArray().size <= 128)
        require(TrustedRulingsProfiles.isDiscovery(metadataPlan.request))
        val unhashed = metadataPlan.copy(planHash = ContentHash("SHA-256", "0".repeat(64)))
        require(metadataPlan.planHash == receiptHash(AcquisitionPayloadCodec.encodePlan(unhashed).toByteArray()))
        val artifact = metadataArtifact
        require(artifact.jobId == metadataPlan.jobId && artifact.framing == ArtifactFraming.TEXT && artifact.compression == ArtifactCompression.NONE)
        require(artifact.pageCount == 0L && !artifact.upstreamChecksumMatched)
        val source = artifact.provenance
        require(source.provider == ProviderId("scryfall") && source.consistency == AcquisitionConsistency.FILE_REPRESENTATION)
        require(source.requestedUri == metadataPlan.request.intent.resource && source.finalUri == source.requestedUri)
        require(source.languageScope == AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED)
        require(source.sourceVersion == metadataPlan.request.sourceVersion && source.sourceDate == metadataPlan.request.sourceDate && source.notice == metadataPlan.request.notice)
        val bytes = BoundedRulingsJson.bytes(metadataBody, RulingsSourceReceiptCodec.MAX_METADATA_BYTES)
        require(bytes > 0 && artifact.bytes == bytes.toLong())
        require(artifact.sha256.hex.equals(receiptHash(metadataBody.toByteArray()).hex, true))
        candidate = ScryfallRulingsMetadataCodec.decode(metadataBody)
    }
    fun facts() = RulingsProfileFacts(id, candidate, metadataArtifact.sha256)
}

internal object RulingsSourceReceiptCodec {
    const val MAX_METADATA_BYTES = 1024 * 1024
    const val MAX_BYTES = 2 * 1024 * 1024 + 128 * 1024
    private const val MAX_COMPONENT_BYTES = 64 * 1024
    fun encode(value: RulingsSourceReceipt): String = guarded {
        BoundedRulingsJson.encode(json(value), MAX_BYTES).also { decode(it) }
    }
    fun decode(payload: String): RulingsSourceReceipt = guarded { read(BoundedRulingsJson.parse(payload, MAX_BYTES)) }
    fun json(value: RulingsSourceReceipt): JsonObject = JsonObject().apply {
        addProperty("version", 1); addProperty("type", "scryfallRulingsReceipt")
        addProperty("id", value.id.value); addProperty("consentId", value.metadataConsentId.value)
        addProperty("issuedSeconds", value.issuedAt.epochSeconds); addProperty("issuedNanos", value.issuedAt.nanoAdjustment)
        addProperty("metadataBody", value.metadataBody)
        add("plan", component(AcquisitionPayloadCodec.encodePlan(value.metadataPlan)))
        add("artifact", component(AcquisitionPayloadCodec.encodeArtifact(value.metadataArtifact)))
    }
    fun read(root: JsonObject): RulingsSourceReceipt {
        BoundedRulingsJson.encode(root, MAX_BYTES)
        BoundedRulingsJson.fields(root, "version", "type", "id", "consentId", "issuedSeconds", "issuedNanos", "metadataBody", "plan", "artifact")
        require(BoundedRulingsJson.number(root["version"]) == 1L && BoundedRulingsJson.text(root["type"]) == "scryfallRulingsReceipt")
        val plan = AcquisitionPayloadCodec.decodePlan(BoundedRulingsJson.encode(root.getAsJsonObject("plan"), MAX_COMPONENT_BYTES))
        val artifact = AcquisitionPayloadCodec.decodeArtifact(BoundedRulingsJson.encode(root.getAsJsonObject("artifact"), MAX_COMPONENT_BYTES))
        val nanos = BoundedRulingsJson.number(root["issuedNanos"]).also { require(it in 0..999_999_999) }.toInt()
        return RulingsSourceReceipt(EntityId(BoundedRulingsJson.text(root["id"])), plan, artifact, EntityId(BoundedRulingsJson.text(root["consentId"])),
            UtcTimestamp(BoundedRulingsJson.number(root["issuedSeconds"]), nanos), BoundedRulingsJson.text(root["metadataBody"]))
    }
    private fun component(payload: String) = BoundedRulingsJson.parse(payload, MAX_COMPONENT_BYTES)
    private inline fun <T> guarded(action: () -> T): T = try { action() }
        catch (_: Exception) { throw IllegalArgumentException("Invalid ruling source receipt") }
}

internal fun receiptHash(bytes: ByteArray) = ContentHash("SHA-256", MessageDigest.getInstance("SHA-256").digest(bytes).hex())
