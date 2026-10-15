package com.architectpsg.commandlibrary.data.vault

import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Narrow seam for AtomicFile's void/silent-failure operations, used only by fault tests. */
internal open class VaultFileOps {
    open fun exists(file: File): Boolean = file.exists()
    open fun finish(file: AtomicFile, stream: FileOutputStream) { file.finishWrite(stream) }
    open fun rollback(file: AtomicFile, stream: FileOutputStream?) { file.failWrite(stream) }
    open fun delete(file: AtomicFile) {
        file.delete()
        // API 23 AtomicFile knows .bak, but not the newer .new recovery name.
        val pending = File(file.baseFile.path + ".new")
        if (exists(pending)) pending.delete()
    }
    open fun sync(stream: FileOutputStream) { stream.fd.sync() }
}

internal class VaultRecordStore(
    private val directory: () -> File,
    private val ops: VaultFileOps = VaultFileOps(),
) {
    private fun base(id: String): File {
        if (!Regex("vault-v1-[0-9a-f]{64}").matches(id)) fault(VaultIssue.MISMATCH)
        return File(directory(), "$id.bin")
    }

    private fun recovery(base: File): List<File> = listOf(File(base.path + ".bak"), File(base.path + ".new"))
    private fun any(base: File): Boolean = (listOf(base) + recovery(base)).any(ops::exists)
    private fun settled(base: File): Boolean = recovery(base).none(ops::exists)

    fun exists(id: String): Boolean = any(base(id))

    /** openRead performs platform recovery, then every relevant recovery path is checked. */
    fun read(id: String): ByteArray? {
        val base = base(id)
        if (!any(base)) return null
        val atomic = AtomicFile(base)
        val result = try {
            atomic.openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > VaultEnvelope.MAX_RECORD_BYTES) fault(VaultIssue.CORRUPT)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        } catch (_: FileNotFoundException) {
            // A stranded .new/.bak or inaccessible existing base is not an absent credential.
            fault(VaultIssue.PERSISTENCE)
        }
        if (!ops.exists(base) || !settled(base)) fault(VaultIssue.PERSISTENCE)
        return result
    }

    fun write(id: String, record: ByteArray, beforeCommit: () -> Unit) {
        if (record.size > VaultEnvelope.MAX_RECORD_BYTES) fault(VaultIssue.PERSISTENCE)
        val base = base(id)
        val prior = read(id)
        val parent = base.parentFile ?: fault(VaultIssue.PERSISTENCE)
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) fault(VaultIssue.PERSISTENCE)
        val atomic = AtomicFile(base)
        var stream: FileOutputStream? = null
        var commitAttempted = false
        try {
            stream = atomic.startWrite()
            stream.write(record)
            ops.sync(stream)
            beforeCommit()
            // finishWrite can have committed even if it throws or silently fails. Never claim rollback afterwards.
            commitAttempted = true
            ops.finish(atomic, stream)
            val actual = read(id)
            if (actual == null || !actual.contentEquals(record) || !settled(base)) fault(VaultIssue.PERSISTENCE)
        } catch (cancelled: CancellationException) {
            if (!commitAttempted) rollback(atomic, stream, id, prior)
            throw cancelled
        } catch (problem: Exception) {
            val restored = !commitAttempted && rollback(atomic, stream, id, prior)
            if (restored && problem is SecurityException) throw problem
            // Even a verified rollback is a failed save; a possible commit is deliberately ambiguous.
            fault(VaultIssue.PERSISTENCE)
        } finally {
            try { stream?.close() } catch (_: IOException) { /* No success is inferred from close. */ }
        }
    }

    private fun rollback(atomic: AtomicFile, stream: FileOutputStream?, id: String, prior: ByteArray?): Boolean =
        try {
            ops.rollback(atomic, stream)
            val actual = read(id)
            val restored = if (prior == null) actual == null && !exists(id) else actual != null && actual.contentEquals(prior)
            restored
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Caller reports PERSISTENCE (or propagates cancellation), never a rollback guarantee.
            false
        }

    fun remove(id: String) {
        val base = base(id)
        if (any(base)) ops.delete(AtomicFile(base))
        if (any(base)) fault(VaultIssue.PERSISTENCE)
    }
}
