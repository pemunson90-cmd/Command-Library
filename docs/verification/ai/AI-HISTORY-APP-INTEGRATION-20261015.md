# Local AI history — scoped host integration

Candidate source `31bfe7884db56b0f4b704e25a525ff7a08d6d2cf`, Android tree `553634f69f635c39a476c92aea8f534d952f8f23`. Independent Astra source ACK for core/schema, codec SOURCE02, SQL SOURCE02, actual UI/shared composition and app proof; independent Sol medium scoped runtime ACK. This implements the local-history prerequisite of H08, not actual provider streaming or full H08/release acceptance.

The actual saved-deck prompt editor explicitly saves the exact rendered prompt and pasted reply after privacy confirmation. Reply editing may clear transient preview but retains the immutable selected artifact; changing prompt inputs requires another render. Per-deck history lists bounded headers/turn summaries, loads content on selection, survives independent Room reopen/activity recreation and explicitly deletes a whole conversation. Stored provenance includes effective-template hash, override flag, catalog snapshot, exact prompt/bindings/deck revision. Offline captures are labeled as saved prompt/reply with no provider/model/usage claim. Current-deck revalidation opens the real existing prompt/reply editor; advice is never applied automatically.

The actual root lazy factory exposes SqlLocalAiHistory only after successful recovery using one process UUID; history initialization failures remain retryable without blocking unrelated screens. Nine real methods cover bounded paging, header/turn reads, creation, CAS append/checkpoints, explicit retry identity, deletion and foreign-epoch interruption. Terminal attempts are immutable; partial raw bytes survive recovery; same-epoch reopen preserves active attempts. IDs remain reserved after deletion, while titles and every prompt/reply payload are cleared. Unknown costs remain hidden and differ from known zero; cached counts and exact decimal/native units stay distinct.

## Executed check

**43 tests / 9 fresh XML, zero failures, errors or skips; 2m04s; all758 tracked Android pins frozen.** One combined local offline Gradle invocation, root-only build slot/max-workers2:

```sh
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 \
 :data:storage:testDebugUnitTest --tests '*AiHistoryPayloadCodecTest' --tests '*SqlLocalAiHistoryTest' \
 :feature:shell:testDebugUnitTest --tests '*AiHistoryControllerTest' --tests '*PromptOverrideControllerTest' --tests '*OfflinePromptControllerTest' \
 :app:testDebugUnitTest --tests '*AiHistoryAppIntegrationTest' --tests '*PromptOverridesAppIntegrationTest' --tests '*OfflinePromptAppIntegrationTest' --tests '*ProviderProfilesRoomIntegrationTest'
```

- Codec6 + actual bundled-SQLite8: large real-engine artifact including duplicated bindings, strict UTF8/budgets/version/corruption and exact Knowledge/decimal/native-unit roundtrip; source-qualified deck isolation, paging, CAS/prefix/terminal/retry rules, foreign-epoch recovery versus same-epoch reopen, byte conservation, deletion/tombstones, cancellation/rollback and metadata-before-BLOB preflight.
- Shell25: history acknowledgement-loss/navigation/init cases3, retained override cases11, offline cases11 including the new captured-artifact/stale-callback regression. Memory doubles establish only their stated controller behavior.
- Actual app4: new full KSP17→Room18/CHAT seeded override/render→reply edit→privacy confirmation/save/independent Room/recreation/history content/delete/private projection clearing/old-state conservation; retained offline grounding, four-mode overrides and full KSP15→18 provider/legacy preservation regressions.

Existing JDK17/Gradle9.6/AGP9.4.1/Kotlin2.2.10/KSP2.3.12/Room3-3.0.3/SQLite2.7.1 pins and RobolectricSDK34; no repin. Activity recreation is not cold process death. Physical opaque legacy life bytes prove preservation, not semantic validity. No provider/account/network/key use occurred.

Evidence `/tmp/cl-ai-history-01/evidence/assembled-attempt2/{source.json,summary.json,gradle.log,test-results}`; runner `/tmp/cl-ai-history-01/run-assembled-gate.sh` SHA256 `921a1d84183ae36dfc48f43a60993dff0e39b5558ba5759bc2c5df610aa7e24e`; log `d456ce0590c4b7ce10831fa661355b45b905726278236b4c6f107d0db20e010f`. Individual names/XML hashes/counts are preserved in the evidence, not inferred from the intended filters.

## Source/failure lineage

Reviewed core/schema boundary `ccfe027`: compile/KSP PASS21s, zero tests requested. Actual KSP18 export `fb97ec30f5b331b381f99431de44ed715b16625b271d003bca2e59c8a4eef8e3`, all50 earlier entities unchanged, exactly2 added tables/4 indexes; actual DDL equals migration. Previously unused Conversation/Turn types were refined directly; unbounded embedded turns and unused persisted-validation authority were removed explicitly. Raw text is reviewed on demand, not silently discarded from a Known validation object.

Exact clean worker returns: codec5 original `6ef3eaf8107bd7d1ee1316c17f97ee73373c0f18`→root `201dc5e`; SQL3 `d2169abe688ec5ac2e6e38b112fb66d5f7239eb2`→`83ef764`, both parent `a9e746b91374b36e99d4748b2ecb6a8c98558d50`; app proof2 `7f8a656db229617680fee1d7a11201e9df6624fb`→`97b9fe9`, parent `201dc5e645a5fdd33c5417451b9de8cf9a6eaf8c`. Parent/path/live/committed/hash checks recorded in `/tmp/cl-ai-history-01/verified-{codec,sql,proof}-return.json`. Original clean worktrees retained, claims released. Root UI/shared14 paths `671940c` independently source-ACKed; current-schema regression fixture update `8c0c03b` separately source-ACKed.

Source HOLDs corrected before execution: codec PENDING must have empty raw text and Unknown usage, with valid STREAMING→INTERRUPTED byte-preserving fixture; SQL wrong test codec import and nullable cross-module model property replaced with actual import/stable local. Runtime attempt1 exact `8c0c03b` FAILED19s at shell compile because Text received an integer resource ID, zero XML/tests. Root `31bfe78` resolves stringResource before Text; no invariant/assertion removed. Failed evidence `assembled-attempt1`, log `bba981c56b8efe9d8d2b707aae51b860d79e2245d4f3b78816a31031004ab9ce` remains failed; attempt2 passed.

Actual provider POST/SSE/socket cancellation/model discovery/tools/eligibility, native process-kill/UI, full backup/restore, Fold7/accessibility/visual/performance/signing/final v1 acceptance remain named following work. No new emulator/broad suite/lint ritual, main merge or finished APK delivery follows. D-30 PR23/issue24 deck intake was recorded during this safe checkpoint without changing active assignments/priorities or implementing tags.
