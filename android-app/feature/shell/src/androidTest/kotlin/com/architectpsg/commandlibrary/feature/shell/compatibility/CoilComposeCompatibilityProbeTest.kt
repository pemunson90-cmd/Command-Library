package com.architectpsg.commandlibrary.feature.shell.compatibility

import android.content.Context
import android.util.Base64
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.serviceLoaderEnabled
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Coil3.3 AsyncImage on the application's Compose BOM; no production binding. */
@RunWith(AndroidJUnit4::class)
class CoilComposeCompatibilityProbeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun localPngRendersAsAccessibleImageWithSuccessfulDimensions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("coil-compose-local", ".png", context.cacheDir)
        val loader = ImageLoader.Builder(context).serviceLoaderEnabled(false)
            .diskCache(null).memoryCache(null).build()
        val success = AtomicReference<SuccessResult?>()
        val error = AtomicReference<ErrorResult?>()
        val mounted = mutableStateOf(true)
        try {
            file.writeBytes(Base64.decode(PNG, Base64.DEFAULT))
            val request = ImageRequest.Builder(context).data(file).size(1, 1).build()
            rule.setContent {
                if (mounted.value) {
                    AsyncImage(
                        model = request,
                        imageLoader = loader,
                        contentDescription = IMAGE_DESCRIPTION,
                        modifier = Modifier.size(64.dp),
                        onSuccess = { success.set(it.result) },
                        onError = { error.set(it.result) },
                    )
                }
            }
            rule.waitUntil(timeoutMillis = 10_000) { success.get() != null || error.get() != null }
            rule.waitForIdle()
            assertNull("AsyncImage must decode the local PNG: ${error.get()?.throwable?.stackTraceToString()}", error.get())
            val result = success.get()
            assertNotNull("Actual AsyncImage onSuccess callback must fire", result)
            assertEquals(1, result!!.image.width)
            assertEquals(1, result.image.height)
            val image = rule.onNodeWithContentDescription(IMAGE_DESCRIPTION).assertIsDisplayed()
            val bounds = image.fetchSemanticsNode().boundsInRoot
            assertTrue("Accessible image must have visible width", bounds.width > 0f)
            assertTrue("Accessible image must have visible height", bounds.height > 0f)
        } finally {
            try {
                // Dispose the real painter/request before its loader and source file.
                rule.runOnIdle { mounted.value = false }
                rule.waitForIdle()
            } finally {
                loader.shutdown()
                file.delete()
            }
        }
    }

    private companion object {
        const val IMAGE_DESCRIPTION = "Local PNG compatibility image"
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGP4z8DwHwAFAAH/iZk9HQAAAABJRU5ErkJggg=="
    }
}
