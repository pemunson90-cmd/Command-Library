# Collection plain-text actual file-app proof — SOURCE01

## Latest continuation delta

Authored NEW2 only in `/workspace/command-library-collection-text-app-proof-01`, branch `agent/campaign-collection-text-app-proof-20261016`, unchanged exact HEAD `9abf6b6f6878ece427b80d3c6585152a2d618e3f`. Canonical root task and post-baseline `campaign-collection-text-app-proof.lock` supply the ownership. Common Git directory `/workspace/Command-Library/.git`; GIT_DIR/GIT_WORK_TREE/GIT_INDEX_FILE/GIT_COMMON_DIR environment overrides absent. Initial worktree clean. Previous worker worktrees/drafts and existing tests/product remain untouched.

Compilation, tests, builds, lint, network, children, commits and push: **NOT_RUN**. ROOT retains the sole build slot. Source/API inspection and new-file whitespace inspection were performed; `git diff --no-index --check /dev/null <new-file>` returned no diagnostics (status1 denotes differing contents). These are authored oracles awaiting independent source review and ROOT's assembled gate, not passing runtime evidence.

## Actual paths and bounded synthetic seam

`CollectionTextExportAppIntegrationTest.kt` uses the actual MainActivity, application AppGraph/ShellPorts, complete LocalInventory apply/snapshot, production collection text rendering binding, retained UI/controller, ActivityResult CreateDocument callback, ContentResolver writer and real Room. No ports/controller/renderer/writer substitution or reflection is used.

An instance-owned test ContentProvider exposes explicit synthetic content URIs backed by real temporary files and ParcelFileDescriptors. Successful opens require mode `wt`. Its one deliberate failure truncates/writes a partial file during open and then throws before returning a descriptor, with a private exception marker; this exercises the actual writer's uncertain-outcome path. This is synthetic OS-provider behavior on the host, not an authentic external provider, native device or remote durability claim. The provider rejects metadata queries and non-file mutations, and tracks opens/modes exactly.

Real setup stores twelve separate lots, quantities0–11 (66 cards), including a zero lot, repeated identity with distinct saved metadata, source-qualified Oracle/printing IDs, Unicode/quoted/multiline original name, Known/Unknown/Absent states, and unique private notes/location markers. No catalog import or inferred name/finish is needed. Captured source is the actual complete post-apply snapshot. Real rendering produces the independently captured expected artifact for each privacy choice; separate literal checks verify all twelve ordered lot sections, IDs/revisions/exact quantities, totals, source-qualified identities, unresolved states, escaped name, privacy markers and SHA256. Thus the app file is compared to exact frozen production bytes, with content checks beyond merely comparing the renderer to itself.

## Two coherent journeys

1. Default privacy choices are off; review shows the captured revision, twelve lots/66 cards/byte size and all exact loss disclosures, while explicitly showing only a ten-lot sample. No picker/provider open occurs automatically. Recreation retains capture. Cancelled chooser across recreation opens nothing. A second chooser is revoked by Hide, navigation to Cards, and reopening Collection; its delivered URI still opens nothing. ROOT explicitly confirmed this reachable journey is sufficient here: direct navigation while the modal is visible is covered by its separately reviewed synchronous navigation fence, without injecting an app action. A new explicit destination writes exactly the captured bytes once using `wt`; duplicate callback and recreation do not reopen it. End resets choices; a new export explicitly enables notes and locations, reviews the inclusion disclosures and writes a separate file containing each private marker intentionally. The original omitted file remains unchanged.
2. The provider modifies its selected file during failed open; UI reports PossiblyModified and a static persistence problem, retaining exact partial file bytes and redacting exception marker, URI and path. Hide/navigation/reopen preserves the uncertain result. Selecting that same URI again is rejected before a second provider open with a static invalid-input problem. A fresh explicit destination writes the same frozen omitted bytes, and recreation does not reopen either destination or undo the failed partial modification.

Every CreateDocument launch checks ACTION_CREATE_DOCUMENT, MIME `text/plain` and the captured-revision-only `.txt` filename. Exact file length verifies that the longer setup sentinel has no stale tail after successful `wt`. No rollback, atomic provider writes or remote flush guarantee is asserted.

## Conservation and independent readback

A baseline after Activity startup and before export captures inventory, unrelated saved deck/library, settings/catalog status, personal tags, provider/player profiles, ordinary game history and named versions. An independently opened actual StorageDatabase constructs a separate real SqlLocalInventory reader and checks the complete original snapshot on every baseline read.

The same independent connection enumerates every SQLite table and captures every row/column, including payload BLOBs, inventory receipts/groups/changes/reservations, deck revisions/undo/drafts/versions, pools/profiles/settings/provider/life/source tables and SQLite metadata. Explicit schema and per-table counts must fit the real bounded query API and match captured row counts, preventing silent truncated baselines. Exact before/after domain plus SQL equality is asserted after preparation, cancelled/revoked callbacks, writes, failure/retry and recreation. All twelve lots remain intact; no UI sample is treated as the full export. The external temporary destination files are the only deliberate output changes.

## Frozen source and next action

Owned test path: `android-app/app/src/test/kotlin/com/architectpsg/commandlibrary/CollectionTextExportAppIntegrationTest.kt`

Test SHA256: `02c2c00f2c55bee32e6521bf995af6b7e364decd3567a9e0f3895ebc38fb1bb0`.

Read-only dependency digests at this baseline:

- CollectionTextExportSurface.kt: `7f196dfb16403c2e4807c3cb863f52902b502d1a3ab47aa2cd733671078318b7`
- CollectionTextExportController.kt: `b765e5069c9d3a83507f7731cb378118702202d714e5c433fa9a16f7ea66177d`
- PlainCollectionTextExporter.kt: `c84fd4cbdb0be73fbbb8d4386077199a0940eb4f8603a130ddb89a97c7fd1d2a`

The full NEW2 manifest, including this handoff's hash, is returned separately to avoid a self-referential digest. No known source blocker identified; host execution remains unverified. Next: independent exact-source review, then ROOT integrates only NEW2 and runs the assembled codec/controller/provider-writer/actual-file-app gate. Preserve native/physical Fold7/security/release gates and richer-format/full-backup successors.
