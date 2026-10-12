# K3 16K full-run triage

- Artifact: `k3-16k.zip`, extracted to `/workspace/scratch/k3-16k-results`.
- Run: `38292134079`; catalog revision: `01e0c40` (as supplied with the artifact).
- Four connected Android test XML reports were present; no JVM `build/test-results` XML was included.

| Module | XML tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|
| `app` | 3 | 0 | 0 | 0 |
| `data/storage` | 20 | 0 | 0 | 0 |
| `feature/catalog` | 6 | 0 | 0 | 0 |
| `feature/shell` | 17 | 1 | 0 | 0 |
| **Total** | **46** | **1** | **0** | **0** |

- Sole failure: `com.architectpsg.commandlibrary.feature.shell.ShellUiTest.expandedWidthShowsResultsAndDetailsTogether` in `feature/shell/build/outputs/androidTest-results/connected/debug/TEST-k2(AVD) - 16.xml`.
- Error: `androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 20000 ms`; stack points to `ShellUiHarness.waitForText(ShellUiHarness.kt:104)` and `ShellUiTest.kt:305`.
- Requested catalog test `LocalCatalogInstrumentedTest.syntheticLateCancellationAfterRealActivationCommitPreservesIndexAndReopen` executed and passed (one testcase, no failure/error) in `feature/catalog/build/outputs/androidTest-results/connected/debug/TEST-k2(AVD) - 16.xml`.
