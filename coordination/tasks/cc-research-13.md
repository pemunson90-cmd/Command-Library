# cc-research-13: R-13 Adaptive layouts, foldables, Windows desktop UX

Path: `coordination/tasks/cc-research-13.md`. Written by the orchestrator. This card plus the files under Brief are everything the agent receives (GAUNTLET.md 5.7).

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
How does each candidate stack support window size classes, list-detail and supporting-pane layouts, foldable postures, and Windows desktop interaction and accessibility?

## Feeds
GAUNTLET.md 2.J (size classes, list-detail, postures, hinge, desktop affordances, accessibility), 2.G (tabletop posture). R-01 adaptive and accessibility criteria.

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
- Window size classes: the current Material definitions and breakpoints (compact, medium, expanded, and any newer classes), from Android developer docs.
- S1: Material 3 adaptive libraries (NavigationSuiteScaffold, ListDetailPaneScaffold, SupportingPaneScaffold) on Android and their availability on Compose Multiplatform desktop (artifact names, versions, status). Jetpack WindowManager posture and hinge APIs (FoldingFeature, tabletop and book detection) and how to keep content off the hinge. State preservation across fold, unfold, and window resize.
- S2 to S6: the equivalent APIs or packages for size classes, list-detail, posture and hinge detection, with maintenance status.
- Windows desktop per stack: keyboard shortcuts, hover states, right-click context menus, drag-and-drop (within the app and from the OS), resizable windows with minimum sizes, multi-window if any.
- Accessibility per stack and platform: TalkBack on Android; Windows Narrator and UI Automation on Windows (for S1, how Compose for Desktop exposes accessibility on Windows, for example through the Java Access Bridge, and its known gaps); text scaling; focus navigation. Cite issue trackers for known gaps.
- Testing hooks: how each stack can render a screen at a given window size and posture in tests (feeds R-15 screenshots).

## Brief (files to read, nothing else)
- `coordination/agents/researcher.md` (role brief, source of truth)
- this card
- `coordination/templates/RESEARCH.md`
- `docs/gauntlet/GAUNTLET.md`: sections 2, 3, 10 and Appendix A, for context only

## Working rules
- Write only `docs/gauntlet/research/R-13-adaptive-layouts.md`. Create it early and extend it section by section, so partial work survives an interruption. Do not commit; the orchestrator commits it to `main`.
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
- `docs/gauntlet/research/R-13-adaptive-layouts.md`
- Handoff: none. A researcher writes only its research file (role brief). The orchestrator tracks status in this card and commits the file. A crash before commit loses only this card's run, which restarts from scratch.
