# Claude Code task: K3 pure rules parser

Status: private parser assignment complete; reviewed hardened4971166 merged unchanged after hosted63-test gate38299615365. Rules lock released; production activation remains gated on genuine fixture/source and storage review. See docs/verification/k3-rules-parser/HOSTED-EVIDENCE.md.
Branch: `agent/cc-k3-rules-parser-01`, based on verified current `main` supplied by root.

## Scope

Design and implement a private, pure Kotlin streaming UTF-8 TXT parser under `android-app/feature/rules/**`, with meaningful synthetic tests under that same owned prefix. Output accepted core DTOs: `RuleEntry`, `RuleCrossReference`, `RuleEntryKind` (`NUMBERED`, `GLOSSARY`, `EXAMPLE`, `NOTICE`). Preserve raw source text/provenance inputs, unknown references, and unknown effective dates. Handle duplicate TOC headings deterministically without dropping entries.

Module-local build.gradle.kts is within your owned prefix; shared Gradle settings/catalog are root-owned and requested in the handoff.

No repository/fake adapter, DB/schema/migration, core, app, UI, DI, navigation, shared settings/build config, or other module edits. No network/download, bundled rules, official source/date/provider guesses, or official-completeness claims. Request all shared edits from root in the handoff.

## Binding gate

The orchestrator has published the K3 requirement/ownership delta and this exact rules lock. Read that delta and verify the lock before writes. Private parser and synthetic tests are permitted under the already-merged rules behavior contract and core DTOs; no repository adapter or production wiring is authorized. Use a separate checkout/worktree based on the main head containing this task/lock.

## Parser design to settle

Keep parser entry private to feature/rules. Suggested shape: `internal suspend fun parse(chunks: Flow<ByteChunk>, provenance: Provenance, corpusId: GenerationId, cancel: CancelToken, limits: ParserLimits, onEntry: suspend (RuleEntry) -> Unit)` or a suspending callback equivalent that does not buffer the corpus. The caller supplies truthful provenance, including unknown source/version/effective dates; never fabricate a `RulesSource.version` to fit its future repository port. Use strict UTF-8 decoding; malformed bytes fail with typed/parser error. Keep input/dependencies minimal and platform independent. Preserve exact line text for entry bodies; normalize only structural recognition. Do not infer provider metadata or effective dates absent source evidence.

Write down a conservative proposed limits set before coding: total bytes, max line bytes, max entries, max nesting/continuation depth. Treat values as parser safeguards, not corpus facts; reject over-limit input with clear errors. Check cancellation during reads and parsing, promptly and without partial “success”. Root must review/adjust limits and grammar against a genuine byte fixture later.

Tests use clearly synthetic labeled strings/streams and cover all four entry kinds, numbering/title/body boundaries, duplicate TOC headings, preserved raw body strings, known and unknown cross-reference targets, unknown effective date, empty/truncated/malformed UTF-8 input, each bound, and cancellation. Tests establish parser behavior only; make no official corpus completeness claim.

## Handoff and finish

Only owned paths: `android-app/feature/rules/**` and `coordination/handoffs/cc-k3-rules-parser-01.md`. Preserve drafts. Never reset, clean, rebase, force-push, merge main, or edit shared configuration. Commit bounded useful work; push the branch and publish a `READY-FOR-REVIEW` handoff with exact SHA/base, changed files, verification actually run (or `not_run`), parser/DTO entry signature, synthetic-fixture caveat, unresolved grammar/limit questions, and exact shared requests. Keep one replaceable `Latest continuation delta` in the handoff per action. Stop for root review/merge; root handles catalog scaling and core/storage/schema/sharedconfig/DI/app/UI. No subagents.

## Latest continuation delta

Ownership explicitly bound; task ready for Claude after reset. Accepted core DTOs and existing rules behavior are dependencies; genuine byte-format fixture and production source/version binding are pending. No parser code/tests run yet. Next create isolated branch, commit parser design/limits and synthetic test plan, then implement internal streaming parser and request root build inclusion.
