# Raw draft schema15 — local physical persistence gate, 2026-10-15 UTC

Root parent03d766a on agent/cx-thread-recovery-20261013. Actual Room/KSP15 export SHA ce46c1bc2919f3a4b94841cc65f1767d230313b13886628a3ad3403189df94ec. All14historical exports remain byte-identical; all46prior entities structurally unchanged,48total. Two new tables/nine indexes only. Exact11DDL Migration14To15 derived from actualexport, registered in real StorageDatabase. New133statement fullSQLfixture, generator convention reproduces old14fixture bytes exactly. No backfill/dependency change/destructive fallback.

Independent actualAstra campaign_engineering_review ACKed entitysource and actualexport/migration/fixture/testsource before execution. Independent actualAstra runtime ACK verified17tests/4XML, both actualtasks executed andall9sourcepins unchanged. Schema physical semantics never substitute for draft repository hash/status/latest/namespace checks.

KSP attempt1 SUCCESS1m18s,2executed9uptodate. First fixture generator stopped on absent indices key in FTS entity; repaired optional-key access, same generator then reproduced historical fixture; no tests/build ran for failed generator. Failure preserved in export-comparison.json, no false runtime failure lineage.

Risk attempt1 SUCCESS1m9s,16executed144uptodate. Exact reviewed source hashes unchanged throughout. Commands offline/maxworkers2: storage testDebugUnitTest selecting DeckDraftMigrationTest, DeckNamedVersionMigrationTest, SchemaVersionGateTest; app testDebugUnitTest selecting DeckSchemaRoomIntegrationTest; storage/app compileDebugAndroidTestKotlin. Pinned installed JDK17.0.16+8/Gradle9.6/KSP2.3.12/Room3-3.0.3/SQLite2.7.1/SDK37target36min23 unchanged, sole root Gradle slot.

Actual17tests/4XML: storage14/3XML,app3/1XML; zero failures/errors/skips. New5physicalSQL tests cover oldobject/payloadpreservation/equalitytofreshschema, immediatequalifiedFKs/cycle/globalintentidentity/receiptuniqueness, boundedlatest/pending/reversereceiptqueryplans, all11objectsrollback, independentfilereopen/exactbytes. Synthetic payload/descriptors explicitly not draftadapter integrity. Existing actualapp3tests include fresh+9/11/12/13/14Roomopens preserving oldprofilebytes and checking drafttablesempty, plus existing realAppGraphdecklibrary/edit/undo/replay journeys.

Evidence /tmp/cl-deck-draft-schema15-01/evidence/{old-exports-before.json,ksp-attempt1.log,export-comparison.json,risk-attempt1-source.json,risk-attempt1.log,risk-attempt1-counts.json,risk-attempt1-xml/}. Source SHA fences: entities f2becfec091d7cd4250ee0e79af3d9328b2c8728871320e2ce7b0b7f44c46e84; registration990db2fc12555491c234748b1f1bfd92931d15fe4871fe73ef4592b216ee49d0; migrationc1c3a9f19c2b6eb485b2d18b4ea76cc1ade52572c572fab9fdb64587873d4ba4; fixture3057259946ec2d6ca56c9ef9f45da1ac411709d646c0743be83fbb28b49e74cd. Full source pins in manifest.

Instrumentation compiled ONLY, no native execution/Fold7/performance/fullfeature/release claim. No LocalDeckDrafts adapter/UI/DI binding yet. Next root shared framing/goldens and permanent globaloperation reservation guards, then isolated complete backend and realpresentation integration. Historical native failures and finishedAPKhold unchanged.

Exact accepted Android tree `5f5dc96312f2bfb281da3a3c79435b07765503ba` (index tree `aade8e9ea4f66bc7b6db4491ef1db5fe4880566d`); postcommit readback required.
