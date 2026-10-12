# Catalog JSONL framer handoff

Status: READY-FOR-REVIEW. Internal parser only; no product binding or full-feed claim.

Base: `478eb4aec3e98f4f739deb0d812ea97049356ce2`.
Code commit: `0ff9a0f`.
Branch: `agent/k3-catalog-framer-01`.

Changed files:
- `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramer.kt`
- `android-app/feature/catalog/src/test/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramerTest.kt`

`JsonLinesFramer(JsonLinesLimits, CancelToken)` consumes contiguous `ByteChunk`s through `accept(chunk, suspend (String) -> Unit)`, then `finish()`. It reports final exact-byte SHA-256, observed input bytes and emitted record count. Its caller must supply finite positive input-byte, record-byte, record-count and nesting limits. These values are parser safeguards, not reviewed production feed budgets. The line buffer is fixed at `maxRecordBytes`; callbacks suspend byte consumption and are invoked once per validated object. `ScryfallCodec.parse` supplies strict object, duplicate-key and nesting validation. LF/CRLF and an unterminated last object are accepted; empty input, blank lines, BOM and bare CR fail. Delimiters count toward total bytes but not record bytes. Final chunk, sequence and offset must match exactly.

Tests cover a derived JSONL wrapper of the three genuine checked-in objects plus synthetic byte splits (including UTF-8), delimiters/EOF, malformed UTF-8/JSON/scalars, duplicate fields, nesting, inclusive/one-over limits, chunk continuity/final, digest, callback suspension, cancellation and many small records. This does not establish full-feed correctness, coverage or throughput.

Verification: `git diff --cached --check` passed before code commit. JVM tests: **not_run** in this worktree; root owns focused `:feature:catalog:testDebugUnitTest` CI and evidence. No SDK/compiler or full Android matrix assumed here.

Next integration request: run focused catalog JVM verification, review any failures and this internal parser, then evaluate actual source bytes and reviewed bulk budgets with disk-backed staging before binding any full-feed import. Keep K2 array importer and public limits intact.

## Latest continuation delta

Internal JSONL framer and focused tests committed at `0ff9a0f`; no source or adapter activation. Await root focused JVM evidence. If tests fail, return exact failure output for a bounded fix in this branch. Verification status: diff check passed; JVM not_run.
