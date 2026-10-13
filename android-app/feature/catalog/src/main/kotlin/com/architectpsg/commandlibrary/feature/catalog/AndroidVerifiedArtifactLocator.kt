package com.architectpsg.commandlibrary.feature.catalog

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.VerifiedArtifact
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow

/** A locator is admitted only during this trusted callback, including after verified EOF.
 * Callback flows and resolver streams are consumed/closed here, never detached or duplicated.
 * Signal admission checks cannot cancel subsequent reads: readers check coroutine/token themselves.
 * Rebinding after recreation reruns durable completion/hash validation; installed data does not
 * depend on reopening an expired locator. This does not cover outer importer acknowledgement.
 * A normal callback result survives late cancellation; its commit/cancellation policy is callback-owned.
 */
internal class AndroidVerifiedArtifactLocator(context: Context, private val engine: LocalAcquisition) {
    private val application = context.applicationContext
    private val authority = application.packageName + ".acquisition.artifact"

    internal suspend fun <T> withPinnedArtifact(
        jobId: EntityId,
        cancel: CancelToken,
        action: suspend (ResourceUri, VerifiedArtifact, Flow<ByteChunk>) -> T,
    ): T = engine.withArtifactFile(jobId, cancel) { file, artifact, chunks ->
        currentCoroutineContext().ensureActive(); cancel.throwIfCancelled()
        @Suppress("DEPRECATION")
        val info = application.packageManager.getProviderInfo(ComponentName(application, AcquisitionArtifactProvider::class.java), PackageManager.GET_META_DATA)
        check(info.authority == authority && !info.exported && !info.grantUriPermissions && info.applicationInfo.uid == Process.myUid())
        check(info.processName == info.applicationInfo.processName)
        val lease = ArtifactLocatorRegistry.register(application.filesDir, info.authority, file, artifact.bytes)
        var primary: Throwable? = null
        try {
            action(ResourceUri(lease.uri.toString()), artifact, chunks)
        } catch (error: Throwable) { primary = error; throw error }
        finally {
            try { ArtifactLocatorRegistry.close(lease) }
            catch (close: Throwable) { if (primary != null) primary.addSuppressed(close) else throw close }
        }
    }
}
