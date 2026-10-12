# K2 final acceptance

Status: ACCEPTED for the K2 Kotlin Android foundation section.

Checkpoint target: `cp-20261012-k2-accepted-27`  
Accepted product base: `21bf53f30ca0ddb9c482d5f605a35f82dff7fcc4`  
Final tested PR head merged unchanged: `2e08e6964b0a1d9ba6dd0017a0c1b55c3ebb597c`  
Final hosted Android run: `38275330760`

This closes K2 only. It does not claim the complete application, public release, Windows version, sync, Fold7 manual acceptance, final TalkBack acceptance, final performance acceptance, pricing/decks/collection/life/recommendation/AI modules, or permanent signing acceptance.

## Accepted implementation

The accepted foundation contains the real Android `:app`, the Compose shell, the process-wide storage/catalog/settings graph, Room3/SQLite-backed local storage, DataStore settings, the bounded user-selected local-file catalog importer, local persisted lookup/status, and the previously accepted twenty-component Kotlin public contract inventory.

The UI exposes only implemented foundation destinations and truthful empty/importing/success/failure/cancel states. Import remains user initiated. No automatic product-data download, Windows implementation, synchronization path, fake success adapter, or implicit non-English download consent was added in K2.

## Review disposition

The final integration was reviewed independently from the Claude Code author session by GPT-5.6 Sol against the K2 task card, accepted ownership/contract boundaries, app wiring, permissions, workflow behavior and retained evidence.

Findings resolved before acceptance:

1. The imported shell initially exposed an import action whose measured height was about 40 dp while the test/interaction contract required at least 48 dp. The import action family was corrected to a 48 dp minimum before merge.
2. The first integrated 16 KiB run exposed an intermittent expanded-layout search timeout. A retry passed, but the failure reproduced after merge, so it was not dismissed as a harmless flake.
3. Increasing the observation window alone did not fix the reproduced 16 KiB failure. Retained logs showed emulator `InputConnection`/IME delays around text injection. The shared shell search test helper was changed to use Compose `SetText` semantics (`performTextReplacement`) plus Compose idle synchronization instead of emulator IME injection. Product behavior and assertions were unchanged.
4. One intermediate API 36 default job failed before instrumentation because its runner could not resolve the already-pinned Kotlin JVM plugin `2.2.10` from configured repositories. It produced no test XML. That transport failure was kept distinct from product/test failures and was superseded by the complete green final run.

No unresolved K2 source, durability, wiring, UI, or hosted-runtime blocker remains in the accepted section.

## Hosted evidence

Final candidate `2e08e6964b0a1d9ba6dd0017a0c1b55c3ebb597c` completed Android workflow run `38275330760` successfully:

- JVM/build lane: success.
- API 23 default x86_64 emulator: success.
- API 36 default x86_64 emulator: success.
- API 36 `google_apis_ps16k` x86_64 emulator: success.
- Debug APK assembly: success.
- 16 KiB ZIP alignment check: success.

The retained JVM artifact contains 156 executed XML-reported tests with 0 failures, 0 errors and 0 skipped tests across the included JVM test-result trees.

Retained debug APK:

- path in artifact: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `0bab305748c0720df91fc44d4953c1386997eef9fe32b5f9061e7dcc5a1ce659`
- workflow artifact: `android-k2-jvm-2e08e6964b0a1d9ba6dd0017a0c1b55c3ebb597c`
- artifact ID: `11758743497`

The final emulator artifacts are retained under the same run for API 23, API 36 default and API 36 16 KiB.

## Merge and continuity evidence

PR #2 integrated the reviewed shell/app wiring and touch-target correction into `main` at `b1707c82823fa2dbeb79ef4105c4b5f8681ffdb6`.

PR #3 integrated the deterministic hosted-shell test correction using an expected-head guard on `2e08e6964b0a1d9ba6dd0017a0c1b55c3ebb597c`. The resulting `main` product base is `21bf53f30ca0ddb9c482d5f605a35f82dff7fcc4`.

Continuity run `38276257988` completed successfully on that `main` merge commit.

GitHub did not enqueue an additional Android matrix for the PR #3 merge commit. Acceptance therefore relies on the complete green matrix on the exact tested PR head that was merged with the expected-head guard, plus successful continuity on the resulting main commit. No unrun post-merge Android job is represented as a pass.

## Functional finish-line disposition

K2 finish-line items are accepted as follows:

- real Android application module and application ID: implemented and built;
- hosted launch/instrumentation path: exercised by connected Android tests on the required emulator matrix;
- truthful catalog generation/source/as-of/language presentation: implemented for the K2 local catalog scope;
- persisted settings with English default: implemented;
- user-selected local file import: implemented with bounded streaming and visible limits/cancel/error states;
- persisted local name lookup: implemented and exercised;
- empty/error/import-in-progress behavior: implemented, not simulated;
- real storage/catalog/settings bootstrap: implemented as one process-wide app graph;
- debug artifact/checksum/alignment evidence: retained above;
- independent final review: accepted after the touch-target and deterministic-input corrections.

Previously accepted K2 component evidence remains in force, including core contracts, settings, storage recovery/durability work, genuine tiny English fixture lineage, strict catalog parsing/atomic activation and packaged storage/catalog checks. This report does not replace those narrower reports; it closes the integrated section.

## Requirement scope

K2 advances only the foundation portions identified by its task card, including portions of N01/N03/A06/A08/K01/K02. The repository's complete 64-requirement and 213-parity inventories remain authoritative. Later requirements are not promoted to complete by this foundation acceptance.

## Closeout actions

- Mark `orch-kotlin-m0-01` accepted.
- Remove stale K2 catalog and shell component locks.
- Release the expired Claude orchestrator lease rather than transferring it implicitly.
- Advance queue/state/CURRENT/human state to checkpoint `cp-20261012-k2-accepted-27`.
- Set next task to `orch-kotlin-k3-offline-data-01`, step 1, ready for a new lease claim.
- Publish the K3 task card without starting K3 implementation.

K3 begins only after its own lease claim and must preserve the Android-first/no-sync/no-Windows boundaries in `V1_KOTLIN_EXECUTION_SPEC.md`.
