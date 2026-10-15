# Durable prompt overrides — scoped host integration

Accepted candidate source `4ee30f7fa534f475e726e89a29e695016f87e433`, Android tree `f2ea2b2b3199440297dae71e0412067c173c0e62`. Independent source review ACK; independent Sol medium runtime readback ACK. This advances retained H09 within campaign `ai-history`; it does not complete H08 or the full AI/release requirements.

The existing advanced prompt editor now explicitly saves separate ROAST/CHAT/UPGRADE/SURPRISE templates through the real AppGraph-bound SqlLocalPromptOverrides. Reset restores the current shipped body while retaining a revisioned reset row. Dirty drafts, newer edits, unrelated modes and render results survive late persistence acknowledgements. Unknown outcomes reload authoritative state without replaying writes; different shipped versions require explicit adoption/reset. Invalid templates use the existing engine parser before database access. No credentials/provider/account/network is needed.

## Executed boundary

One assembled local host gate: **34 tests / 6 fresh XML, zero failures, errors or skips; 1m25s**. All741 tracked Android source pins stayed frozen and match the candidate. Exact command is preserved in `/tmp/cl-prompt-overrides-01/run-assembled-gate.sh`:

```sh
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 \
 :data:storage:testDebugUnitTest --tests '*SqlLocalPromptOverridesTest' \
 :feature:shell:testDebugUnitTest --tests '*PromptOverrideControllerTest' --tests '*OfflinePromptControllerTest' \
 :app:testDebugUnitTest --tests '*PromptOverridesAppIntegrationTest' --tests '*OfflinePromptAppIntegrationTest' --tests '*ProviderProfilesRoomIntegrationTest'
```

- 10 actual bundled-SQLite/parser cases: strict UTF8/grammar/bounds before I/O, exact bodies/reopen, CAS-before-no-op/reset ABA, other-mode conservation, future-template preservation/unsupported storage, checksum/type/length preflight, overflow/clock and cancellation.
- 11 new controller cases plus10 unchanged offline controller cases: four-mode loading, errors/stale/unknown/cancelled outcomes, version review/adoption, newer drafts and old callbacks, delayed acknowledgement/result conservation and legacy session-only compatibility.
- 3 actual app cases: new full KSP16→17 migration and four-mode explicit UI save/preview/independent Room read/recreation/reset; retained offline prompt/clipboard/grounding journey; existing full KSP15→17 provider-profile/legacy-state conservation proof updated to the actual current schema.

The new app fixture preserves all49 old entity definitions, seeded opaque legacy life BLOB/Unicode and a provider tombstone, with exactly one new prompt_override table. Deck/inventory/settings/catalog state and other saved modes are conserved. Opaque bytes establish physical preservation, not semantic validity. Activity recreation is not cold process restart; independent Room reopen establishes the stated persistence evidence.

Environment: existing pinned Gradle9.6/AGP9.4.1/Kotlin2.2.10/KSP2.3.12/Room3-3.0.3/SQLite2.7.1/JDK17, host RobolectricSDK34; offline dependencies, root-only build slot/max-workers2. No dependency or SDK repin. Raw evidence `/tmp/cl-prompt-overrides-01/evidence/assembled-attempt2/{source.json,summary.json,gradle.log,test-results}`. Runner SHA256 `5ec4f9df3271bf355f60bb916a5e21565dc173c734eebaf7890653c7b268f908`; log SHA256 `78ffb6b79a07c0b0f4e3de6830338adaaaae80b9ce8558b00d74a7665752f093`. XML hashes and individual names are in summary/XML, not inferred from expected counts.

## Exact source/review lineage

- Reviewed core/schema17 boundary `b8dc813`; compile/KSP PASS25s, zero tests requested. Actual KSP17 export `7fd1f6ea2be7f5ccf1ab988f323c43a2ea98db24c22420e124a37f438e1f414f`, all49 schema16 entities unchanged. READY01 missing independent storage-format discriminator corrected before READY02/source ACK; format1 and template version remain distinct.
- SQL original `765b47fa78bb72065194b3304f8311dd4fa25513`, parent `23a7f3f643f6ec0a578ecc0b199bd88c97000bdd`, exact3 new paths→root `e05fe686d2949d1f5ac76d418fd24a509eaa7bfd`.
- UI original `89157b0a461880aae0fe96913d2e603855a5fc67`, parent `bb06734e40716b3d1cd1dbfa424798f005e23160`, exact3 modified+2 new paths→root `6cd9480`. SOURCE01 held unconditional global invalidation from unrelated loads/older save acknowledgements; SOURCE02 updates bookkeeping without clearing unchanged active results and adds one focused regression. Independent Astra high source ACK.
- App proof original `9ac90feb8f04b6bb2bf30a81e0f5913e166e87f8`, same parentbb06734, exact2 new paths→root `db67c8e`. Source review corrected a LazyColumn preview wait to scroll before asserting; no runtime failure was claimed for that correction. Root verified requested narrow fix.
- Root real AppGraph binding `17d24d7`, controller binding `8f6081c`; shared source ACK. Existing provider proof capability/version expectations updated to17 while preserving all old-state assertions.
- All original worker worktrees clean/retained, exact parents/path sets/live files/committed blobs/manifests verified before integration; locks released. Root verification manifests `/tmp/cl-prompt-overrides-01/verified-{return,ui-return,proof-return}.json`.
- Assembled attempt1 exact `db67c8e` FAILED12s at shell compilation: cross-module public row.body cannot smart-cast. Zero fresh XML/tests executed. Evidence retained at `assembled-attempt1`, log `7b43ed610a8dc52e810097ce27d06495c9eccca936648abb232cb9d26e26b1ee`. Root `4ee30f7` uses the equivalent stable nullable let expression; no assertion/invariant removed. Attempt2 success does not relabel attempt1.

Not run here: new native/emulator tests, cold process-kill/UI, Fold7/accessibility/visual/performance, backup/restore, provider/streaming/history, comprehensive final v1 acceptance or APK delivery. Prior vault four-case native proof remains separately source-scoped. Full retained history/provider/grounding and release obligations remain named campaign work; no implicit deferral or main-merge authority follows.
