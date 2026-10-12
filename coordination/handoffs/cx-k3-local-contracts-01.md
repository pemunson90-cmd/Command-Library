# K3 local rules contract candidate

Status: ready for independent public-contract review; no implementation/schema binding accepted here.
Owner: `agent/k3-local-contracts-01`. Scope: `LocalRulesContracts.kt`, its focused core-model test, and this handoff only.

## Candidate

- Adds `LocalRulesImportSource`: selected `content://` URI, English scope and caller-supplied `Knowledge` for upstream version, effective/source dates, source URI, notice, expected SHA-256 and bytes. Unknown values remain unknown. File selection authorizes reading that local input only; it creates no `ConsentRecord` and grants no remote download.
- Adds independently injectable `LocalRulesImport` with a reopenable `() -> Flow<ByteChunk>` input for the parser's two passes, progress callback and cancellation, returning an active generation with observed bytes, computed hash, verification state and activation time. `LocalRulesStatus` separates the last good corpus from a durable latest attempt/failure.
- Adds `LocalRulesLookup` with bounded `RulesQuery`/`Page<RuleEntry>` search and explicit `(RuleId, corpusId)` entry lookup. Search with a null corpus selection binds to the active corpus; the returned `Page.snapshot` identifies it. A later activation must not silently substitute an entry with the same RuleId from a new corpus.
- Leaves `RulesSource`, `RulesCorpus`, `RulesRepository` and all other aggregate/public declarations unchanged. This is no partial aggregate adapter and includes no storage, parser, app, UI or network implementation.

## Binding and review requests

1. Root and independent cross-module reviewer decide whether these narrow names/signatures and status semantics are the accepted local rules capability. In particular, assess the reopenable stream lifecycle and whether retaining a URI in `LocalRulesCorpus` needs a separate display-only policy when picker permission ends.
2. Root/storage owner designs Room v1→v2 rules corpus/entry/reference/FTS tables, active pointer and durable attempt row. Stage both parser passes, discard all staged entries on any failure, activate only after exact-byte hash agreement in one transaction. Preserve earlier active generation and guard cleanup against post-commit cancellation. Independent durability review required.
3. Production rules adapter must enforce finite private parser byte/line/entry/reference ceilings and bounded replay/spooling; this contract does not claim a source-sized budget. Genuine TXT byte/header/version/encoding and source-rights review gate production activation. An unversioned local file cannot be passed through required-string `RulesSource.version` by invention.
4. Shell/search later wire only working local actions; use `Page.snapshot.generation` to reopen a selected result. Test stale cursors and old-corpus entry behavior on replacement, close/reopen and cancellation. Keep remote acquisition disabled until separately reviewed.

## Verification

Added synthetic model tests for unversioned provenance, local/English scope, expected metadata validity, active result and page generation. Tried `./gradlew --no-daemon :core:model:test` in the isolated worktree; it did **not run** because downloading Gradle 9.6.0 from services.gradle.org failed with `Connection refused` after three attempts. No test pass is claimed. Hosted focused model test is needed after review.

## Latest continuation delta

Candidate is confined to the three assigned files in the isolated branch. Next: root reviews public contract and storage implications, runs hosted model tests, then decides integration. No source call, product download or schema/app change was made.

Independent recovery review found signatures sound conditional on public KDoc fixes. Snapshot/cursor, stream lifecycle, cancellation/bounds/commit guarantees now specified in capability contract. Hosted validation pending; no production acceptance.
