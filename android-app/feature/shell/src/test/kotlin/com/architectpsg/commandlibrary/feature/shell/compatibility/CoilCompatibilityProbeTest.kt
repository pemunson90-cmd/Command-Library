package com.architectpsg.commandlibrary.feature.shell.compatibility

import coil3.disk.DiskCache
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

/** Real Coil cache code can run on JVM; cache paths never include retained packs. */
class CoilDiskCacheCompatibilityProbeTest {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun lruEvictionAndClearPreserveSeparatelyRetainedPack() = runTest {
        val root = Files.createTempDirectory("coil-compatibility").toFile()
        val pack = root.resolve("packs/retained.png")
        pack.parentFile.mkdirs()
        val retained = byteArrayOf(1, 2, 3, 4)
        pack.writeBytes(retained)
        val cache = DiskCache.Builder().directory(root.resolve("cache").toOkioPath())
            .maxSizeBytes(64).cleanupCoroutineContext(StandardTestDispatcher(testScheduler)).build()
        try {
            fun put(key: String) {
                val editor = checkNotNull(cache.openEditor(key))
                FileSystem.SYSTEM.write(editor.metadata) { writeUtf8("") }
                FileSystem.SYSTEM.write(editor.data) { write(ByteArray(40) { 7 }) }
                editor.commit()
            }
            put("older")
            assertNotNull(cache.openSnapshot("older")?.also { it.close() })
            put("newer")
            testScheduler.runCurrent()
            assertNull(cache.openSnapshot("older"))
            assertNotNull(cache.openSnapshot("newer")?.also { it.close() })
            assertTrue(cache.size <= 64)
            assertArrayEquals(retained, pack.readBytes())
            cache.clear()
            assertNull(cache.openSnapshot("newer"))
            assertArrayEquals(retained, pack.readBytes())
        } finally {
            cache.shutdown()
            root.deleteRecursively()
        }
    }
}
