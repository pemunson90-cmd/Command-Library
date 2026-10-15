# Durable inventory schema prerequisite — accepted local boundary

Root on continuationb5fd7c11940c19fc36d015ad62781e75c26dbacf plus exact scoped schema source. Complete journal semantics and contract/request source were independently accepted first. This is an additive schema prerequisite, not an implemented LocalInventory backend or full collection feature.

Actual Room KSP export12.json SHA256 fdf731c3b6ff53e02405d5de68cd573a5217b8166cde6c03a11a4d66e6d8d705: all40old entities exactly unchanged;45total. Five new tables inventory_operation/group/state/lot/change. Operation IDs unique, sequence AUTOINCREMENT; immediate NO_ACTION FKs/indexes preserve journal lineage and reservations, no cascades/deletes. Composite active+lot_id index supports internal BINARY keysets. All13 Migration11To12 statements exactly match new KSP tables/indexes; full113-statement inventory-schema12.sql fixture exactly matches complete export. StorageDatabase registers contiguous11→12 chain; no destructive fallback.

Root initial KSP gate SUCCESS30s. Before acceptance changed the active-row index to composite(active,lot_id), then KSP SUCCESS21s. No historical schema altered. Actual independent Astrahigh source/migration/export/fixture/test/evidence ACK, reviewer ran no builds/tests. Schema does not enforce every semantic invariant with SQL CHECKs; complete backend must validate all types/relationships/payloads against accepted design before use.

Executed pinned offline local risk-boundary gate, one serialized Gradle slot:

```
:data:storage:testDebugUnitTest :app:testDebugUnitTest --tests '*DeckSchemaRoomIntegrationTest' :data:storage:compileDebugAndroidTestKotlin
```

Attempt r1 SUCCESS2m22s; actual36XML304tests0failures/errors/skips: storage301/35XML incl3new InventoryMigrationTest, app3/1XML. New migration tests compare old object definitions/bytes, fresh12 vs11→12, reopen, immediate FK write order/reservation retention, duplicate/missing references and rollback every five-table write prefix. Actual Room app probe executes fresh,9→12 and11→12 preserving profile bytes and checking empty inventory/deck tables; two existing real deck create/edit/undo/replay/crossconnection app journeys also pass on12. These are actual host SQLite/Room, not source-only or device/process-kill/Fold7 evidence. Android-test Kotlin compilation executed; no native tests executed.

Evidence /tmp/cl-inventory-projection-01/evidence/inventory-schema12-boundary-r1.log (SHAace8b8ddfbe3366c50a8b29173f973f9f1821e97a9946f1d2bdc3210fcb3e206), inventory-schema12-r1-xml and inventory-schema12-r1-summary.json. Existing frozen native failure source/run remains unchanged/unrerun.

Source SHA: InventoryTables e50e36c7b0acdf2059417b2c04bc575323bc55f2d5b9d96707190479913a39ba; Migration11To12 0ba58f9ae20a8458948614652a5bca47999d40c440b69decc642aec9e7454ee3; StorageSchemaDatabase edff7a1cebfd86501f7f2efad98e1836a6db42cdc16d0b3a6bfa3107217a7f0f; StorageDatabase025aea006225a282aa5de7cec59d143d6d6b1e04ccbcb9161d90a871c98337a4; fixture e4762be08adbffeb8f8b0994fb02d865acbb553e7cd8c37312cc4a6919a28387; InventoryMigrationTest276ada294acd6f7027e7338e9763ba9f60269fad9ddf3ba0c6840d3b35faf5d2; appRoomtest dc1a1678951b9ddc49e15377304c44d7e75b72bf5060818ca8371cdbca863cd2.

Root adds5abstract LocalInventoryContractCases plus storage testImplementation(feature:collection) only after this gate, compiled consumer examples separately SUCCESS17s; independent Astrahigh fixture/packet ACK (fixtureSHA9272730393d7bb7b21ad3189065b007cf38fed79d9c411a072afe814af64c97f). Abstract compilation is not backend behavior evidence. Worker must execute them against real complete implementation/codecs/SQLite; no production binding before independent source and actual app integration. Catalog finish eligibility, S12/CSV/bulk, stored inventory→quantity UI, native/Fold7/canonical and finished APK hold remain retained.
