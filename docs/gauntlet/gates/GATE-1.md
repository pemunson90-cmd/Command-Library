# GATE-1: Research filed for ruling

Date: 2026-10-11. Orchestrator: Codex, sole through Gate 3 under latest D-05. Status: research complete as an evidence inventory; awaiting Pat's decision. Gate 2 has not started.

## Delivered

- R-01 through R-17 filed, each with question, sourced findings/access dates, options, recommendation, risks and confidence. Unconfirmed scope facts are explicitly UNVERIFIED with attempted sources.
- RESEARCH_SUMMARY.md contains one recommended ruling per topic and the complete Moxfield, Archidekt and life-counter build/adapt/skip matrices.
- DECISIONS.md proposes D-04/D-06/D-07 and D-12/D-13/D-14; preserves prior rulings including the concurrent 13a26c3 D-03 update.
- Two independent review notes preserved, with final dispositions in coordination/handoffs/cx-gate1-final-review.md. No successful independent review of root-authored R-10 is claimed.
- No app code, builds or target-device tests. Signing sheets remain outside git.

## Rulings requested

1. D-07: Flutter, baseline 88.6 versus Kotlin 84.0, for stronger documented Narrator path. Tradeoff: replace known Kotlin layer; actual accessibility still needs validation. Kotlin remains evidence-sensitive fallback.
2. D-04: USD/Scryfall reference pricing by default, conditional on source/terms checks; retain EUR and TIX options. Default owned-as-free. Tradeoff: US-market estimate rather than Pat's exact buying venue.
3. D-06: unsigned per-user Inno Setup EXE and portable ZIP if Flutter, defer trusted signing for personal use. Tradeoff: Windows policy/SmartScreen restrictions may affect installation.
4. D-12: adopt all topic recommendations and full matrices; preserve required baseline, use guided imports, disclose Lotus/Mox cohort. Tradeoff: some reference extras are skipped or adapted and vendor API contracts remain conditional.
5. D-13: full offline rules through personal official-source acquisition/cache where permitted, with local import fallback; public verbatim bundling remains separate. Tradeoff: acquisition may require an initial user import.
6. D-14: Full includes all printings/languages, not default_cards-only completeness. Tradeoff: larger download/storage budget must be measured.

## Material limitations carried forward

Current Scryfall terms and new byte/count/image measurements remain unresolved; retained same-day metadata is not a fresh probe. Current Game Changer source-set equality and dynamic official wording were not fully verified. Current vendor export fixtures, exact link HTTP behavior and several cloud/local contract details remain unverified. Gemini API personal-use eligibility needs applicable-terms resolution. Native LLM performance, secure-storage plugin tuple, engine settings and TalkBack/Narrator behavior require actual packaged-runtime checks.

These are explicit research limitations, not passed acceptance tests or permission to drop mandatory features. Gate 2 must map them into coverage/acceptance work; runtime checks occur after architecture authorizes implementation.

## Checkpoints and transport

Recovery f5acefef preserved 14 filed topics and recovered drafts; R16 33776e0 and R17 3b05f9e filed next. Concurrent signing-ledger update 13a26c3 was inspected and preserved. R10 checkpoint 143e4fc filed the final topic. This Gate 1 commit files the synthesis and final handoff.

GitHub connector Git-data writes use current parent, base tree, expected head and fast-forward only. The local directory is a recovered text snapshot without .git, not a working checkout. Shell proxy failure prevented normal Git/network probes. No worktree verification is claimed.

## Handoff

Read coordination/handoffs/cx-gate1-resume.md and STATE.md. Wait for Pat's six rulings, record verbatim, then begin Gate 2 SPEC/design/coverage/performance/data-model/dependency/screen planning and its independent review. Do not redo filed research or start application code.
