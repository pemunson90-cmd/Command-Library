# K2 QA test matrix (cc-k2-qa-01)

Author: Claude Code, delegated QA writer. Branch `agent/cc-k2-qa-01`, base `main` a61daf604ffe8ecff6fcae3b0f1c5e31da42d9db.
Scope: the K2 functional finish line in `coordination/tasks/orch-kotlin-m0-01.md`, the foundation design in `docs/architecture/K2-FOUNDATION-DESIGN.md`, the Kotlin contract rules in `docs/architecture/KOTLIN_CONTRACTS.md`, and the four corrections required by `docs/gauntlet/gates/K2-DESIGN-REVIEW.md`.

Every row below is a planned assertion. **No row has been executed by this QA task.** The Status column starts at `not_run` for every row; only a recorded run with its log, commit and runner may change it. Vector IDs refer to `synthetic-vectors.json` in this folder, which holds synthetic inputs only.

## Layers

| Code | Layer | Where it runs | Notes |
|---|---|---|---|
| JVM | Pure Kotlin unit test | `android-app/core/model`, `android-app/core/common`, and pure domain parts of feature modules, on a hosted JDK | No Android, Room, SQLite, Compose or network types |
| AIT | Android instrumentation | `connectedDebugAndroidTest` on a hosted emulator | Real Room3-generated schema, bundled SQLite FTS5, DataStore, transactions |
| EMU | Emulator UI | Compose UI tests and launch checks on hosted emulators (minimum API 23 and a modern API 36 image, as available) | Adaptive widths, recreation, labels, large text |
| ART | Artifact provenance | Hosted workflow output | APK, SHA-256, tested commit, run URL, alignment observations |
| PHYS | Physical device | Pat's Galaxy Z Fold7 | Not part of K2; listed so it is never inferred from an emulator |

## Matrix

### M01 Source-qualified identity

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M01-1 | Two IDs with the same raw string and different `ProviderId` are unequal for every ID type (`CardId`, `OracleId`, `RuleId`, `DeckId`, `GameId`, `FormatId`). | JVM | SV-ID-01 | KOTLIN_CONTRACTS shared rules | not_run |
| M01-2 | Blank raw IDs and blank provider IDs are rejected at construction. | JVM | SV-ID-03 | Values.kt ID types | not_run |
| M01-3 | Imported records keep the producer's oracle ID, printing ID and provider exactly through import, storage and lookup (byte-equal round trip). | AIT | SV-ID-05 | K2 design, oracle_card and printing tables | not_run |
| M01-4 | A foreign-language printing with unresolved oracle mapping keeps its raw identity and language; no oracle ID is guessed from the name and no language download starts. | JVM, AIT | SV-ID-02 | DATA_MODEL identity; SPEC A02 | not_run |
| M01-5 | Identity is never inferred from a localized or normalized name: two oracles with the same normalized name remain two records. | AIT | SV-ID-06 | KOTLIN_CONTRACTS shared rules | not_run |
| M01-6 | Language tag comparison for the K2 English-only rule uses one documented canonical form. | JVM | SV-ID-04 | Values.kt `LanguageTag`; see CONTRACT-REVIEW F9 | not_run (decision required) |

### M02 Original Unicode versus normalized lookup

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M02-1 | Original name and text are stored and returned unchanged (same code points), including precomposed versus combining forms. | AIT | SV-UNI-01 | SPEC A08 "without destructive normalization"; DATA_MODEL line 53 | not_run |
| M02-2 | Lookup input is trimmed; length is counted in Unicode code points, not UTF-16 units; 128 code points accepted, 129 rejected with a typed `INVALID_INPUT` failure. | JVM | SV-UNI-02, SV-UNI-04 | K2 design, bound local lookup | not_run |
| M02-3 | Empty or whitespace-only input returns a truthful bounded empty result; no remote call is attempted. | JVM, AIT | SV-UNI-03 | K2 design, bound local lookup | not_run |
| M02-4 | The normalization applied to the search key is documented (Unicode normal form, case folding, diacritic handling) and matches the FTS tokenizer configuration. | JVM, AIT | SV-UNI-01, SV-FTS-06 | Not yet specified anywhere; see CONTRACT-REVIEW "Unspecified behavior" | not_run (decision required) |

