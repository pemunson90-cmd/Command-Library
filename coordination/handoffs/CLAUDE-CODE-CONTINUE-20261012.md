# Claude Code continuation packet — K2 after catalog merge

This is the single-file continuation brief for the next Claude Code session. It is intentionally self-contained so you do **not** need to reread the full history or crawl every branch before acting. Treat repository evidence as authoritative over stale narrative state files where explicitly noted below.

## Mission

Continue K2 from the current durable repository state. The immediate job is **review + integrate the finished Compose shell branch, wire the real app, run hosted verification, then update the coordination checkpoint atomically**. Do not reopen already accepted catalog/core/settings/storage work unless a current build/test exposes a concrete regression.

Repository: `pemunson90-cmd/Command-Library`
Integration branch: `main`

## Critical reality correction: CURRENT/QUEUE/state are stale

`coordination/handoffs/CURRENT.md`, `coordination/QUEUE.md`, `coordination/state.json`, and `coordination/ORCHESTRATOR.json` still describe the state before final catalog integration. Their last checkpoint is `cp-20261012-k2-catalog-review-25` and they still say final catalog hosted acceptance is pending. That is no longer true.

The prior Codex orchestrator lease in `coordination/ORCHESTRATOR.json` expired at `2026-10-12T13:54:34Z`. Per `CONTINUE.md`, inspect/preserve the named work, then a new session may claim the orchestrator lease atomically. Do not impersonate Codex; record `model_family: Claude Code` and your own session ID.

Do **not** manually patch only CURRENT/QUEUE/state. When you establish the next checkpoint, use the repository checkpoint helper so CURRENT, both state records, queue, lease, journal and event log advance together.

## Current accepted integration base

Catalog PR #1 is merged.

- Reviewed catalog candidate: `638cabaeb34507febd9919fc701e2a66fed08ad7`
- PR: `#1 Integrate K2 catalog implementation`
- Merge commit on main: `e5ae2648ee74312282905b8a17af579d6eb36f76`
- Merge parent before catalog: `25a4f00d75daee98012e30fd1404c7142d9e0bdf`
- Main Android K2 workflow run `38257616078`: **SUCCESS**
- Main Continuity workflow run `38257616194`: **SUCCESS**
- The merged candidate had also already passed its own hosted JVM/assemble job and all three emulator jobs: API 23, API 36 default, API 36 16 KiB.

The catalog implementation now on main includes the bounded JSON-array framer, strict Scryfall parser/mapping, local English import, staged atomic activation/recovery, status, FTS name lookup, pagination/cursor stale-generation checks, provenance/source-metadata rehydration, genuine test fixtures and real `StorageDatabase` instrumentation. Do not redo its source review absent a concrete failure.

### Already accepted/present components

- `:core:model` and `:core:common`: compiled/reviewed contracts, already integrated.
- `:feature:settings`: integrated. Prior evidence recorded 68 JVM tests, fatal API lint and library assembly passing.
- `:data:storage`: real Room3 storage implementation is present on main and was exercised successfully by the catalog instrumentation on API 23 / API 36 / API 36 16 KiB.
- `:feature:catalog`: merged and green as above.

The old queue text still underreports storage/catalog completion. Prefer the actual main tree and successful merge workflows.

## The live unfinished component: Compose shell

Branch: `agent/cc-k2-shell-01`
Current branch head: `b1064f2129e429ae165ad4d2163d6d51b91501dd`
Last code commit before the handoff-only commit: `86d986a9ed4a85c1696b985b4d7af56172dc1558`
Status: **READY-FOR-REVIEW, not yet accepted or merged**.

The branch was authored by a previous Claude Code session. If this is a new independent session, you may perform the required independent review, but do not treat the author's handoff as self-acceptance.

You only need these shell-context files before reviewing changed code:

1. `coordination/tasks/cc-k2-shell-01.md`
2. `coordination/handoffs/cc-k2-shell-01.md` on `agent/cc-k2-shell-01`
3. `coordination/locks/shell.lock`
4. The current main Gradle/catalog/settings/storage/core interfaces actually imported by shell.

Do not reread unrelated historical branches unless a concrete inconsistency forces it.

### What the shell branch contains

