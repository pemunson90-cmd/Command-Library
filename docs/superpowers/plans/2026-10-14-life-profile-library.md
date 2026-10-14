# Player Profile Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Root implements inline under Pat's continuous-work delegation; existing independent reviewer covers required boundaries. No new worker requested.

**Goal:** Real reusable-profile list/create/edit/delete UI over the checked SQLite repository.

**Architecture:** Add optional complete profile port to ShellPorts and wire real SqlPlayerProfileRepository over AppGraph's existing database. One scoped shell controller owns drafts, list paging and revision-checked writes; Compose renders truthful states. GameService remains unregistered.

**Tech Stack:** Existing Kotlin/Compose/coroutines/Room3/JUnit/Robolectric versions, minSdk23.

**Spec:** docs/superpowers/specs/2026-10-14-life-profile-library-design.md

## Global Constraints

- No GameService binding, source-gate relaxation, schema/codec/CI changes or final APK delivery.
- Externalized English; touch targets at least48dp, labelled numeric fields, scrolling at narrow/wide/large font.
- Signed Int life allowed; adjustmentStep>0. Preserve exact name and all advanced fields.
- Exact revisions and actual persistence success govern saves; failures retain drafts.
- Local-first gates; native/physical evidence reported separately.

## Review Focus

- Existing advanced definitions/commanders/appearance must survive basic edits: task1 preservation test.
- Stale writes must retain the entered draft and original revision: task1 stale-outcome test.
- Refresh during suspended load-more must reject old append: task1 generation/cancellation test.
- Numeric overflow and retained counter bounds must fail before repository writes: task1 validation tests.
- Cancel/clear after possible database commit must not assert rollback: task1 scope cancellation and task3 actual readback.

### Task1: Revision-safe profile controller

Files: create shell presentation/ProfilesController.kt and matching src/test/.../presentation/ProfilesControllerTest.kt.

Interfaces: consumes PlayerProfileRepository and existing callPort dispatcher/token handling. Produces internal ProfilesController(scope,repository,portDispatcher), state:StateFlow<ProfilesUiState>, refresh(), loadMore(), create(lifeLabel:String), edit(profile), changeName(String), changeLife(String), changeStep(String), save(), requestDelete(profile), dismissDelete(), confirmDelete(), closeEditor(), reloadEditor(). ProfilesUiState exposes immutable items/nextCursor/loading/busy/editor/pendingDelete/problem/validation; ProfileEditor retains original snapshot plus name/life/step strings. Numeric error enum local to controller, no raw exception/UI messages.

- [x] Write failing tests: pages use actual cursors and refresh resets; duplicate submissions produce one write; basic edit preserves advanced fields and updates LIFE.initial only; signed life works; blank name/overflow/step0/retained life bounds reject before writes; each typed non-success retains draft; stale revision passes exact original and explicit reload alone replaces draft from repository.read; confirmed delete passes exact revision and refreshes; stale page can't append after refresh; cancelled scope leaks no publication/detached write.
- [x] Run :feature:shell:testDebugUnitTest --tests '*ProfilesControllerTest'; observe intended missingAPI/behavior RED.
- [x] Implement minimal scoped controller; use exact loaded.value.copy and real callPort, separate list/write jobs and generation guards; block double write/back while busy, explicit later reload after stale.
- [x] Run full shell unit tests; all pass, report actual XML.
- [x] Commit controller/tests plus current task delta, without prematurely enabling a destination.

### Task2: Profile library screen and navigation

Files: shell ui/profiles/ProfilesScreen.kt, ui/ShellRoot.kt, presentation/ShellControllers.kt, ShellPorts.kt, res/values/strings.xml; sharedTest profile UI tests following existing layout.

Interfaces: consumes task1 controller/state; add ShellPorts.playerProfiles:PlayerProfileRepository?=null tail parameter. Conditional Profiles destination only when supplied; controller initializes refresh in existing scope.

- [ ] Write failing Compose tests for absence without port, create/edit validation and retained input on failure, explicit delete confirmation/cancel, empty/loading/retry/load-more, narrow/large-font/wide scrolling/touch semantics.
- [ ] Run named UI tests and observe RED before screen/navigation changes.
- [ ] Implement labelled list/editor/dialog using existing adaptive scaffold and Problem resource mapping. No advanced edit controls that do nothing; retained advanced values may display as summaries. Busy write is visible; screen back cannot misreport a committed operation as unsaved.
- [ ] Run full shell suite and lint; resolve actual failures.
- [ ] Commit UI and truthful continuation delta.

### Task3: Real app profile assembly and verification

Files: app/build.gradle.kts app/di/AppGraph.kt app Android integration test; reviewed architecture/contracts/evidence and current coordination.

Interfaces: construct SqlPlayerProfileRepository(opened.value,LifePayloadCodec(),SystemUtcClock,RandomUuidIdSource), pass via optional ShellPorts.playerProfiles. Same opened DB, existing success-only cache. Add allowed app→life dependency specifically for profiles. Never construct/register GameService.

- [ ] Add integration probe asserting actual app graph profiles capability is present/cached, unique synthetic profile create/read/update/list/delete succeeds, unknown read after delete remains typed; cleanup only own row. Compile is not native execution.
- [ ] Wire exact complete repository, preserving all non-success/cancellation branches and existing shell/catalog/settings initialization.
- [ ] Run check :app:assembleDebug :app:assembleDebugAndroidTest :feature:shell:assembleDebugAndroidTest and appropriate storage compile gate. Count exact XML; independently check APK ZIP alignment; record runtime not_run where applicable.
- [ ] Obtain independent exact source/publicAPI/durability/UI review and fix findings, rerun affected checks only.
- [ ] Publish source/evidence and synchronized checkpoint with continuity/31Python checks; advance directly to retained profile advanced/apply or named variant/source prerequisites. No checkpoint stop or unfinished APK delivery.

## Inline progress and rulings

Task1 BASE6587568; controller APIabsenceRED131s → focused7GREEN88s → fullshell87tests12XML0failures/errors/skips290s. Containing source commit records task1 completion; final whole-feature review remains pending. Task2 UI/navigation RED tests next. Provider prior whole954JVM evidence is separate. Required independent architecture/publicAPI/UI composition APPROVE recorded in spec. Reuse active isolated recovery branch, preserve frozen PR21 and all other checkout/authority files. Canonical currenttaskdelta is the durable continuation ledger under repository documentation policy; no essential state only in git-ignored skill scratch.

Pre-flight task1→task2: controller draft/cursor/status methods match screen needs. Ruling: create takes lifeLabel:String from the caller's actual string resource, so the pure controller never hardcodes persisted user-facing English. Explicit reloadEditor() rereads only the current existing profile after user requests discarding edits; failed reload retains draft. Needed to make stale-write reconciliation concrete rather than force endless retries against an old revision. Cost if wrong: internal controller/UI signature change only, no core API/migration.

Pre-flight task2→task3: optional tail PlayerProfileRepository?=null preserves existing shell callers and absent destination; app supplies full real profile repository only. GameService gate stays unchanged. Ruling: use a text keyboard for signed life input if the existing numeric IME omits minus; step remains positive numeric. Validation uses full signed Int range and retained LIFE bounds, never an invented positive-life rule.
