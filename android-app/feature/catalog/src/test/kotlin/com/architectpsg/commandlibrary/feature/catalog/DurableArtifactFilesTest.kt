package com.architectpsg.commandlibrary.feature.catalog

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DurableArtifactFilesTest {
    @Test fun recoveredPermissivePartialIsPrivateBeforeWritingAndRemainsPrivateAfterPromotion() = fixture { dir, files ->
        files.create("partial")
        val partial = File(dir, "partial")
        val privateMode = java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")
        assertEquals(privateMode, Files.getPosixFilePermissions(partial.toPath()))
        Files.setPosixFilePermissions(partial.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"))
        files.truncateToCheckpoint("partial", 0)
        assertEquals(privateMode, Files.getPosixFilePermissions(partial.toPath()))
        files.append("partial", 0, byteArrayOf(1), 1)
        files.promote("partial", "candidate")
        assertEquals(privateMode, Files.getPosixFilePermissions(File(dir, "candidate").toPath()))
    }
    private fun entryKind(file: File): ArtifactEntryKind = when {
        Files.isSymbolicLink(file.toPath()) -> ArtifactEntryKind.SYMLINK
        Files.isRegularFile(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) -> ArtifactEntryKind.REGULAR
        !Files.exists(file.toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS) -> ArtifactEntryKind.MISSING
        else -> ArtifactEntryKind.OTHER
    }
    private fun fixture(action: (File, DurableArtifactFiles) -> Unit) {
        val dir = Files.createTempDirectory("artifact-test").toFile()
        try { action(dir, DurableArtifactFiles(dir, ::entryKind) {}) } finally { dir.deleteRecursively() }
    }
    @Test fun checkpointRejectsTailUntilExplicitRecoveryAndNeverInventsMissingBytes() = fixture { dir, files ->
        files.create("partial")
        assertEquals(3L, files.append("partial", 0, byteArrayOf(1, 2, 3), 10))
        File(dir, "partial").appendBytes(byteArrayOf(4, 5))
        assertThrows(IOException::class.java) { files.append("partial", 3, byteArrayOf(6), 10) }
        files.truncateToCheckpoint("partial", 3)
        assertEquals(4L, files.append("partial", 3, byteArrayOf(6), 10))
        assertThrows(IOException::class.java) { files.truncateToCheckpoint("partial", 5) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 6), File(dir, "partial").readBytes())
    }
    @Test fun boundsAndPrivateNamesCannotOverwriteOrEscape() = fixture { dir, files ->
        files.create("partial")
        assertThrows(IOException::class.java) { files.create("partial") }
        assertThrows(IllegalArgumentException::class.java) { files.create("../escape") }
        assertThrows(IllegalArgumentException::class.java) { files.append("partial", 0, ByteArray(65537), 100000) }
        assertThrows(IllegalArgumentException::class.java) { files.append("partial", Long.MAX_VALUE, byteArrayOf(1), Long.MAX_VALUE) }
        assertThrows(IllegalArgumentException::class.java) { files.append("partial", 0, byteArrayOf(1, 2), 1) }
        files.create("target")
        assertThrows(IOException::class.java) { files.promote("partial", "target") }
        assertEquals(0L, File(dir, "partial").length())
    }
    @Test fun verifiedRenameHasExactHashAndPropagatesDirectorySyncFailure() = fixture { dir, files ->
        files.create("partial")
        files.append("partial", 0, "abc".toByteArray(), 3)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", files.verify("partial", 3, 3) {})
        assertThrows(IOException::class.java) { files.verify("partial", 2, 3) {} }
        val failing = DurableArtifactFiles(dir, ::entryKind) { throw IOException("injected directory sync failure") }
        assertThrows(IOException::class.java) { failing.promote("partial", "candidate") }
        assertFalse(File(dir, "partial").exists())
        assertTrue(File(dir, "candidate").exists())
        assertEquals(3L, File(dir, "candidate").length())
    }
    @Test fun symlinkedArtifactIsRejected() = fixture { dir, files ->
        File(dir, "real").writeText("data")
        Files.createSymbolicLink(File(dir, "link").toPath(), File(dir, "real").toPath())
        assertThrows(IOException::class.java) { files.verify("link", 4, 4) {} }
    }
    @Test fun danglingLinksCannotBecomeCandidatesAndCancellationStopsVerification() = fixture { dir, files ->
        files.create("partial")
        files.append("partial", 0, ByteArray(65536), 65536)
        Files.createSymbolicLink(File(dir, "dangling").toPath(), File(dir, "missing").toPath())
        assertThrows(IOException::class.java) { files.promote("partial", "dangling") }
        assertTrue(Files.isSymbolicLink(File(dir, "dangling").toPath()))
        assertTrue(File(dir, "partial").exists())
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            files.verify("partial", 65536, 65536) { throw java.util.concurrent.CancellationException() }
        }
    }

    @Test fun pinnedReadSeesExactBytesAndDeletionDoesNotTouchOtherArtifact() = fixture { _, files ->
        files.create("one"); files.create("other")
        files.append("one", 0, byteArrayOf(8, 9), 2)
        assertEquals(2L, files.length("one"))
        files.openRead("one").use { assertArrayEquals(byteArrayOf(8, 9), it.readBytes()) }
        files.delete("one")
        assertNull(files.length("one"))
        assertThrows(IOException::class.java) { files.openRead("one") }
        assertEquals(0L, files.length("other"))
    }

}
