package com.architectpsg.commandlibrary.feature.catalog

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException

/** Real Android directory durability; failures propagate before callers claim successful commit. */
internal fun androidArtifactFiles(directory: File): DurableArtifactFiles {
    val privateDirectory = directory.canonicalFile
    val parent = privateDirectory.parentFile ?: throw IOException("Artifact directory has no private parent")
    if (!parent.isDirectory) throw IOException("Private artifact parent unavailable")
    if (!privateDirectory.exists() && !privateDirectory.mkdir()) throw IOException("Artifact directory creation failed")
    // Do not rely on the process umask: artifacts are private even on permissive hosts.
    requirePrivateArtifactPermissions(privateDirectory, directory = true)
    syncArtifactDirectory(privateDirectory)
    // Sync parent even on reopen: another initialization may have created the entry before a crash.
    syncArtifactDirectory(parent)
    return DurableArtifactFiles(privateDirectory, ::artifactEntryKind, ::syncArtifactDirectory)
}
private fun syncArtifactDirectory(parent: File) {
    val descriptor = Os.open(parent.absolutePath, OsConstants.O_RDONLY, 0)
    try {
        if (!OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode)) throw IOException("Artifact sync target is not a directory")
        Os.fsync(descriptor)
    } finally { Os.close(descriptor) }
}

private fun artifactEntryKind(file: File): ArtifactEntryKind {
    val stat = try { Os.lstat(file.absolutePath) } catch (error: ErrnoException) {
        if (error.errno == OsConstants.ENOENT) return ArtifactEntryKind.MISSING
        throw error
    }
    return when {
        OsConstants.S_ISLNK(stat.st_mode) -> ArtifactEntryKind.SYMLINK
        OsConstants.S_ISREG(stat.st_mode) -> ArtifactEntryKind.REGULAR
        else -> ArtifactEntryKind.OTHER
    }
}
