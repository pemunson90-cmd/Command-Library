package com.architectpsg.commandlibrary.feature.shell.compatibility

import android.content.Context
import android.os.Build
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.serviceLoaderEnabled
import coil3.compose.AsyncImagePainter
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Actual Android codec probes: Robolectric4.17 does not support SDK35 FileSource. */
@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CoilCompatibilityProbeTest {
    @Test fun localPngDecodesWithoutNetworkComponents() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        val file = File.createTempFile("coil-local", ".png", context.cacheDir)
        try {
            file.writeBytes(Base64.decode(PNG, Base64.DEFAULT))
            val result = loader.execute(ImageRequest.Builder(context).data(file).size(1, 1).build())
            assertTrue(describeResult("local PNG: expected SuccessResult", result), result is SuccessResult)
            result as SuccessResult
            assertEquals(1, result.image.width)
            assertEquals(1, result.image.height)
            // Actual published Compose state API resolves; rendering itself needs UI tests.
            assertEquals(SuccessResult::class.java, AsyncImagePainter.State.Success::class.java.getMethod("getResult").returnType)
        } finally { loader.shutdown(); file.delete(); Dispatchers.resetMain() }
    }

    @Test fun malformedLocalImageReturnsError() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data(byteArrayOf(0, 1, 2)).size(1, 1).build())
            assertTrue(describeResult("malformed local bytes: expected ErrorResult", result), result is ErrorResult)
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun externalHttpsHasNoFetcherWhenServiceLoadingIsDisabled() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data("https://example.invalid/image.png").size(1, 1).build())
            assertTrue(describeResult("external HTTPS: expected ErrorResult", result), result is ErrorResult)
            assertTrue(describeResult("HTTPS: expected no supported fetcher", result),
                (result as ErrorResult).throwable.message.orEmpty().contains("Unable to create a fetcher"))
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    @Test fun cancellingSuspendedLocalRequestPropagatesWithoutSuccess() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val context = ApplicationProvider.getApplicationContext<Context>()
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
            val request = ImageRequest.Builder(context).data(Base64.decode(PNG, Base64.DEFAULT)).size(1, 1).build()
            val job = launch { loader.execute(request); completed = true }
            entered.await()
            job.cancel()
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(completed)
        } finally { loader.shutdown(); Dispatchers.resetMain() }
    }

    private fun describeResult(expected: String, result: ImageResult): String {
        val actual = when (result) {
            is SuccessResult -> "SuccessResult image=${result.image.width}x${result.image.height}, source=${result.dataSource}"
            is ErrorResult -> "ErrorResult cause=" + result.throwable.stackTraceToString().lineSequence().take(16).joinToString("\n")
        }
        return "$expected; actual=$actual; Android instrumentation, runtime sdk=${Build.VERSION.SDK_INT}".also { println(it) }
    }

    private companion object {
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
    }
}
