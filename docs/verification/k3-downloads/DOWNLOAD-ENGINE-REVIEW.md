# Independent acquisition engine re-review

**Current disposition: ACCEPT bounded engine source `e658091a727631f6cdba46f2dc3f1ac4ff6090a5` for integration. All four independently reproduced defects from `36343f746f84cb40a944110c3199505ce155c788` are resolved; no new source blocker found in this focused re-review. Production binding and native acceptance remain pending the explicit integration requirements below.**

Reviewed the exact committed LocalAcquisition.kt/test/handoff changes against the previous candidate and independent findings. Worktree was clean; no reviewer repository edits occurred.

## Corrections verified

- **Stale controls:** queue validates expected revision and legal state under the per-job control lock before signalling a worker, then validates again after quiescence. Stale start/resume and invalid-state resume leave the running transfer untouched.
- **Cancellation and pins:** admission/control/file/search mutex acquisition checks coroutine and token cancellation every 50 ms, including after acquisition. Controlled cancel/join waits let the waiting caller withdraw while the runner retains its own file fence until settlement; no premature pause acknowledgment is published. Existing pin holders remain protected.
- **Durable removal:** the transaction deletes artifact/seen-ID rows, marks CANCELLED, and persists removal-pending-v1 before touching files. Recovery processes that marker before skipping ordinary cancelled jobs. Attempt-derived partial/candidate/sync keys are removed, then a directory-sync sentinel establishes durability even when an earlier unlink succeeded but its directory fsync failed. Only a checked CAS clears the marker; failures retain it and permit other recovery work to proceed. Ordinary cancellation still retains bytes.
- **Scoped removal:** removal checks current consent membership under control before signalling, checks it again under file exclusion, and cancels/joins only selected workers. Replacement consent and unrelated active transfers are preserved.
- **Partial recovery:** missing/short transfer files become per-job FAILED/CORRUPT_DATA without changing durable counters or job-wide reservations; later jobs still reconcile. Explicit resume creates a fresh bounded attempt rather than reconstructing missing bytes.

## Physical dispatch authorization

The new authorizeWireDispatch seam uses a synchronized IdentityHashMap keyed by the exact originally registered RequestSpec object. Equal copies and released contexts fail closed. Before each enqueue it reloads and matches immutable plan, attempt, owner, revision, RUNNING state and consent ID, reads authoritative settings/live policy, validates the actual URI against source policy, and invokes the trusted endpoint factory for that target.

The shared consent gate covers the final cancellation/context checks and synchronous enqueue, with no suspension between those checks and enqueue. Registration happens under the gate; collection starts after releasing it. Physical English starts are spaced at least 600 ms using monotonic time, including delays in transport origin slots; waits release the gate and repeat validation. Context removal in finally prevents later reuse. A scrubbed transport denial triggers an authoritative policy recheck, preserving WAITING_CONSENT/NETWORK/WIFI classification. Synthetic tests exercise registered/forged/released identity, changed durable context, delayed-slot revocation, redirected targets, unapproved origins and actual guarded start timing.

**Required binding:** root must construct only the reviewed guarded production transport for this engine, passing the ORIGINAL registered spec and actual target to this callback for every initial and redirected physical enqueue. A copied RequestSpec or an unguarded client does not meet this protocol. This source review does not claim the future factory or real OkHttp integration has been executed. The shared coordinator must also remain the one used by settled settings writes. Settings removal runs after settlement and outside that gate; provider descriptors/private flows remain within their callback pins.

The prior inspected plan/row identity, checked CAS rollback, raw Range/EOF handling, English page/ID/revalidation budget, prepared artifact recovery and cold verified-read requirements remain intact in the revised diff.

## Independent verification

Fresh actual-source compilation of core-model, core-common and pure catalog production/tests into `/workspace/scratch/k3-engine-independent-revised` passed. JUnit: **OK (134 tests), 6.707 seconds**, including 42 engine cases. Only diagnostics were missing optional core test-resource-directory warnings. AndroidArtifactFiles.kt was explicitly excluded. Strict explicit-API compilation remains worker-reported evidence, not rerun here.

Recompiled and reran the original independent probe class against those new engine classes: **OK (31 tests), 1.178 seconds**, including all four previously failing regressions. The sole semantic fixture adaptation sets the post-removal-commit crash state's failure_payload to the new removal-pending-v1 marker; assertions remain unchanged. Source/output: `/workspace/scratch/LocalAcquisitionIndependentRevisedProbe.kt` and `/workspace/scratch/k3-engine-independent-revised-probes-result.txt`. The previous failing probe/output remain separately available for provenance.

Not run: engine + actual Room/DataStore/OkHttp integrated execution, hosted Android Gradle/lint/API23/API36/16KiB, native power-loss/provider/scheduler lifetimes, or genuine transfer/import/activation. This is source integration approval, not K3/full-app completion.

Latest continuation: focused exact-SHA re-review and fresh regressions complete. Root may integrate this engine, finish and verify the guarded-only factory and lifetime/removal bindings, then obtain the required exact-source hosted/native integration evidence.
