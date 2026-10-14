# Profile color and orientation — 2026-10-14

Bounded extension of the existing profile editor; prior verified counter baseline is 8759b61. This batch adds explicit optional player color and all four seat orientations, retaining exact artwork references, fit, crop, commanders and counters. Transparent ARGB zero, unsigned FFFFFFFF and unset remain distinct. Apply changes only the draft; Cancel/Back returns to the previous appearance. Whole-profile Save retains the original revision/CAS and failure/stale behavior. No public port, schema, source acquisition or GameService binding changes.

The scrolling screen uses grouped, labelled 48dp radio choices, a full-row color checkbox, text/hex alongside the color preview, and externalized strings. Shared profile radio/toggle helpers keep the previous counter behavior. Host idle-budget helper reuse affects tests only.

Six appearance controller tests passed with all 24 profile controller cases (37s). Earlier UI compilation failed on then-absent screen/tags/resources (38s); no UI tests executed in that run. The new five shared UI tests and an actual MainActivity → AppGraph → Room preservation test are now included in one combined integration gate, following Pat's D-28 batching preference. The Room probe asserts the whole old row before Save, then revision+1 and exact whole updated value; cleanup touches only its unique UUID name and uses latest revisions.

Independent consolidated source/UI/durability/composition review: APPROVE, no blockers. Combined local gate passed: see exact results below. Native/device accessibility, art rendering, gameplay, complete M2/v1 and a finished APK are not established by source review or host tests.

## Verified integration boundary

Accepted JDK17/SDK/Gradle tuple, one combined invocation:

`bash android-app/gradlew -p android-app --daemon --console=plain check :app:assembleDebug :app:assembleDebugAndroidTest :feature:shell:assembleDebugAndroidTest :data:storage:assembleDebugAndroidTest`

SUCCESS in 7m36s, 441 tasks (72 executed, 369 up-to-date). Actual 104 JUnit XML reports contain 1,001 tests, zero failures/errors/skips, including unchanged cached modules. Shell:124 tests/17XML; app:7/3XML; storage:198/21XML. New shared appearance UI:5 pass/13.067s; appearance controllers:6 pass/0.382s in this gate; actual app UI counter+appearance cases:2 pass/43.573s, appearance preservation itself2.858s. Check/lint and affected app/shell/storage instrumentation APK compilation passed. No additional emulator run.

Each APK passed independent 16KiB ZIP alignment (`zipalign -c -P 16 4`, exit0). DEX contains the appearance screen in app, actual appearance preservation probe in appTest, and shared appearance UI tests in shellTest.

| APK | Bytes | SHA-256 |
|---|---:|---|
| app | 21242373 | dc87236c8c8c21c9d22e7823181ded79d9e584764675e2f4705dd90c3382c2d7 |
| appTest | 1478420 | 8d15173064bb61881747c933afb6329a835426aa77a6b484caba749c49a66f4a |
| shellTest | 15157702 | dfd886c015f617b7411d3358d90320a98dab7b36509e886df1c81417613c7bc9 |
| storageTest | 10273143 | 2a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d |

This evidence covers the committed appearance source, not the next commander extension. Native execution, Fold7/TalkBack, art loading and full-app acceptance remain unverified. No finished APK delivery. Further testing cadence follows D-28: focused checks for the next related implementation batch, with a broader gate only when a new risk boundary justifies it.
