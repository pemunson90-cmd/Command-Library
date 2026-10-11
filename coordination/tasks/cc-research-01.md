# cc-research-01: R-01 Stack selection

Path: `coordination/tasks/cc-research-01.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | strongest (Opus). Raised per D-10: every later gate rests on the stack ruling, and this card must reconcile R-11 to R-15 into one scored matrix. |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | filed by Codex; scope/evidence audited with explicit verification limits |

## Question
Which stack builds Command Library from one codebase for Android (phones, foldables, tablets) and Windows desktop, scored against the GAUNTLET.md section 3 criteria with Pat's known setup as the fallback?

## Feeds
D-07 (stack). GAUNTLET.md section 3 Stack rule. Every later gate.

## Candidate stacks (shared by R-01 and R-11 to R-15)

| ID | Stack | Android target | Windows target |
|---|---|---|---|
| S1 | Kotlin Multiplatform + Compose Multiplatform. The fallback: Pat's known Kotlin, Jetpack Compose, Room, Navigation Compose, DataStore, Gradle setup, extended to desktop | Jetpack Compose | Compose for Desktop (JVM) |
| S2 | Flutter (Dart) | Flutter Android | Flutter Windows desktop |
| S3 | .NET MAUI (C#) | MAUI Android | WinUI 3 |
| S4 | Avalonia UI (C#) | Avalonia Android | Avalonia desktop |
| S5 | React Native + React Native for Windows | RN Android | RN Windows |
| S6 | Tauri 2 (Rust core, web UI) | Tauri mobile | Tauri desktop |

Depth: S1 and S2 in full depth. S3 to S6 deep enough to score them, or to rule one out with a sourced reason. Add a candidate not listed only with evidence that it could win the R-01 matrix, and say why.

## Scope (must cover)
- Score every candidate S1 to S6 on the section 3 criteria: one codebase for Android and Windows; foldable and adaptive support; Windows packaging from GitHub Actions; SQLite and full-text search performance; on-device LLM interop; build reliability; ecosystem maturity.
- Add these criteria and say why each belongs: secure key storage per platform; streaming HTTP (SSE) client; accessibility (TalkBack, Windows Narrator); testability in CI (unit, UI smoke, screenshots per window size class); how reliably AI coding agents produce correct code in the stack (sourced evidence where it exists, otherwise labeled as judgment).
- Propose weights with a rationale. Show raw scores, weighted totals, and a sensitivity check: does the winner change under reasonable weight changes?
- Fallback rule (GAUNTLET.md 3): departing from S1 requires a stated reason that wins on the matrix. Say plainly whether any candidate clears that bar.
- Cite R-11 to R-15 by file and finding for the evidence they hold. Add your own sourced findings for what they do not cover: current stable versions and release dates per stack; Compose for Desktop maturity on Windows; Windows on ARM support; bundled runtime size of the Windows package; cold start and memory figures where published.
- Known working setup: Pocket-Steward CI (ubuntu-latest, Temurin JDK 21, `gradle/actions/setup-gradle@v4`, unit tests, lint, assembleDebug), recorded in `docs/gauntlet/gates/GATE-0.md`.
- For the recommended stack: a concrete library set with current stable versions and sources (UI, navigation, adaptive layout, DB and FTS, DI, HTTP and SSE, JSON streaming, settings storage, image loading and caching, secure storage, unit test, UI test, screenshot test). State which fallback libraries (Room, Navigation Compose, DataStore) carry over to Windows, which need a multiplatform equivalent, and the evidence.
- Recommendation for D-07, with the one-line tradeoff Pat rules on.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only
- `docs/gauntlet/gates/GATE-0.md` (Pocket-Steward reference)
- `docs/gauntlet/research/R-11-on-device-llm.md`, `R-12-key-storage.md`, `R-13-adaptive-layouts.md`, `R-14-database-fts.md`, `R-15-ci-cd.md` (all filed before this card launches; read-only)

## Working rules
- Write only `docs/gauntlet/research/R-01-stack.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
- Do not change branches, stage, commit, or run any git command that changes state.
- Bash is for measurement only (`curl -sI` for sizes, `curl ... | gzip -dc | wc -l` for counts, `jq` on small JSON). Stream through pipes. Never save bulk files or any other file to disk.
- Network etiquette: send a descriptive `User-Agent` (for example `CommandLibraryGauntlet/0.1 (research)`) and `Accept` header to Scryfall, stay well under its rate limit, and download bulk data only from the bulk file hosts.
- Every claim carries its source URL and access date (today, from `date -u +%F`). Anything not confirmed by a source is written UNVERIFIED with what was tried. Never infer an endpoint, field, format, price, or capability.
- Terms and licensing get their own findings, quoting the relevant clauses.
- No em dashes anywhere in the file or the return message.

## Acceptance
- RESEARCH.md template sections present: question, findings, options, recommendation, risks, confidence.
- Every Scope item is answered or marked UNVERIFIED with what was tried.
- Every finding has a source URL and access date.
- Zero em dashes.

## Output
- `docs/gauntlet/research/R-01-stack.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex cx-research-01 filed 2026-10-11. Scored/source evidence verified, runtime/permission unknowns explicit.
