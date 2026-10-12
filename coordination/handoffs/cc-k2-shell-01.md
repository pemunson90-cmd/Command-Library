# Handoff cc-k2-shell-01

READY-FOR-REVIEW. Independent review required (Codex); the author has not reviewed its own work as acceptance.

- Checkpoint ID: cc-k2-shell-01-ready-20261012
- Owner: external Claude Code (Opus tier), delegated writer under `coordination/locks/shell.lock`. Orchestrator and integrator: Codex.
- Task and step: cc-k2-shell-01, steps 1 to 4 of 5 committed; step 5 is this handoff.
- Branch: `agent/cc-k2-shell-01`. Worktree: `/home/user/cl-shell` (cloud container, ephemeral; GitHub is the durable copy). Head: the tip of the branch (a file cannot embed its own SHA; last code commit `86d986a`).
- Base: main `25a4f00d75daee98012e30fd1404c7142d9e0bdf` (contains this task card and lock). Main has since advanced to `e5ae264` (catalog merge); this branch touches no path that main changed, so it merges without conflict.
- Dependency: accepted pure core `:core:model` and `:core:common` as at `25a4f00`, read only. The shell imports only `CatalogLookup`, `CatalogStatus`, `CatalogImport`, `SettingsRepository`, their DTOs, and `CoroutineCancelToken`. No catalog, storage or settings implementation package is imported.
- Owned paths changed: `android-app/feature/shell/**` (38 new files) and this handoff. Shared paths changed: none.
- Work status: code complete for the task's finish line, verified locally on the JVM (JVM tests plus Compose UI tests under Robolectric). Not run on an emulator or device. Not reviewed. No subagents used (task card).

## Commits

| Step | Commit | Content |
|---|---|---|
| 1/5 | 6db3921 | Module build file, SAF chunk reader, presentation controllers |
| 2/5 | e06162c | Synthetic test ports, 42 JVM presentation tests |
| 3/5 | 28404d0 | Adaptive Compose shell, screens, theme, Compose UI tests |
| 4/5 | 86d986a | Port calls off the UI thread, lint fixes, string scan, reader and restoration tests |
| 5/5 | branch tip | This handoff |

## Public entry signature (for root `:app`)

