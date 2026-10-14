package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal val RULINGS_UI_ORACLE = OracleId(ProviderId("scryfall"), "00000000-0000-0000-0000-000000000001")
internal val RULINGS_UI_OTHER = OracleId(ProviderId("scryfall"), "00000000-0000-0000-0000-000000000002")

/** Synthetic presentation only. No metadata/file bytes, source pin or publisher authenticity. */
internal class RulingsFixture(val downloads: DownloadsFixture = DownloadsFixture()) : CatalogRulings {
    var active: RulingsGeneration? = null
    var metadataAdmissions = 0
    var installs = 0
    var admissionFailure: Outcome<Nothing>? = null
    var installFailure: Outcome<Nothing>? = null
    var statusFailure: Outcome<Nothing>? = null
    var pageFailure: Outcome<Nothing>? = null
    var responseGeneration: GenerationId? = null
    val reads = mutableListOf<Pair<OracleId, PageRequest>>()
    val gates = mutableMapOf<OracleId, CompletableDeferred<Unit>>()
    var ignoreCancellation = false
    var contents = listOf(RulingContent(RULINGS_UI_ORACLE, RulingPublisher.WOTC, RulingPublicationDay("2026-10-14"), "  Synthetic café 😀 \n"))
    private val unknown = Knowledge.Unknown("Synthetic source facts", SnapshotList.empty())
    private val hash = ContentHash("SHA-256", "a".repeat(64))
    val access = RulingsCatalogAccess { token -> token.throwIfCancelled(); Outcome.Success(session) }
    val session = RulingsCatalogDownloads(downloads, downloads, { name, wifi -> request(name, wifi, false) }, { id, name, wifi, token ->
        token.throwIfCancelled(); metadataAdmissions++
        admissionFailure ?: if(downloads.rows[id]?.state == AcquisitionState.COMPLETED) Outcome.Success(request(name, wifi, true))
            else failure(FailureCode.SOURCE_MISMATCH)
    }, { id, progress, token ->
        installs++; downloads.installGate?.await(); token.throwIfCancelled()
        progress(JobProgress(id, JobPhase.STAGING, 1, Knowledge.Known(1), "Synthetic ruling progress", null))
        installFailure ?: Outcome.Success(result(id)).also { active = it.value.generation }
    })
    private fun request(name: String, wifi: Boolean, file: Boolean): AcquisitionRequest {
        val uri = ResourceUri(if(file) "https://data.scryfall.io/rulings/synthetic.jsonl.gz" else "https://api.scryfall.com/bulk-data/rulings")
        val size: Knowledge<Long> = if(file) Knowledge.Known(2) else unknown
        return AcquisitionRequest(DownloadIntent(EntityId(if(file) "synthetic-ruling-file" else "synthetic-ruling-metadata"), DownloadKind.CATALOG,
            ProviderId("scryfall"), uri, if(file) "synthetic-ruling-file" else "synthetic-ruling-metadata", null, null, null, size), name,
            AcquisitionSource.RemoteFile(uri, SnapshotList.empty(), if(file) ArtifactFraming.JSONL else ArtifactFraming.TEXT,
                if(file) ArtifactCompression.GZIP else ArtifactCompression.NONE, size, unknown),
            AcquisitionLanguageScope.RULINGS_LANGUAGE_NOT_ASSERTED, unknown, unknown, unknown,
            AcquisitionLimits("synthetic-rulings", 2048, 2048, 2048, 100, 1, 2048, 2048, 1024, 16), wifi)
    }
    private fun result(id: EntityId): RulingsImportResult {
        val artifact = (downloads.rows.getValue(id).artifact as Knowledge.Known).value
        val p = artifact.provenance; val g = GenerationId("synthetic-rulings-${id.value}")
        val generation = RulingsGeneration(g, Provenance(p.provider, p.requestedUri, p.sourceVersion, p.sourceDate, p.retrievalCompletedAt,
            unknown, g, hash, null), 1, GenerationStatus.ACTIVE)
        return RulingsImportResult(generation, 2, hash, 20, hash, SYNTHETIC_TIME, artifact)
    }
    fun installed() {
        val id = GenerationId("synthetic-ruling-corpus")
        active = RulingsGeneration(id, Provenance(ProviderId("scryfall"), ResourceUri("https://data.scryfall.io/rulings/synthetic.jsonl.gz"),
            unknown, unknown, SYNTHETIC_TIME, unknown, id, hash, null), contents.size.toLong(), GenerationStatus.ACTIVE)
    }
    override suspend fun status(cancel: CancelToken): Outcome<RulingsStatus> = statusFailure ?: Outcome.Success(RulingsStatus(active))
    override suspend fun rulings(oracleId: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<StoredRuling>> {
        reads += oracleId to page
        val read: suspend () -> Outcome<Page<StoredRuling>> = {
            gates[oracleId]?.await()
            pageFailure ?: active?.let { generation ->
                val start = page.cursor?.removePrefix("offset:")?.toInt() ?: 0
                val all = contents.filter { it.oracleId == oracleId }; val slice = all.drop(start).take(page.limit)
                Outcome.Success(Page(SnapshotList.copyOf(slice.mapIndexed { index, value -> StoredRuling(RulingId(generation.id, start + index + 1L), value, generation.provenance) }),
                    if(start + slice.size < all.size) "offset:${start + slice.size}" else null,
                    SnapshotReference(ProviderId("scryfall"), responseGeneration ?: generation.id, null, null)))
            } ?: Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "Synthetic absent corpus")
        }
        return if(ignoreCancellation) withContext(NonCancellable) { read() } else read()
    }
}