Exclusive changes are `android-app/feature/shell/**` plus its handoff. No shared root files were changed by that writer.

Public entry:

```kotlin
public class ShellPorts(
    public val catalogLookup: CatalogLookup,
    public val catalogStatus: CatalogStatus,
    public val catalogImport: CatalogImport,
    public val settings: SettingsRepository,
    public val importLimits: CatalogImportLimits = CatalogImportLimits(),
)

@Composable
public fun CommandLibraryShell(ports: ShellPorts, modifier: Modifier = Modifier)
```

Behavior already implemented on that branch:

- Cards / Catalog / Settings destinations only.
- Search by name with validation, paging, stale-generation handling, details, cancellation and no mixed generations.
- Local SAF Scryfall file import with visible limits, bounded 32 KiB chunking, real progress/cancel, no whole-file buffering, truthful unknown metadata and no automatic network/download/art behavior.
- Catalog status with source/provenance/counts/hash/generation/language and truthful Unknown states.
- Persisted System / Light / Dark theme through `SettingsRepository` with stale-revision handling.
- Width-adaptive Compose layout: compact `<600dp`, medium `600..839dp`, expanded `>=840dp`.
- Externalized English strings, accessibility semantics, large-font coverage and Material touch targets.
- Port calls/document reads off UI thread, ViewModel cancellation/recreation behavior.

Author evidence at code commit `86d986a`:

- `:feature:shell:check :feature:shell:assembleDebug :feature:shell:assembleDebugAndroidTest` succeeded in a throwaway build root because root shared Gradle files were outside the task's ownership.
- 67 JVM/Robolectric tests, 0 failures/errors/skips.
- Fatal NewApi lint: no issues.
- Test rerun twice: 67/67 both times.
- No emulator/device/TalkBack run.
- No run against the real `LocalCatalog`, `StorageDatabase` or `DataStoreSettingsRepository` because root integration was intentionally deferred.
- Branch continuity run at handoff head `b1064f2`, run `38259954727`: SUCCESS.

Do not mistake earlier Android K2 runs on that branch for shell verification: before root inclusion, the workflow did not build `:feature:shell`.

## Shared changes explicitly requested by the shell author

### 1. Include the module

In `android-app/settings.gradle.kts`, add:

```kotlin
":feature:shell"
```

to the existing include list.

### 2. Add required version-catalog entries

Current main `android-app/gradle/libs.versions.toml` does **not** yet contain these. Add:

