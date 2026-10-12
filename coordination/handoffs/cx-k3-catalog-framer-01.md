# Catalog JSONL framer handoff

Status: READY-FOR-REVIEW. Internal parser only; no product binding or full-feed claim.

Base: `478eb4aec3e98f4f739deb0d812ea97049356ce2`.
Code commit: `0ff9a0f`.
Branch: `agent/k3-catalog-framer-01`.

Changed files:
- `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramer.kt`
- `android-app/feature/catalog/src/test/kotlin/com/architectpsg/commandlibrary/feature/catalog/JsonLinesFramerTest.kt`

`JsonLinesFramer(JsonLinesLimits, CancelToken)` consumes contiguous `ByteChunk`s through `accept(chunk, suspend (String) -> Unit)`, then `finish()`. It reports final exact-byte SHA-256, observed input bytes and emitted record count. Its caller must supply positive input-byte and record-count budgets; record bytes are capped at 128 KiB and nesting at 32 before buffer allocation or parsing. These are parser safeguards, not reviewed production feed budgets. The line buffer is fixed at `maxRecordBytes`; callbacks suspend byte consumption and are invoked once per validated object. Both the token and coroutine Job are checked during parsing and around emission. `ScryfallCodec.parse` supplies strict object, duplicate-key and nesting validation. LF/CRLF and an unterminated last object are accepted; empty input, blank lines, BOM and bare CR fail. Delimiters count toward total bytes but not record bytes. Final chunk, sequence and offset must match exactly.

Tests cover a derived JSONL wrapper of the three genuine checked-in objects plus synthetic byte splits (including UTF-8), delimiters/EOF, malformed UTF-8/JSON/scalars, duplicate fields, nesting, inclusive/one-over limits, construction ceilings, chunk continuity/final, digest, callback suspension, token and coroutine cancellation, and many small records. This does not establish full-feed correctness, coverage or throughput.

Verification: `git diff --cached --check` passed before code commit. JVM tests: **not_run** in this worktree; root owns focused `:feature:catalog:testDebugUnitTest` CI and evidence. No SDK/compiler or full Android matrix assumed here.

Next integration request: run focused catalog JVM verification, review any failures and this internal parser, then evaluate actual source bytes and reviewed bulk budgets with disk-backed staging before binding any full-feed import. Keep K2 array importer and public limits intact.

## Latest continuation delta

Audit follow-up: added coroutine Job cancellation checks so a live token plus a non-suspending callback cannot emit after parent cancellation; constructor now enforces 128 KiB record and 32 nesting ceilings before allocation/recursive parse. Focused tests added for both. No source or adapter activation. Await root focused JVM evidence. Verification status: diff check passed for initial commit; corrected JVM source not_run.
