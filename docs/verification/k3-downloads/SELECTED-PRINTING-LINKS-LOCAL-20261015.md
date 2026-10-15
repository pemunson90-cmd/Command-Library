# Selected local import printing links —2026-10-15

Implemented with independent design/exact-source durability ACK. Parentb003b0d on agent/cx-thread-recovery-20261013. No schema, source authority, network, DI or release change.

The actualschema9 regression failed before the fix: selected tiny import committed the printing but no WHOLE membership, so LocalCatalogIdentities.printing returned a typed validation failure and Oracle printing pages omitted the real row. LocalCatalog's tiny flush used shared insertPrinting; only bulk's later resolver and migration3→4 supplied membership links. Log `/tmp/selected-printing-membership-repro.log`:1test FAILED7s. This was an actual producer/reader mismatch, not a documentation or API-absence test.

The tiny flush now inserts exactly one source-qualified slot-1/WHOLE row with each actual whole-Oracle printing in the same transaction and requires one affected row. The shared helper/bulk delayed FACE/WHOLE resolver remain unchanged. Failed membership writes roll back that batch and never activate partial data; prior active data remains readable.

Legacy selected imports with validated USER_CONTENT_URI origin and NULL source_payload may have no WHOLE row. Their printing reader accepts that only when actual raw/stored direct Oracle relation agrees and the exact Oracle target exists in the same generation/provider/language. It performs no backfill, repair or source mutation. Present contradictory links, missing full proof-envelope membership and reversible FACE slots remain typed errors. Printing pages deduplicate actual direct relation UNION actual source-membership rows, preserving generation/Oracle/provider/ID cursor identity and existing returned-page byte limits. This does not establish a hard SQL read-work/performance bound for compound sorting.

Actual final batch SUCCESS30s/150tasks7executed143up-to-date:

- Catalog4XML36tests0failure/error/skip: identities (10), bulk importer (17), literal catalog text (4), preparation adapter (5).
- Existing actualRoom9root2XML12tests0failure/error/skip, including public local preparation factory toREADY without acquisition.
- Catalog and app native Kotlin test compilation passed; no device execution.

Total48tests/6XML, retained `/tmp/selected-printing-links-xml` and `/tmp/selected-printing-links-r1.log`. Four new methods pin selected roundtrip/WHOLE row, retained read-only fallback/dedup/stable pages/missing target, full-proof missing-link corruption and injected atomic write failure preserving prior active. Source review explicitly checked target existence and unchanged bulk/reversible strictness.

No fullcatalog/storage/Android acceptance rerun is claimed; prior full269catalog/lint and later5adapter evidence remain scoped. Resume typed query semantic/bounded execution design. Full source/indexed executor/FullLean/context/M2–M9 remain incomplete; frozen native failure preserved/no rerun/no finished APK.