### M03 Multi-face records and collector numbers

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M03-1 | Two printings of one multi-face oracle produce one canonical card and keep their own per-face image assets keyed by printing ID and face index. | JVM, AIT | SV-MF-01 | K2-DESIGN-REVIEW blocker 1; PrintingFaceAssetTest | not_run |
| M03-2 | A real canonical game-field conflict between printings of the same oracle fails the import before activation. | AIT | SV-MF-02 | K2 design, oracle_card integrity | not_run |
| M03-3 | Face order and face count are preserved; face identity is oracle plus index, never a guessed producer face ID. | JVM, AIT | SV-MF-01 | CanonicalCardComparison.kt | not_run |
| M03-4 | Collector numbers stay text: "001a", "0012" and "12" are distinct and round-trip exactly; no integer coercion in storage, sort or lookup. | AIT | SV-MF-03 | K2 design, printing table | not_run |
| M03-5 | A related part supplied only by raw printing ID round-trips with `relatedOracle` unknown and no name or language guess. | JVM, AIT | SV-MF-04 | K2-DESIGN-REVIEW blocker 2 | not_run |
| M03-6 | Face records whose identity disagrees with their parent record (wrong oracle ID, duplicate or gapped index) are rejected. | JVM, AIT | SV-MF-05 | See CONTRACT-REVIEW F4 | not_run (decision required) |

### M04 Known, absent, unknown and exact decimal units

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M04-1 | `Known("0")`, `Known("")`, `Absent` and `Unknown` stay distinct through import, storage and lookup for stats, text and mana cost. | JVM, AIT | SV-KN-01, SV-KN-02, SV-KN-03 | KOTLIN_CONTRACTS; ValuesTest | not_run |
| M04-2 | Half mana values keep exact decimal value; canonical comparison treats 3 and 3.0 as equal. | JVM | SV-KN-04 | CanonicalCardComparison.kt line 21 | not_run |
| M04-3 | Decimal arithmetic keeps exact value, scale and native unit (USD, TIX); no binary floating point; negative quantities rejected. | JVM | SV-DEC-01 | ContractSemantics.kt `ExactDecimalPolicy` | not_run |
| M04-4 | Unknown price is never zero; a missing quote is `Unknown` or `Unavailable`, never `Known(0.00)`. | JVM | SV-DEC-03 | KOTLIN_CONTRACTS shared rules | not_run |
| M04-5 | Exponent-form decimal text has a defined behavior (accept with normalized scale, or typed rejection), never an uncaught exception at an import boundary. | JVM | SV-DEC-02 | See CONTRACT-REVIEW F8 | not_run (decision required) |
| M04-6 | Lowercase or malformed currency codes are rejected. | JVM | SV-DEC-04 | Values.kt `DecimalUnit.Fiat` | not_run |
| M04-7 | A source date that was not supplied stays `Unknown`; it is never filled from file modification time or import time. | JVM, AIT | SV-IMP-15 | LocalCatalogContracts.kt `CatalogImportSource` | not_run |

### M05 Bounded pages and cursors

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M05-1 | `PageRequest` accepts limits 1 to 100 and rejects 0 and 101. | JVM | SV-PG-01 | Values.kt `PageRequest` | not_run |
| M05-2 | Cursors are bounded at 2048 UTF-8 bytes in both requests and responses, measured in bytes, not characters. | JVM | SV-PG-02 | Values.kt; ValuesTest | not_run |
| M05-3 | With 101 matching rows and limit 100, the page has exactly 100 items and a next cursor; with exactly 100 rows, no next cursor. The lookahead row never appears in the page. | AIT | SV-PG-03 | K2-DESIGN-REVIEW blocker 3 | not_run |
| M05-4 | A page never holds more items than its request's limit (not only the global 100 ceiling). | AIT | SV-PG-03 | See CONTRACT-REVIEW F14 | not_run |
| M05-5 | A cursor issued under one active generation and presented after another activates yields a typed `STALE_GENERATION` failure; no mixed-generation page and no silent restart. | AIT | SV-PG-04 | K2 design, bound local lookup | not_run |
| M05-6 | A malformed or tampered cursor yields a typed `INVALID_INPUT` failure. | JVM, AIT | SV-PG-05 | K2 design | not_run |
| M05-7 | Results are ordered by normalized name, then producer ID, including ties, and the order is stable across pages. | AIT | SV-FTS-08 | K2 design | not_run |

### M06 Stale revision

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M06-1 | A mutation with a stale expected revision fails with `STALE_REVISION` and changes nothing. | JVM, AIT | SV-REV-01 | ContractSemantics.kt | not_run |
| M06-2 | A committed mutation advances the revision exactly once; a failed or cancelled one does not advance it. | JVM, AIT | SV-REV-01, SV-REV-03 | KOTLIN_CONTRACTS shared rules | not_run |
| M06-3 | Revision exhaustion returns a typed failure. | JVM | SV-REV-02 | ContractSemantics.kt | not_run |
| M06-4 | Settings revision comparison happens inside the DataStore update, so two concurrent updates with the same expected revision cannot both commit. | AIT | SV-REV-03 | K2 design, settings | not_run |

