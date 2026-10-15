# Selected local import printing links

Root bounded regression fix discovered while designing typed query printing domains. Independent source/durability review required. No schema/new source/network/DI change.

Observed source: LocalCatalog flush calls shared insertPrinting, which stores printing/assets but not printing_oracle_membership. LocalBulkCatalog later resolves memberships, and migration3→4 backfills direct rows, but a new tiny import after that migration never enters either path. LocalCatalogIdentities.printing requires one WHOLE membership and printings pages read only membership rows. A valid selected import therefore fails identity reads and disappears from its Oracle printing page. Actualschema9 reproduction confirmed1test FAILED7s before the fix.

Selected correction:

1. In LocalCatalog's own tiny-import transaction, after inserting a printing with its actual whole Oracle relation, insert exactly one source-qualified WHOLE membership at slot-1 and require one affected row. Do not put this in shared insertPrinting: bulk import already owns delayed FACE/WHOLE resolution and must not double-insert. Tiny parser supports actual whole Oracle only. Keep facts/printing/membership/progress atomic and rollback/cancel behavior unchanged.
2. Retained user-selected legacy rows (source_payload NULL, validated USER_CONTENT_URI origin) may legitimately have no WHOLE membership. readPrinting accepts zero or one WHOLE row only when raw+stored direct Oracle relation is exact and that legacy origin is proven. If present, validate its original slot/kind/identity normally. Full proof-envelope imports still require exact membership counts; reversible FACE source slots always remain mandatory. Never repair/mutate retained originals or fabricate a source row.
3. Printing pages deduplicate UNION of actual stored direct Oracle relation and actual source membership rows, with identical provider/ID cursor and generation/Oracle pinning. Read/validate each returned actual printing as before. Contradictory present membership still causes typed corruption; no guessed Oracle. Direct row fallback covers already retained tiny imports without an original-data backfill. This page is bounded in returned records/bytes, not a new hard query read-work/performance claim; UNION sorting optimization is a later measured/compiler concern.

Tests: actualschema9 new selected import roundtrip/page; simulate legacy missing WHOLE and prove read-only fallback/no implicit insert; direct+member dedup and stable pages, full-proof missing membership still CORRUPT_DATA, reversible source-slot rules unchanged; inject failed membership write to prove no activation and prior active source preserved. Existing identity/importer/controller/root cases are regression checks.

Use focused catalog batch plus relevant realRoom9 root regression/native compile; full storage/emulator is unwarranted without schema/native changes. Publish exact source review/result, then resume typed query semantic/bounded execution design. No completed-app/finished APK claim.

Independent design/exact-source durability ACK; implemented. Actualfocused36catalog+12Room9root=48tests6XML0failure/error/skip SUCCESS30s, nativecatalog+appKotlincompile. Evidence SELECTED-PRINTING-LINKS-LOCAL-20261015.md.
