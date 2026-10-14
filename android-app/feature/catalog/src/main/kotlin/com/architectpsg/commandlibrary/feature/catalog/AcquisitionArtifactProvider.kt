package com.architectpsg.commandlibrary.feature.catalog

import android.annotation.TargetApi
import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileNotFoundException

/** Same-process framework entrypoint. It never loads a graph or looks up durable state. */
internal class AcquisitionArtifactProvider : ContentProvider() {
    private var installedAuthority: String? = null

    override fun attachInfo(context: Context, info: ProviderInfo) {
        require(info.authority == context.packageName + ".acquisition.artifact")
        require(!info.exported && !info.grantUriPermissions && info.applicationInfo.uid == Process.myUid())
        require(info.processName == info.applicationInfo.processName)
        installedAuthority = info.authority
        super.attachInfo(context, info)
    }

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = open(uri, mode, null)
    override fun openFile(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = open(uri, mode, signal)
    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor = asset(uri, mode, null)
    override fun openAssetFile(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor = asset(uri, mode, signal)
    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?): AssetFileDescriptor = typed(uri, mimeTypeFilter, opts, null)
    override fun openTypedAssetFile(uri: Uri, mimeTypeFilter: String, opts: Bundle?, signal: CancellationSignal?): AssetFileDescriptor = typed(uri, mimeTypeFilter, opts, signal)

    private fun open(uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        ArtifactLocatorRegistry.open(authority(), uri, mode, signal)
    private fun asset(uri: Uri, mode: String, signal: CancellationSignal?): AssetFileDescriptor =
        AssetFileDescriptor(open(uri, mode, signal), 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    private fun typed(uri: Uri, mime: String, opts: Bundle?, signal: CancellationSignal?): AssetFileDescriptor {
        // Authenticate even rejected conversion requests. No MIME conversion or options are supported.
        ArtifactLocatorRegistry.admitted(authority(), uri) {
            if (mime != "*/*" && mime != MIME || opts != null && !opts.isEmpty) denied()
        }
        return asset(uri, "r", signal)
    }

    override fun getType(uri: Uri): String = ArtifactLocatorRegistry.admitted(authority(), uri) { MIME }
    override fun getStreamTypes(uri: Uri, mimeTypeFilter: String): Array<String>? = ArtifactLocatorRegistry.admitted(authority(), uri) {
        if (mimeTypeFilter == "*/*" || mimeTypeFilter == MIME) arrayOf(MIME) else null
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
        metadata(uri, projection, selection, selectionArgs, sortOrder, null)
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?, cancellationSignal: CancellationSignal?): Cursor =
        metadata(uri, projection, selection, selectionArgs, sortOrder, cancellationSignal)
    override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor {
        if (queryArgs != null && !queryArgs.isEmpty) {
            ArtifactLocatorRegistry.admitted(authority(), uri) { denied() }
        }
        return metadata(uri, projection, null, null, null, cancellationSignal)
    }
    private fun metadata(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?, signal: CancellationSignal?): Cursor =
        ArtifactLocatorRegistry.admitted(authority(), uri) { lease ->
            signal?.throwIfCanceled()
            if (selection != null || selectionArgs != null || sortOrder != null) denied()
            val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            if (columns.any { it != OpenableColumns.DISPLAY_NAME && it != OpenableColumns.SIZE }) denied()
            val cursor = MatrixCursor(columns, 1)
            try {
                cursor.addRow(columns.map { if (it == OpenableColumns.DISPLAY_NAME) lease.file.name else lease.bytes })
                signal?.throwIfCanceled()
                cursor
            } catch (error: Throwable) { cursor.close(); throw error }
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri = rejectMutation()
    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = rejectMutation()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = rejectMutation()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = rejectMutation()
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> = rejectMutation()
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = rejectMutation()
    override fun canonicalize(uri: Uri): Uri = rejectMutation()
    override fun uncanonicalize(uri: Uri): Uri = rejectMutation()
    private fun rejectMutation(): Nothing { ArtifactLocatorRegistry.checkUid(); throw UnsupportedOperationException("Read-only private artifact provider") }
    private fun authority(): String = installedAuthority ?: denied()
    private companion object { const val MIME = "application/octet-stream" }
}

private fun denied(): Nothing = throw FileNotFoundException("Private verified artifact unavailable")

/** Only AndroidVerifiedArtifactLocator registers, inside LocalAcquisition's completed-job pin.
 * No detached/duplicated consumer handles: resolver wrappers/streams must be lexical use owners.
 * Removing admission and all adapter-owned handles is synchronous before that pin is released.
 * Consumer process must not exec/spawn subprocesses or load untrusted executable code; adding
 * those behaviors requires renewed lifetime/security review. API23–26 temporary FDs and API23–29
 * final FDs lack a CLOEXEC guarantee. Modern CLOEXEC cannot revoke FD copies or prevent fork-only inheritance.
 */
internal object ArtifactLocatorRegistry {
    private val monitor = Any()
    private val leases = mutableMapOf<String, Lease>()
    internal class Lease internal constructor(
        val uri: Uri, val file: File, val device: Long, val inode: Long, val bytes: Long,
    ) { val descriptors = mutableListOf<ParcelFileDescriptor>() }

    fun checkUid() { if (Binder.getCallingUid() != Process.myUid()) throw SecurityException("Private artifact caller denied") }

    fun register(privateRoot: File, authority: String, file: File, expectedBytes: Long): Lease = synchronized(monitor) {
        checkUid()
        val root = privateRoot.canonicalFile
        val absolute = file.absoluteFile
        if (absolute.canonicalFile != absolute || absolute.parentFile == null || !absolute.name.matches(Regex("[a-z0-9][a-z0-9-]{0,127}"))) denied()
        // Ancestors below the trusted app-private filesDir are canonical, owned directories.
        var parent = absolute.parentFile
        while (true) {
            val current = parent ?: denied()
            val stat = Os.lstat(current.path)
            // Framework filesDir can be 0771 on API23: its app-owned GID is trusted too.
            if (current.canonicalFile != current || !OsConstants.S_ISDIR(stat.st_mode) || !privateEntry(stat)) denied()
            if (current == root) break
            parent = current.parentFile
        }
        val stat = Os.lstat(absolute.path)
        if (!OsConstants.S_ISREG(stat.st_mode) || !privateEntry(stat) || stat.st_size != expectedBytes) denied()
        val uri = Uri.Builder().scheme("content").authority(authority).appendPath("completed").appendPath(absolute.name).build()
        if (leases.containsKey(uri.toString())) denied()
        Lease(uri, absolute, stat.st_dev, stat.st_ino, expectedBytes).also { leases[uri.toString()] = it }
    }

    private fun privateEntry(stat: android.system.StructStat): Boolean =
        stat.st_uid == Process.myUid() && stat.st_mode and (OsConstants.S_IROTH or OsConstants.S_IWOTH) == 0 &&
            (stat.st_mode and (OsConstants.S_IRGRP or OsConstants.S_IWGRP) == 0 || stat.st_gid == Process.myUid())

    fun <T> admitted(authority: String, uri: Uri, action: (Lease) -> T): T = synchronized(monitor) {
        checkUid()
        val lease = leases[uri.toString()] ?: denied()
        if (uri.scheme != "content" || uri.authority != authority || uri.query != null || uri.fragment != null || uri.userInfo != null || uri.toString() != lease.uri.toString()) denied()
        action(lease)
    }

    fun open(authority: String, uri: Uri, mode: String, signal: CancellationSignal?): ParcelFileDescriptor = admitted(authority, uri) { lease ->
        if (mode != "r") denied()
        signal?.throwIfCanceled()
        var owned: ParcelFileDescriptor? = null
        try {
            // O_CLOEXEC is public from API27; legacy opens retain no-follow/nonblocking admission.
            val closeOnExec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) OsConstants.O_CLOEXEC else 0
            val temporary = Os.open(lease.file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or closeOnExec, 0)
            var primary: Throwable? = null
            try {
                val stat = Os.fstat(temporary)
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_dev != lease.device || stat.st_ino != lease.inode || stat.st_size != lease.bytes) denied()
                owned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Api30.duplicate(temporary)
                    else ParcelFileDescriptor.dup(temporary) // API23–29: no final-FD CLOEXEC promise.
            } catch (error: Throwable) { primary = error; throw error }
            finally {
                try { Os.close(temporary) } catch (close: Throwable) { if (primary != null) primary.addSuppressed(close) else throw close }
            }
            signal?.throwIfCanceled()
            requireNotNull(owned).also { lease.descriptors += it }
        } catch (error: Throwable) {
            try { owned?.close() } catch (close: Throwable) { error.addSuppressed(close) }
            if (error is android.system.ErrnoException) throw FileNotFoundException("Private verified artifact unavailable").also { it.initCause(error) }
            throw error
        }
    }

    @TargetApi(30)
    private object Api30 {
        fun duplicate(temporary: java.io.FileDescriptor): ParcelFileDescriptor =
            // No intervening work after duplication: adoptFd owns the valid returned integer once.
            ParcelFileDescriptor.adoptFd(Os.fcntlInt(temporary, OsConstants.F_DUPFD_CLOEXEC, 0))
    }

    fun close(lease: Lease): Unit = synchronized(monitor) {
        // Exact identity: a stale close can never invalidate a later registration of the same URI.
        if (leases[lease.uri.toString()] !== lease) return@synchronized
        leases.remove(lease.uri.toString())
        var primary: Throwable? = null
        for (descriptor in lease.descriptors) {
            try { descriptor.close() } catch (error: Throwable) {
                if (primary == null) primary = error else primary.addSuppressed(error)
            }
        }
        lease.descriptors.clear()
        primary?.let { throw it }
    }
}
