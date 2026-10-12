# Catalog JSONL framer handoff

Status: READY-FOR-REVIEW. Internal parser plus authorized narrow catalog cleanup durability fix; no product binding or full-feed claim.

Base: `478eb4aec3e98f4f739deb0d812ea97049356ce2`.
Parser commits: `0ff9a0f`, `581126e`, `86dc00d`. Durability code commit: `ed26b32608ca5eb2a3b9b16d6d7c70574f73bc74`.
Branch: `agent/k3-catalog-framer-01`.

Changed files:
- `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramer.kt`
- `android-app/feature/catalog/src/test/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramerTest.kt`
- `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/LocalCatalog.kt`
- `android-app/feature/catalog/src/androidTest/kotlin/com/architectpsg/commandlibrary/feature/catalog/LocalCatalogInstrumentedTest.kt`

`JsonLinesFramer(JsonLinesLimits, CancelToken)` consumes contiguous `ByteChunk`s through `accept(chunk, suspend (String) -> Unit)`, then `finish()`. It reports final exact-byte SHA-256, observed input bytes and emitted record count. Its caller must supply positive input-byte and record-count budgets; record bytes are capped at 128 KiB and nesting at 32 before buffer allocation or parsing. These are parser safeguards, not reviewed production feed budgets. The line buffer is fixed at `maxRecordBytes`; callbacks suspend byte consumption and are invoked once per validated object. Both the token and coroutine Job are checked during parsing and around emission. `ScryfallCodec.parse` supplies strict object, duplicate-key and nesting validation. LF/CRLF and an unterminated last object are accepted; empty input, blank lines, BOM and bare CR fail. Delimiters count toward total bytes but not record bytes. Final chunk, sequence and offset must match exactly.

Tests cover a derived JSONL wrapper of the three genuine checked-in objects plus synthetic byte splits (including UTF-8), delimiters/EOF, malformed UTF-8/JSON/scalars, duplicate fields, nesting, inclusive/one-over limits, construction ceilings, chunk continuity/final, digest, callback suspension, token and coroutine cancellation, and many small records. This does not establish full-feed correctness, coverage or throughput.

The authorized durability follow-up guards cleanup's FTS deletion with an `EXISTS` check that the generation still has `STAGING` status in the same transaction. A new native instrumentation test decorates the real storage port: after SQLite activation commits and returns success, the decorator injects a synthetic late `CancellationException`. The test checks active generation status and indexed name lookup immediately and after database close/reopen. The late exception timing is synthetic; activation, cleanup and persisted lookup use real storage. Prior malformed and precommit-cancellation cleanup tests remain.

Verification: `git diff --cached --check` passed for code commits. Root focused JVM run `38290489789` / job `114949124628` compiled source and passed eight existing tests; all nine new tests failed during setup because the test default exceeded the new constructor ceiling. Corrected focused JVM run `38291004261` completed **success** at exact code SHA `86dc00d7c1ac869152f26ac4a47b9940df011552`, per root `gh` readback. Durability instrumentation at `ed26b32`: **not_run**; root owns independent narrow durability review and full hosted Android matrix before integration. No SDK/compiler or full Android matrix assumed in this worktree.

Next integration request: review the narrow cleanup guard and run full hosted Android matrix including the real-storage late-cancellation test; correct any failures before integration. Later evaluate actual source bytes and reviewed bulk budgets with disk-backed staging before binding any full-feed import. Keep K2 array importer and public limits intact.

## Latest continuation delta

Authorized durability follow-up at `ed26b32`: cleanup FTS deletion is conditional on the generation still being `STAGING` in the same SQLite transaction. Native regression simulates only a late cancellation after the real activation commit and checks active status/index through close and reopen. Existing parser focused JVM run `38291004261` succeeded on `86dc00d`; new durability instrumentation **not_run**. Await independent narrow durability review and full hosted Android matrix. No full-feed source or adapter activation.
