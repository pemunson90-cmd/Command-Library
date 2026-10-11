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
| D-06 | Windows installer format and code signing | Ruled; see latest Gate 1 answers |
| D-07 | Stack | Ruled; see latest Gate 1 answers |
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

## Open

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
  - Evidence: on 2026-10-11, six Sonnet researchers launched together all stopped on the account session limit (HTTP 429; JOURNAL and cards cc-research-02, 11 to 15). At M0 the dependency graph allows at most three parallel components after `core` (storage, network, vault; `docs/architecture/registry.json`). Merges are serial with review per merge (GAUNTLET.md 5.4).

## Ruled

### D-01 Package / application ID
- Proposed: `com.architectpsg.commandlibrary`.
- Recommendation: accept.
- Tradeoff: permanent once installed. Changing it later makes a separate app and needs a manual data move.
- Pat, 2026-10-11, verbatim: "com.architectpsg.commandlibrary"
- Applied: application ID `com.architectpsg.commandlibrary` on Android; the same string is the Windows package identifier where the packaging format takes one (R-15).

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
- Effect on D-07, flagged to Pat: Flutter won 88.6 to 84.0 mainly on Windows Narrator evidence, and Kotlin wins under reuse-heavy weighting (RISKS RK-08, RESEARCH_SUMMARY D-07). With Windows in version 2 the main reason for the choice no longer applies to version 1. Pat is asked whether to rescore D-07 for an Android-first version 1 before the toolchain is pinned (`orch-toolchain-01`). Until he rules, D-07 stands.
