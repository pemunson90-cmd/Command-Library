# Named-version schema14 — local boundary, 2026-10-15

Adds immutable named references to existing complete deck revisions, plus indexed reverse receipt lookup. This is a schema prerequisite, not a working version repository or S11 feature. Root owns shared schema/registration; isolated UI and pure framing writers touch only new files.

## Exact source and preservation

Local parent3afad036e3a13c8f655780c06518133667317009, final Android tree `d70247990367eb0526bfb2a79a67288d02312322` (root-index-tree b4da8a1deb4ab3fdfff5e91c3014364f4c58fbb6). Actual Room/KSP generated14.json SHA `1c36c3583f840b5268a31bee7c3d057bf08c37289f0f402a48f3eacf542e867d`. Old13.json remains byte-identical; all45 previous entities structurally unchanged except one additive deck_operation index;46tables total. No payload/schema/dependency rewrite or upgrade.

DeckNamedVersionEntity has global version_id PK, metadata_version, same-deck source/predecessor/target FKs, unique creating_sequence receipt association, exact label/time/source/kind/hash, and indexes for source/pred/target/list/association. Same-deck self-FKs use exact unique(provider,deck_id,version_id); receipt sequence-only FK uses existing receipt PK. Runtime must still validate receipt deck/kind/result/request, chronology, metadata/payload hashes, missing newest association and direct recovery-target semantics; FK success is not semantic integrity.

Migration13To14's8DDL statements derive exactly from actual export (one table, six named indexes, one receipt index). MigrationSHA `6af65b813266dcc69ec526835d00c101854e5072d099396907f86534d128433f`. New full122statement fixture SHA `84d6c86b352104dc70bd347f6d6e62ba48be82725cf28f938a310d5dd2f677f3`. Actual StorageDatabase registers13→14; no destructive fallback. Existing fixtures unchanged.

## Executed verification and review

Existing pinned local JDK17/SDK/Gradle/Room/KSP, offline/max-workers2, one build at a time. KSP command `:data:storage:kspDebugKotlin`, SUCCESS1m43s, actual generated export retained. Then `:data:storage:testDebugUnitTest --tests '*DeckNamedVersionMigrationTest' :app:testDebugUnitTest --tests '*DeckSchemaRoomIntegrationTest' --tests '*InventoryRoomIntegrationTest' :data:storage:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin`, SUCCESS2m25s.

**9tests/3XML, zero failures/errors/skips**:4actual host SQLite migration cases plus5actual host Room deck/inventory cases. Both test tasks and both Android-test Kotlin compilation tasks actually executed. No failure/retry at this boundary. Four SQL cases cover unchanged old objects/bytes/fresh-export equivalence; immediate wrong-deck source/pred/target, duplicate receipt/global ID and missing receipt constraints; indexed fixed-kind latest receipt/association/predecessor EXPLAIN queries without table scan; complete rollback of additive objects. SQL fixtures use explicit synthetic payload/hash values and do not certify backend integrity. A deliberate newest-row deletion leaves its receipt, establishing the condition the future backend reverse guard must detect.

Actual Room case opens fresh and schema9/11/12/13→14, preserves old profile bytes and verifies empty new table. Existing real deck create/edit/undo and inventory/deck allocation/replay/cancellation lifecycle cases retained. Independent actual Astra high inventory_journal_review sourceACKed exact export/DDL/fixture/registration/test semantics, then independently parsed final archived9tests/log and runtimeACKed exact final tree. No reviewer builds/edits.

Evidence: /tmp/cl-deck-schema14-01/evidence/{ksp-r1.log,migration-r1.log,migration-r1-counts.json,migration-r1-xml/,migration-r1-tested-tree.txt}. Source frozen throughout each executed gate. New schema source/test evidence does not alter the historical frozen native failure or prior CI lineage.

## Remaining work

Pure metadata/cursor helper subsequently passed source/runtimeACK74tests and root namespace oracle; see NAMED-VERSION-BOUNDARY-20261015.md. Complete LocalDeckVersions backend, shared receipt-kind interoperability, actual AppGraph/S11 composition remain required; no placeholder binding. Native instrumentation/device/process-kill/Fold7/performance/canonical release remain unrun/open; finished-APK hold remains. Publication currently blocked by invalid configured GitHub token; local commits preserved and reconnect requested. No main merge/release.
