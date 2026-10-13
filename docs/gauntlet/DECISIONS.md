# DECISIONS

Ledger of every decision. Pat's rulings are quoted verbatim. Status values: Open, Ruled, Superseded.
Intake rulings (the twelve answers) are in GAUNTLET.md Appendix A, verbatim, and outrank this ledger's recommendations.
Conflicts between orchestrators, or between GAUNTLET.md and Appendix A, are logged here for Pat.

## Index

| ID | Decision | Status |
|---|---|---|
| D-01 | Package / application ID | Ruled 2026-10-11 |
| D-02 | Repo name and visibility | Ruled 2026-10-11 |
| D-03 | Release signing key | Ruled 2026-10-11 |
| D-04 | Default currency and price source | Ruled; latest approval below |
| D-05 | Claude Code and Codex concurrency | Superseded by D-17 sequential cross-model handoff |
| D-06 | Windows installer format and code signing | Ruled; see latest Gate 1 answers; version 2 per D-19 |
| D-07 | Stack | Superseded for Android version 1 by D-20; retained as Windows v2 historical evidence |
| D-08 | Max concurrent implementer agents | Ruled 2026-10-11: 3 |
| D-09 | Integration branch and push permissions | Ruled 2026-10-11 |
| D-10 | Subagent model tiering | Ruled 2026-10-11 |
| D-11 | Cloud session setup: repos attached per session | Ruled 2026-10-11 |
| D-12 | Gate 1 matrices and topic recommendations | Ruled; see latest Gate 1 answers |
| D-13 | Full offline rules acquisition versus public bundling | Ruled; see latest Gate 1 answers |
| D-14 | Full catalog printing/language scope | Ruled; see latest Gate 1 answers |
| D-15 | Gate 2 specification/design package | Working technical baseline under D-17; no fabricated explicit approval |
| D-16 | Gate 2 performance targets | Provisional targets under D-17; not measured or relaxed |
| D-17 | Shared continue mandate and delegated technical stage progression | Ruled: latest user instruction below |
| D-18 | Interface language and localization | Ruled 2026-10-11 |
| D-19 | Platform priority: Android first, Windows is version 2 | Ruled 2026-10-11 |
| D-20 | Kotlin Android v1; separate Flutter Windows v2; sync after Windows runs | Ruled 2026-10-11 |
| D-21 | K1 execution release and complete-section checkpoint policy | Ruled 2026-10-11 |
| D-22 | Fold7-first acceptance and genuine input acquisition | Ruled 2026-10-12 |
| D-23 | Parallel Claude Code assignment | Ruled 2026-10-11 |
| D-24 | Complete Android v1 delivery reaffirmed | Ruled 2026-10-12 |
| D-25 | Life contract rulings and delegated shared-clock semantics | Ruled 2026-10-13 |
| D-26 | On-device AI engines deferred to v1.1 | Ruled 2026-10-13 |
| D-27 | Fold7-focused, minimum sufficient verification | Ruled 2026-10-13 |

## Open

- No stack-rescore blocker remains. Future platform or sync changes require a new explicit ruling.

## Ruled

### D-04 Default currency and price source
- Open for Gate 1 ruling. Recommend USD/Scryfall reference prices conditional on current source/terms verification; selectable EUR/Cardmarket and MTGO TIX. MTGJSON/public Cardmarket data are documented alternatives. Owned-as-free default with missing prices unknown. See R-08.

### D-06 Windows installer format and code signing
- Ruled after R-15.
- Gate 1 proposal conditional on Flutter: unsigned per-user Inno Setup EXE plus portable ZIP; defer trusted code signing for personal use. Accept possible Windows policy/SmartScreen restrictions, without promising one click resolves them. If Kotlin is selected, revisit jpackage MSI/ZIP. See R-15 and POST_RELEASE.md.

### D-07 Stack
- Open for Gate 1 ruling. Recommend Flutter S2, 88.6/100 baseline versus Kotlin S1 84.0, for a documented Windows Narrator accessibility path. Tradeoff: replace known Kotlin application layer and implement adaptive/SSE composition; real pinned-runtime Narrator validation remains required. Kotlin wins with reuse-heavy weighting or verified Narrator support. See R-01 scored matrix and sensitivity. Preserve fallback Kotlin/Compose/Room3/Gradle with Compose Multiplatform desktop.

