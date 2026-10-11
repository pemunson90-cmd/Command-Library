# cc-research-14: R-14 Local database and full-text search

Path: `coordination/tasks/cc-research-14.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | active |

## Question
Which local database and full-text search setup holds the full card set and rules on Android and Windows within the performance needed, and how does each candidate stack reach it?

## Feeds
GAUNTLET.md 2.A (offline search, Scryfall-style syntax, full-text over Oracle and rules), 3 Data and Efficiency. R-01 SQLite and FTS criterion.

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
- SQLite on Android: framework SQLite version per API level (cite the official table), and whether FTS3, FTS4, and FTS5 are enabled in the framework build. Bundled SQLite options (AndroidX bundled SQLite driver, requery sqlite-android, others) and their FTS5 status, size cost, and license.
- S1: Room with Kotlin Multiplatform (versions with JVM desktop support, driver options, FTS entity support and whether FTS5 is supported or needs raw SQL), SQLDelight (dialects, FTS5 support, drivers for Android and JVM). Cite the docs and release notes.
- S2 to S6: the SQLite packages each stack uses (for example sqlite3 and drift for Flutter, Microsoft.Data.Sqlite with SQLitePCLRaw bundles for .NET, the RN and Tauri options) and their FTS5 status.
- Scale: card counts from R-02 if filed, otherwise from Scryfall's bulk-data metadata. Published SQLite and FTS5 benchmarks (insert throughput in batched transactions, query latency, index size) relevant to about 35,000 oracle cards and about 100,000 printings. Mark estimates as estimates.
- Streaming JSON parsing per stack for bulk import without loading the whole file (for example kotlinx.serialization streaming, Okio, Jackson streaming, Dart, System.Text.Json Utf8JsonReader, serde_json StreamDeserializer).
- Scryfall-syntax search: existing open-source query parsers or grammars, their language and license, and whether translating a query AST to SQL plus FTS is the documented approach elsewhere.
- Atomic data refresh patterns in SQLite (build in a new DB file then swap, or transaction plus WAL), with sources.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-14-database-fts.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-14-database-fts.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
