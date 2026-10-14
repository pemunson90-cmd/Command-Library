package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Opens the shared process acquisition owner; never grants consent or starts a request. */
public fun interface RulingsCatalogAccess {
    public suspend fun openRulings(cancel: CancelToken): Outcome<RulingsCatalogDownloads>
}

/** Complete ruling source session. Discovery and file execution have separate exact approvals. */
public class RulingsCatalogDownloads(
    public val jobs: DownloadService,
    public val runner: DownloadRunner,
    private val discoveryFor: (String, Boolean) -> AcquisitionRequest,
    private val fileFor: suspend (EntityId, String, Boolean, CancelToken) -> Outcome<AcquisitionRequest>,
    private val installVerified: suspend (EntityId, (JobProgress) -> Unit, CancelToken) -> Outcome<RulingsImportResult>,
) {
    public fun discoveryRequest(displayName: String, wifiOnly: Boolean): AcquisitionRequest = discoveryFor(displayName, wifiOnly)
    public suspend fun fileRequest(metadataJobId: EntityId, displayName: String, wifiOnly: Boolean, cancel: CancelToken): Outcome<AcquisitionRequest> =
        fileFor(metadataJobId, displayName, wifiOnly, cancel)
    public suspend fun install(jobId: EntityId, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<RulingsImportResult> =
        installVerified(jobId, onProgress, cancel)
}
