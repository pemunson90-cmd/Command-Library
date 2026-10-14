package com.architectpsg.commandlibrary

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class RealHostDirectoryLinuxTest {
    private fun errno(expected: Int, block: () -> Unit) {
        try { block(); fail("Expected errno $expected") } catch (failure: ErrnoException) { assertEquals(expected, failure.errno) }
    }

    @Test fun realLstatDistinguishesMissingRegularDirectoryAndUnfollowedSymlink() {
        val root = Files.createTempDirectory("directory-bridge-")
        try {
            errno(OsConstants.ENOENT) { Os.lstat(root.resolve("missing").toString()) }
            val file = Files.write(root.resolve("regular"), byteArrayOf(1))
            val directory = Files.createDirectory(root.resolve("directory"))
            val link = Files.createSymbolicLink(root.resolve("link"), directory)
            assertTrue(OsConstants.S_ISREG(Os.lstat(file.toString()).st_mode))
            assertTrue(OsConstants.S_ISDIR(Os.lstat(directory.toString()).st_mode))
            assertTrue(OsConstants.S_ISLNK(Os.lstat(link.toString()).st_mode))
            assertFalse(OsConstants.S_ISDIR(Os.lstat(link.toString()).st_mode))
        } finally { root.toFile().deleteRecursively() }
    }

    @Test fun realDirectoryDescriptorSurvivesPathReplacementSyncsAndClosesOnce() {
        val root = Files.createTempDirectory("directory-fd-bridge-")
        val original = Files.createDirectory(root.resolve("directory"))
        val descriptor = Os.open(original.toString(), OsConstants.O_RDONLY, 0)
        var closed = false
        try {
            Files.move(original, root.resolve("renamed")); Files.write(original, byteArrayOf(1))
            assertTrue(OsConstants.S_ISDIR(Os.fstat(descriptor).st_mode))
            assertTrue(OsConstants.S_ISREG(Os.lstat(original.toString()).st_mode))
            Os.fsync(descriptor); Os.close(descriptor); closed = true
            assertFalse(descriptor.valid())
            errno(OsConstants.EBADF) { Os.fstat(descriptor) }
            errno(OsConstants.EBADF) { Os.fsync(descriptor) }
            errno(OsConstants.EBADF) { Os.close(descriptor) }
        } finally { if (!closed) Os.close(descriptor); root.toFile().deleteRecursively() }
    }
}