### M07 Cancellation rethrow and rollback after writes

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M07-1 | Cancellation is rethrown unchanged by every public suspend boundary; it is never converted to `Failure`, `Unavailable`, `Unknown` or `Success`. | JVM | SV-TX-04 | KOTLIN_CONTRACTS; ContractSemanticsTest | not_run |
| M07-2 | Write, then a failed operation whose result the callback ignores: no row commits, outer result is that failure. | AIT | SV-TX-01 | Infrastructure contract `Database.transaction` | not_run |
| M07-3 | Write, then a caught `TransactionAbort`: no row commits, outer result is the abort failure. | AIT | SV-TX-02 | K2-DESIGN-REVIEW blocker 4 | not_run |
| M07-4 | Write, then the callback returns a non-success `Outcome` normally: no row commits; outer result is that status, never `Success(non-success)`. | AIT | SV-TX-03 | Infrastructure contract | not_run |
| M07-5 | The first latched status wins over later failures. | JVM, AIT | SV-TX-06 | RollbackOnlyLatch.kt | not_run |
| M07-6 | Cancellation takes precedence over any latched status and still rolls back. | AIT | SV-TX-07 | Infrastructure contract | not_run |
| M07-7 | Cancellation arriving after a durable commit leaves the commit in place; a later status read reports it. | AIT | SV-TX-05 | K2 design, local-file import step 5 | not_run |
| M07-8 | Import cancellation during staging rethrows, keeps the old active generation and cleans or visibly marks only the abandoned staging generation. | AIT | SV-ACT-02 | K2 design, local-file import step 4 | not_run |

### M08 Malformed and oversized input

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M08-1 | Invalid UTF-8 fails the import with a typed failure; nothing activates. | JVM, AIT | SV-IMP-01 | K2 design, import step 2 | not_run |
| M08-2 | Truncated JSON, a stream ending without a final chunk, out-of-order or gapped chunks, and data after the final chunk all fail; none yields partial success. | JVM, AIT | SV-IMP-02, SV-IMP-03, SV-IMP-04, SV-IMP-05 | K2-DESIGN-REVIEW obligations | not_run |
| M08-3 | Each limit (input bytes, printing count, record bytes, nesting depth) fails deterministically when exceeded by one, and passes at exactly the limit. Tests may use reduced `CatalogImportLimits` values. | JVM, AIT | SV-IMP-06 to SV-IMP-09 | LocalCatalogContracts.kt `CatalogImportLimits` | not_run |
| M08-4 | Nesting is rejected before recursive growth (no stack overflow on deep input). | JVM | SV-IMP-09 | K2-DESIGN-REVIEW obligations | not_run |
| M08-5 | Duplicate printing IDs, missing identity fields and invalid numeric fields fail the import. | JVM, AIT | SV-IMP-10, SV-IMP-11, SV-IMP-19 | K2 design, import step 2 | not_run |
| M08-6 | Expected checksum or byte-count mismatch fails with `SOURCE_MISMATCH`; the computed SHA-256 covers the exact incoming bytes. | JVM, AIT | SV-IMP-13, SV-IMP-14 | LocalCatalogContracts.kt | not_run |
| M08-7 | Unsupported framing (a top-level object instead of an array) fails. | JVM | SV-IMP-18 | K2 design, import step 1 | not_run |
| M08-8 | Non-English records, a UTF-8 byte order mark, and an empty array each have one documented outcome. | JVM, AIT | SV-IMP-12, SV-IMP-16, SV-IMP-17 | See CONTRACT-REVIEW F3 and "Unspecified behavior" | not_run (decision required) |
| M08-9 | Batch inserts never exceed 128 rows per staging batch. | AIT | SV-IMP-07 | K2 design, import step 2 | not_run |

### M09 Atomic old-generation preservation

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M09-1 | A failed import leaves the previous active generation, its counts and its lookups unchanged. | AIT | SV-ACT-01 | SPEC A06; K2 design | not_run |
| M09-2 | During staging, lookups keep reading the committed active generation; no partially staged record is visible. | AIT | SV-ACT-01 | K2 design, import step 4 | not_run |
| M09-3 | Successful activation retires the old generation and moves the single active pointer in one transaction; counts match the imported data. | AIT | SV-ACT-03 | K2 design, import step 5 | not_run |
| M09-4 | Only one import runs at a time; a second import waits or is rejected with a typed status, and never interleaves staging. | AIT | SV-ACT-05 | K2 design, import step 4 | not_run |

### M10 Close, reopen and process recreation

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M10-1 | After close and reopen, catalog status, active generation, counts and lookup results are identical. | AIT | SV-PER-01 | K2 design | not_run |
| M10-2 | Killing the process during staging, then restarting, discards only the abandoned staging generation; active, retired and user data survive. | AIT | SV-ACT-04 | K2 design, import step 5 | not_run |
| M10-3 | Opening a database with a newer unsupported schema shows a visible recoverable error and clears nothing. | AIT | SV-PER-02 | K2 design, storage schema | not_run |
| M10-4 | Activity recreation shows committed state, not a remembered earlier success. | EMU | none (UI) | K2 design, settings and UI | not_run |

