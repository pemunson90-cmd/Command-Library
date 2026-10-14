package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject

/** Structural consistency only; the Android binding establishes actual durable issuance. */
internal data class RulingsTrustedInputProof(val receipt: RulingsSourceReceipt, val filePlan: DownloadPlan) {
    init {
        require(filePlan.request == TrustedRulingsProfiles.file(receipt.facts(), filePlan.request.displayName, filePlan.request.wifiOnly))
        val zero = filePlan.copy(planHash = ContentHash("SHA-256", "0".repeat(64)))
        require(filePlan.planHash == receiptHash(AcquisitionPayloadCodec.encodePlan(zero).toByteArray()))
        BoundedRulingsJson.bytes(filePlan.jobId.value, 128)
        require('\u0000' !in filePlan.jobId.value && filePlan.jobId != receipt.metadataPlan.jobId)
    }
    fun validate(artifact: VerifiedArtifact, consentId: EntityId) {
        BoundedRulingsJson.bytes(consentId.value, 128)
        require(consentId.value.isNotBlank() && '\u0000' !in consentId.value && consentId != receipt.metadataConsentId)
        val r = filePlan.request; val source = artifact.provenance
        require(artifact.jobId == filePlan.jobId && artifact.framing == ArtifactFraming.JSONL && artifact.compression == ArtifactCompression.GZIP)
        require(artifact.bytes == receipt.candidate.compressedBytes && artifact.pageCount == 0L && !artifact.upstreamChecksumMatched)
        require(source.provider == r.intent.source && source.requestedUri == r.intent.resource && source.finalUri == source.requestedUri)
        require(source.languageScope == r.languageScope && source.consistency == AcquisitionConsistency.FILE_REPRESENTATION)
        require(source.sourceVersion == r.sourceVersion && source.sourceDate == r.sourceDate && source.notice == r.notice)
    }
}

internal data class RulingsTrustedInstallation(val installation: StoredRulingsInstallation, val proof: RulingsTrustedInputProof) {
    init { proof.validate(installation.result.acquiredArtifact, installation.consentId) }
}

/** Dedicated bound: no widening of legacy acquisition or ruling payloads. Objects are embedded once. */
internal object RulingsTrustedInstallationCodec {
    const val MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_COMPONENT_BYTES = 128 * 1024
    fun encode(value: RulingsTrustedInstallation): String = guarded {
        val root = JsonObject().apply {
            addProperty("version", 3); addProperty("type", "trustedRulingsInstallation")
            add("receipt", RulingsSourceReceiptCodec.json(value.proof.receipt))
            add("filePlan", BoundedRulingsJson.parse(AcquisitionPayloadCodec.encodePlan(value.proof.filePlan), MAX_COMPONENT_BYTES))
            add("installation", BoundedRulingsJson.parse(AcquisitionPayloadCodec.encodeRulingsInstallation(value.installation.result, value.installation.consentId), MAX_COMPONENT_BYTES))
        }
        BoundedRulingsJson.encode(root, MAX_BYTES).also { decode(it) }
    }
    fun decode(payload: String): RulingsTrustedInstallation = guarded {
        val root = BoundedRulingsJson.fields(BoundedRulingsJson.parse(payload, MAX_BYTES), "version", "type", "receipt", "filePlan", "installation")
        require(BoundedRulingsJson.number(root["version"]) == 3L && BoundedRulingsJson.text(root["type"]) == "trustedRulingsInstallation")
        val receipt = RulingsSourceReceiptCodec.read(root.getAsJsonObject("receipt"))
        val plan = AcquisitionPayloadCodec.decodePlan(BoundedRulingsJson.encode(root.getAsJsonObject("filePlan"), MAX_COMPONENT_BYTES))
        val installed = AcquisitionPayloadCodec.decodeRulingsInstallation(BoundedRulingsJson.encode(root.getAsJsonObject("installation"), MAX_COMPONENT_BYTES))
        RulingsTrustedInstallation(installed, RulingsTrustedInputProof(receipt, plan))
    }
    private inline fun <T> guarded(action: () -> T): T = try { action() }
        catch (_: Exception) { throw IllegalArgumentException("Invalid trusted ruling installation") }
}
