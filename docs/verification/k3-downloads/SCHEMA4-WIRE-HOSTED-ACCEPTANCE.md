# Schema4 and acquisition wire/source boundary — hosted acceptance

PR15 merged expected candidate `44d02c9100f82dd4a58c97bbe8228a91a2574f98` at main `5c9ff3f6980cf3cfefc0f08638f18b508578c6ab`. Exact hosted run [38328542087](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38328542087) succeeded on all four lanes: 347 JVM tests and 66 native tests in each API23, API36 and API36 16KiB lane, zero failures/errors. The 16KiB lane reported actual page size 16384. Skip counts are not asserted here.

Jobs: JVM 115072768146; API23 115072768135; API36 115072768227; 16KiB 115072767954. This verifies the additive schema/migration and wire/source changes in that exact candidate, not later engine/full-importer/platform bindings.

Actual hosted KSP export `StorageSchemaDatabase/4.json` was retrieved from JVM artifact 11782177865, not synthesized: 56505 bytes, SHA256 `6f1f94a2c2653635e70784a77b933bbcc2a7b23b636e9d26b7abb830afa0dcec`. It contains 17 entities. Comparison to schema3 preserves all 16 prior entities except the five reviewed additive provenance columns; old fields, primary keys, indexes and foreign keys match. The real export is committed under data/storage/schemas.

Required source/design reviews are SCHEMA4-DESIGN-REVIEW, STORAGE-SCHEMA4-REVIEW, FULL-SOURCE-CODEC-REVIEW and WIRE-DISPATCH-IMPLEMENTATION-REVIEW. The later guarded engine, full-corpus installation, removal drainer, downloadable UI, canonical signing and physical Fold7 checks remain separate obligations. This is not K3/full-v1 acceptance.
