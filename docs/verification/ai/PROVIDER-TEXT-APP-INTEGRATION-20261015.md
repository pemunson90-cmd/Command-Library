# Explicit custom HTTPS provider text — integration in progress

Replacement root resolved remote continuation `a1b140358d203b52fc772f4c16599fea8c0189a0`, verified explicit voluntary release and claimed lease in `1dc7a49c`. No main integration or finished APK acceptance. Current provider packet remains `coordination/tasks/cx-campaign-provider-text-01.md`; D30 future deck intake does not change priority.

Independent Astra source review ACKed exact HTTP SOURCE02 `4b821d409cc4765676ae70d026e219faf04e9b91` and CC SOURCE01 `81001dedfb6ac0d43bc082bbfdf8011a863f93f6`, each parent `1ca7770199bdc5d19ac933ebde73825f9f491f74`. Root verified expected M1+A2/NEW5 paths, all committed/live blobs and clean review worktrees, then integrated unchanged in `1a0dbcc8`/`81331eb0`. Prior worker temporary manifests were unavailable in this new environment; recreated exact Git-anchored SHA256 manifests are `/tmp/cl-provider-text-01/{http,cc-codec}-git-return.sha256`. No claim of comparison against the absent original manifests. The original commits and reconstructed isolated worktrees remain preserved.

HTTP SOURCE01 had a source-only HOLD on a 5ms test budget, corrected before this review to 5s budget/6s backoff in SOURCE02. Production metadata adds verified status/Retry-After with unknown charge, excludes bodies/private diagnostics, and preserves existing no-replay policy. Seven synthetic authored tests NOT_RUN.

CC SOURCE01 has thirteen authored synthetic tests NOT_RUN. Source review covered request omission/strict JSON and Unicode bounds, SSE byte splits, metadata/order admission, usage, unsupported tools/refusal, cancellation and terminal completion only after finish reason plus `[DONE]`. These fixtures do not establish authentic provider eligibility, compatibility or billing.

Root source `8190a8ea` was independently HOLD: Compose consent/send callbacks read current delegated frame rather than rendered frame; IO progress callback could race navigation state. `1e9c97a1` captures renderedFrame and marshals progress to the controller scope dispatcher while retaining the collection job. Reviewer inspected these corrections and ACKed exact `1e9c97a167c336011992252c9198e62e6f009a27`. Five shell oracles authored NOT_RUN. Preexisting unrun auth fixture referenced nonexistent FailureCode.NOT_FOUND; corrected to existing PERSISTENCE for its synthetic vault failure, assertions unchanged. Four auth oracles NOT_RUN.

Root AppGraph/real-network/real-vault/recovered-history binding and explicit exact prompt/profile revision/destination/model/private-data/possible-charge/local-history consent are drafted. A new attempt always requires fresh consent and ID; uncertain acknowledgements permit only original-ID reads or explicit abandonment preserving any stored history. Navigation cancels collection; restart/open never sends. Durable coordinator and actual app proof are separately assigned disjoint NEW3/NEW2 packets; no provider runtime or compile claim at this point.

Next: finish independent coordinator/shared/proof source reviews, integrate exact returns, run one assembled local network/auth/SSE/history/UI/AppGraph gate. Keep failed-source and executed-attempt lineage. No live paid calls, new dependencies/pins or source-policy bypass. Full provider protocols/eligibility/model discovery/tools, native/Fold7/accessibility/performance/backup/signing/final release remain open.

Independent Astra final batch SOURCE ACK confirmed exact root1e9c97a1, coordinatorNEW3 hashes5b9211e0/478a5d35/456253df and appproofSOURCE02 hashes103da735/5b70f256. Parent/path/frozenhash checks passed; root copied exactNEW3 into12fd07a8 andNEW2 intoddff703b, preserving workerdrafts. Coordinator21/root5/appproof1 authored oracles source-reviewed. Proof SOURCE01 HOLD incorrectly expected dialog dismissal after Activity recreation; SOURCE02 verifies retained completed prompt/reply/status and consentoff/senddisabled/requestcount1, then explicitly dismisses before history navigation. No production behavior or durability/no-replay assertion weakened. One root-only assembled local gate started atddff703b; pending result.

## Executed assembled host gate — scoped runtime ACK

Exact candidate `6519bff16b558356d5f1b3c62194c9b516525dcd`, Android tree `3cd772d067e712b4f898ae5e4ead9d3157dadea3`: successful local invocation 21s, 79 scoped XML-reported cases across11 suites, zero failures/errors/skips,774 tracked Android source pins unchanged. AI38/network23/storage8 passed in attempt3 and were legitimately UP-TO-DATE in attempt4 with unchanged relevant source. Shell8/app2 executed in attempt4; this is **not79 freshly rerun tests**. No lint/native/APK/signing/Fold7 gate was requested or inferred.

Root-only/maxworkers2 command (attempt4 offline, existing accepted dependency pins):

