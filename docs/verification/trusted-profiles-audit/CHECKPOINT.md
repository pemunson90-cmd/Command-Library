# Trusted resource profiles audit: checkpoint

Read-only implementation-readiness audit requested by Pat through the Claude Code packet `coordination/handoffs/environment-transition-2026-10-13/CLAUDE-CODE.md` on `agent/cx-full-v1-resume-01` (315747d). Scope: three user-downloadable profiles (strict English cards, Scryfall rulings, Wizards Comprehensive Rules TXT) matched to the actual Kotlin contracts. This is evidence for Codex, not permission to implement and not a runtime or acceptance claim. No production code, CI, schema, contracts, navigation, shared coordination or locks were touched. Everything here is new files under this directory on session branch `ccr-d1e9ac59-b90cv2`.

## Status (2026-10-13)

| Step | State | Output |
|---|---|---|
| 1. Evidence: 6 read-only agents (engine/network code, consumer code, Scryfall search sources + 1 live page-1 probe, Scryfall rulings sources + metadata/HEAD/64 KiB Range probes, Wizards CR page/TXT/terms, repo rulings and measurements) | DONE | `evidence/*.md` (finding IDs cited below) |
| 2. Synthesis: per-profile profile tables + cross-cutting gaps, each checked by 3 adversarial lenses (code, official source, packet/rulings), then revised | RUNNING when this checkpoint was written | not yet committed; rerun `workflows/2-synthesis.js` if lost |
| 3. Final report: one compact profile table, prioritized gaps, acceptance cases, unknowns | NOT STARTED | target `TRUSTED-PROFILES-REPORT.md` in this directory |

Probe bytes stayed outside Git (container scratch, lost on environment move). Nothing from the corpus is committed beyond short quoted lines in the evidence files.

## How to resume (Codex or Claude)

1. Read the six `evidence/*.md` files. They are the full inputs; finding IDs (for example F42, RUL-11, W-20, MEAS-08) are stable references.
2. Either rerun step 2 with `workflows/2-synthesis.js` (paths inside point to the original scratch worktree; change `OLD`/`NEW`/`ROOT`/`EV` to a fresh checkout of 315747d and this `evidence/` directory), or write the report directly from the evidence plus the preliminary conclusions below. Each conclusion still needs the verification pass before Codex relies on it.
3. Output constraint from the packet: at most one compact profile table, then prioritized contract/implementation gaps and acceptance cases. Every material claim tagged VERIFIED (code file:line or official URL fetched with date), INFERRED_POLICY, or UNRESOLVED.

## Preliminary conclusions (from step 1 evidence; not yet adversarially verified)

High impact, code-level (file:line in evidence):

- No production `validateTrustedRequest`, `endpointFor` or `connection` exists; test lambdas accept everything or one fixture (code-engine X02). No production construction of `LocalAcquisition` or `OkHttpNetworkClient`; `:app` does not depend on `:data:network`; no INTERNET permission in project manifests (X01, X03).
- `OkHttpNetworkClient` converts every non-2xx (429, 5xx) and the 120 s overall deadline into `Outcome.Failure(TRANSPORT)` without metadata, so the engine's `waitRateLimit` path is dead with the production transport. The job ends FAILED, and resume from FAILED restarts from offset 0 / page 1, consuming one of `MAX_ATTEMPTS = 3` (F42, F43, F53). The English draft's "429 pauses then waits for explicit resume" does not match the code.
- User-Agent is the constant `CommandLibrary/1 Android (offline catalog acquisition)`; Accept is always `application/json`; no profile field can change headers or the 120 s timeout. RequestSpec headers can override Accept in OkHttp, but the engine never supplies one (X04, F37, F92). Scryfall requires an accurate app name/version UA and an Accept header on api.scryfall.com (src-cards C-13).
- A RemoteFile labelled `ENGLISH_CARDS` (for example default_cards) passes engine validation and `CatalogBulkImportSource`; only the absent trusted validator can block it (F71).
- Consent is exact `DownloadIntent` data-class equality, including `resource`, `expectedBytes` (and Knowledge reason strings), `checksum`, `version`. Dated file URLs (rulings rotate about every 12 h; CR per release) therefore need a new consent per refresh (F80, F82).
- Once the guard is wired, `authorizeWireDispatch` rejects any request not registered by `LocalAcquisition.transfer` (LocalAcquisition.kt:422), and the binding review forbids a second ungated client. Metadata or rules-page discovery outside the engine is therefore not compliant; the engine has no discovery source variant (F90, F91).
- `maxDecodedBytes`, `maxRecords`, `maxRecordBytes`, `maxNesting`, `maxPages`, `maxPageBytes` are enforced only for EnglishSearch; inert for RemoteFile gzip (F19, F93). No Content-Type or framing check; with size and sha unknown an HTML 200 could complete as a verified artifact (F96). No free-space preflight anywhere (F95, D01).
- Transfer budget: search reserves `maxPageBytes` per page and settles to actual; RemoteFile reserves `min(maxArtifactBytes, Known size)` per response; non-completing responses stay charged; restarts never reset `transferred_bytes` (F50 to F56 give the formulas).
- Whole remote response must finish inside the fixed 120 s deadline, including per-chunk fsync and DB transaction (F57). Needed sustained rates: rulings 45.2 kB/s, largest English page 29.7 kB/s, CR 8.1 kB/s (MEAS-16).

