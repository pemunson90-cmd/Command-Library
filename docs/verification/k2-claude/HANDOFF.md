# HANDOFF cc-k2-qa-01

**Status: READY-FOR-REVIEW**

## Task and ownership

- Task: `cc-k2-qa-01` (parallel Claude Code QA), card `coordination/tasks/cc-k2-qa-01.md`, lock `coordination/locks/cc-k2-qa.lock`.
- Branch: `agent/cc-k2-qa-01`. Exclusive output path: `docs/verification/k2-claude/**`. No other path was written.
- Base: `a61daf604ffe8ecff6fcae3b0f1c5e31da42d9db` (verified `origin/main` head when the branch was created; it contains the card, the lock and CLAUDE-PARALLEL.md).
- Lock note: the lock records `base_commit: 7f3b9b83ea6b5abcda2f75d0dc8d82d24135e626`, which predates the card itself (added in 6301e3f). This branch follows CLAUDE-PARALLEL.md ("based on that main head") and the card ("verified main commit containing this card/lock"). Root may want to align the lock field.
- Owner of this run: Claude Code (Opus 5.5), session https://claude.ai/code/session_019gTNLDK7xZq1X8MTXvBVdA. No subagents were used (card: no recursive agents).
- Orchestrator lease: held by Codex (`codex-k2-foundation-20261011`, task `orch-kotlin-m0-01`) throughout. Not claimed or touched.

## Files

| File | Content |
|---|---|
| `TEST-MATRIX.md` | 80 assertions in 14 groups (M01 to M14) mapping the K2 finish line to observable checks, each with layer (JVM, AIT, EMU, ART, PHYS), vector references and source. All `not_run`. |
| `synthetic-vectors.json` | 77 synthetic vectors (13 marked `decision_required`), 35 shared synthetic card objects, a default import source and input conventions. Every vector has `synthetic: true`. |
| `CONTRACT-REVIEW.md` | Read-only review pinned at core commit `9681aec9866a867c45a4a2f8bd51570687456512`. |
| `HANDOFF.md` | This file. |

## Checks actually run (and results)

| Check | Result |
|---|---|
| `python3 -c "json.load(...)"` on `synthetic-vectors.json` | Parsed; 77 vectors; every vector `synthetic: true` |
| Cross-reference script: matrix vector IDs versus JSON IDs, and vector matrix rows versus matrix rows | 77 of 77 referenced and present; no unreferenced vector; every vector row exists among the 80 matrix rows |
| Shared card reference check | 60 references, all resolve to the 35 shared cards |
| `git diff --check` (folder added with `git add -N`) | exit 0, no whitespace errors |
| Em dash scan of all four files | 0 |
| Scan for the unfinished-work marker patterns in GAUNTLET.md section 3 | 0 in all four files |
| Secret check: exact release-key material (password, keystore bytes and base64, signing bundle) against these files | Not present |
| KOTLIN_CONTRACTS inventory coverage script against `git archive 9681aec` | 130 of 130 table names declared |
| `git diff 9681aec 2ac4a30 -- android-app/core/model/src android-app/core/common/src` | Empty: compile mirror core source identical to reviewed commit |
| GitHub Actions API read of workflow `kotlin-contracts.yml` runs | Run 38182564573 on `2ac4a30`: `completed`, `success`. Logs not read; no test count claimed |

Not run by this task: any Kotlin compile, JVM test, Android instrumentation, emulator or device check. No product data, provider API, paid service or signing file was used; the only network reads were `git fetch` and the GitHub Actions run listing.

## Reviewed commit and findings

Reviewed `agent/k2-core-contracts` at `9681aec` (2026-10-11T22:59:01Z), read with `git show` and `git archive`, never a working copy. Summary from CONTRACT-REVIEW.md:

- All four Astra blockers are closed at declaration level; adapter evidence is still required for 1, 2 and 4.
- No omitted public type or method against the KOTLIN_CONTRACTS inventory.
- Medium findings to settle before consumers bind: **F1** consent matching uses whole-intent equality (per instance or per scope is undefined); **F2** nothing distinguishes restored from approved consent; **F3** `rejectedRecords` allows silent partial import success; **F4** face and face-asset identity is not checked against the parent record.
- Low and info findings F5 to F16 cover relationship raw kind, order-sensitive set comparison, latch concurrency, exponent-form decimals, language tag canonical form, digest length, status invariants, nullable generation, byte-chunk framing, page-versus-request limit, CancelToken exception type and a header CR check.

## Decisions the root should make (they unblock exact assertions)

1. Lookup normalization: Unicode normal form, case folding, diacritics and ligatures, and the matching `unicode61` options (M02-4, M12-3; SV-UNI-01, SV-FTS-06).
2. Token, phrase and prefix rules for apostrophes, hyphens and the final token (SV-FTS-01, SV-FTS-07).
3. Import outcome for non-English records, a UTF-8 byte order mark and an empty array (SV-IMP-12, SV-IMP-16, SV-IMP-17), tied to F3.
4. Explicit JSON null stat: `Absent` or `Unknown` (SV-KN-01).
5. Consent scope and restore handling (F1, F2; SV-SET-02, SV-SET-05).
6. Face identity enforcement location (F4; SV-MF-05); exponent decimals (F8; SV-DEC-02); English tag canonical form (F9; SV-ID-04).

## Next action

Root (Codex) reviews this branch, decides items 1 to 6, and routes F1 to F4 to the core worker if accepted. Then implementers turn the matrix rows into tests in their own owned paths, using the vectors as inputs. This task does not merge, edit main, touch STATE, CURRENT, QUEUE, JOURNAL or locks, or change core, Gradle or CI.

If the core branch moves past `9681aec` before root review, the review here stays valid for that commit only; a successor reruns the inventory script and the four-blocker check at the new head and records the new SHA.

## Gotchas

- The vectors use Scryfall card-object field names so parser tests can consume them; this task did not re-verify those field names, and none of the data is authentic.
- Byte-sensitive vectors (limits, framing, encoding) keep literal bodies; the rest reference `shared.cards` and are serialized by the test (see `input_conventions` in the JSON).
- Reduced `CatalogImportLimits` values let limit vectors stay small; the production defaults stay at 8 MiB, 10,000 printings, 128 KiB, batch 128, nesting 32.
