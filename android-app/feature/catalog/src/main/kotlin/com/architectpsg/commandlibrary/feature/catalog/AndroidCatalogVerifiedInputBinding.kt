package com.architectpsg.commandlibrary.feature.catalog

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** Binds a completed English acquisition to the importer while its private locator is pinned.
 * Source metadata may survive the callback; the URI can be read only during a new trusted pin.
 */
internal class AndroidCatalogVerifiedInputBinding(context: Context, private val engine: LocalAcquisition) : CatalogVerifiedInputBinding {
    private val resolver = context.applicationContext.contentResolver
    private val locator = AndroidVerifiedArtifactLocator(context, engine)

    internal suspend fun catalogSource(jobId: EntityId, cancel: CancelToken): Outcome<CatalogBulkImportSource> = try {
        Outcome.Success(locator.withPinnedArtifact(jobId, cancel) { uri, artifact, _ ->
            val current = sourceFromPinnedStatus(uri, artifact, jobId, cancel).first
            currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
            try {
                val descriptor = resolver.openFileDescriptor(Uri.parse(uri.value), "r") ?: throw IOException("Private locator unavailable")
                descriptor.use {
                    ParcelFileDescriptor.AutoCloseInputStream(it).use { input ->
                        if (input.read(ByteArray(4096)) <= 0) throw IOException("Empty private locator")
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw invalid(FailureCode.CORRUPT_DATA) }
            currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
            current
        })
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (invalid: ArtifactReadException) { Outcome.Failure(invalid.failure) }
    catch (_: Exception) { Outcome.Failure(failure(FailureCode.CORRUPT_DATA)) }

    override suspend fun withPinnedInput(
        source: CatalogBulkImportSource,
        cancel: CancelToken,
        consume: suspend (Flow<ByteChunk>, EntityId) -> CatalogBulkImportResult,
    ): CatalogBulkImportResult {
        if (source.inputKind != SourceInputKind.VERIFIED_ARTIFACT) throw invalid(FailureCode.SOURCE_MISMATCH)
        val claimed = source.verifiedArtifact ?: throw invalid(FailureCode.SOURCE_MISMATCH)
        return locator.withPinnedArtifact(claimed.jobId, cancel) { uri, artifact, verifiedChunks ->
            val (current, consentId) = sourceFromPinnedStatus(uri, artifact, claimed.jobId, cancel)
            if (artifact != claimed || source != current) throw invalid(FailureCode.SOURCE_MISMATCH)
            consume(verifiedChunks, consentId)
        }
    }

    /** Shared construction also supplies the exact consent from the same durable completed row. */
    private suspend fun sourceFromPinnedStatus(
        uri: ResourceUri, artifact: VerifiedArtifact, jobId: EntityId, cancel: CancelToken,
    ): Pair<CatalogBulkImportSource, EntityId> {
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
        val snapshot = when (val status = engine.status(jobId, cancel)) {
            is Outcome.Success -> status.value
            is Outcome.Failure -> throw ArtifactReadException(status.error)
            else -> throw invalid(FailureCode.CORRUPT_DATA)
        }
        if (snapshot.state != AcquisitionState.COMPLETED || snapshot.phase != AcquisitionPhase.COMPLETE || snapshot.plan.jobId != jobId)
            throw invalid(FailureCode.CORRUPT_DATA)
        val consentId = snapshot.consentId ?: throw invalid(FailureCode.CORRUPT_DATA)
        val durable = (snapshot.artifact as? Knowledge.Known<*>)?.value as? VerifiedArtifact
            ?: throw invalid(FailureCode.CORRUPT_DATA)
        val request = snapshot.plan.request
        if (durable != artifact || artifact.jobId != jobId || artifact.bytes <= 0 ||
            request.intent.kind != DownloadKind.CATALOG || request.intent.source != ProviderId("scryfall") ||
            request.intent.language != LanguageTag.ENGLISH || request.languageScope != AcquisitionLanguageScope.ENGLISH_CARDS ||
            request.source !is AcquisitionSource.EnglishSearch || TrustedEnglishSearchProfile.validate(request) !is Outcome.Success ||
            artifact.framing !in setOf(ArtifactFraming.JSONL, ArtifactFraming.JSON_ARRAY) || artifact.compression != ArtifactCompression.NONE ||
            artifact.provenance.provider != request.intent.source || artifact.provenance.requestedUri != request.intent.resource ||
            artifact.provenance.languageScope != request.languageScope || artifact.provenance.sourceVersion != request.sourceVersion ||
            artifact.provenance.sourceDate != request.sourceDate || artifact.provenance.notice != request.notice)
            throw invalid(FailureCode.SOURCE_MISMATCH)
        val source = try {
            CatalogBulkImportSource(uri, request.displayName, request.intent.source, LanguageTag.ENGLISH,
                artifact.provenance.sourceDate, Knowledge.Known(artifact.provenance.requestedUri),
                Knowledge.Known(artifact.sha256), Knowledge.Known(artifact.bytes), artifact.provenance.notice,
                artifact.framing, artifact.compression, SourceInputKind.VERIFIED_ARTIFACT, artifact)
        } catch (_: IllegalArgumentException) { throw invalid(FailureCode.SOURCE_MISMATCH) }
        return source to consentId
    }

    private fun invalid(code: FailureCode) = ArtifactReadException(failure(code))
    private fun failure(code: FailureCode) = DomainFailure(code, "Verified catalog source unavailable", SnapshotList.empty())
}