Cards (strict English search):

- Live 2026-10-13 page 1: 200, no redirect, `application/json; charset=utf-8`, no Content-Encoding, 860,498 B, `total_cards` 116,009, 175 cards all `lang=en`, real `next_page` (alphabetized params plus `format=json`) accepted by the codec as page 2 (src-cards C-04 to C-06). Fixtures do not pin the real shape (C-31). Explicit `"warnings": null` would be rejected (C-30).
- Measured 2026-10-12 full traversal: 116,009 records, 663 pages, 620,971,334 B transferred, largest page 3,569,145 B, derived JSONL 620,805,214 B, max record 94,378 B incl LF (MEAS-01, MEAS-02). `ENGLISH-PROFILE-DRAFT.md` numeric rationale is stale on this point; its ceilings still hold.
- JVM harness import only: DB 1,767,976,960 B, WAL 18,404,072 B (MEAS-06). This already exceeds the provisional P12 1.5 GiB target by 9.77% for cards alone (MEAS-17). No Android/device staging or WAL figure exists.
- Scryfall FAQ steers huge data needs to bulk files and warns against searches that iterate over every card value; no English-only bulk file exists (C-18, C-25). Tolerance of a per-device 663-request traversal is UNRESOLVED.

Rulings:

- `GET https://api.scryfall.com/bulk-data/rulings` (2026-10-13): stable id `06f54c0b-ab9c-452d-b35a-8297db5eb940`, `jsonl_download_uri` `https://data.scryfall.io/rulings/rulings-<YYYYMMDDHHMMSS>.jsonl.gz`, `compressed_size` 5,422,271; no `download_uri`, `size`, content type, encoding or checksum (src-rulings RUL-11 to RUL-14). File: 200 `application/gzip`, no Content-Encoding, strong ETag, Range 206 works, 1-year cache; URL rotates about every 12 h; superseded files still served at least 67.9 h; size and 64 KiB prefix unchanged across rotations (RUL-17 to RUL-25).
- Ruling object: `object`, `oracle_id`, `source` (wotc|scryfall), `published_at` (date), `comment`; no language field; no official English statement (RUL-06, RUL-07). Scope must stay `RULINGS_LANGUAGE_NOT_ASSERTED`, intent kind `CATALOG`, language null (LocalAcquisition.kt:758).
- Measured full file 2026-10-12: 79,706 records, decoded 26,403,900 B, max record 1,710 B (MEAS-08).
- No rulings import contract, storage, mapper or UI; `Ruling` DTO lacks the `source` field and maps a date to `UtcTimestamp`; `retainRulings` default false is documented TEMPORARY (code-consumers L01 to L06).
- OkHttp's exact `/bulk-data` 6 s spacing does not apply to `/bulk-data/rulings` and conflates the `/cards/manifest` 10/minute limit; docs put bulk-data under "all other methods" 10/s (RUL-33, F31).

Rules (Wizards CR TXT):

- `https://magic.wizards.com/en/rules` (2026-10-13) still offers TXT `https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt` (raw space in the href). Bytes identical to recorded evidence: 977,752 B, SHA-256 `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`; `text/plain` without charset; strict UTF-8, no BOM, LF only, 9,372 lines, max line 2,883 B; effective-date sentence lines 3 and 9356 equals the filename date; Last-Modified 2026-08-17 is not the effective date; no official checksum, version or feed (src-rules W-01 to W-37).
- `LocalRulesImportSource` accepts only `content://` with caller metadata; no verified-artifact input kind; parser never extracts the effective date; `:feature:rules` cannot reach internal acquisition (code-consumers R01 to R12).
- Parser defect: CR line 5561 `704.5aa` is the only two-letter subrule. `SUBRULE` regex (RulesStructure.kt:359) allows one letter, so the rule is appended to `704.5z` with no anomaly (W-19, W-20). Supporting arithmetic: recorded genuine parse NUMBERED = 3,321 equals 3,165 single-letter rule lines + 147 chapter + 9 section headings. Needs the step 2 code check before Codex acts on it.

## Next action

Finish step 2, write `TRUSTED-PROFILES-REPORT.md`, commit and push to `ccr-d1e9ac59-b90cv2`. If this session stops first, Codex resumes from "How to resume" above. Verification actually run: repository/code reading, official page fetches and bounded probes listed in each evidence file. Builds, JVM, native and runtime tests: not_run.