### D-08 Max concurrent implementer agents
- Set at Gate 3. Default 3.
- Pat, 2026-10-11, verbatim: "I guess 3 default is fine unless you have recs for parallel agents"
- Applied: at most 3 implementer agents run concurrently. Operating rules added by Claude Code under D-17, which Pat may override: (1) at most 4 subagents of any role run at once, implementers, test-writers and reviewers together, because they share one usage limit; (2) after any subagent stops on a usage limit, the cap drops to 2 for the rest of that session.
  - Evidence: on 2026-10-11, six Sonnet researchers launched together all stopped on the account usage limit (commit 4b69954; JOURNAL.md wave 1 line). At M0 the dependency graph allows at most three parallel components after `core` (storage, network, vault; `docs/architecture/registry.json`). Merges are serial with review per merge (GAUNTLET.md 5.4).

### D-01 Package / application ID
- Proposed: `com.architectpsg.commandlibrary`.
- Recommendation: accept.
- Tradeoff: permanent once installed. Changing it later makes a separate app and needs a manual data move.
- Pat, 2026-10-11, verbatim: "com.architectpsg.commandlibrary"
- Applied: application ID `com.architectpsg.commandlibrary` on Android; the same string is the Windows package identifier where the packaging format takes one (R-15; version 2, D-19).

