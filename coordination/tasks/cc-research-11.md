# cc-research-11: R-11 On-device LLM runtimes

Path: `coordination/tasks/cc-research-11.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

| Field | Value |
|---|---|
| Role | researcher |
| Component | research (Gate 1) |
| Orchestrator | claude-code |
| Model tier | standard (Sonnet, D-10) |
| Branch | none. Researchers write one file in the main checkout; the orchestrator commits it to `main` |
| Base commit | b1c922eaa96c25209691253c9849066b89a71846 |
| Steps | 1/1: write the research file |
| Status | filed by Codex; scope/evidence audited with explicit verification limits |

## Question
Which on-device LLM runtimes can run on Android and Windows inside Command Library, with which model formats and sizes, at what performance, and how does each interoperate with the candidate stacks?

## Feeds
GAUNTLET.md 2.H (on-device models, model download manager). R-01 on-device LLM interop criterion.

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
- Runtimes: llama.cpp (and bindings), Google AI Edge (LiteRT-LM, MediaPipe LLM Inference), ONNX Runtime GenAI, MLC LLM, ExecuTorch, Windows-native options (for example Windows AI APIs, Foundry Local), others found. Per runtime: platforms (Android, Windows x64, Windows ARM64), license, current stable version and date, model formats, supported model families, GPU or NPU acceleration per platform, streaming token output, tool calling or structured output support.
- Model candidates that fit on a mid-range Android phone (state the RAM assumption) and a typical Windows PC: names, parameter counts, quantization, file sizes, licenses, and download sources (for example Hugging Face) with any terms on redistribution or in-app download.
- Published performance figures (tokens per second, time to first token, memory) on named devices. Mark vendor-published figures as such.
- Interop with each candidate stack S1 to S6: JNI or Kotlin bindings, Dart FFI, .NET P/Invoke or packages, React Native native modules, Rust crates. Name the actual libraries with sources and maintenance status.
- Packaging cost: native library size added to the APK and the Windows package.
- Model download manager facts: resumable download support of the hosts (HTTP range requests), checksums published.

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-11-on-device-llm.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-11-on-device-llm.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.

Codex audit cx-research-11 filed 2026-10-11. All scope items evidenced or explicitly UNVERIFIED; no build/runtime success claimed.