```toml
[versions]
lifecycle = "2.9.4"
robolectric = "4.17"

[libraries]
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

Compose UI test artifacts are BOM-managed.

### 3. Root app is still missing

Current `android-app/` on main contains `core/`, `data/`, `feature/`, Gradle files and wrapper, but **no `app/` directory**.

Create the real `:app` as orchestrator-owned K2 integration work. It should depend on:

- `:feature:shell`
- `:feature:catalog`
- `:feature:settings`
- `:data:storage`

Wire actual implementations once per process:

```kotlin
val limits = CatalogImportLimits()
val database = StorageDatabase.open(...)
val catalog = LocalCatalog(database, clock, ids, limits)
val settings = DataStoreSettingsRepository.create(context)
val ports = ShellPorts(catalog, catalog, catalog, settings, limits)
```

Use one `DataStoreSettingsRepository` instance per store file. The `CatalogImportLimits` object passed to the shell must be the same effective limits used by `LocalCatalog` so disclosed limits and actual importer limits cannot drift.

Host the shell from a `ComponentActivity` with `setContent { CommandLibraryShell(ports) }`; activity owns edge-to-edge/system bars. The shell handles its own safe-drawing insets/theme composition.

Do not add fake product adapters, preview-only product wiring, network download UI, card-art fetching, language downloads, storage-mode controls, consent UI or other deferred features merely to make the app look fuller.

## Exact continuation order

### Step A — resume correctly

1. Fetch latest `main` and verify the catalog merge ancestor `e5ae2648...` is present. This handoff file may be a later docs-only commit.
2. Read `README.md`, `CONTINUE.md`, the current machine state, CURRENT, the last ~20 journal lines, `coordination/locks/shell.lock`, and this file.
3. Inspect `agent/cc-k2-shell-01` head `b1064f2...` and preserve it unchanged.
4. The old Codex lease is expired. Record takeover reason and claim a new Claude Code orchestrator lease in an expected-head atomic commit before shared-file writes.

### Step B — independent shell review

Review the diff from the shell branch against `coordination/tasks/cc-k2-shell-01.md`, the accepted K2 foundation/design requirements it references, and the current core port interfaces. Focus on correctness, lifecycle/cancellation, bounded I/O, stale snapshots, Compose state/restoration, accessibility, and whether any test-only assumptions leaked into product source.

Do not require rereading every historical commit. The meaningful code content is the current branch tree; commit sequence is only supporting provenance.

If you find a real blocker, fix it on an integration/review branch, preserve the author's branch, run focused tests, and document the issue. If no blocker remains, integrate the exact reviewed shell tree.

### Step C — root integration

Apply the requested shared Gradle changes, create/wire `:app`, and include the shell in the real build. Prefer a bounded integration branch/PR rather than mutating the author's completed branch.

Minimum expected local/hosted commands once included:

```bash
cd android-app
bash gradlew --no-daemon --console=plain projects
bash gradlew --no-daemon --console=plain check
bash gradlew --no-daemon --console=plain :feature:shell:check :feature:shell:assembleDebug
bash gradlew --no-daemon --console=plain :app:assembleDebug
```

Run the repository hosted Android workflow and require successful connected instrumentation on:

- API 23 default x86_64
- API 36 default x86_64
- API 36 `google_apis_ps16k` x86_64

After real root inclusion, these emulator jobs must actually execute shell instrumentation; do not count a skipped module as evidence.

Also verify real app wiring can construct the accepted adapters. If the workflow supports app instrumentation/launch, run it. If physical-device/TalkBack testing is unavailable, mark it `not_run`; do not invent it.

### Step D — K2 acceptance / next-stage handoff

K2 is not complete merely because shell source merges. Before declaring K2 complete, establish evidence for the integrated foundation: core + storage + settings + catalog + shell + app wiring, with hosted build/test/emulator results and independent review status.

Then use:

```bash
python3 scripts/checkpoint.py --checkpoint <new-id> --task orch-kotlin-m0-01 --step <next-step> --action '<exact next action>' --summary '<what actually passed/merged and what remains>' --status <honest-status> --lease active
python3 scripts/check_continuity.py
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Publish coordination changes atomically, read them back, and ensure CURRENT/QUEUE/state/lease/journal agree. Release the lease at a stopping point.

If K2 acceptance is complete, advance to the next Kotlin milestone according to the current MILESTONES/queue rules rather than resurrecting historical Flutter/Dart tasks.

## Things you should NOT spend time re-investigating

Unless a new failing test/build contradicts the evidence:

- Whether the catalog candidate passed final hosted CI: it did and is merged.
- The old catalog JVM inclusive-byte-limit failure: fixed in `638caba` before merge.
- Whether API 23 / API 36 / API 36 16 KiB catalog/storage instrumentation ran: yes, and post-merge main Android K2 also succeeded.
- Core contract compilation/review.
- The prior settings remediation result.
- Historical Flutter M0/M1 tasks; Kotlin v1 superseded them.
- The previous Codex five-hour stop itself. Recover from GitHub state, not missing chat.

## Known shell limitations that are not automatic blockers for this task

The shell handoff explicitly records: no hinge/posture API (width fallback only), no keyboard shortcuts from the broader design system, no custom focus ring beyond Material defaults, no device/TalkBack run, UTC timestamp display, provider-reported size treated only as advisory display/early limit check, and no destructive settings reset. Evaluate these only against the actual K2/task acceptance criteria; do not inflate K2 scope with deferred polish unless the specification requires it now.

## Short version

**Do not restart K2.** Catalog is already merged and green. Shell implementation is finished but awaiting independent review at `agent/cc-k2-shell-01` / `b1064f2`. Review it, integrate it with the requested root Gradle changes, create the missing real `:app`, wire actual catalog/settings/storage adapters, run full hosted JVM/build/emulator verification including shell instrumentation, then advance the atomic coordination checkpoint. The old CURRENT/QUEUE/state still say catalog is pending and must be corrected through the checkpoint process after you establish the new evidence.
