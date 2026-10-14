package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Opens real English-card acquisition lazily; opening never approves or starts a download. */
public fun interface EnglishCatalogAccess {
    public suspend fun open(cancel: CancelToken): Outcome<EnglishCatalogDownloads>
}

/** Complete supported-source session. The root owns its transport and verified artifact binding. */
public class EnglishCatalogDownloads(
    public val jobs: DownloadService,
    public val runner: DownloadRunner,
    public val bulkLimits: CatalogBulkImportLimits,
    private val requestFor: (String, Boolean) -> AcquisitionRequest,
    private val installVerified: suspend (EntityId, (JobProgress) -> Unit, CancelToken) -> Outcome<CatalogBulkImportResult>,
) {
    public fun request(displayName: String, wifiOnly: Boolean): AcquisitionRequest = requestFor(displayName, wifiOnly)
    public suspend fun install(jobId: EntityId, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<CatalogBulkImportResult> =
        installVerified(jobId, onProgress, cancel)
}
