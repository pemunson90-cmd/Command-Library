# Manual deck tag planner — frozen SOURCE01, verification NOT_RUN

Base `7c5095a4ef11dcea9838456547267b1b5f6b65f4`, branch `agent/campaign-manual-tags-planner-20261016`, isolated `/workspace/command-library-manual-tags-planner-01`. Owned NEW3 only; root canonical task/lock remain authoritative. No commit, compilation, test, build, network or child execution performed. This worker authored the implementation and cannot independently runtime-review it.

Public pure `ManualDeckTagPlanner.assign/remove/categories` emits ordinary `DeckPatch`; no I/O, time, IDs, new contracts or storage changes. Current and projected graphs use actual `DeckSnapshotValidator` plus primitive `DeckPatchReducer`. Package lineage remains explicitly unsupported. Static errors do not include personal names.

Assignment validates unique bounded selection and complete pool identity/name/active-name uniqueness, requires active requested tags, preserves existing deck-local names after pool rename, and materializes legacy first-appearance definitions before changed replacements. Only changed selected entries are replaced, in existing entry order. Removal needs no pool, retains all definitions including those whose last membership disappears, and never touches unselected entries. Explicit categories may rename/reorder/remove existing definitions; actual reducer updates memberships without deleting physical entries. Genuine no-ops have empty patches and preserve legacy null versus explicit-empty state. A patch is capped at1024 edits including Categories, with no silent split. Physical identity/quantity/board/printing/notes/role overrides and deck metadata stay unchanged.

Ten synthetic tests authored NOT_RUN: simultaneous Removal/ArtifactSynergy/BetterWithCommander with exact metadata conservation; multi-entry single revision; local-name/empty-definition retention and retired rejection; legacy ordered union and last-membership removal; selected-only removal; category rename/reorder/removal; no-op/null/empty semantics; invalid selection, normalized active collisions, Unicode/ID/name bounds and pool header state; graph/category conflicts and capacity; 1023+category versus1024+category edit boundary; unsupported valid package lineage. Tests project through actual reducer and retain the original as recovery reasoning; real SQL grouped undo/CAS is explicitly the root gate, not proven here.

No pool serialization is added: persisted pool framing/checksum/encoded payload budget remain `LocalPersonalTags` persistence responsibility. The planner validates in-memory selection and tag definitions; whole-deck structural validation is not a CLDK wire representability certificate. Root durable apply still performs its actual codec/transaction checks. Manual defaults, suppression provenance, auto roles, providers and UI are outside this packet.

Frozen code/test SHA-256:

```
85fd7268053ae1855547d32fb54c63944551f0109b0b16145d964e70b0aec0e3  android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/ManualDeckTagPlanner.kt
dcb3c7a273311cc1d09b81bc900f12c7ad64707f89c73d45b7f15dbbac0fcb57  android-app/feature/decks/src/test/kotlin/com/architectpsg/commandlibrary/feature/decks/ManualDeckTagPlannerTest.kt
```

The full NEW3 manifest, including this handoff's hash, is returned to root separately to avoid a self-referential hash. Next: independent Astra source review of exact bytes, root byte-checked integration and the coherent persistence/planner/app risk gate. Compilation/tests remain NOT_RUN until root executes them.
