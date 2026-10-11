# cc-research-15: R-15 CI/CD, signing, Windows packaging, screenshot tests

Path: `coordination/tasks/cc-research-15.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
How does GitHub Actions build a release-signed Android APK and a Windows package for each candidate stack, run tests and screenshot tests per size class, and enforce the placeholder scan, all without a local machine?

## Feeds
D-03 (signing mechanics), D-06 (Windows installer and code signing), D-02 (billing figures). GAUNTLET.md 1 (all builds on GitHub Actions), 2.J (CI screenshots per size class), 3 (placeholder scan). R-01 packaging criterion.

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
- Android release signing on GitHub Actions: keystore stored as a base64 secret, Gradle signingConfigs fed from environment variables, apksigner verification, and how to keep key material out of logs. Cover both D-03 options: reuse an existing keystore, or create a new one. For a new key, how Pat creates it with only a phone plus a Windows PC (for example keytool from a JDK on the PC), and why generating it inside a workflow is or is not safe.
- Windows packaging per candidate stack S1 to S6: formats (MSI, EXE installer, MSIX, portable ZIP), the tooling (for S1: Compose Desktop native distributions via jpackage, WiX requirements, Conveyor and its license terms for open source; for others: their documented packagers), runner requirements (windows-latest), and output sizes where published.
- Windows code signing: what SmartScreen does with unsigned and with signed packages; current options and prices (Azure Artifact Signing or Trusted Signing eligibility for individuals, SignPath Foundation for open source, OV and EV certificates). Quote eligibility terms. Recommendation for D-06.
- GitHub Actions billing today for public repositories (standard runners, Windows and macOS multipliers), artifact retention defaults, release creation from a workflow (note: this cloud session cannot push tags, so releases must be created by a workflow).
- Tests in CI: unit tests per stack; Android UI smoke tests on an emulator in Actions (for example reactivecircus/android-emulator-runner, KVM on Linux runners); desktop UI tests.
- Screenshot testing per window size class and posture: Roborazzi, Paparazzi, Compose Preview Screenshot Testing, Compose Desktop image capture for S1; golden tests for S2; options for S3 to S6. Which can render compact, medium, and expanded sizes and a tabletop or book posture without a device. How to attach screenshots to a run (artifacts, job summary).
- Placeholder scan: a grep-based job that fails on the GAUNTLET.md section 3 patterns, with an allowlist read from `docs/gauntlet/IMPOSSIBLE.md` file:line entries. Show a minimal workflow fragment marked as a design sketch, not a tested file.
- Dependency pinning and caching on Actions (Gradle dependency verification or lockfiles, action pinning by SHA).

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-15-ci-cd.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-15-ci-cd.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
