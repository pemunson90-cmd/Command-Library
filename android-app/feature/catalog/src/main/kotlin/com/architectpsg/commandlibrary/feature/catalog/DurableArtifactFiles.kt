package com.architectpsg.commandlibrary.feature.catalog

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Caller holds the job's exclusive file/commit guard. Directory sync is a required platform adapter.
 * Directory must already be durably created; no other writer may mutate this private namespace.
 * This helper never marks a database job complete; renamed candidates require DB reconciliation.
 */
internal enum class ArtifactEntryKind { MISSING, REGULAR, SYMLINK, OTHER }

internal class DurableArtifactFiles(
    directory: File,
    private val entryKind: (File) -> ArtifactEntryKind,
    private val syncDirectory: (File) -> Unit,
) {
    private val directory = directory.canonicalFile.also {
        if (!it.isDirectory) throw IOException("Artifact directory unavailable")
    }

    // All creators/promoters in this single-process private namespace share exclusion.
    private val namespaceLock = Any().let { proposed -> namespaceLocks.putIfAbsent(this.directory.path, proposed) ?: proposed }

    fun create(key: String): Unit = synchronized(namespaceLock) {
        val file = file(key)
        if (!file.createNewFile()) throw IOException("Artifact key already exists")
        RandomAccessFile(file, "rw").use { it.fd.sync() }
        syncDirectory(directory)
    }

    fun append(key: String, durableOffset: Long, bytes: ByteArray, maximumBytes: Long): Long {
        require(durableOffset >= 0 && maximumBytes > 0 && bytes.size <= 64 * 1024)
        require(durableOffset <= maximumBytes && bytes.size.toLong() <= maximumBytes - durableOffset)
        val file = existing(key)
        RandomAccessFile(file, "rw").use {
            if (it.length() != durableOffset) throw IOException("Artifact offset disagrees with durable checkpoint")
            it.seek(durableOffset)
            it.write(bytes)
            it.fd.sync()
        }
        return durableOffset + bytes.size
    }

    fun truncateToCheckpoint(key: String, durableOffset: Long) {
        require(durableOffset >= 0)
        RandomAccessFile(existing(key), "rw").use {
            if (it.length() < durableOffset) throw IOException("Artifact shorter than durable checkpoint")
            it.setLength(durableOffset)
            it.fd.sync()
        }
    }

    fun verify(key: String, expectedBytes: Long, maximumBytes: Long, checkCancellation: () -> Unit): String {
        require(expectedBytes >= 0 && maximumBytes > 0 && expectedBytes <= maximumBytes)
        val file = existing(key)
        if (file.length() != expectedBytes) throw IOException("Artifact length mismatch")
        val digest = MessageDigest.getInstance("SHA-256")
        var readBytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkCancellation()
                val read = input.read(buffer)
                if (read == -1) break
                if (read.toLong() > expectedBytes - readBytes) throw IOException("Artifact grew during verification")
                digest.update(buffer, 0, read)
                readBytes += read
            }
        }
        if (readBytes != expectedBytes) throw IOException("Artifact changed during verification")
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    /** Persist candidateKey in the job before calling; a directory-sync failure leaves it recoverable. */
    fun promote(partialKey: String, candidateKey: String): Unit = synchronized(namespaceLock) {
        require(partialKey != candidateKey)
        val source = existing(partialKey)
        val target = file(candidateKey)
        if (target.exists()) throw IOException("Artifact candidate already exists")
        RandomAccessFile(source, "rw").use { it.fd.sync() }
        if (!source.renameTo(target)) throw IOException("Artifact rename failed")
        syncDirectory(directory)
    }

    private companion object {
        val namespaceLocks = ConcurrentHashMap<String, Any>()
    }

    private fun existing(key: String): File = file(key).also {
        if (!it.isFile) throw IOException("Artifact file unavailable")
    }
    private fun file(key: String): File {
        require(key.matches(Regex("[a-z0-9][a-z0-9-]{0,127}"))) { "Invalid private artifact key" }
        val file = File(directory, key)
        if (entryKind(file) in listOf(ArtifactEntryKind.SYMLINK, ArtifactEntryKind.OTHER) || file.canonicalFile != file.absoluteFile) throw IOException("Artifact link or path escape")
        return file
    }
}