```sh
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 \
 :feature:ai:test --tests '*ChatCompletionsWireCodecTest' --tests '*HttpsChatCompletionsClientTest' --tests '*DurableProviderGenerationTest' \
 :data:network:testDebugUnitTest --tests '*HttpFailureMetadataTest' --tests '*Acquisition429MetadataTest' \
 --tests '*OkHttpNetworkClientTest.zeroRetryAfterCannotTriggerHiddenPostOrChargedReplay' \
 --tests '*OkHttpNetworkClientTest.misdirectedStatusIsPreservedWithoutReplayAndWireGuardRejectsSecondProceed' \
 --tests '*OkHttpNetworkClientTest.coroutineAndTokenCancelBlockedReadWithinOneSecond' \
 --tests '*OkHttpNetworkClientTest.syntheticTlsSlowHandshakeSucceedsAndCancellationAbortsHandshake' \
 --tests '*OkHttpNetworkClientTest.globalCookieHandlerAndAuthenticatorNeverSupplyCredentials' \
 :data:storage:testDebugUnitTest --tests '*SqlLocalAiHistoryTest' \
 :feature:shell:testDebugUnitTest --tests '*ProviderGenerationControllerTest' --tests '*AiHistoryControllerTest' \
 :app:testDebugUnitTest --tests '*ProviderGenerationAppIntegrationTest' --tests '*AiHistoryAppIntegrationTest'
```

Coverage: CC13, auth4, durable coordinator21; HTTP metadata7, acquisition429 regression11, selected old network5; actual bundled-SQLite history8; new controller5/prior historycontroller3; new actual provider app1/prior actual historyapp1. Coordinator covers ACK-before-single-POST, no ACK/no dispatch, exact capture/null options, revision/epoch/prefix, usage/zero/cache, incomplete and malformed streams, immediate upstream closure, bounded cancellation and collector-failure cleanup, uncertain possible commits with no overwrite/replay, explicit fresh-ID retry lineage. Actual app checks savedprofile revision2/manualmodel, exact rendered prompt, no send on open/selection/consent alone, separately visible committed PENDING before synthetic transport, committed STREAMING prefix, COMPLETE, unchanged prompt/provenance, independent Room reopen, retained completed UI across recreation, consentoff/senddisabled, one request and history browsing. Production AppGraph generation binding is source-reviewed; test replaces only the cached generation capability with the same real AppProviderGenerationAccess using its synthetic NetworkClient factory via test-only reflection. It retains actual history/codec/client/Room/UI and tests no real provider/socket/account/billing or native credential.

| Attempt | Result and lineage | Log SHA256 |
| --- | --- | --- |
| 1 atddff703b | FAILED25s task configuration, zero tests: root runner used aggregate Android network `test` with `--tests`; corrected to `testDebugUnitTest`, no product edit | `56cb98f893b83f30a2db9ddf721f1b7e4cf9c6efbac2ddf255e8f07d97b74cf1` |
| 2 at9499eabd | FAILED2s dependency resolution, zero tests: fresh environment lacked existing pinned `sqlite-bundled-jvm:2.7.1` offline; allowed normal configured resolution for attempt3, no pins changed | `e1f92c3d97190865e1ece8b5f76f30ada5dafebcf3543863e3a9da5e070742e9` |
| 3 at9499eabd | FAILED2m10s,77cases/9XML/1failure/0errors/skips; app notrun. Root controller JavaProxy fixture rejected actual inline-ID JVM method `getTurn-tWyn4Fw`, proved by javap. Test-only6519bff1 matches source method prefix and additionally asserts exact original unboxed ID; product/assertions retained | `0c53a54c9c2d866681b830b9f34c7836c84a9abc69085eb73345f683941e42c4` |
| 4 at6519bff1 | SUCCESS21s, scoped79/11XML,69unchanged reused+10fresh,0failures/errors/skips;774pins unchanged | `dc2174fbca5c4dac5c132a9ca121a242f960b360e7d72d5eb26ac123b05759da` |

Full local runner/pins/copiedXML/summary at `/tmp/cl-provider-text-01/evidence/assembled-attempt{1,2,3,4}`; Independent Sol medium scoped runtime ACK confirmed exact head/tree/pins/logs/XML,69 reused+10fresh, sole test-only repair, and actual app seam/limits. Original failed-source/runtime attempts remain failed. Normal continuation push initially GH007 rejected; repository-local Codex noreply configuration restored normal subsequent pushes, no force-push or published history rewrite. Original drafts/commits remain preserved.

Review limitation retained: mutable local runner now records attempt4; earlier commands are disclosed by the recorded runner edits (attempt1 wrongaggregate/offline, attempt2 correctedtask/offline, attempt3 correctedtask/online), not independently recoverable from logs alone. Subsequent evidence tooling must retain the executed runner alongside each attempt before launch. No broader result is inferred.
