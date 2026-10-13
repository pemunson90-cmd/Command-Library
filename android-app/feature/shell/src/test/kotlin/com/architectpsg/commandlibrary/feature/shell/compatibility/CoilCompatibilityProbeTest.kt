package com.architectpsg.commandlibrary.feature.shell.compatibility

import coil3.ImageLoader
import coil3.serviceLoaderEnabled
import coil3.compose.AsyncImagePainter
import coil3.disk.DiskCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.util.Base64

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

/** Android runtime probes: require actual Robolectric/Android resources, never SDK stubs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoilCompatibilityProbeTest {
    @Test fun localPngDecodesWithoutNetworkComponents() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        val file = Files.createTempFile("coil-local", ".png").toFile()
        try {
            file.writeBytes(Base64.getDecoder().decode(PNG))
            val result = loader.execute(ImageRequest.Builder(context).data(file).size(1, 1).build())
            assertTrue(result is SuccessResult)
            result as SuccessResult
            assertEquals(1, result.image.width)
            assertEquals(1, result.image.height)
            // Actual published Compose state API resolves; rendering itself needs UI tests.
            assertEquals(SuccessResult::class.java, AsyncImagePainter.State.Success::class.java.getMethod("getResult").returnType)
        } finally { loader.shutdown(); file.delete(); Dispatchers.resetMain() }
    }

    @Test fun malformedLocalImageReturnsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data(byteArrayOf(0, 1, 2)).size(1, 1).build())
            assertTrue(result is ErrorResult)
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun externalHttpsHasNoFetcherWhenServiceLoadingIsDisabled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data("https://example.invalid/image.png").size(1, 1).build())
            assertTrue(result is ErrorResult)
            assertTrue((result as ErrorResult).throwable.message.orEmpty().contains("Unable to create a fetcher"))
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun cancellingSuspendedLocalRequestPropagatesWithoutSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = RuntimeEnvironment.getApplication()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).components {
                add(coil3.intercept.Interceptor { chain ->
                    entered.complete(Unit)
                    release.await()
                    chain.proceed()
                })
            }.build()
        var completed = false
        try {
            val request = ImageRequest.Builder(context).data(Base64.getDecoder().decode(PNG)).size(1, 1).build()
            val job = launch { loader.execute(request); completed = true }
            entered.await()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(completed)
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    private companion object {
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
    }
}