### M11 Persisted English settings and restore without consent

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M11-1 | A fresh install reads data language `en`, mode FULL, no consents, and catalog not initialized ("Full English" is a default, not installed data). | AIT | SV-SET-01 | SPEC K01; K2 design | not_run |
| M11-2 | `approve` stores a consent record and starts no job or transfer. | AIT | SV-SET-03 | K2 design, settings | not_run |
| M11-3 | Revocation persists across reopen and blocks matching intents afterwards. | AIT | SV-SET-04 | InfrastructureContracts.kt `ConsentRecord` | not_run |
| M11-4 | Restoring settings never reinstates effective download permission. | AIT | SV-SET-02 | KOTLIN_CONTRACTS; SPEC A02; see CONTRACT-REVIEW F2 | not_run |
| M11-5 | Consent scope matching has one documented rule (per intent instance or per scope). | JVM | SV-SET-05 | See CONTRACT-REVIEW F1 | not_run (decision required) |
| M11-6 | Settings survive close/reopen with the same revision; a failed or cancelled update leaves the previous settings. | AIT | SV-REV-03 | K2 design, settings | not_run |

### M12 Bound FTS token and punctuation behavior

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M12-1 | User input reaches FTS5 only as an escaped literal bound as a SQL parameter; quotes, apostrophes, `*`, `^`, `:`, parentheses and FTS keywords (AND, OR, NOT, NEAR) never raise a syntax error or change query meaning. | AIT | SV-FTS-01 to SV-FTS-04 | K2 design, bound local lookup | not_run |
| M12-2 | SQL injection text is inert. | AIT | SV-FTS-05 | K2 design | not_run |
| M12-3 | Token, phrase and prefix behavior matches a written policy, tested with apostrophes, hyphens, diacritics and non-Latin names. | AIT | SV-FTS-01, SV-FTS-06, SV-FTS-07 | K2 design (policy to be written) | not_run (decision required) |
| M12-4 | The bundled SQLite build provides FTS5 with the configured `unicode61` tokenizer on every tested API level; no platform SQLite assumption. | AIT | none | K2 design; K1-FTS5-SOURCES | not_run |

### M13 Usable compact, medium and expanded UI

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M13-1 | Empty, import-in-progress, failed import, successful import and lookup states render real data at compact, medium and expanded widths, with every control reachable. | EMU | none | orch-kotlin-m0-01 finish line; SPEC J01 | not_run |
| M13-2 | Import can be cancelled from the UI; the old catalog stays usable. | EMU | SV-ACT-02 | K2 design, UI | not_run |
| M13-3 | Every visible string comes from externalized English resources; no future-feature tabs or inert controls appear. | EMU, JVM | none | SPEC J03; D-18 | not_run |
| M13-4 | Labels exist for screen readers and layouts survive 200% text. Real TalkBack speech stays PHYS. | EMU | none | SPEC J03 | not_run |
| M13-5 | Missing source date displays as unknown; displayed counts and generation equal persisted values. | EMU | SV-IMP-15 | K2 design, UI | not_run |
| M13-6 | No card-art request or network transfer happens in K2 flows. | EMU | none | K2 design | not_run |

### M14 Artifact, commit, hash and install provenance

| ID | Assertion | Layer | Vectors | Basis | Status |
|---|---|---|---|---|---|
| M14-1 | The debug APK has application ID `com.architectpsg.commandlibrary` and installs and launches on the hosted emulators. | ART, EMU | none | orch-kotlin-m0-01 finish line | not_run |
| M14-2 | The retained APK's SHA-256, tested commit and run URL are recorded together. | ART | none | orch-kotlin-m0-01 step 4 | not_run |
| M14-3 | ZIP 16 KiB alignment (`zipalign -c -P 16 -v 4`) and ELF segment alignment are both observed and recorded separately. | ART | none | orch-kotlin-m0-01 step 4 | not_run |
| M14-4 | The genuine fixture used by K2 has source URL, observed retrieval date, SHA-256 and parser lineage recorded by the executor; synthetic vectors from this folder are never presented as that fixture. | ART | all vectors are synthetic | orch-kotlin-m0-01 step 2 | not_run |
| M14-5 | No signing secret is used in K2 workflows. | ART | none | orch-kotlin-m0-01 step 5 | not_run |

## Partial, not waived

K2 finishes only the foundation portions of N01, N03, A06, A08, K01 and K02. These remain open after K2 even if every row above passes: the full corpus and performance budgets, physical Fold7 hinge, posture and TalkBack evidence (IMPOSSIBLE.md IM-01 to IM-04), backup and restore, the vault, permanent-key signed release, and the rest of the 64 requirements and 213 parity obligations.
