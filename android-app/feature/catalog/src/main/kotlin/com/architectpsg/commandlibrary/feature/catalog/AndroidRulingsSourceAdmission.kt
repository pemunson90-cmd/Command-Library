package com.architectpsg.commandlibrary.feature.catalog

import android.content.Context
import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Issues authority only from a completed fixed-endpoint job, pinned through durable receipt ACK. */
internal class AndroidRulingsSourceAdmission(
    context: Context, private val engine: LocalAcquisition, private val store: RulingsReceiptStore,
    private val profiles: TrustedCatalogProfiles, private val clock: Clock, private val ids: IdSource,
) {
    private val locator = AndroidVerifiedArtifactLocator(context, engine)
    private val admission = Mutex()

    suspend fun fileRequest(metadataJobId: EntityId, displayName: String, wifiOnly: Boolean, cancel: CancelToken): Outcome<AcquisitionRequest> =
        admission.withLock {
            try {
                currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
                rulingInputIdentity(metadataJobId)
                require(displayName.isNotBlank()); BoundedRulingsJson.bytes(displayName, 512)
                // Existing validated authority outlives retention of its original job/artifact.
                val receipt = store.byMetadataJob(metadataJobId, cancel).bulkValue() ?: locator.withPinnedArtifact(metadataJobId, cancel) { _, artifact, chunks ->
                    val snapshot = completedRulingSnapshot(engine, metadataJobId, artifact, cancel)
                    require(TrustedRulingsProfiles.isDiscovery(snapshot.plan.request))
                    val maximum = RulingsSourceReceiptCodec.MAX_METADATA_BYTES
                    require(artifact.framing == ArtifactFraming.TEXT && artifact.compression == ArtifactCompression.NONE && artifact.bytes in 1..maximum.toLong())
                    val output = ByteArrayOutputStream(minOf(artifact.bytes.toInt(), 64 * 1024))
                    val proof = FullCatalogDecodedInput(ArtifactDecodeLimits(maximum.toLong(), maximum.toLong(), 64 * 1024), cancel)
                        .consume(ArtifactCompression.NONE, chunks) { chunk -> output.write(chunk.bytes.copyBytes()) }
                    require(proof.rawBytes == artifact.bytes && proof.rawHash == artifact.sha256)
                    val body = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(output.toByteArray())).toString()
                    val value = RulingsSourceReceipt(EntityId(ids.nextId()), snapshot.plan, artifact, requireNotNull(snapshot.consentId), clock.utcNow(), body)
                    store.save(value, cancel).bulkValue()
                }
                // No publication on failed, cancelled or inconclusive persistence. A proven late ACK is durable Success.
                profiles.publishVerified(receipt.facts())
                Outcome.Success(TrustedRulingsProfiles.file(receipt.facts(), displayName, wifiOnly))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: BulkFailure) { failure.outcome }
            catch (failure: ArtifactReadException) { Outcome.Failure(bulkError(failure.failure.code, "Ruling metadata input unavailable")) }
            catch (_: Exception) { Outcome.Failure(bulkError(FailureCode.SOURCE_MISMATCH, "Ruling metadata admission failed")) }
        }
}

/** The same engine/locator owns the full file pin, import and bounded activation acknowledgement. */
internal class AndroidRulingsVerifiedInputBinding(
    context: Context, private val engine: LocalAcquisition, private val store: RulingsReceiptStore,
    private val profiles: TrustedCatalogProfiles,
) : RulingsVerifiedInputBinding {
    private val locator = AndroidVerifiedArtifactLocator(context, engine)
    override suspend fun withPinnedInput(jobId: EntityId, cancel: CancelToken,
        consume: suspend (VerifiedArtifact, EntityId, kotlinx.coroutines.flow.Flow<ByteChunk>, RulingsTrustedInputProof?) -> RulingsImportResult): RulingsImportResult {
        rulingInputIdentity(jobId)
        return locator.withPinnedArtifact(jobId, cancel) { _, artifact, chunks ->
            val snapshot = completedRulingSnapshot(engine, jobId, artifact, cancel)
            val request = snapshot.plan.request
            val receiptId = TrustedRulingsProfiles.receiptId(request) ?: throw rulingSourceMismatch()
            val receipt = store.byId(receiptId, cancel).bulkValue() ?: throw rulingSourceMismatch()
            if(!profiles.matchesVerified(receipt.facts()) || profiles.validate(request) !is Outcome.Success) throw rulingSourceMismatch()
            val proof = try { RulingsTrustedInputProof(receipt, snapshot.plan).also { it.validate(artifact, requireNotNull(snapshot.consentId)) } }
                catch (_: IllegalArgumentException) { throw rulingSourceMismatch() }
            consume(artifact, requireNotNull(snapshot.consentId), chunks, proof)
        }
    }
}

private suspend fun completedRulingSnapshot(engine: LocalAcquisition, jobId: EntityId, artifact: VerifiedArtifact, cancel: CancelToken): AcquisitionSnapshot {
    currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
    val value = engine.status(jobId, cancel).bulkValue()
    if(value.state != AcquisitionState.COMPLETED || value.phase != AcquisitionPhase.COMPLETE || value.plan.jobId != jobId ||
        value.consentId == null || (value.artifact as? Knowledge.Known)?.value != artifact || artifact.jobId != jobId)
        throw rulingSourceMismatch()
    return value
}
private fun rulingInputIdentity(value: EntityId) {
    BoundedRulingsJson.bytes(value.value, 128); require(value.value.isNotBlank() && '\u0000' !in value.value)
}
private fun rulingSourceMismatch() = ArtifactReadException(bulkError(FailureCode.SOURCE_MISMATCH, "Verified ruling source unavailable"))
