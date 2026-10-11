# K2 core contract review, read-only (cc-k2-qa-01)

Reviewer: Claude Code (Opus 5.5), delegated QA writer, separate from the core author and from the Astra reviewer. Read-only: no core source, test, Gradle, CI or coordination file was changed.

## Pinned revision and method

- **Reviewed commit:** `9681aec9866a867c45a4a2f8bd51570687456512` on `agent/k2-core-contracts` ("[K2 contracts] Separate printing assets and enforce rollback and query bounds", committed 2026-10-11T22:59:01Z). Files were read with `git show` and a `git archive` extract of that commit. No working copy of an actively edited branch was read.
- **Compile mirror:** `agent/k2-contract-compile` at `2ac4a302f6832a9ec33e2739a687b5e3905a6015`. `git diff 9681aec 2ac4a30 -- android-app/core/model/src android-app/core/common/src` is empty, so its core source is identical to the reviewed commit.
- **Hosted evidence observed, not produced:** GitHub Actions run 38182564573 ("Kotlin contract compile", head `2ac4a30`) reports `conclusion: success` through the GitHub API (https://github.com/pemunson90-cmd/Command-Library/actions/runs/38182564573). This reviewer did not read its logs, so no test count or task list is claimed for this revision. The earlier 15-test result recorded in K2-PROGRESS.md belongs to `bcb6654`, not to this commit.
- **Scope read in full:** `Values.kt`, `CatalogContracts.kt`, `LocalCatalogContracts.kt`, `InfrastructureContracts.kt`, all three `core/common` sources, and all five test files. The other contract files (AI/inference, deck/collection, game, links/shell, pricing/validation, transfer) were checked by the name-coverage script below, and the transfer file's version fields by grep; they were not line-reviewed.
- **Not run:** no Kotlin compile, no test execution, no Android or device check. Every finding below is from reading declarations.

## Coverage of the KOTLIN_CONTRACTS inventory

A script extracted every backticked type name in the component table of `docs/architecture/KOTLIN_CONTRACTS.md` (main at a61daf6) and matched it against `interface|class|object` declarations in the reviewed `src/main` trees: **130 of 130 names declared, none missing**. The five phrase entries are present: source-qualified ID types (`CardId`, `OracleId`, `RuleId`, `DeckId`, `GameId`, `FormatId`), retry/charge classification (`RetryClassification`, `RequestCharge`), migration and generation metadata (`MigrationMetadata`, `GenerationMetadata`), immutable snapshots (`SnapshotList`, `SnapshotBytes`, data classes), and versioned interchange records (`InterchangeVersion` on transfer records). `Any` appears only in two `equals` overrides (`Values.kt` lines 15, 24); no raw maps, platform types, unfinished-work markers or error placeholders were found in the main sources read.

`CatalogRepository` keeps all six methods (`CatalogContracts.kt` lines 195-208). The segregated local ports `CatalogLookup`, `CatalogStatus` and `CatalogImport` (`LocalCatalogContracts.kt` lines 6-8, 19-21, 64-71) match the signatures accepted in K2-DESIGN-REVIEW.

## Astra blockers from K2-DESIGN-REVIEW (reviewed at bcb6654)

| # | Blocker | At 9681aec | Remaining evidence needed |
|---|---|---|---|
| 1 | Printing face images inside canonical faces | **Closed at declaration level.** `CardFace` has no image (`CatalogContracts.kt` 45-53). Assets moved to `PrintingFaceAsset` keyed by `PrintingFaceIdentity(printingId, index)` (54-58, 73-84). `sameCanonicalGameData` excludes printing assets (`CanonicalCardComparison.kt` 9-33). `PrintingFaceAssetTest` asserts two printings share canonical faces and keep their own art. | Importer and storage regression with real rows (matrix M03-1, vector SV-MF-01). |
| 2 | Unresolved related identity lost | **Closed at declaration level.** `FaceRelationship` adds `relatedPrinting: Knowledge<CardId>` and `rawRelatedIdentity: Knowledge<ProviderIdentity>` (37-44); canonical relationships hold no printing IDs (30-35). Test asserts the raw ID round-trips with oracle unknown. | Parser regression (M03-5, SV-MF-04). |
| 3 | Storage could not fetch the 101st row | **Closed.** `SqlRequest.MAX_ROWS = 101`, default `maxRows = 100` (`InfrastructureContracts.kt` 73-83); `PageRequest` stays 1..100. Test `sqlLookaheadAllows101RowsWithoutRelaxingPublicPageBound`. | Adapter test that the extra row never leaks (M05-3, SV-PG-03). |
| 4 | No rollback semantics for typed failures | **Closed at contract level.** `DbTransactionState` with irreversible `RollbackOnly` (115-120), typed `TransactionAbort` (122), documented latch and precedence rules on `DbTransaction` and `Database.transaction` (124-148), and a pure `RollbackOnlyLatch` with tests. | The actual Room3 adapter tests the review demanded: write then failure, write then abort, write then non-success return, cancel around commit (M07-2 to M07-7, SV-TX-01 to SV-TX-07). |

## Findings

Severity: **medium** means a consumer or adapter can satisfy the declared type and still violate a documented K2 rule; **low** means a weak spot or ambiguity with a narrow impact; **info** means an adapter obligation the type system cannot express. None is a compile blocker. Suggested changes to core belong to the core worker and root; this review applies none.

| ID | Sev | File:line | Finding | Suggested resolution |
|---|---|---|---|---|
| F1 | medium | `InfrastructureContracts.kt` 183 | `ConsentRecord.permits` compares the whole `DownloadIntent` with `==`, including its `id` and `expectedBytes`. A new intent object for the same pack, with a fresh ID, is not permitted; a changed byte estimate also voids consent. Whether consent is per instance or per scope is not written anywhere. | Decide per-scope or per-instance; if per-scope, compare (kind, source, resource, version, language, packId) explicitly. Vector SV-SET-05. |
| F2 | medium | `InfrastructureContracts.kt` 175-184, 195 | `SettingsSnapshot.consents` round-trips full `ConsentRecord`s, and nothing distinguishes a consent approved on this install from one restored from a backup. "Restore never grants consent" therefore rests entirely on the future restore adapter. | Add an origin marker or keep consents out of restorable settings; test M11-4 / SV-SET-02. |
| F3 | medium | `LocalCatalogContracts.kt` 50-56 | `CatalogImportVerification.rejectedRecords` allows a `Success` result that skipped records, while K2-FOUNDATION-DESIGN says invalid input fails deterministically with no partial success. Which records may be skipped (if any) is undefined. | Require `rejectedRecords == 0` on success, or document exactly which record kinds are skipped and surface the count. Vector SV-IMP-12. |
| F4 | medium | `CatalogContracts.kt` 86-106, 108-125 | Neither `CardRecord` nor `PrintingRecord` checks that its faces belong to it: face `oracleId` equal to the record's, face indices unique and 0..n-1, face-asset `printingId` equal to the printing's `id`, asset language consistent. An adapter bug can produce an internally inconsistent snapshot that still type-checks. | Add `init` checks, or importer validation with tests. Vector SV-MF-05. |
| F5 | low | `CatalogContracts.kt` 28, 37-44 | `FaceRelationshipKind.OTHER` collapses supplied relationship kinds that have no enum value (for example token or combo-piece parts), and no raw kind string is kept. K2-DESIGN-REVIEW asked to preserve the supplied relationship kind. | Add `rawKind: String` beside the enum. |
| F6 | low | `CanonicalCardComparison.kt` 22-23, 29 | `colors`, `colorIdentity` and `keywords` compare as ordered lists, so identical sets in a different order would report a canonical conflict and fail activation. | Compare as sets, or normalize order on import and test it. |
| F7 | low | `RollbackOnlyLatch.kt` 10, 35-37 | The latch is a plain `var` with a check-then-set. If a transaction callback launches concurrent operations, two failures can race and "first status wins" is not guaranteed. | Use an atomic compare-and-set, or document that a transaction is confined to one coroutine and test that. |
| F8 | low | `Values.kt` 122-130 | `DecimalAmount` requires `scale >= 0`, so `DecimalAmount.exact("1E+3", ...)` throws `IllegalArgumentException`. A provider number in exponent form becomes an uncaught exception rather than a typed result. | Normalize scale in the parser, or map to typed `INVALID_INPUT`. Vector SV-DEC-02. |
| F9 | low | `Values.kt` 31-34 | `LanguageTag` accepts any non-blank text; "EN" and "en-US" are not equal to `LanguageTag.ENGLISH`. The K2 English-only rule has no canonical form. | Define canonicalization (lowercase, region handling) at one boundary. Vector SV-ID-04. |
| F10 | low | `Values.kt` 49-51 | `ContentHash` checks hex characters only, not digest length for the algorithm (a 63-character "SHA-256" is accepted). | Validate length for known algorithms. |
| F11 | low | `LocalCatalogContracts.kt` 9-18 | `initialized` can disagree with `activeGenerations`, and single `source`, `sourceDate` and `fetchedAt` values sit beside a list of generations. | Derive `initialized`, or document the invariant and which generation the single fields describe. |
| F12 | low | `Values.kt` 82-92, 89 | `Provenance.generation` is nullable, so a local lookup result can omit the generation K2 needs for cursor pinning and status. | Require non-null generation for catalog records from the local store, or assert it in adapter tests (SV-ID-05). |
| F13 | info | `InfrastructureContracts.kt` 51-57 | `ByteChunk` cannot express framing: contiguous sequence numbers, offsets equal to bytes received, exactly one final chunk, nothing after it. The importer must enforce all four. | Adapter tests SV-IMP-03 to SV-IMP-05. |
| F14 | info | `Values.kt` 109-115 | `Page` checks the global 100-item ceiling but cannot see the request's own limit. | Adapter test that items never exceed the request limit (M05-4). |
| F15 | info | `Values.kt` 135-138 | `CancelToken.throwIfCancelled` is not required to throw `CancellationException`; `CoroutineCancelToken` does (`ContractSemantics.kt` 26-29), but another implementation could throw something the classifier turns into `Failure`. | State the requirement in the KDoc and test every token implementation. |
| F16 | low | `InfrastructureContracts.kt` 10 | `HttpHeader` rejects line feed in names but not carriage return. Not consumed by K2. | Reject both in names. |

## Test gaps at 9681aec

Present tests cover page and cursor bounds, the 101-row lookahead, defensive copies, known/absent/unknown distinctions, foreign identity, provenance separation, consent revocation, exact decimals, import limit construction, the latch, cancellation precedence in `cancellableOutcome`, canonical comparison, and both Astra regressions. Missing for the declared semantics:

- F1 same-scope new-instance consent; F4 inconsistent face identity; F6 reordered colors or keywords; F7 concurrent latch use; F8 exponent-form decimals; F9 language tag spellings; F10 digest length; F11 status invariants.
- Everything adapter-level is untested by construction at this layer: real transactions, import framing and limits, FTS binding, generation activation, DataStore persistence. The matrix rows marked AIT carry those.

## Unspecified behavior that blocks exact assertions

No accepted document defines these, so the matrix marks them `decision required` instead of inventing an expected result:

1. The Unicode normalization of the lookup key (normal form, case folding, diacritic and ligature handling) and its match with the FTS5 `unicode61` options.
2. Token, phrase and prefix rules for apostrophes, hyphens and the final token.
3. Whether a non-English record, a UTF-8 byte order mark, or an empty array fails the import.
4. Whether an explicit JSON null for a stat is `Absent` or `Unknown`.
5. Consent scope (F1) and restore handling (F2).

## Verdict

**The declarations at 9681aec close Astra's four blockers at contract level.** The 130 inventory names are present, and this review found no omitted public type or method. Four medium findings (F1 to F4) are semantic gaps, not compile problems, and are cheaper to settle before consumers bind to the types. Consumer acceptance still needs the root's own recheck plus the adapter evidence Astra required. This review is QA input, not that acceptance.
