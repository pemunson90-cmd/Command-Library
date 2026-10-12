package com.architectpsg.commandlibrary.feature.settings

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The random installation ID that every consent is bound to. The file must live where backup and
 * device transfer never reach (Context.noBackupFilesDir), so a restored or transferred settings
 * store meets a different ID and its consents stop being effective.
 *
 * Fail closed: a file whose content is not a UUID is replaced with a new ID. That only makes existing
 * consents ineffective (the user approves again); it never grants anything. An unreadable file or a
 * failed write is an IOException that the caller reports as a persistence failure.
 */
internal class InstallationIdStore(
    private val file: File,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    @Volatile
    private var cached: String? = null

    suspend fun get(): String {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(dispatcher) { loadOrCreate() }.also { cached = it }
        }
    }

    private fun loadOrCreate(): String {
        if (file.exists()) {
            val existing = parse(file.readText(Charsets.UTF_8))
            if (existing != null) return existing
        }
        val created = UUID.randomUUID().toString()
        val parent = file.absoluteFile.parentFile ?: throw IOException("Installation ID file has no parent directory")
        if (!parent.isDirectory && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Installation ID directory could not be created")
        }
        val temporary = File(parent, file.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(created.toByteArray(Charsets.UTF_8))
                stream.flush()
                stream.fd.sync()
            }
            if (!temporary.renameTo(file)) throw IOException("Installation ID file could not be moved into place")
        } finally {
            if (temporary.exists()) temporary.delete()
        }
        return created
    }

    private fun parse(text: String): String? =
        try {
            UUID.fromString(text.trim()).toString().takeIf { it.equals(text.trim(), ignoreCase = true) }
        } catch (invalid: IllegalArgumentException) {
            null
        }
}
