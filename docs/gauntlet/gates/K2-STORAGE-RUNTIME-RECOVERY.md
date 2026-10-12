# K2 storage runtime recovery

Status: incomplete; corrected source independently accepted, hosted verification pending. This is not storage integration or K2 completion.

## Retained failure diagnosis

Original run [38200190935](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38200190935), source `080cd39145a48faf3a234e4a6c400c84f5b2db99`: all three emulator XML reports execute 19 tests with exactly one failure, zero errors/skips. The sole failing method is `foreignCoroutineAndThreadCallsCannotPoisonOwnerTransaction`; all other 18 storage methods pass on each image. Failure occurs at owner query after nested coroutine scope/thread/deferred work, with INVALID_INPUT: Transaction belongs to its active callback coroutine. This does not establish complete runtime acceptance.

Retained exact XML in docs/verification/k2-storage-runtime:

| Image | Artifact ID | XML SHA-256 |
| --- | --- | --- |
| API23 default | 11724909462 | 3215fb6211fd7d22a6a01f8402cfcd33382f7bbaee690c08ec9ddc96ee31ff95 |
| API36 default, 4096-byte pages | 11725250569 | 536d1b3770b7373d43ac077fe334967fb9fc783ea0725d4fabacad347360271a |
| API36 ps16k, 16384-byte pages | 11725660164 | c0115eac91a83e3904eb17929c6c4346f6b6ed2bf730291a7f64cb9dc7474197 |

API23 additionally reports that additionalTestOutput is unsupported, followed by AndroidAdditionalTestOutputCollector recursion/File name too long. XML still records the same assertion failure; do not mistake collector noise for its cause. Resolve any remaining collector failure from the corrected run's actual evidence, without dropping mandatory instrumentation results.

## Correction and independent review

Original agent/cx-storage-01 and all prior branches preserved. Recovery branch agent/cx-storage-recovery-01 merges current main without dropping integrated settings; settings.gradle includes both staged Android modules. Source correction `985e03d91a7d61a5e23c2f3890698f4ad4fdd300` replaces a Job-capturing ThreadContextElement with a stable token using CopyableThreadContextElement. Capture the actually installed element token, since withContext can copy appended elements. launch/async child builders receive distinct tokens; sequential scopes preserve the token. Suspend query/execute retain their exact Job check. Synchronous abort accepts the callback's sequential scopes and rejects independently launched coroutines, foreign threads and expired scopes.

Coroutines source semantics verified against [1.10.2 CoroutineContext.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/1.10.2/kotlinx-coroutines-core/jvm/src/CoroutineContext.kt). Changing dispatchers or trusting arbitrary completed Job objects was rejected as insufficient.

Independent reviewer requested gpt-6-astra/high, separate storage_review agent. Reviewed exact19764a2..985e03d: accepted for hosted verification, no new source blocker. Runtime remains pending. New regressions exercise default launch, undispatched launch, async, resumed owner query/write, and immediate owner abort after suspended nested scopes; existing raw-thread/escaped-scope checks remain. Optional stronger INVALID_INPUT assertions may be added later; no scope/security budget is relaxed.

## Exact continuation

Observe [38248782526](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38248782526) at985e03d. Require actual JVM/lint/library assembly and all three instrumentation suites, with 20 storage methods per image, before integrating corrected storage. Download artifacts via connected GitHub artifact tool if gh download's signed blob URL returns Forbidden; the connector download succeeded in this session. Update this report with exact results and any remaining API23 collector diagnosis. No unchanged retry without new evidence.

Then reconcile core/model+common registry envelope and implement genuine bounded catalog importer/app per orch-kotlin-m0-01, preserving original source/QA/settings/core evidence. No app/importer/debug release or physical device result exists yet.
