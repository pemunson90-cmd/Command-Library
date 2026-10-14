# API 23+ screenshot capture brief

Use `InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()` as the single capture path on API 23 and API 36, and write each successful returned `Bitmap` as the screenshot for that configured view. Compose 1.12.1's published `ui-test-android` source marks `SemanticsNodeInteraction.captureToImage()` `@RequiresApi(Build.VERSION_CODES.O)` (API 26). Its ordinary single-window path calls `Window.captureRegionToImage`, whose implementation calls `PixelCopy.request(Window, ...)`; both helpers are API 26-gated. Compose's multi-window path already uses `UiAutomation.takeScreenshot()` and crops to selected-node bounds, but that path is also unnecessarily annotated API 26. This explains why the pinned helper cannot supply one API 23/36 capture path.

`UiAutomation` is available from API 18, and `takeScreenshot()` captures the real display (`Rect(0,0,displayWidth,displayHeight)` in AOSP implementation). It returns `null` on capture failure, so assert non-null and fail the test; do not substitute a synthetic bitmap. A full-display PNG is sufficient when the artifact is meant to show the actual rendered screen for each of the 36 configured views. Cropping semantics bounds is only needed if a downstream assertion specifically expects a node-sized image; it adds coordinate/window-frame handling and discards the rest of the actual display. Preserve all configured cases and capture after normal Compose synchronization/rendering.

Evidence checked 2026-10-13:

- Published source archive, `androidx.compose.ui:ui-test-android:1.12.1`: https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-test-android/1.12.1/ui-test-android-1.12.1-sources.jar (`AndroidImageHelpers.android.kt`, `WindowCapture.android.kt`).
- Android API reference: https://developer.android.com/reference/android/app/UiAutomation — `UiAutomation` added API 18; documents `Bitmap takeScreenshot()`.
- AOSP implementation: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/UiAutomation.java — captures the full real display and returns null on failure.
- PixelCopy API reference: https://developer.android.com/reference/android/view/PixelCopy — class added API24; the Window overload used by this helper requires API26.
