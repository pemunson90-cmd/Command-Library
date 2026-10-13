Independent focused source review: APPROVE
Exact candidate: b5609c9c17058b6c3538a7f9baf00b21af68153e
Scope: VisualSystemAcceptanceTest screenshot API and provenance selector correction; source brief ce95b3c. No candidate files edited.

Findings
- Independently fetched published androidx.compose.ui:ui-test-android:1.12.1 sources; captureToImage and Window.captureRegionToImage are API26-annotated. Ordinary capture uses PixelCopy Window overload. API23 therefore cannot use that path.
- Replacement uses actual UiAutomation.takeScreenshot (API18+) for both supported lanes and full real display. Null or compression failure fails the case; no synthetic bitmap, skip or offscreen replacement. Bitmap is recycled in finally. Existing waitForIdle, output path/names and post-success VISUAL_CAPTURE_PATH marker remain. Full display captures require actual hosted artifact inspection, especially under ForcedSize.
- Source heading now requires Heading semantics. Provider field now requires both Source label and actual provider on the merged node and asserts absence of a click action. This removes known duplicate-label ambiguity while strengthening metadata verification.
- All36 intended cases, GameAction identity assertions, minimum target/full bounds, hidden exclusion/retention and completed/bound checks remain. Actual source comparison preserves their counts.
- Minor documentation precision: PixelCopy class is API24; its Window overload used by the Compose helper is API26. Last source-brief bullet should identify the Window overload. This does not affect source correctness.

Actual reviewer checks: exact commit/diff inspection, git diff --check PASS; exact published Compose API inspection PASS; test/action/target/state/hidden/marker source comparison PASS.
Parent-reported prior hosted38345526072:522 JVM passed,4 Coil native cases passed on each lane; API36 captured all36 but12 detail selectors failed; API23 visual capture failed on unsupported PixelCopy. Reviewer did not rerun or independently collect this hosted evidence.
Corrected native compilation/instrumentation/capture/render acceptance: NOT RUN. Root next: batch this reviewed source with the separately reviewed compiling provider changes, then require exact hosted gates,36 executed visual cases/36 complete PNGs without skips per lane and actual render inspection. No native/visual/finished-app approval inferred.