```kotlin
package com.architectpsg.commandlibrary.feature.shell

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

Wiring rules:

- Build `ShellPorts` once per process (Application scope). `DataStoreSettingsRepository` must be a single instance per store file, and the shell's ViewModel keeps the ports it was created with.
- `importLimits` must be the same `CatalogImportLimits` instance given to `LocalCatalog`; the UI discloses those limits and the reader stops at `maxInputBytes`.
- Host in any `ComponentActivity`: `setContent { CommandLibraryShell(ports) }`. The ViewModel is scoped to the nearest `ViewModelStoreOwner`. Clearing it cancels a running import. After process death the shell reads committed status and settings; it restores no previous result.
- Edge-to-edge and system bar appearance belong to the activity (`enableEdgeToEdge()` recommended). The shell consumes safe-drawing insets itself and applies the persisted theme inside its own composition.
- Example process wiring: `val catalog = LocalCatalog(database, clock, ids, limits)`, then `ShellPorts(catalog, catalog, catalog, DataStoreSettingsRepository.create(context), limits)`.

## What the module does

- Status (Catalog destination): empty state says no catalog is installed and shows the default data language; installed state shows source, source date (unknown stays "Unknown" with an explanation, never inferred from file time), imported time (UTC), card, printing and face counts, format, generation ID, file SHA-256, installed languages. Status failures show a plain reason and Try again.
- Import: user-initiated SAF `OpenDocument` (`application/json`, `text/json`, `text/plain`, `application/octet-stream`); the importer still validates content. Limits shown from `importLimits`: 8 MiB, 10,000 printings, 128 KiB per record, nesting 32, English only. The provider-reported size, when larger than the limit, is rejected before the file is opened. Reading is a single-use flow of contiguous `ByteChunk`s of at most 32,768 bytes on the IO dispatcher, rendezvous-buffered (at most two chunk buffers held, never the whole file), `final` on the last chunk, metadata null. Reading stops once more than `maxInputBytes` bytes are read. Cancelling closes the stream at once, which also releases a read blocked inside a provider (tested under 1 s). The stream is closed in every outcome, including an importer that returns without reading.
- Import source carries only known facts: content URI, display name (bounded to 512 UTF-8 bytes at a code point boundary), provider `scryfall`, language `en`. Source date, origin URI, expected checksum, expected byte count and notice are `Knowledge.Unknown` with reasons.
- Progress shows the importer's phase and records saved (from `onProgress`) and bytes read; the bar is determinate only when the provider reported a size, otherwise indeterminate. Cancel is a real job cancellation; once requested the outcome is shown as cancelled and committed status is read again (a cancellation after the importer's durable commit cannot undo it; the re-read shows the truth). Failures map to plain reasons: too large, unreadable, permission denied, invalid file, size or checksum mismatch, storage failure, else the generic problem text. Domain messages never reach the UI.
- Search (Cards destination): trimmed name query, empty and over-128-code-point input rejected at the field without a port call; pages of 50 through `PageRequest` cursors; Load more until `nextCursor` is null; a new search cancels the one in flight. Results come from one snapshot: a `STALE_GENERATION` cursor failure, a next page from another generation, or a status read showing a new active generation marks results stale, refuses more pages and offers Search again; nothing is mixed. No catalog installed leads to the Catalog destination. Details show Oracle text and game fields, faces for multi-face cards, and provenance. No card art is requested.
- Settings: System, Light, Dark radio group persisted with `update(patch, expected = read revision)`. The applied theme follows the committed snapshot only. `STALE_REVISION` re-reads and shows that settings changed elsewhere without overwriting. Controls lock while saving. About text and any source notices (settings or active catalog provenance) are read only. No storage mode, language, download, consent or vault controls.
- Layout by the width the shell actually receives: compact under 600 dp uses a bottom navigation bar and one pane (details replace results; Back button and system back return); medium 600 to 839 dp uses a navigation rail and one pane; expanded from 840 dp uses a rail and side-by-side results and details. Catalog shows status and import side by side when expanded. Every screen scrolls; buttons wrap rather than clip. Destinations are only Cards, Catalog, Settings.
- Accessibility: all text from `res/values/strings.xml` (English only, plurals where counts vary); headings marked; label and value read as one item; outcome messages are polite live regions; errors carry text titles; result rows carry a "Show details" click label and selected state in two-pane mode; radio semantics on theme rows; icons decorative next to labels; Material minimum 48 dp targets.
- Threading: every port call and document read runs on `Dispatchers.IO`, so an adapter parsing inside the collector context never blocks the UI thread. Controller state is `StateFlow`; jobs live in `viewModelScope`.

## Evidence (commands actually run)

Environment: this container, JDK 17 (`/usr/lib/jvm/java-17-openjdk-amd64`), Gradle wrapper 9.6.0, AGP 9.4.1, SDK `platforms;android-37.0` and `build-tools;36.0.0` at `/opt/android-sdk`. No `/dev/kvm`.

The repository's shared Gradle files do not include `:feature:shell` and cannot be edited by this task. Verification therefore ran from a throwaway build root in the session scratch directory: a copy of `android-app/build.gradle.kts`, `gradle.properties`, the wrapper and a version catalog equal to the repository's plus the requested entries below, with `settings.gradle.kts` mapping `:core:model`, `:core:common` and `:feature:shell` to this worktree's real directories. No repository file outside the owned paths was modified, staged or committed.

- `bash gradlew --console=plain :feature:shell:check :feature:shell:assembleDebug :feature:shell:assembleDebugAndroidTest`: BUILD SUCCESSFUL at the content of `86d986a`. `check` ran `testDebugUnitTest` and `lintDebug` (AGP 9 runs debug host tests only). Lint: "No issues found" (fatal NewApi). No Kotlin compiler warnings in main or test sources.
- Test counts from `build/test-results/testDebugUnitTest/*.xml`: 67 tests, 0 failures, 0 errors, 0 skipped. `ShellUiTest` 16, `ImportControllerTest` 13, `SearchControllerTest` 10, `DocumentChunkStreamTest` 8, `SettingsControllerTest` 6, `ShellControllersTest` 6, `ContentResolverDocumentSourceTest` 4, `ExternalizedStringsTest` 3, `CommandLibraryShellTest` 1.
- `:feature:shell:testDebugUnitTest --rerun` twice more: 67 of 67 both times.
- `ShellUiTest` and `CommandLibraryShellTest` ran under Robolectric 4.17 with sandbox SDK 34; SDK 35 and newer sandboxes need a Java 21 test JVM and the project toolchain is 17.
- Earlier failures fixed on the way (all before `86d986a`): JVM tests first used `backgroundScope`, whose work `advanceUntilIdle` skips (31 failures, fixed with a foreground owner scope); Robolectric SDK 36 refused Java 17 (moved to SDK 34); three UI waits polled controller state without idling the paused main looper (fixed by `awaitState`); lint `NonObservableLocale` errors (fixed with `LocalLocale`).
- Placeholder scan `\b(TODO|FIXME|XXX|NotImplementedError)\b|TODO\(` over `android-app/feature/shell`: no matches. Em dash scan: no matches. `ExternalizedStringsTest` scans all UI sources for hardcoded user text (AC-J03) and checks its own detector against known violations.
- Hosted Actions on this branch: Android K2 runs 29 and 31 and Continuity runs 95, 98, 99 succeeded; Android K2 run 32 (for `86d986a`) was in progress when this was written. These runs do not build `:feature:shell` because the settings file does not include it; they are not evidence for this module.

Not run: `connectedDebugAndroidTest` (no emulator here), any device or TalkBack run, any run against the real `LocalCatalog`, `StorageDatabase` or `DataStoreSettingsRepository`, any app launch. All UI and presentation evidence uses synthetic, test-only ports.

## Tests (what they show)

- Reader: 32 KiB chunk sizes, contiguous sequence and offset, final only on the last chunk, exact multiples, empty file, byte cap stops reading within one chunk of the limit, file exactly at the limit accepted, read errors reported, single-use flow, cancellation closes a provider stream blocked in `read()` within 1 s and is not reported as a read error.
- Import controller: success with only known source facts and closed stream; running state shows bytes, phase and records; cancel closes the file, the importer sees cancellation and the token, the previous generation stays active, status is re-read; reported size over limit never opens the file; growth past the limit reports too large; importer rejection, open refusals, non-reading importer, throwing importer, importer that swallows cancellation, display-name fallback and bounding, dismiss rules.
- Search controller: query validation without port calls, first page and cursor pages to the end, stale cursor keeps one snapshot and Search again starts fresh, a next page from another snapshot is not appended, active generation change marks stale, failures and retry for both page kinds, a new search cancels the previous one's token, honest empty catalog, selection, duplicate identities shown once.
- Settings controller: load, theme saved against the read revision with the applied theme waiting for commit, stale revision reloads without overwriting, failed save keeps the committed theme, no-op selections, read failure and retry.
- Recreation (`ShellControllersTest`): a new owner reads committed state only; clearing an owner cancels its import and a new owner shows the previous catalog with no result; after success a new owner shows the committed generation without replaying success; importing marks earlier search results stale.
- Compose UI (`ShellUiTest`, shared with instrumentation): empty status with visible limits and heading semantics; import through the real `rememberLauncherForActivityResult` path with a test `ActivityResultRegistry`, progress bar semantics and text, cancel; successful import refreshes status; failed import shows the plain reason and no domain text; compact search paging and details with Back; stale cursor recovery; no-catalog navigation; empty-query message; theme persistence and stale notice; controls lock while saving; 599 dp bar, 600 dp rail and one pane, 839 dp single pane, 840 dp two panes with selection state; 200% font scale keeps search, import and theme controls reachable; state restoration keeps the destination and the running import without restarting it. `CommandLibraryShellTest` composes the public entry with a real ViewModel.

## Requested shared changes (orchestrator)

1. `android-app/settings.gradle.kts`: add `":feature:shell"` to the `include(...)` list.
2. `android-app/gradle/libs.versions.toml`:

```toml
[versions]
lifecycle = "2.9.4"        # equals the lifecycle version activity-compose 1.13.0 and BOM 2026.09.00 already resolve
robolectric = "4.17"       # published 2026-09-10; android-all 14 sandbox is fetched from Maven Central at test time (145 MiB)

[libraries]
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }       # BOM-managed, resolves 1.12.1
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }   # BOM-managed, debugImplementation
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
```

3. CI: no workflow change needed. `check` will run the shell's JVM and Robolectric tests; the emulator job's `connectedDebugAndroidTest` will run the shared Compose UI tests on all three images. Optionally cache `~/.m2/repository/org/robolectric` to avoid refetching the sandbox jar (Maven Central returned HTTP 429 to an earlier probe from this environment).
4. `:app` (orchestrator-owned): depend on `:feature:shell`, `:feature:catalog`, `:feature:settings`, `:data:storage`; build `ShellPorts` per the wiring rules; `setContent { CommandLibraryShell(ports) }`.

If Robolectric is declined, move `src/sharedTest` to `src/androidTest`, drop the two `sourceSets` lines, `robolectric.properties`, and the `testImplementation` lines for ui-test, ext-junit and Robolectric, and delete `ContentResolverDocumentSourceTest`; the JVM presentation tests and the string scan stay.

## Limitations and gotchas

- Hinge and posture: not implemented (no `androidx.window`); layout is contiguous by width only, which is the specified fallback when no sensor data is used. No manual posture override.
- Large and extra-large widths reuse the expanded two-pane layout; no supporting pane.
- Keyboard shortcuts (Ctrl+K and others from DESIGN_SYSTEM) are not implemented; all controls are keyboard focusable through Material components. Focus indication is Material's default, not a custom 2 dp ring.
- Timestamps are formatted in UTC with a "UTC" suffix using the current locale.
- The provider-reported size is unverified and used only for display and the early limit rejection; it is never passed as `expectedBytes`.
- The shell writes no settings other than theme and starts no network transfer, art request, consent or download.
- A damaged or newer settings store shows the plain problem and Try again; no reset is offered (deleting user data is a product decision).

## Exact next action (Codex)

1. Independently review branch `agent/cc-k2-shell-01` at its tip against this card, K2-FOUNDATION-DESIGN and DESIGN_SYSTEM.
2. Apply the shared changes above on main (or a root-owned integration commit on this branch, as was done for catalog), then run in `android-app/`: `bash gradlew --no-daemon --console=plain :feature:shell:check :feature:shell:assembleDebug` and the hosted workflow so `connectedDebugAndroidTest` runs the shell UI tests on API 23, API 36 and API 36 16 KiB images.
3. Wire `:app` with the real adapters, then launch and exercise import, cancel, search and theme on an emulator. Report results honestly; nothing here claims those runs.
4. Merge only after review and the hosted runs pass. Claude Code does not merge and does not touch other paths.