### D-02 Repo name and visibility
- Current: `pemunson90-cmd/Command-Library`, public, default branch `main`.
- Recommendation: keep the name, keep it public for now.
- Tradeoff: public repos run standard GitHub-hosted runners free (source: https://docs.github.com/en/billing/concepts/product-billing/github-actions, accessed 2026-10-11). Private repos draw from a plan minute quota, and Windows minutes cost more than Linux minutes. Cost of public: the spec and code are visible to anyone. Secrets never enter the repo either way. R-15 verifies current billing figures.
- Pat, 2026-10-11, verbatim: "Stay public."
- Applied: repo stays `pemunson90-cmd/Command-Library`, public.

### D-03 Release signing key
- Finding: Pocket-Steward CI builds debug-signed APKs only (`.github/workflows/main.yml`, `BUILD_ENVIRONMENT.md` line 157). No release-key workflow exists there to copy.
- Options: (a) Pat's existing permanent key, added to this repo as GitHub Actions secrets; (b) a new key for this app.
- Recommendation: (a) if the keystore file exists and Pat can reach it. One key to back up.
- Tradeoff: (a) a leak of one key affects every app signed with it; (b) isolates the blast radius but adds a second keystore Pat must back up forever. Losing the key means users cannot update in place.
- Needed from Pat: does the keystore exist, and where.
- Pat, 2026-10-11, verbatim: "[where your keystore is, or "make a new key"]"
  - Context: this is the fill-in text from the ruling template, sent unfilled. No option was chosen, so D-03 stayed open until the follow-up below.
- Pat, 2026-10-11, verbatim: "Make a new key"
- Applied (first pass, superseded by the follow-up below): option (b), with generation held back from the cloud session because the key would have to reach Pat as a file.
- Pat, 2026-10-11, verbatim: "Key as file is fine if it stays off git"
- Applied: keystore generated 2026-10-11 in the session scratchpad, outside the git checkout, with JDK 21 keytool. The password was generated and used inside one script and never printed to logs or chat. Delivered to Pat as two files: the keystore and a secrets sheet (password, alias, base64 value for the Actions secret, backup steps). Both are deleted from the session once Pat confirms the secrets are added and two backups exist.
  - Keystore: PKCS12, alias `commandlibrary`, RSA 4096, SHA384withRSA, valid 2026-10-11 to 2054-02-26, subject `CN=Command Library, O=Architect PSG LLC`.
  - Certificate SHA-256 (public, used by CI to verify signed APKs): `E2:D8:F5:0D:A6:01:48:CE:E8:55:BF:C6:F3:01:D4:FE:38:E9:E3:DE:28:10:C6:05:83:30:16:7F:B4:F5:77:E7`
  - GitHub Actions secret names, (superseded below by the single secret `ANDROID_SIGNING`): `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
  - Pat adds the four secrets in the GitHub UI before M0. Losing every copy of the keystore means installed builds cannot be updated in place.
- Pat, 2026-10-11, verbatim: "Can't you just add the secrets? Isn't there a way? I don't want to manually touch anything"
  - Finding: no tool reachable from the Claude Code session writes Actions secrets. The GitHub connector has no secrets tool, and the workflow `GITHUB_TOKEN` permission list has no secrets scope (https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax, accessed 2026-10-11). The four values were packed into one secret so Pat's step is a single paste.
- Pat, 2026-10-11, verbatim: "Added"
- Applied (current): one repository secret, `ANDROID_SIGNING`. Its value is base64 of compact JSON with keys `keystore_base64`, `store_password`, `key_alias`, `key_password`; round-trip verified before delivery (decoded keystore opens with the bundled password). CI design for M0: decode the secret in the first signing step, register `::add-mask::` for each extracted password before anything else runs, write the keystore to a runner temp path, sign, verify with apksigner against the certificate SHA-256 above, delete the keystore at job end. The secret's presence cannot be read back from a session; the first signed M0 build is the check. Claude Code's session copies of the key files stay until Pat confirms a saved copy outside chat.
- Pat, 2026-10-11, verbatim: "I saved it but don't delete it. Just never commit it. I'm ok with you having a backup"
  - Applied: Pat holds a saved copy. Claude Code keeps its copies in the session scratchpad, outside the git checkout and never committed. That copy is not durable: it is lost when the cloud container is recycled. Durable copies are Pat's saved file and the file attachments in the Claude chat.
  - Public exposure check, 2026-10-11, all three public branches and full history through `main` 68f053f (452 objects): exact-match search for the password, keystore bytes, keystore base64, and the `ANDROID_SIGNING` value, including partial fragments, found nothing; a positive control confirmed the matcher detects each value. A read-only pattern sweep, a full current-tree read, and a check of GitHub surfaces (no Actions runs, issues, pull requests, or releases exist) found no secret. GitHub's own secret scanner could not run: GitHub Advanced Security is not enabled on this repo.
  - `.gitignore` now also ignores `*.p12`, `*.pfx`, `.env`, `.env.*`, and `command-library-*SECRET*` (the signing sheet names).

### D-05 Claude Code and Codex concurrency
- Recommendation: Claude Code alone through Gate 3; both orchestrators concurrently from M0 onward under the lock protocol.
- Tradeoff: Codex sits idle early, versus two orchestrators producing duplicate research and conflicting edits to SPEC, DECISIONS, and STATE, which are orchestrator-owned shared files with no lock.
- Pat, 2026-10-11, verbatim: "Claude Code alone through Gate 3."
- Applied: Claude Code is the only orchestrator through Gate 3. Codex concurrency from M0 onward is not yet ruled; it returns at Gate 3 together with D-08.

### D-09 Integration branch and push permissions
- Conflict: GAUNTLET.md 5.2 and 5.4 require the orchestrator to claim locks and merge on `main`, and implementers to push `agent/<task-id>` branches. This cloud session is instructed to push only to `ccr-b0894b79-qzhxhw` unless Pat gives explicit permission. Every new cloud session gets a new `ccr-*` branch name.
- Finding (R-00): subagent worktrees are created from the repo default branch (`main`, commit 512cdcc), not from the session branch. With work held on a `ccr-*` branch, every implementer starts on stale code unless it re-checks out the base by hand.
- Options:
  - (a) Pat authorizes Claude Code to push to `main` and to `agent/*` branches in this repo. Protocol runs as written; Codex sees locks on `main`.
  - (b) Work stays on the session branch; Pat merges a PR to `main` at every gate. Locks and merges stay invisible to Codex and to fresh worktrees until Pat merges.
- Recommendation: (a), starting by merging this Gate 0 branch into `main`.
- Pat, 2026-10-11, verbatim: "A"
  - Context: answering the offer: Option A, tell me "merge to main" and I'll push it now. That also rules D-09.
- Applied: option (a). Integration branch is `main`. The first push to `main` was blocked by the session's auto-mode permission check. Pat then chose, verbatim, "2." (switch out of auto mode and approve the push), and `main` was fast-forwarded from 512cdcc to 08cd06a. Each new cloud session still needs Pat to grant push to `main` and `agent/*` in that session, because the session's own instructions limit pushes to its branch.
- Pat, 2026-10-11, verbatim (Gate 1 session, branch `ccr-d1e9ac59-b90cv2`): "Approved. You may push to main and to agent/* branches in this repo."
  - Applied: commits land on the session branch and are pushed to both it and `main`, so the two stay identical and `main` remains the integration branch.

### D-10 Subagent model tiering
- Pat, 2026-10-11, verbatim: "Delegate subagents wisely. If you can use sonnet or haiku subagents, do so. Not every task is worthy of Opus"
- Applied: architect and reviewer use the strongest model (GAUNTLET.md 11). Researcher, test-writer, ui-auditor: Sonnet. Implementer: Sonnet, confirmed at Gate 3 once step plans exist. Mechanical probes and scans: Haiku. The orchestrator may raise a single invocation's tier when the task card justifies it (for example R-01 stack selection) and records why in the task card.

### D-11 Cloud session setup: repos attached per session
- Finding (R-00): this session has Command-Library and Pocket-Steward attached. In an Anthropic-hosted session with several repositories, no repository's `.claude/settings.json` hooks, permission rules, or `env` load (https://code.claude.com/docs/en/cloud-environments, accessed 2026-10-11).
- Recommendation: start future gauntlet sessions with only Command-Library attached.
- Tradeoff: repo hooks and permission rules load (fewer prompts, enforcement hooks possible at Gate 3), versus losing direct read access to Pocket-Steward as a reference. What R-01 and R-15 need from it is already recorded in `gates/GATE-0.md`, and a research agent can read it from GitHub when it is attached.
- Pat, 2026-10-11, verbatim: "Approved."
- Applied: the Gate 1 session (session branch `ccr-d1e9ac59-b90cv2`) has only Command-Library attached. Verified at session start: the six `.claude/agents/` definitions loaded as subagent types without hot reload, and `CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1` is still set by the environment. No `.claude/settings.json` exists yet, so no repo hooks or permission rules are in play.

## Codex resume rulings, 2026-10-11

- D-09, Pat verbatim: "Approved. You may push to main and to agent/* branches in this repo." Applies to this Codex session.
- D-01, Pat verbatim: "com.architectpsg.commandlibrary". Reaffirms existing ID.
- D-02, Pat verbatim: "Stay public." Reaffirms public repository.
- D-03, Pat verbatim: "[where your keystore is, or \"make a new key\"]". Unfilled template does not supersede the existing completed new-key ruling. Uploaded signing sheets remain outside git; no key regeneration.
- D-05, Pat verbatim: "Codex alone through Gate 3." Supersedes the prior Claude-only ruling. Codex is sole orchestrator through Gate 3.
- D-11, Pat verbatim: "Approved." Only Command-Library is used for this session.
- Runtime note: Sonnet/Haiku are not available in this Codex toolset. Native research agents use the inherited OpenAI model; no unsupported model configuration is claimed.

## Gate 1 proposed rulings, 2026-10-11

These are Codex recommendations, not Pat approvals. Gate 1 is filed; wait for Pat before Gate 2.

### D-12 Matrices and topic recommendations

Adopt RESEARCH_SUMMARY.md's 17-topic recommendations and complete R-05/R-06/R-07 build/adapt/skip matrices. Preserve every mandatory baseline even if competitor evidence is UNVERIFIED. Lotus and Mox are the disclosed established-app comparison cohort, not an exhaustive global top-two claim. Guided export/paste replaces automatic Moxfield/Archidekt retrieval pending current permission; automated EDHREC enrichment is excluded under current terms while links remain. All eight cloud and six local candidates remain in scope with applicable API terms and exact contract checks before verified support claims; Gemini personal-use eligibility is unresolved. Optional competitor extras are explicitly proposed, never silent cuts. Details and evidence limits remain in each report.

### D-13 Full offline rules acquisition

Preserve full searchable offline Comprehensive Rules and cross-links. Recommend personal official-source download/cache where applicable terms allow, plus user-initiated local import fallback. Do not claim full public corpus bundling permission; that separate republication question remains unresolved. Official links alone are insufficient. Parser/encoding/count validation follows acquisition before implementation acceptance.

### D-14 Full catalog scope

Full covers every Oracle card and every printing/language, preserving foreign owned identities. Do not label default_cards every-language completeness. Resolve actual current all_cards/alternative feed coverage, transfer and disk measurements before performance budgets. Lean retains all fetched gameplay/rulings offline. No narrower language exclusion is proposed.

User checkpoint policy, verbatim: "Continue and make sure to always print checkpoints and handoffs before usage runs out if possible". Applied: durable checkpoint commits and handoff links printed during work; final handoff identifies the next gate and open rulings.

## Pat's Gate 1 answers (latest ruling)

Verbatim, mapped to the six numbered proposals in the Gate 1 message:

1. D-07: "Fine." Applied: Flutter approved; retain documented runtime/accessibility verification requirements.
2. D-04: "Explain". Not approval. Default price source/currency remains open while Codex explains estimates, source choice, ownership and selectable alternatives.
3. D-06: "Fine". Applied: unsigned per-user Windows EXE plus portable ZIP approved for the selected Flutter stack; trusted signing deferred for personal use.
4. D-12: "Yes". Applied: Gate 1 topic recommendations and full parity matrices approved, preserving required baseline and disclosed limitations.
5. D-13: "Yes". Applied: full offline rules acquisition/import approach approved; no new public corpus redistribution permission inferred.
6. D-14: "Yes except language default to English the rest needs user approved download." Applied: default Full download includes English card data and printings. Non-English language data downloads require explicit user approval before starting; no silent background download. Preserve foreign owned identities and unresolved rows without automatically downloading their language data. Full offline rules and rulings remain in scope. This supersedes the proposed automatic all-language download scope.

Gate status: five rulings applied; D-04 is the sole outstanding Gate 1 ruling. Do not start Gate 2 until that ruling arrives. Earlier proposal wording is historical; these applied rulings control.

## D-04 final Gate 1 ruling

Pat, verbatim: "Approved". Context: approval of the explained default, USD with Scryfall reference pricing conditional on source/terms verification, owned copies counted as zero acquisition cost while market value stays visible, missing prices unknown, selectable supported sources/currencies including EUR/Cardmarket and Magic Online tickets. Applied: D-04 ruled. All six Gate 1 choices are now ruled; Gate 2 specification is authorized. Earlier D-04-open pointers are superseded.

## Gate 2 proposed decisions

D-15 open: approve SPEC, DESIGN_SYSTEM, COVERAGE, DATA_MODEL, COMPONENTS, SCREENS and PARITY_ACCEPTANCE with64 observable contracts and all approved matrix obligations. Includes detailed any-eligible-owned-printing default/strict pin policy, package copy/link semantics and user-enabled life variants/local sharing. All source/runtime verification limits remain explicit. Independent review resolved all five findings; Pat has not yet ruled Gate2.

D-16 open: approve PERFORMANCE P01-P12/reference methods. Key targets p95 cold2.5s, DB query150ms, visible search250ms after debounce, full local import10min, steady base RSS300MiB/peak450MiB excluding loaded LLM only, decoded art64MiB steady/96MiB transient, provisional base-disk1.5GiB. Values are proposed acceptance targets, not measured results; failed targets need reported correction or subsequent ruled change, never scope deletion.

## D-17 shared continuous handoff mandate

Pat, verbatim:

> I need you to check the plans and orchestrate the next several stages leaving files here and on the GitHub with instructions so that codex or other GPT models, as well as Claude and Claude code can continuously pick up where the other leaves off. I should not have to explain anything in chat I should simply be able to state continue, and at any point either set of models should be able to look at a readme and the current state, continue intelligently while maintaining proper notes and checkpointing, this way whenever I run out of usage on either account, the other can just continue work a reasonable amount and then stop and nothing is lost.

Applied: model-neutral sequential orchestration and repository-first bounded sessions. This supersedes prior D05 Codex-only restrictions and generic stop-for-chat at every routine technical gate. Check and orchestrate subsequent stages using current independently reviewed SPEC/design and provisional performance targets, without inventing explicit D15/D16 approval words. Independent review and prerequisite evidence still required before technical advance; material product/security/consent changes, budget relaxation, new costs, destructive actions and public release need actual authorization. README/CONTINUE/CURRENT/STATE/QUEUE become shared entry points. An account limit or model switch requires no task explanation from Pat. Usual GitHub/tool permissions still apply. No background account switching mechanism is claimed.

## D-18 Interface language and localization
- Gap G-15 (`docs/gauntlet/gates/GATE-3-CC-GAPS.md`): PARITY_ACCEPTANCE PX07-071 said "languages Pat rules", and no ledger entry, SPEC row, or acceptance ID existed.
- Recommendation put to Pat by Claude Code: "English-only interface, built so translations can be added later, with no translations now."
- Pat, 2026-10-11, verbatim: "Approve all"
- Applied (branch `agent/cc-plangaps-01`): SPEC J03 and COVERAGE AC-J03 require English as the only shipped interface language, every user-facing string from externalized localization resources with no hardcoded UI strings in widgets (checked by a scan of widget code), no other translation, and no interface-language selector. Card data language stays governed by A02. PX07-071 stays Adapt with the ruling noted.

## D-19 Platform priority: Android first, Windows is version 2
- Pat, 2026-10-11, verbatim: "Own foldable Samsung Galaxy fold 7.  Usa. Prioritize full Android app before touching any windows code. That's v2 spec."
- Applied: version 1 is the complete Android app (phones, foldables, tablets). Windows is version 2 and starts after M9. No Windows code, Windows packaging, Windows CI job, or Windows-only platform adapter is built in version 1. Planning documents mark Windows-only scope "(v2, D-19)" and MILESTONES gains a final "V2 Windows" row; no scope is deleted. GAUNTLET.md and Appendix A stay as the historical baseline; this ruling overrides them (GAUNTLET.md section 0). Keyboard, mouse, hover, right-click, drag-and-drop and large-window layouts stay version 1 because Android supports them.
- Facts from the same message: Pat owns a Samsung Galaxy Z Fold7, the physical device for manual posture, hinge and TalkBack evidence (it is not the mid-range performance reference). Pat is in the USA, which matters for Windows code-signing eligibility if version 2 adopts signing (D-06, RISKS RK-07).
- Effect on D-06: Windows installer format and signing move to version 2 unchanged.
- Effect on D-07, flagged to Pat: Flutter won 88.6 to 84.0 mainly on Windows Narrator evidence, and Kotlin wins under reuse-heavy weighting (RISKS RK-08, RESEARCH_SUMMARY D-07). With Windows in version 2 the main reason for the choice no longer applies to version 1. Pat is asked whether to rescore D-07 for an Android-first version 1 before the toolchain is pinned (`orch-toolchain-01`). D-07 stands until he rules, but the toolchain is not pinned until he answers: `orch-toolchain-01` is blocked on that answer and `orch-source-fixtures-01` is the eligible fallback. Either way the version 1 tuple is Android plus pure Dart only.

## D-20 Kotlin Android version 1, separate Flutter Windows version 2, sync after Windows runs

- Pat, 2026-10-11, verbatim: "Just run with kotlin, but plan for expansion to flutter for windows when v2 launches. It would be a separate file entirely. Then we build sync into it but that's for after it runs period."
- Applied: supersedes the Android-v1 portion of D-07. Version 1 uses Kotlin on Android. Flutter is reserved for a separate Windows version-2 application source tree, which begins only after the complete Android application runs. Sync planning and implementation begin only after the Windows app runs and receives a future explicit authorization. `V1_KOTLIN_EXECUTION_SPEC.md` is the execution-specification authority. No code, pin, build, download, signing or sync work is authorized by this ruling; K0 audit and an explicit later user execution release are required before K1.

## D-21 K1 execution release and complete-section checkpoint policy

- Pat, 2026-10-11, verbatim: "Use astra to verify and confirm plan to start build, I don't want chunks or unfinished releases. I need a complete, scalable, cleanly noted, highly efficient codebase to support the entire app. I need the app fully built and running, throughly bug checked, and capable of being expanded. Zero placeholders unless literally unbuildable without additional tools/approvals. Stop after each complete section for documentation, handoff, updated notes etc. every single date change and action taken must be functional and resumable after compaction or usage limits hit"
- Applied: Astra high approved K1. K1 begins now. The full Android app remains the goal; section boundaries are internal verified milestones, not partial releases. Every section must publish evidence, updated coordination files, journal and exact next action; incomplete recovery checkpoints are permitted only when labelled incomplete. Architecture/security/durability/compatibility conclusions require Astra high review. K2 and later implementation require their own accepted cards and evidence.

## D-22 Compute economy and ordinary-chat research

- Pat, 2026-10-11, verbatim: "Continue. Make sure to use the weakest agents/subagents capable of each task. Be responsible with token usage."
- Pat, 2026-10-11, verbatim: "Also properly defer any tasks or research work where you could give me a hand off for regular chat because it uses usage more efficiently than cloud"
- Applied: narrow mechanical docs/validation use Luna; routine isolated implementation uses Sol medium unless complexity requires more. Keep required independent Astra architecture/security/durability reviews narrow. Defer standalone API/terms/source-format surveys and design alternatives to self-contained regular-chat packets, not additional cloud research. Cloud remains responsible for repository integration, builds, tests and authentic artifact/byte verification. Deferred research is required before its affected consumers ship, not an invented blocker for unrelated stages. Already-running narrow source checks are saved once and not repeated.

## D-23 Parallel Claude Code work

- Pat, 2026-10-11, verbatim: "Also I want to have Claude code working in parallel so give me an instruction set so you don't step on each other's toes please"
- Applied: Codex retains the active integration lease; Claude Code receives one bounded independent K2 QA task on agent/cc-k2-qa-01, writing only docs/verification/k2-claude/** under its own published lock. This delegated parallel writer does not take over orchestration or write shared state/build configuration. Codex reviews and integrates the worker branch sequentially. Existing core implementation ownership remains exclusive. Further production-code delegation requires an explicit task/lock and accepted dependency interfaces, not a broad parallel mandate. Ordinary-chat research remains with Pat.

## Lease takeover, 2026-10-12
- Context: Codex ran out of usage; its lease (session codex-k2-foundation-20261011, task orch-kotlin-m0-01) expired at 2026-10-11T23:47:41Z without release. GAUNTLET.md 5.8 requires Pat's ruling before another orchestrator takes over. Claude Code offered: (1) wait for Codex, or (2) claim the expired lease and start K2 step 2.
- Pat, 2026-10-12, verbatim: "2. Just leave very clear notes so I can tell codex to read the next steps.md or wherever you put your work notes so it knows what's up and can continue without having to audit the whole codebase."
- Applied: Claude Code holds the lease from checkpoint cp-20261012-claude-k2-14. Work notes for Codex: `coordination/handoffs/K2-STEP2-CLAUDE.md`.


## Lease takeover, 2026-10-12 (catalog to shell)
- Context: the Codex lease (session codex-k2-catalog-20261012, task orch-kotlin-m0-01) expired at 2026-10-12T13:54:34Z after catalog PR #1 merged (e5ae264). Codex left `coordination/handoffs/CLAUDE-CODE-CONTINUE-20261012.md`.
- Pat, 2026-10-12, verbatim: "Fetch the latest \"main\" from \"pemunson90-cmd/Command-Library\", then read \"coordination/handoffs/CLAUDE-CODE-CONTINUE-20261012.md\" and continue the project from that packet."
- Applied: Claude Code holds the lease from checkpoint cp-20261012-claude-k2-26. Shell branch agent/cc-k2-shell-01 (b1064f2) was authored under this same Claude Code session, so its review uses fresh-context reviewer subagents and is recorded as not independent of the author session; an Astra review may still be required for formal acceptance.

## D-22 Fold7-first acceptance and genuine input acquisition

- Pat, 2026-10-12, answering whether genuine English inputs already exist: "you need to find them". Applied: find and acquire official English representative cards/rulings and rules TXT for personal validation; retain exact-byte provenance and keep source payloads out of public Git/artifacts. This does not authorize non-English downloads or settle public redistribution rights.
- Pat, answering whether a separate mid-range reference device is available: "irrelevant. it needs to work on my phone. backwards compatibility is an afterthought". Applied: the Galaxy Z Fold7 is the primary Android acceptance target. The separate Snapdragon778G-class reference-device prerequisite is superseded; retained budgets are provisional engineering targets evaluated on the Fold7, not measured passes. Existing minSdk23 and compatibility checks remain unless a later technical change is needed; backward compatibility is secondary, not the release-driving hardware criterion. Physical Fold7/manual behavior still requires actual evidence and cannot be claimed from an emulator.

## Superseded scope interpretation: immediate functional Fold7 build

- Pat: "The goal is to get the app fully running and functional on my device, with no placeholders or errors. Polish, feature specifications, updates etc can be handled later."
- Applied: prioritize an installable, tested Fold7 build with real catalog/rules import, persistent local search/status and settings; expose only working actions. Do not expand specifications or chase later feature/polish milestones before producing this functional build. No fake adapters, fabricated test results or placeholder navigation. This working-build milestone does not claim every retained v1 requirement or K3 full-corpus coverage is accepted. Physical Fold7 installation/runtime remains actual evidence, not inferred from emulators.

## D-24 Complete Android v1 delivery reaffirmed

- Pat, 2026-10-12: "I said I don't want an app until everything in it is fully functional to the spec that was provided".
- Pat: "please do exactly what the plan is. If you need to re-familiarize yourself with the gauntlet or the other documentation files do it now."
- Pat: "This should come to me as a full triple a release ready professional looking masterpiece of an app" and "it should work in every single way required by it."
- Applied: complete the existing Android v1 plan and retained 64 requirements/194 build-adapt parity rows; 19 approved skips remain. Internal builds are verification checkpoints and must not be handed off as finished delivery. Full catalog/rules/rulings and other supported source datasets/packs/models require working in-app Download controls; local import remains available. Canonical permanent-key signing/certificate verification, complete professional visual system and Fold7-focused acceptance remain final obligations. Personal release readiness does not authorize public publishing, Windows v2 or sync.
- Correction: the earlier immediate-build interpretation narrowed scope without user authorization and is superseded. Its duplicate D-23 identifier is removed; the original D-23 parallel Claude ruling remains. "Polish later" never waived existing functionality. No test or unrun physical-device outcome is promoted to acceptance.

## D-25 Life contract rulings and delegated shared-clock semantics

- Pat, 2026-10-13, on rule-based elimination: "Ask because certain cards/effects prevent losing/dieing." Applied: detected loss is a candidate requiring user confirmation; logged/undoable eliminate and restore preserve seat identities, counters, commander damage and history. Users can account for effects/overrides; detection does not silently eliminate a seat or finalize a result.
- Pat, on undefined SHARED clock mode: "what, like a chess clock? idk figure it out lol". Applied as delegated technical judgment: SHARED means one game countdown pool while running, separate from per-seat CHESS banks and elapsed game/turn timers. Root chooses this meaning; Pat did not prescribe its exact implementation. It requires explicit state and tested semantics before exposure.
- Pat, on CR903.10a and stolen own commanders: "Follow the official rule (Recommended)". Applied: commander damage belongs to the commander identity, regardless of controller; a victim's own commander can count when another player controls it. Partner commander subtotals remain separate.
- These rulings resolve handoff requests3/5/12, do not accept the domain or a partial app, and do not waive the retained G01–G13 requirements.

## D-26 Approved execution; on-device AI engines move to v1.1

- Pat, 2026-10-13, approving the research-based professional release plan: "1. Ai engines can be v1.1 they can't block functional release" followed by "Approved".
- Applied: approve execution of `docs/superpowers/plans/2026-10-13-research-based-professional-release.md`, with on-device engines/runtime integration (H03), dependent model download/load/runtime management (S25), native-engine benchmarks and engine-only portions of other rows assigned to v1.1. No llama.cpp/LiteRT-LM/ExecuTorch selection, build, model-weight acquisition or physical inference benchmark is a v1 release prerequisite.
- This refers to the researched on-device engines, not all AI features: prompt export/pasteback, cloud/custom/LAN providers, grounding, bounded tools, secrets, streaming/history and prompts remain in v1 under H01/H02/H04–H09. Their on-device-only acceptance clauses are deferred with H03. These modes must work without a local runtime.
- Do not ship an inert model-manager destination or nonworking local-runtime controls in v1. Required downloads for retained v1 catalogs/rules/rulings/art/variants/brackets/enrichment/languages still work in-app. Model/runtime downloads return with functioning engines in v1.1.
- All other functionality, professional visual/adaptive/accessibility requirements, canonical signing and Fold7 acceptance remain required. Mark the approved deferral in current acceptance tracking; preserve historical audit evidence rather than counting deferred engines as implemented.

## D-27 Fold7-focused, minimum sufficient verification

- Pat, 2026-10-13: "Why do we have a three device test when I only currently need it to work on a galaxy z7old 7 class device currently?" Interpreted in established context as Galaxy Z Fold7-class target, not three physical device models.
- Pat: "Good we should always be aiming for the most efficient use of time and compute between codex, chat, work when relevant, GitHub for things like versioning and when we specifically need it, and the minimum required testing/emulation. Not cutting corners but we shouldn't be doing more than we need."
- Applied: local compilation/JVM/real bundledSQLite and static API checks first; changed Android-specific flows use a focused modern native lane; broad modern UI/device acceptance only at meaningful checkpoints. Historical API23/alternate16KiB lanes remain optional when an actual compatibility/API/native concern justifies them. Routine workflow defaults to API36; manual compatibility_matrix enables the prior three configurations. Declared minSdk23 source/lint compatibility remains, but do not invent runtime acceptance for untested source revisions.
- Physical Fold7/posture/hinge/TalkBack/performance and canonical signing remain actual final obligations. One generic modern emulator is not Samsung/fold acceptance. Full functional v1/D24 remains unchanged; no test expectation weakened or unsupported feature counted as working. Use GitHub for versioning/public checkpoints and unavailable local device execution, not checks Codex can perform locally. Route missing standalone research to regular chat; keep the weakest capable bounded workers and required independent reviews narrow.
- Pat's continuous goal: "/goal keep working and check pointing. Only stop for research and questions". Continue independent authorized work through checkpoints instead of ending after each component; pause only where missing research or a material question actually blocks dependent progress.
