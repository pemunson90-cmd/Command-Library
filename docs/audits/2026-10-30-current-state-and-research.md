# Command Library: assessment, research plan and build horizon

Prepared for Pat by Marlow • 30 October 2026, America/New_York.

## Verdict

Command Library has substantial Kotlin product code on its continuation branch, but no evidence establishing a finished, canonically signed, physically accepted first release. It is past a bare foundation. The immediate work is completing essential consumers and privacy controls, resolving remaining core workflow gaps, then verifying the assembled app on the actual target phone. The larger retained roadmap still includes major unbuilt subsystems.

The recommended approach is to finish the existing D31 release and expand through useful updates. A rewrite would discard considerable durable-domain work. Completing the original entire roadmap before first release would contradict the approved cut. Research should close named uncertainties, not become another universal prerequisite to coding.

## Exact baseline and coverage limits

Repository: https://github.com/pemunson90-cmd/Command-Library

| Surface | Audited revision |
|---|---|
| Frozen main | 4a14703fe548c10f4d82e465df7ecda4ba550437 |
| Active continuation | agent/cx-thread-recovery-20261013 |
| Continuation head | 7f822e6c746cf7411f1f7030b1d08a734b1694aa |
| Latest checkpoint | cp-20261016-artwork-boundary-270 |
| Current task | orch-android-v1-campaign-01, step 3 |
| Last published continuation commit | 15 October 2026, 10:16 p.m. New York |

Main and continuation have 4 main-only and 466 continuation-only commits. That is ancestry divergence, not 466 unreviewed features. Net difference: 1,078 files, 142,380 insertions and 331 deletions, including schemas, documentation and tests. There are 1,733 tracked files; the Android tree includes 391 production Kotlin files with 48,515 lines, and 722 Kotlin files across production/test source sets.

All 35 execution tasks, 64 requirement IDs, 213 parity rows and 25 screen contracts are accounted for in this report. The registered-work inventory is exhaustive. This is not a claim that every latent defect in 48,515 production lines has been discovered. Inspection covered authority, current coordination, full structured task inventory, historical audit, current verification reports, production wiring and selected feature/UI/controller paths, release configuration/workflows, a source-wide unfinished-marker scan and remote branch ancestry. Appendix task observations retain source-recorded evidence, including older text, rather than presenting every statement as freshly verified.

This environment has no configured Android SDK or existing Gradle cache. No Android build, emulator test, whole-feed benchmark or physical phone test ran during this audit. No product code, remote branch, issue, PR, lease or release was changed. Stopped-session private files, unpublished commits and other environments' worktrees cannot be recovered from a clone. Referenced temporary evidence paths are historical references, not proof the bytes remain accessible.

The current-head GitHub workflow lookup returned no associated runs. Earlier exact-source reports and runs exist; an empty current-head listing does not invalidate them or establish current release readiness.

## Fresh checks

The repository continuity checker passed: pointer/lease shape, DAG/ownership, all 64 requirement joins and 213 parity rows, and Kotlin registry checks. All 31 Python tests passed in 1.806 seconds. Synthetic capture messages emitted by those tests concern fixtures, not a fresh device screenshot audit.

Commands actually run:

~~~sh
python3 scripts/check_continuity.py
python3 -m unittest discover -s scripts -p 'test_*.py'
~~~

These establish coordination consistency, not Android correctness. Historical host/native results below remain historical and scoped.

## Goals and approved scope

DECISIONS D20, D26, D30 and D31 govern. Android v1 is Kotlin/Compose/Room, personal-first and offline-first. English is default; additional languages require explicit approval. Windows is a separate Flutter v2 after Android acceptance. Sync requires both offline apps and later authorization.

D31 first release requires actual card access/search/details/art/rulings where data exists; durable deck creation/editing, commander/printing selection, reusable manual multi-tags and tag-aware baseline analysis; relevant truthful legality; useful import/export; collection owned/missing quantities; ordinary 1–6-player life/commander-damage/counters/undo/save/resume/results; existing provider-free prompt sharing; appropriate setup/privacy/data integrity; readable compact/unfolded Fold7 UI/basic accessibility; canonical signing and actual installation/acceptance.

Pat later accepted plain-text collection export for v1.0. Richer formats remain later. D31 permits later advanced formats/brackets/recommendations, printing/price optimization and currencies, specialty games, extensive AI dialects/tools/discovery, QR/extra sharing, full private-state migration, bulk art packs and exhaustive non-target layouts. D26 independently defers on-device inference to v1.1. Stable implemented optional code remains.

| Inventory | First release | Mixed first/later | Retained later | Approved skips |
|---|---:|---:|---:|---:|
| 35 execution tasks | 5 | 19 | 11 | 0 |
| 64 requirements | 21 | 27 | 16 | 0 |
| 213 parity rows | 144 | 22 | 28 | 19 |

These are scope dispositions, not completion percentages. Whole-task acceptance remains open in the registry. Mixed items need concrete first-release clauses; their later tails must not silently gate delivery.

## Principal findings

### The continuation is the real product baseline

Main intentionally freezes an earlier tested foundation. Auditing main alone would wrongly report much newer deck/collection/life/AI functionality absent. FEATURE-AUDIT at f4760ba is historical evidence, not a current absent-feature count. Use current AppGraph and actual consumers to interpret it.

### Structured observations lag implementation

The user-storage-contracts observation still says the deck repository is not implemented, while current AppGraph constructs SqlLocalDeckLibrary, edits, drafts, versions and inventory. links-ui says all consumers absent, while CardPrintings renders selection and external launches and AppGraph binds LocalCardLinks. Some AI observations also retain earlier pending steps later completed.

Continuity checks pass because joins and pointers are structurally consistent; they do not establish prose recency. Reconcile exact stale rows at the next engineering checkpoint and preserve historical evidence.

### Ordinary artwork is still missing

CardDetail calls CardArtworkFrame without content; the frame displays an unavailable fallback. CardArtworkAccess is a capability declaration, not a wired implementation. Current AppGraph does not construct or bind an artwork adapter. The live packet requests a five-path producer; this snapshot contains no published completion.

First check for an unpublished worker return rather than claiming it was lost. Finish trusted-source binary access, controller, DI and detail rendering using the reviewed initial memory-only architecture: exact printing/generation/face binding, explicit consent, bounded bytes/pixels, cancellation/revocation, attribution and uncropped Fit rendering. Contract limits: 3 MiB compressed, maximum edge 2048, maximum 2,000,000 pixels. Respect cache=0; do not silently introduce disk caching. Full persistent cache and packs remain distinct later obligations.

### Privacy infrastructure lacks complete user control

DataStore, ConsentCoordinator and removal-request infrastructure exist. Current SettingsController exposes theme updates; SettingsScreen provides setup guidance, theme, providers and notices, not the full consent review/revoke/remove journey. The live task identifies missing removal-outbox consumption.

Implement actual removal and its UI with ownership boundaries that preserve private decks, inventory, games and history. Enqueued removal is not completed removal. Test revoke during transfer and crash/restart during cleanup.

### Ordinary life is narrower than the full domain

Actual setup/service/UI supports 1–6 named players, manual commander labels, configurable initial life, life/tax/poison/energy/experience, +/-1 or 5, grouped commander-damage/life undo, saved resume/events, explicit results and reopen. Its report explicitly uses NONE countdown and disabled elapsed timing. The controller admits a small action subset, not every domain operation.

Basic timing, linked setup and broader retained ordinary tools need completion/verification. Host service-epoch changes and Activity recreation do not establish OS process-death behavior. Specialty variants must remain independent.

### Deck and collection implementations should be finished, not rebuilt

AppGraph binds real deck library/edits/versions/drafts, manual tags, statistics, basic validation, inventory and owned-quantity allocation. Text transfer has actual document-writer and host-app evidence.

Remaining first-release work includes deck-local category management, complete usable commander/printing/identity-resolution paths, native autosave/recovery/upgrade and target-device usability. Explicit manual tags do not require inventing a global automatic inheritance engine first.

### Genuine data and performance evidence remain open

Historical state records Scryfall host restrictions and source-completeness gaps. Recheck current actual access before calling the same restriction current. A permitted local-import path is valid, but must work without developer intervention and show installed scope honestly. Representative fixtures and old genuine rules counts do not establish current whole-feed coverage.

### Release signing remains absent

The app build file still uses versionName 0.1.0-k2, versionCode 1 and has no canonical release signing configuration. The Android workflow assembles debug artifacts and labels them internal. The decision ledger records a public canonical certificate; it does not prove the signing secret is present/usable.

Add a masked temporary signing path using the approved existing key, verify the certificate, then actual same-key install/update with data preservation. An APK file checksum is not its certificate fingerprint.

### Branches and open work need semantic reconciliation

Three open draft PRs: #19 CI, #23 tag design, #26 D31 scope. Four open non-PR issues: #20, #22, #24, #25. D30/D31 have already been incorporated on the live branch; #19 overlaps newer workflow changes. These seven items are not seven missing product components.

Many non-ancestor remote branches preserve held SOURCE01 or alternate reviewed candidates. Compare patches/current consumers before calling them orphaned or missing. Do not merge stale drafts wholesale or delete preserved evidence.

## Remaining work by component

Every registered task and its original observation appears in Appendix A. This table translates them into concrete remaining outcomes.

| Component/tasks | Remaining work |
|---|---|
| data-finish | Actual source-qualified feed verification, interrupted acquisition/reopen/atomic activation; usable import fallback and explicit language consent |
| rules-finish | Current genuine corpus and complete offline glossary/cross-reference/keyword/ruling journey |
| query-execution | Full-corpus differential SQL/reference correctness, unknown/multiface/printing/cursor/cancel and native performance |
| user-storage-contracts | Reconcile stale observations with real implementations; review latest migration chain and old-install preservation |
| deck-edits | Retained full edit/diff fields and actual editor grouped undo; package apply is later |
| deck-persistence | Native draft/autosave/process-death/recovery/ambiguous-ACK and upgrade durability |
| deck-ui | Deck-local category management, retained commander/printing/notes/primer/tag workflows and accessible native navigation |
| packages | Repository, contribution-safe linked/copied apply/update/remove, versioning and undo; DTOs are not implementation |
| collection | Native CRUD/quick-add/mark-owned/bulk/owned-missing/restart and upgrade; plain-text export sufficient now |
| pricing | Keep real quantity allocator; add source-qualified quotes, totals/policy/cost consumers later |
| optimizer | Later eligible deterministic printing optimization, preview/versioned apply/undo after pricing |
| analysis | Real-data and phone acceptance for tag-aware curve/type/pips/sources/average; advanced inference later |
| playtest | Keep calculator; later opening hand/mulligan/conserved zones/scenarios/replay/undo |
| validation | Essential Commander real-data/unknown behavior now; full registry and exceptions later |
| brackets | Later sourced versioned criteria/Game Changers/combos, reconciliation/evidence/confidence/intent/targets |
| recommendations | Later deterministic explainable offline candidates, manual overrides and constrained revalidated patches |
| links | Keep resolver; prove source/selected-printing routes, encoding and external failure states |
| life-source-variants | Later authentic named definitions/managers/zones/undo; no ordinary-life dependency |
| life-service-ui | Basic timing, retained linked setup/tools, process death, fold-safe 1–6 player use and results |
| profile-game-transfer | Later public privacy-aware profile/game schemas, collision preview and unique-result reconciliation |
| transfer-codecs | Authentic useful common text fixtures now; vendor CSV/native lossless formats later |
| transfer-ui | Native picker/export/partial-write/recreate/retry and adequate identity resolution; richer mapping later |
| backup-restore | Later full versioned private archive/atomic previewed restore; safe persistence/upgrades mandatory now |
| qr-sharing | Later dependency trial, bounded local frames/hash/camera/image decode and privacy previews |
| query-context | Real exact-revision owned/in-deck/qualified-price execution; unsupported until wired |
| ai-prompts-grounding | Accept existing provider-free prompt/copy/pasteback/privacy on phone; structured patch/tools later |
| vault | Real origin/key/corruption/redaction/backup/reinstall evidence for any enabled provider |
| ai-providers | Retain stable custom HTTPS/Anthropic/Responses; authentic remaining dialects/tools/discovery later |
| ai-history | Enabled-path deletion/privacy/no-replay acceptance; richer usage/history/backup later |
| images | Outstanding ordinary adapter/controller/DI/UI; native bounded decoder proof; full disk cache/packs later |
| settings-onboarding | Consent review/revoke/remove and actual removal consumer, usable first-install data path; honest Full/Lean behavior |
| adaptive-input | Safe compact/unfolded/compact-height/200%/TalkBack/keyboard and state preservation; exhaustive others later |
| release-acceptance | Current assembled native safety, physical Fold7/performance, canonical signing/install/update |
| engine-v1.1 | Explicitly later actual model/runtime/inference with license/hash/ABI/memory/cancel/quality proof |
| links-ui | Correct stale absence claim; verify existing selection/launch; template settings/proven defaults remain |

## All 25 screen contracts

Dialog/sheet implementation counts as a screen workflow when it actually satisfies the contract. All rows remain short of full original-screen acceptance; later clauses do not automatically gate D31.

| Screen | Current state and remaining outcome |
|---|---|
| S01 Setup | Guidance/catalog/rules entry exists; finish consent/data first-use and demonstrate clean install without developer steps |
| S02 Search | Name/text/suggestions/advanced query exists; genuine corpus correctness/cancel/pagination/native latency |
| S03 Detail | Text/faces/rulings/printing/links exists; actual art/consent and selected-printing native proof |
| S04 Rules | Search/reader/glossary/links exists; current corpus/offline/rollback/native proof |
| S05 Library | Real library exists; retained organization/create/duplicate/search/sort/restart and focus |
| S06 Editor | Real edits/drafts/tags/undo exists; native autosave/recovery/all retained field actions |
| S07 Entry/category | Dialogs/tag pool exists; deck-local categories and accessible reorder/field control |
| S08 Import | App text/file preview exists; authentic examples, unresolved identities and native picker atomicity |
| S09 Analysis | Baseline stats/basic checks exists; real facts, uncertainty and readable phone evidence |
| S10 Playtest | Probability calculator exists; later hand/mulligan/zones/scenarios/replay |
| S11 Versions | Capture/diff/restore/recovery exists; native immutable snapshots and restore safety |
| S12 Collection | CRUD/bulk/mark-owned/text export exists; native quantities/privacy/update preservation |
| S13 Recommendations | No complete product path; later explainable constrained candidates/patches |
| S14 Pricing | Quantity allocator/settings foundation; later actual quotes/policy/unknown totals/budgets |
| S15 Brackets | Contracts/research; later sourced criteria/intent/evidence/targets |
| S16 AI | Provider-free prompts and optional provider/history paths; phone privacy/grounding proof; richer later |
| S17 Transfer | Deck/collection text paths; native partial-write/SAF/privacy; archive/QR later |
| S18 Downloads | Acquisition/import UI; genuine source/scope/recovery proof; packs and extended modes later |
| S19 Help | Guidance/notices partial; accurate version/offline limits/help/redacted diagnostics |
| S20 Game setup | Profiles/manual setup exists; retained linked selectors/1–6 persistent configuration |
| S21 Table | Ordinary real table; timing/process death/hinge-safe native use |
| S22 Tools | Ordinary counter subset; audit/expose required ordinary tools; specialty later |
| S23 Results | Results/draw/reopen/events/list exists; linked stats/history filters/restart/no double count |
| S24 Settings | Theme/setup/providers/notices; complete consent/revocation/removal/privacy controls |
| S25 Models | Explicitly deferred v1.1; actual runtime/model manager absent |

## Research pass: decisions, evidence and deliverables

This plans fresh external research; it does not claim upstream documentation has already been freshly verified. Repository investigation above has been performed. Use official primary sources for technical/API/source-policy claims and capture exact revision, date and applicability. Source reading does not replace app testing.

| Packet | Question and primary evidence | Experiment / return | Decision unlocked |
|---|---|---|---|
| R1 Data | Current Scryfall bulk/card/ruling/image guidance and official rules source; actual access/redirect/header behavior and personal-use terms | Genuine permitted bytes/hash/count/date/language metadata; supported import replay; exact access failure separately | Reliable first-install data route and truthful offline scope |
| R2 Artwork | Existing pinned review, official image host/formats/attribution and Android decoding behavior | Exact URI/consent/generation, oversized/animated/malformed bodies, revocation/cancel and native decoder probes | Complete current artwork without reinventing it |
| R3 Removal | Existing removal/settings contracts and Android storage/backup behavior | Resource ownership map; revoke/remove/crash/restart and independent private-data readback | Safe actual removal and privacy controls |
| R4 Import | Authorized authentic deck text exports and documented field representation | Represented fields/boards/printing/finish/tags/loss table; real import and unresolved preview | Useful first-release transfer, later adapters |
| R5 Facts/legality | Current official Commander rules and source legality facts/versions | Positive/negative/unknown and multiface/partner/background/companion fixtures | Essential checks without false clean verdicts |
| R6 Durability | Official Android lifecycle/Room/SAF/backup guidance and actual current implementation | OS process kill/relaunch, interrupted write/import/restore/export, real old-install upgrade | Data safety and restart acceptance |
| R7 Fold7 | Actual device configuration and official adaptive/accessibility guidance | Compact/unfolded/rotation/font/TalkBack/keyboard task script, captures/latency/focus/safe-region evidence | Target-phone usability and bounded later tails |
| R8 Signing | Existing approved certificate and current Android signing/update documentation | Masked same-key signing, certificate/hash metadata, install then update preserving data | Canonical personal delivery |
| R9 Later | Official bracket/format/provider/local-runtime/model/Windows sources when their update is selected | One narrow compatibility/license/ABI/source probe before pinning | Sustainable expansion without delaying D31 |

Each packet records: ID; precise uncertainty; affected requirement/release slice; exact source baseline; source URL/title/version/date; fact and applicability; contradictions; proposed decision; executable probe; actual result/artifact/hash; confidence; expiry trigger; next action. Label repository fact, historical report, fresh execution, upstream fact, inference and proposal distinctly.

Conflicting channels establish different things. Documentation can establish a supported contract; an HTTP failure establishes that request failed in that environment, not a universal prohibition. A fixture proves bounded behavior, not whole-feed completeness. Host execution, Android-native execution and physical Fold7 acceptance remain independent dimensions.

### Recursive research analysis

1. Begin with a decision that changes engineering: for example, how a fresh install obtains usable English cards and art. Split into source admission, transfer/import safety, metadata completeness, UI and acceptance.
2. Each child states its parent assumption, competing hypotheses, smallest discriminating source/probe, and implementation consequence. Reject branches unable to change a decision or test.
3. Pass one establishes primary-source contracts and repo fit. Pass two attacks contradictions/failure cases. Pass three performs an authentic bounded reproduction or disposable prototype.
4. Default depth is three levels. Deeper recursion needs a concrete unresolved dependency, not a generic instruction to investigate more.
5. Reconcile upward after each return: supported facts, falsified assumptions, remaining uncertainty and dependency/scope impact. Reopen a parent only when new evidence can change its action.
6. Stop a branch when it is actionable, the next necessary probe is executable, or access is concretely blocked. Record exact endpoint/status/environment and a permitted alternative for blocks; do not repeatedly retry the same restriction.
7. Stop the release pass when each D31 blocker has a justified action and named acceptance test. Carry nonblocking uncertainty to its update. Stop the horizon pass once each retained task has dependencies, a useful outcome and one next research question.

Prioritize high-consequence data/security uncertainties, then release blockers, then frequent usability problems, then later differentiation. Track information gained versus effort qualitatively. Repeated reading without a changed question is waste. Maintain one evidence ledger linked to raw artifacts, rather than flooding handoffs with logs.

## Next steps and long-horizon plan

### Approaches assessed

A rewrite/consolidation now would increase disruption with uncertain payoff. Finishing the entire original scope before release would contradict D31. Completing and verifying D31 while retaining all later tasks is recommended. This applies the existing scope decision; it does not create another release cut.

### Phase 0: reconcile current engineering state

- [ ] Fetch fresh heads; preserve unpublished work and inspect the named artwork return/locks. Lease expiry alone does not justify discarding a stopped session's work.
- [ ] Reconcile storage/links/AI stale observations against source/newer evidence. Separate fixed historical failures from current defects.
- [ ] Make first-release clauses of mixed tasks checkable in the existing registry; preserve parent IDs/later tails. Do not create a second queue.
- [ ] Compare open PR/issue semantic integration before proposing merges/closures.

Exit: one accurate baseline and a finite list distinguishing missing behavior from missing proof. Do not turn this into a rewrite of all documentation.

### Phase 1: finish essential user journeys

- [ ] Finish ordinary artwork adapter/source review, controller, DI and detail rendering with bounded native decoder proof.
- [ ] Add consent review/revocation/removal and durable removal consumption. Crash/retry must preserve user-created data.
- [ ] Finish deck-local categories and any retained commander/printing/identity-resolution gaps. Verify multi-tags and tag-aware analysis over real data.
- [ ] Complete ordinary basic timing, retained linked setup and practical tools. Keep specialty modes independent.
- [ ] Validate a clean-install acquisition or local-import route for cards/rules with accurate installed-scope guidance.

Exit: every D31 journey runs through real UI and persistence with honest failure states. Optional provider work cannot displace these outcomes.

### Phase 2: integration, native safety and usable design

- [ ] Run a coherent current-source host gate covering retained cards/rules/decks/collection/life/transfer/settings and enabled optional code.
- [ ] Run native migrations, real descriptors/pickers, decoder, vault and lifecycle checks; instrumentation compilation alone is not execution.
- [ ] Investigate historical native failures only where relevant to current source. Preserve failure provenance and do not blindly rewrite assertions to get green.
- [ ] Exercise actual process death and interruption at transfer/parse/index/activation/draft/undo/restore/export boundaries; independently reopen data and verify conservation.
- [ ] Execute compact/unfolded Fold7 workflows at large text and TalkBack, inspecting actual rendered captures. Verify focus return, keyboard/touch alternatives, reachable primary actions and offline/stale/error behavior.
- [ ] Measure import/search/memory/startup/storage/timer behavior on target hardware against existing provisional goals; a target change requires a justified explicit decision.

Exit: retained first-release criteria have actual scoped current proof. Phone checks that require Pat remain concrete observable scripts; model review cannot pass them.

### Phase 3: signed personal release

- [ ] Configure release versioning and approved-key signing with masked temporary files.
- [ ] Verify certificate, artifact hash, package identity and relevant ABI/alignment/platform checks.
- [ ] Execute clean install and realistic same-key update preserving user data. Handle incompatible debug-key installs explicitly.
- [ ] Deliver the agreed complete package and concise later-scope limitations.

Main merge and public publishing remain distinct actions governed by actual authorization. This audit performs neither. No schedule or completion percentage follows from commit counts.

### Phase 4: useful expansion updates

Proposed sequencing below is dependency-based and should respond to actual use. It is not a new scope ruling.

| Track | User outcome | Prerequisites and acceptance |
|---|---|---|
| Data/portability | Full/Lean refinement, genuine source completeness, richer vendor/native interchange, private archive/restore | Stable schema/consent/resource boundaries; authentic round trips and recovery |
| Deck depth | Rich organization, packages, bulk edits, hand/mulligan/goldfish/scenarios | Durable identities/versions; contribution-safe undo and quantity conservation |
| Costs | Source-qualified quotes, totals, policies, budgets, optimizer | Reviewed sources, exact decimal/unit/owned allocation; deterministic explainable plans |
| Brewing | Advanced validation/brackets/combo evidence/recommendations/role inference | Versioned facts/costs; manual suppression/provenance; revalidated reversible edits |
| Table depth | Tools/history/deck stats/clocks/specialty modes/game transport | Authentic definitions, stable clocks/zones/undo and privacy-aware schemas |
| AI depth | Remaining cloud/local dialects/discovery/tools/history | Credential lifecycle, consent/no-replay, authentic fixtures/account terms |
| Inference v1.1 | Actual native model manager/inference and useful measured quality | License/hash/ABI/download/load/cancel; target memory/thermal/performance |
| Artwork/sharing | Persistent bounded/pinned packs and local QR/image sharing | Explicit storage/consent, resumable integrity, bounded codecs/privacy |
| Windows v2 | Separate offline Flutter Windows app | Android acceptance first; portable interchange contracts, Windows install/storage/Narrator proof |
| Sync later | Optional cross-device state | Both offline apps functional, explicit authorization and conflict/security/recovery design |

Accept each update as a useful complete slice. Do not withhold it for unrelated advanced tracks. Plan exact signatures only for dependency-ready work, rather than freezing unstable future interfaces.

## Reusable research gauntlet prompt

This prompt authorizes assessment/research/planning. Implementation authority comes from the surrounding user instruction and current repository rules.

~~~text
Audit pemunson90-cmd/Command-Library for Pat. Produce evidence-backed decisions and a build plan.

1. Fetch main and the live continuation named by current state. Read AGENTS, CONTINUE, state/ORCHESTRATOR and exact next task. Record heads/time/access limits. Preserve unpublished work and current ownership. Read D20/D26/D30/D31 and the export clarification before classifying scope.
2. Enumerate every task, requirement, parity row, screen and production component. Reconcile registry text with actual AppGraph/adapters/UI/current evidence. Contracts, schemas, tests and module registration do not alone establish product functionality. Dialogs/sheets can satisfy workflows without dedicated routes.
3. Record six dimensions independently: backend, actual app wiring, assembled host proof, executed native proof, physical target proof and release readiness. Classify absent/partial/integrated/unverified/blocked/approved-later/approved-skip honestly. Split mixed first/later clauses; never change unknown into pass.
4. Inventory branches, issues, PRs, held drafts and CI. Check tree/patch/semantic equivalence before declaring non-ancestor work missing. Distinguish published source from inaccessible stopped-session state. Do not merge, delete or close merely to tidy the list.
5. Build a deduplicated gap ledger: exact SHA/path/line, user impact, scope authority, missing behavior or proof, dependencies, smallest fix/probe, acceptance test, owner and next action. Find unimplemented components without TODO markers and stale evidence/disconnected consumers.
6. Research only uncertainties that alter an implementation or acceptance decision. Use current official primary sources and authentic permitted fixtures. Record URL/revision/date/applicability. Separate documentation contract, observed access, historical report, fresh execution, inference and proposal. Never invent counts, permissions, provider eligibility, billing or test outcomes.
7. Attack proposed choices with malformed/partial/unknown inputs, cancellation, stale revisions/generations, revocation, process death, migration, partial file writes and accessibility. Choose probes capable of falsifying the choice. Apply independent review where the active task requires it for durability/security/source/public-contract boundaries.
8. Recurse only on consequential uncertainties. Each child names the parent assumption, competing hypotheses, smallest discriminating probe and engineering consequence. Default depth three; deeper needs a concrete unresolved dependency. Stop when actionable/executable/concretely blocked, keeping exact block evidence and permitted alternatives.
9. Recommend the shortest chain to the complete signed D31 Fold7 release and a separate full retained horizon. Keep optional providers/variants/optimizer/non-target tails off the release gate. Never fabricate percentages/timelines.
10. Deliver a concise verdict, exact coverage limits, exhaustive registered gap/requirement/parity/screen ledger, research packets/stopping rules, next steps/horizon, actual commands/results and only genuinely material unresolved decisions. Save durable artifacts and one small resumable next action. No public release, charged calls, access-policy bypass, secret disclosure or invented human/device acceptance.
~~~

## Reusable moonshot prompt

This explores proposals; they are not automatically approved features or release blockers.

~~~text
Use the current evidence-backed Command Library audit and Pat's actual goals to explore an unusually capable personal offline Magic workbench over the long horizon. Preserve Kotlin Android v1, D31, separate Windows v2 and later authorized sync. Do not delay first release.

Start with user jobs: organize/brew a deck, understand mana/roles/tradeoffs, know owned versus missing cards, play/resume a real game, and preserve trusted local state across future tools. Ground ideas in existing code/schema/source permissions.

Develop three genuinely different ambitious directions, not cosmetic variants. Possible lenses: a local deck research notebook linking tags/facts/versions/testable what-if edits; an explainable constrained brewing engine exposing evidence/unknowns/cost/reversible patches; a game-to-library learning loop linking deck versions and deliberate playtest/results without causal overclaiming. Replace these if evidence supports better directions.

For each: give a concrete five-minute user story, new capability, reusable code, exact missing systems, data-rights/offline needs, correctness/privacy risks, dependency order, resource hypotheses, smallest vertical prototype, falsifiable success metric, kill criterion and useful fallback artifact.

Red-team double-counted physical cards, stale facts, false certainty, recommendation feedback loops, inferred tags overriding manual intent, irreversible migration, hallucination, hidden network/spend and inaccessible complexity. Separate deterministic checks, heuristics and model suggestions. Suggested edits must be previewed, revalidated and reversible.

Rank qualitatively by user value, existing-code leverage, research uncertainty, maintenance burden and cost to first useful prototype. State what evidence reverses the ranking. Choose one moonshot and one bounded disposable experiment capable of disproving its central assumption. Do not scaffold a second application or freeze every future interface.

Return a compact vision, evidence/dependency comparison, experiment card, promotion/abandonment criteria and map back to existing retained tasks or explicitly new proposed scope. Keep first-release commitments, existing later obligations and speculation visibly distinct.
~~~

## Appendix conventions

The following generated appendices preserve every current registered task, requirement and parity row, plus all published branch heads. Source observations are copied fully and may contain superseded statements; findings above explicitly identify known stale rows. Whole-parent acceptance stays open even when substantial subsets work. This is traceability, not a claim of fresh exhaustive behavioral testing.

## Appendix A: all 35 execution tasks

### data-finish

Scope: **mixed**. Status: planned. Requirements: A01, A02, A06.

Outcome: Finish source-specific Full English/rulings acquisition, resume/background recovery and atomic activation; additional-language explicit consent.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/catalog/**, android-app/app/**, android-app/feature/shell/**.

Verification target: actual source bytes/hash/counts; interruption/reopen/consent; real Room/native acquisition journey

**Historical/current source-recorded observation:** LocalAcquisition, LocalBulkCatalog, AndroidCatalogServices, AppEnglishCatalogAccess, DownloadsController; local reviewed tests; live Scryfall policy denied; no full-feed/device acceptance

### rules-finish

Scope: **mixed**. Status: planned. Requirements: A04.

Outcome: Complete permitted official rules acquisition/import, offline glossary/crosslinks/keyword+ruling journey.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/rules/**, android-app/feature/shell/**.

Verification target: genuine corpus reacquisition and exact counts; invalid corpus rollback; offline links/native UI

**Historical/current source-recorded observation:** LocalRules/RulesController, exact glossary and rulings UI; earlier genuine corpus report scoped; private input no longer present

### query-execution

Scope: **mixed**. Status: typed_catalog_host_integrated_acceptance_open. Requirements: A07, A08.

Outcome: Pin executable AST truth/face/printing/unknown semantics, implement indexed generation-bound local execution and autocomplete.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/search/**, android-app/feature/catalog/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: reference-vs-SQL differential fixtures; unknown/NOT/face/printing/cursor cancellation; real schema9; full corpus P02-P04

**Historical/current source-recorded observation:** Actual bounded compiler/one-transaction generation-pinned reader and explicit preparation/advanced-query app UI integrated. Source1c81bfa39tests5XML0f/e/s; unchanged production source5bef3ae actual app journey1test0f/e/s after fixture-only repairs. Independent SOURCE ACK; scoped runtime readback. INDEXED-QUERY-APP-INTEGRATION-20261015.md preserves compile/SQL/fixture failure lineage and source-equivalence. Contextual owned/in-deck/price, full-corpus/native/physical/fullA07-A08 remain open.

### user-storage-contracts

Scope: **mixed**. Status: planned. Requirements: B01, B02, B05, B10, C01, I02.

Outcome: Review minimal durable deck/inventory codecs, revision/undo/version contracts and additive schema ordering.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/core/model/**, android-app/data/storage/**, android-app/database/registry/**.

Verification target: independent durability review; actual migration old/new preservation and failure atomicity

**Historical/current source-recorded observation:** Deck domain/CLDK1 exact-source ACK and integrated codec2ae5f55; schema10 adds3deck tables with35oldentities unchanged, actualhostRoom fresh/9upgrade checked. LocalDeckLibrary completeport reviewed; repository notyet implemented, inventory planned. No complete persistence acceptance.

### deck-edits

Scope: **first_release**. Status: producer_integrated_requirement_acceptance_open. Requirements: B01, B02, B05, B10.

Outcome: Pure atomic edit proposal and entry-identity diff using accepted DTOs; no fabricated repository.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/decks/**.

Verification target: focused identity/order/quantity/atomic rejection/stale revision/diff fixtures

**Historical/current source-recorded observation:** Pure DeckPatchReducer and DeckEntryDiff integrated/reviewed; f9fc8d4 adds primer/category registry/reorder with19deck tests. Full document diff125fb83 integrated/reviewed, now34decktests includingrequestfixtures; RestoreVersion/ApplyPackage and complete downstream acceptance remain open.

### deck-persistence

Scope: **first_release**. Status: primitive_durability_integrated_acceptance_open. Requirements: B01, B02, B05, B07, B10.

Outcome: Real create/list/load/edit/autosave/grouped undo/duplicate/version/restore/library repository.

Producer prerequisites: user-storage-contracts, deck-edits. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/decks/**, android-app/data/storage/**.

Verification target: real SQLite CAS/restart/grouped undo/immutable version/pre-restore recovery; cancellations

**Historical/current source-recorded observation:** Real LocalDeckLibrary/SqlLocalDeckEdits/SqlLocalDeckVersions implemented and actual AppGraph bound over current schema15; complete immutable graphs, persistent grouped undo, named capture/list/load/preview/restore and enumerable direct recovery. Whole package copy/undo preserve/remap complete references; primitive package APPLY remains restricted. Namedversion assembled exactsource/runtimeACK653baseline+27overlappingfocused tests; NAMED-VERSION-APP-INTEGRATION-20261015.md preserves scope/failure lineage/nativecompiledONLY. Shared primitive transaction extraction3bca390 source/runtimeACK29SQLtests. Raw draft pure planner/CLDF codec accepted86cf3f5/140tests, exact additive schema15ce13ecf/17tests, all6namespace guards a6731fe/191tests, request/metadataframes243936b/117tests with scoped source/runtimeACK. Complete SqlLocalDeckDrafts producer45de882b integrated5807a11e; independent source/runtimeACK248tests15XML over actual bundledSQLite. Retained actor d2df02b integratedf7427f8d, independent94hosttests4XML. Actual AppGraph/Room/UI wiring candidatea4fddd9 assembled149tests18XML at558a028, but source HOLD found4consumer gaps. Clean hidden successor refresh878546a integrateda94fbe8 sourceACK/10newtestsnot_run; root repairs ADD staging, readRetry and explicit recovery confirmation with real app+Compose regressions pending combined gate. Native/Fold7 unfinished. Advanced library query/package editing and full native/Fold7 acceptance remain open. Durable draft consumer SOURCE/RUNTIME ACK atb653063: reviewed four assembled HOLDs closed within hostscope. Attempt2 actual154shell/1testdisplayphasefailure preserved; source-equivalent production+reviewedtestonly correction attempt3 actual16/8XML allpass includesrealAdd+postundo successor/Room/5dialog, instrumentationcompiledONLY. Report docs/verification/decks/DURABLE-DRAFT-APP-INTEGRATION-20261015.md; matchingpinnedpickerproofrefresh sourceonly, native/Fold7/fullfeatureopen.

### deck-ui

Scope: **first_release**. Status: durable_editor_host_integrated_native_feature_acceptance_open. Requirements: B01, B02, B05, B07, B10.

Outcome: S05-S07/S11 actual deck library/editor/version workflow, categories/notes/primer/order/views, accessible bulk editing.

Producer prerequisites: deck-persistence. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/shell/**, android-app/app/**.

Verification target: create/edit/version/restore/restart; externalized strings/keyboard/200%/resize; native UI

**Historical/current source-recorded observation:** Actual root library/editor/S11 checkpoint-full-diff-preview-restore-recovery workflow integrated1bed55c; pending Close/Back admission guard repaired/exactsource-runtimeACK, navbar/draft/ack invalidation exercised. NAMED-VERSION-APP-INTEGRATION-20261015.md and DURABLE-DECK-EDITOR-INTEGRATION-20261015.md retain actual evidence. Actual raw-draft actor/store/UI/AppGraph binding is integrated and host-verified; full S05/S06/S11 acceptance including autosave/background native timing, package editing/CSV/deck bulk, native/Fold7/release remains open. Historical firstcandidatea4fddd9 passed149hosttests18XML at558a028 but4assembled source gaps held acceptance; successor878546a integrateda94fbe8 and root UI/Add/undo-successor repairs were subsequently verified as recorded below. No native/device or full feature acceptance. Durable draft consumer SOURCE/RUNTIME ACK atb653063: reviewed four assembled HOLDs closed within hostscope. Attempt2 actual154shell/1testdisplayphasefailure preserved; source-equivalent production+reviewedtestonly correction attempt3 actual16/8XML allpass includesrealAdd+postundo successor/Room/5dialog, instrumentationcompiledONLY. Report docs/verification/decks/DURABLE-DRAFT-APP-INTEGRATION-20261015.md; matchingpinnedpickerproofrefresh sourceonly, native/Fold7/fullfeatureopen. RequiredD31 reusablemanualpool/multilabelbulk layer accepted12c38027/treeeee00622c:SOURCE/shared/runtimeACK147cases12XML0f/e/s14s803pins (143reused4fresh). Realpool/Room/actualUIcreate/search/rename/order/retire/restore/bulkassign/conservation/undo/recreation/2nddeckreuse; reportMANUAL-TAGS-APP-INTEGRATION-20261016.md. FullB02/defaults/suppression/provenance/decklocalcategorymanagement/analysis/nativeFold7release remainopen.

### packages

Scope: **retained_later**. Status: planned. Requirements: B09.

Outcome: Immutable packages, copied/linked contribution lineage, preview update/remove and undo.

Producer prerequisites: deck-persistence. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/decks/**, android-app/feature/shell/**.

Verification target: overlapping same-card independent quantity conservation; copied isolation; restart/restore

**Historical/current source-recorded observation:** Package DTOs only; do not use generic reducer rejection as feature implementation

### collection

Scope: **first_release**. Status: inventory_ui_integrated_acceptance_open. Requirements: C01, B10.

Outcome: Inventory CRUD/list/mark-owned/bulk preview/apply/undo preserving exact lots and foreign unresolved identities.

Producer prerequisites: user-storage-contracts. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/collection/**, android-app/feature/shell/**, android-app/app/**.

Verification target: real DB revision/atomic bulk/ineligible rows/nonnegative/unknown/no language transfer/restart

**Historical/current source-recorded observation:** Complete projector/codecs/schema13/SqlLocalInventory/actual AppGraph and existing collection workflow integrated. Mixed bulk Keep/Set/range/eligibility/fullrowpreview/atomicapply-undo integratedc23c033/abad49a plus rootgenerationwiring andactualMainActivity/AppGraph/Room journey; independent exactsource/runtimeACK300assembledtests43XML, producer54overlap+lint0errors1inherited. See COLLECTION-BULK-APP-INTEGRATION-20261015.md. ActualRoom storageprobe7543a4c preserveszero/unknown/unselected/undo/replay/reopen. Full C01/B10/S12 CSV and remainingacceptance/native/Fold7/performance remain open. Shared retained inventory ownership gateec913ad actual2pass ACK; existing collection fullACK/Undo/rejection/freshrevision+timestamp floors and read-onlyknownretry integrated6b2dd70 independentlySOURCE/RUNTIMEACK59tests4XML; earlier65/9failurelineage and4separateactualapp/Roompass preserved. Report INVENTORY-MUTATION-OWNER-20261015.md. Mark-owned producer2f9a412 integrated8f9eaa1/source04ACK; accepted65bffe6 actual38faithful-port tests2XML0f/e/s1m14, prior source-only HOLDs retained. Two READY-reviewed isolated consumers claimed738d24b: dialog/strings/hostUI4NEWpaths and actualAppGraphjourneys2NEWpaths. Rootsharedfloor/compositiond49a460+globalinset7ef2e1f and consumers19f6742/ebaaffc integrated, boundedHOSTworkflow SOURCE/RUNTIMEACK4ed0c9c/treeb7601daf: focused5UI+2actualMainActivity/AppGraph/SqlInventory/Room=7tests2XML0f/e/s2m15, nativeKotlincompiledONLY. Prior115/2failed preserved; all64Collectionincl5newfloor/38markcontracts/2gate/all6app passed separately, remaining2UItestharness detachedMaterialcallbacks repaired via actualproductionactionfactories8b164fd, all5UI nowpass. Report DECK-MARK-OWNED-APP-INTEGRATION-20261015.md; canonicalconsumerclaimsreleased. No fullC01/native/Fold7/release acceptance. Native/fullC01open. D31/Pat2026-10-16 ordinary plain-text export bounded host accepted2432ae54/treecb4e8836:25cases5XML0f/e/s21s847pins,10reused15fresh, SOURCE/shared/runtimeACK. Actual12lots66copies/fullcapturedfile/privateoff+optin/SAF-PFD/providerfaults/noappdatawrites; PLAIN-TEXT-EXPORT-APP-INTEGRATION-20261016.md. CSV/richer later, no fullcollection/native/releaseclaim.

### pricing

Scope: **mixed**. Status: planned. Requirements: C02, C03, C04.

Outcome: Exact source/unit/statistic quote snapshots, owned-once allocation, market/owned/acquisition totals and policy UI.

Producer prerequisites: collection. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/pricing/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: q4/o2/USD3=6; fully-owned missing quote=0; unowned unknown; shared-lot conservation; no TIX FX; source terms

**Historical/current source-recorded observation:** Actual LocalOwnedQuantityAllocator integratedd1b743b fromdee85cd with independent Astra source/evidenceACK:25tests2XML0fail/error/skip; root assembled gate passed including real stored deck/inventory allocation. Complete bounded purequantitysubset independently of collectionCRUD. Sourceprobe0e0ec99 and designowned-quantity-boundary define pin/unknown/IGNORE/conservation/determinism/finitework. Full provider/statistic selection, storedpins/pricedplan/FX/optimizer/UI stillopen. ExactDecimalPolicy.multiply alreadyexists; do notduplicate.

### optimizer

Scope: **retained_later**. Status: planned. Requirements: B11.

Outcome: Deterministic eligible printing optimization preview and atomic versioned apply/undo.

Producer prerequisites: pricing, deck-persistence. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/pricing/**, android-app/feature/decks/**, android-app/feature/shell/**.

Verification target: source/finish/language/pinning/quantity and unknown optimality fixtures

**Historical/current source-recorded observation:** DTOs only

### analysis

Scope: **first_release**. Status: baseline_statistics_host_integrated_acceptance_open. Requirements: B03.

Outcome: Catalog-backed curve/pips/source/type/average/token analysis with explicit face/X/unknown policies and S09.

Producer prerequisites: none declared. Acceptance prerequisites: deck-ui.

Affected paths: android-app/core/model/**, android-app/feature/decks/**, android-app/feature/shell/**.

Verification target: known multi-face/hybrid/X/conditional production fixtures; textual chart summary

**Historical/current source-recorded observation:** D31 baseline tag-aware statistics bounded host accepteda1b42611/tree3e0f6a20:50cases6XML0f/e/s12s823pins,48unchangednonapp+2freshactualapp, independent SOURCE/shared/runtime ACK. Real installed source facts, weighted MV/types/exact cost alternatives/qualified land production/token references/manual tag AND-OR/board-entry filters and no-write actual AppGraph/MainActivity/Room journey. See DECK-STATISTICS-APP-INTEGRATION-20261016.md. Held source/fixture failure lineage retained, all producer locks released. FullB03/advanced analysis/recommendations/native/Fold7/release acceptance remains open.

### playtest

Scope: **retained_later**. Status: calculator_host_integrated_playtest_acceptance_open. Requirements: B04, B12.

Outcome: Exact probability/opening hands/London mulligan/manual goldfish/interactions replay/reset/undo with persisted scenarios.

Producer prerequisites: deck-persistence. Acceptance prerequisites: deck-ui.

Affected paths: android-app/feature/decks/**, android-app/feature/shell/**.

Verification target: small exact reference distributions; every action conserves instances; seeded tests/fresh ordinary RNG; restart/group undo

**Historical/current source-recorded observation:** Actual retained draw calculator HOSTintegrated b9b394b/Androidtree68bad0611d4df093cfe9cd2555d619cb38d7d1a9:11tests4XML0f/e/s1m34,666pinsfrozen,6exactmath+3controller+1actualAppGraph/Room calculator+1overlapping exportregression. Exact counts/explicit bounds/rounding, MAINonlyinitialpopulation/invalidcorrection/configrecreate/read-onlyconservation. Report DRAW-PROBABILITY-APP-INTEGRATION-20261015.md. Sixpoint mathreview/rootconsumerSOURCEACK; nativecompiledONLY. Openinghand/mulligan/manualzones/scenariopersistence/replay/undo/fullB04/native/Fold7 acceptance remainopen.

### validation

Scope: **mixed**. Status: basic_commander_host_integrated_acceptance_open. Requirements: B06.

Outcome: Versioned all-format source registry and local constraints, manual/unknown evidence plus live deck warnings.

Producer prerequisites: none declared. Acceptance prerequisites: deck-ui, data-finish, rules-finish.

Affected paths: android-app/feature/validation/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: known good/bad/unknown per actual format; basic/text exceptions/pairs/companion/limited pools; no false legal

**Historical/current source-recorded observation:** Actual saved-deck user-defined board-size checks integrated:bounded helper10tests at4e1adbe; unchanged helper plus retained controller3/actualapp1 atd471e34 pass. SOURCE ACK; original shell helper-name collision and source-only inputrejection HOLD preserved. BOARD-SIZE-CHECKS-APP-INTEGRATION-20261015.md. Official/versioned registry/cardlegality/remaining predicates and persisted custom definitions/native/fullB06 remain open. D31 explicit basic Commander checks bounded host accepted8a1479b4/treed9f09ccd:63cases9XML0f/e/s23s838pins,26reused37fresh, independent SOURCE/shared/runtime ACK. Actual source consensus/qualified basic checks/unknown-versus-review/definite count-source issues/explicitformat/recreation/no datawrites. BASIC-LEGALITY-APP-INTEGRATION-20261016.md preserves source holds/failure/diagnostic/page100/versionfix evidence. Full generic formats/exceptions/native/release open.

### brackets

Scope: **retained_later**. Status: planned. Requirements: E01, E02.

Outcome: Versioned reconciled criteria/Game Changers/combos; confidence/intent/evidence/target assessment and download/UI.

Producer prerequisites: validation. Acceptance prerequisites: data-finish, deck-ui.

Affected paths: android-app/feature/brackets/**, android-app/feature/shell/**.

Verification target: authentic set reconciliation/conflict retains prior; unknown intent/no official certainty; constrained targets

**Historical/current source-recorded observation:** Ports only; source evidence E03 required, not a deferral

### recommendations

Scope: **retained_later**. Status: planned. Requirements: D01, D02.

Outcome: Offline role/enabler/payoff/curve/identity recommendations with overrides, all budget levels, explainable patch preview/apply.

Producer prerequisites: query-context, pricing, validation, brackets, analysis. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/recommendations/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: exact budget equality/unknown exclusion/owned toggle; override persistence; stale fences; apply revalidation

**Historical/current source-recorded observation:** Ports only; per-card/pass/deck caps need explicit reviewed contract

### links

Scope: **mixed**. Status: producer_integrated_requirement_acceptance_open. Requirements: F01.

Outcome: Safe complete supplied-link/template resolver first; evidence-backed default target adapters/settings and card-detail launch follow.

Producer prerequisites: none declared. Acceptance prerequisites: links-ui.

Affected paths: android-app/feature/links/**, android-app/feature/shell/**, android-app/app/**.

Verification target: strict http(s)/origin/encoding/unknown tokens/missing IDs/selected printing/unsafe authority fixtures; actual UI launch; source E05; atomic per-target batch outcome, no silent omission; UI one target per call

**Historical/current source-recorded observation:** CardLinks port only; R17 route HTTP behavior UNVERIFIED; no guessed seller shortcuts; pilot real producer integrated/source-reviewed; see PARALLEL-PILOT-LOCAL-20261015.md; downstream complete acceptance open

### life-source-variants

Scope: **retained_later**. Status: planned. Requirements: G12.

Outcome: Resolve authentic Planechase/Archenemy/Bounty instructions/card data/rights; persist imported/custom execution definitions and all required managers.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/life/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: authentic each-module draw/action/reset/undo/restart; rights review E13

**Historical/current source-recorded observation:** Custom manifest factory/domain/persistence exists; named-source authority remains unresolved; root life lock active

### life-service-ui

Scope: **mixed**. Status: ordinary_table_host_integrated_acceptance_open. Requirements: G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11, G13.

Outcome: Complete retained game service platform/DI/setup/live tools/history/results, clocks/alerts and linked statistics.

Producer prerequisites: none declared. Acceptance prerequisites: deck-persistence, images.

Affected paths: android-app/feature/life/**, android-app/feature/shell/**, android-app/app/**.

Verification target: create/play/hold/group undo/checkpoint/restart/finish/reopen/history/unique result;1-6 seats; modern native process kill; physical fold/alert evidence

**Historical/current source-recorded observation:** Real persisted domain/service/clock/random providers; profiles actual UI wired; GameService HOLD/unbound until named family completeness; native probes compile only D31 removes ordinarytable variantdependency. ExistingrealSqlPersistedGameService bound24dfc881 SOURCEACK withprocessstableclock/identity/no constructorIO, noUIyet; isolatedcontroller/tableM1+NEW5 TAKEN exactsamebase canonicalpacket/lock. No G01-G14/fullsource/nativeclaim. Ordinary life host accepted186aa4e4/tree766193f3:28 fresh cases4XML0f/e/s24s815pins, independent SOURCE/shared/runtime ACK. Actual MainActivity/AppGraph/Room proves manual multiplayer setup, life and practical counters, commander damage grouped with life, one undo, recreation, saved resume, draw/reopen and unrelated-data conservation. See ORDINARY-LIFE-APP-INTEGRATION-20261016.md. Specialty modes decoupled; OS process-death, basic timing, fullG07, device and release acceptance remain open.

### profile-game-transfer

Scope: **retained_later**. Status: planned. Requirements: G14.

Outcome: Privacy-reviewed standalone profile and public game CSV/JSON exchange, collision preview/copy default and unique result reconciliation.

Producer prerequisites: life-service-ui, transfer-codecs. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/transfer/**, android-app/feature/shell/**.

Verification target: roundtrip/preferences/art refs/no private defaults/no download/duplicate result; CSV loss preview

**Historical/current source-recorded observation:** Internal bounded CLIF codec is not public exchange

### transfer-codecs

Scope: **mixed**. Status: application_text_codec_integrated_acceptance_open. Requirements: B08, I01.

Outcome: Bounded authentic text/Arena/vendor CSV and stable native transfer codecs, unresolved preview, loss-aware exports/guided retrieval.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/transfer/**, android-app/core/model/**.

Verification target: authentic E04 fixtures; multiline/BOM/quotes/collector numbers/oversize/cancel/unknown-version; source terms before URL retrieval

**Historical/current source-recorded observation:** Application-owned PlainTextDeckListCodec aa00e03→287a552 SOURCE/RUNTIMEACK26puretests. Retained importer/boundedinput/actualcodec-validator-Room boundary c69aae7 SOURCE/RUNTIMEACK35tests3XML. Actualfileimport HOST nowacceptedcbff7ac; see transfer-ui and PLAIN-DECK-IMPORT-APP-INTEGRATION-20261015.md. Application-ownedformat ONLY; authenticvendorfixtures/CSV/stablenativeinterchange/aggregateTransferService remainopen. No fullB08/I01/native acceptance.

### transfer-ui

Scope: **mixed**. Status: text_import_export_host_integrated_acceptance_open. Requirements: B08, I01.

Outcome: Real file/paste/mapping/identity preview, atomic selected deck/collection apply, export/share feedback.

Producer prerequisites: transfer-codecs, deck-persistence, collection. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/transfer/**, android-app/feature/shell/**, android-app/app/**.

Verification target: all represented identities/boards/quantity retained; unresolved editable; cancel none/commit all; authentic round trips

**Historical/current source-recorded observation:** Boundedselected-file→physicalrowreview→newunresolveddeck actualHOST acceptedcbff7ac/tree dc6b48199d1718cf00842191bba826996e888086. Finalfocused17tests2XML0f/e/s1m47 =2actualMainActivity/provider/registry/AppGraph/Room journeys +15newunboundselected-documentwriter tests, bothnativeKotlincompiledONLY/651pinsfrozen/independentSOURCE-RUNTIMEACKscopes. Exactrowtext/visibility, duplicates/boards/quantities/und identity, invalidnosubset, hidden-draftrefusal/discard/cleanadmission, picker cancellation/recreation, onecreate/conservation/independentRoom/list/detail verified. Earlier44/1failed retained;39shell+3otherapp passes unchanged/independentlysourceequivalent; overlappingappcaseNOTadditive. Report PLAIN-DECK-IMPORT-APP-INTEGRATION-20261015.md. ImportUI/app/outputclaimsreleased/cleanoriginalsretained. Outputwriter now bound to actual retained export app workflow; export integration evidence below. Paste/mapping/resolution/vendorCSV/collectiontransfer/backup/nativeinterchange/fullB08-I01/native/Fold7/release remainopen. Complete retainedexportproducer9086017→436be32 plusrootactualVMfactorycomposition andindependent7boardcodec-validator-Roomoracle HOSTacceptedb545f41:30tests2XML0f/e/s1m18/655pins,29faithfulcontroller+1realcompatibility. Report PLAIN-DECK-EXPORT-BOUNDARY-20261015.md. Actualexportscreen/CreateDocument/provider-PFD/controller-writer/AppGraph/Room HOSTintegrated987c886:9tests3XML0f/e/s2m48; legacyeditoradmissionguard4b6a8a6 sourceACK/postguard2actualapp0f/e/s1m10,659pinsfrozen. Runs overlap; no11uniqueclaim. Full81losses/229bytes/all7boards/recreate/revokedpicker/partial-openfault/sameURIrefusal/newdestination/storeconservation. Report PLAIN-DECK-EXPORT-APP-INTEGRATION-20261015.md. Claimsreleased; nativecompiledONLY/fullparityacceptanceopen.

### backup-restore

Scope: **retained_later**. Status: planned. Requirements: I01, I02.

Outcome: One-file validated full user archive, preview/recovery and atomic selected restore excluding secrets/consent activation.

Producer prerequisites: transfer-ui, profile-game-transfer, ai-history, user-storage-contracts. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/transfer/**, android-app/data/storage/**, android-app/feature/shell/**.

Verification target: byte/hash/identity integrity; traversal/zip bomb/interrupt stage+commit; complete old/new; no secrets/download side effects

**Historical/current source-recorded observation:** No backup adapter; schema includes no deck/inventory/AI state yet; partial serializer work may proceed as each boundary stabilizes

### qr-sharing

Scope: **retained_later**. Status: planned. Requirements: B13.

Outcome: Privacy preview, selected-field image export and hashed single/multi-frame local QR image/camera import.

Producer prerequisites: transfer-codecs, deck-persistence. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/transfer/**, android-app/feature/shell/**, android-app/app/**.

Verification target: E13 real native encoder/scanner roundtrip; missing/malformed/oversized frames; no silent card loss

**Historical/current source-recorded observation:** ShareManifest/QrFrame DTOs only; native dependency trial needed before pin

### query-context

Scope: **mixed**. Status: planned. Requirements: A07, A08.

Outcome: Owned/in-deck/price context filters against real exact revision/source/unit snapshots.

Producer prerequisites: query-execution, deck-persistence, collection. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/search/**, android-app/feature/shell/**, android-app/app/**.

Verification target: revision/generation fences; unknown vs zero; source-qualified IDs; cancellation; no fake empty context

**Historical/current source-recorded observation:** Parser clauses only; absent context must remain explicitly unsupported

### ai-prompts-grounding

Scope: **mixed**. Status: offline_prompt_host_integrated_acceptance_open. Requirements: H01, H04, H05, H09.

Outcome: All retained lab modes, versioned/editable prompt export/pasteback and deterministic local grounded suggestion validation.

Producer prerequisites: none declared. Acceptance prerequisites: deck-ui.

Affected paths: android-app/feature/ai/**, android-app/core/model/**, android-app/feature/shell/**.

Verification target: mode/roast intensity/template errors/provenance/unresolved vs invented refs; no silent apply; real deck path waits deck-persistence

**Historical/current source-recorded observation:** Reviewed additive OfflineDeckPrompts/four explicit OfflineLabMode API and actual saved-deck provider-free prompt/copy/paste/reference-detail workflow integrated. Engine19cd93917host tests; source-equivalent engine consumer87e0c81 tencontroller+oneactualAppGraph/Room/clipboard journey pass, independent SOURCE02/runtime ACK. OFFLINE-PROMPT-APP-INTEGRATION-20261015.md preserves source HOLDs/interruptedattempt/scoped evidence. Private opt-in/session overrides/unknownconstraint checks explicit; fullH01/H04/H05/H09, persistent overrides, providers/vault/history/Apply and native/physical acceptance open. Legacy LabMode aggregate remains unused, never silently aliased.

### vault

Scope: **mixed**. Status: in_progress. Requirements: H07.

Outcome: Android Keystore origin/provider scoped encrypted credentials, deletion/corruption and backup exclusions.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/data/vault/**, android-app/app/**.

Verification target: independent security review; native Keystore reinstall/corruption/origin/redaction; no secrets in logs or archive

**Historical/current source-recorded observation:** Actual scoped AndroidSecretVault production AES256-GCM/AndroidKeyStore/noBackup AtomicFile source and lazy AppGraph binding integratede3f0691; independent Astra SOURCE02/sharedCI ACK. Exact8 original28e44b3/base6601 verified clean/live. Host harness correctedfe0c142 SDK34 matching existing Java17 host setup after SDK36 initialization failure; focused sourceACK. Combinedfe0c142/tree092599a5 host15tests3XML0f/e/s2m28/714pins +lint/nativecompile PASS and independent Solmedium runtimeACK; Nativeattempts38799223425 ZIP and38801665400 missinglibpulse failedbeforetests; real API36 nativeKeystore run38802768872 exact29f7b3f4cases0f/e/s loggedXML independentlyACK; providerkeycoordinator/config host37casegate79f9a96 ACK. ActualnativeUI/min23/reinstall/backup/fullH07/security/release acceptance open.

### ai-providers

Scope: **retained_later**. Status: in_progress. Requirements: H02, H06, H08.

Outcome: Retained eight cloud/six local/custom provider dialects, manual model ID/listing, bounded read tools/stream/cancel/retry.

Producer prerequisites: vault, ai-prompts-grounding. Acceptance prerequisites: query-context, recommendations, ai-history.

Affected paths: android-app/feature/ai/**, android-app/data/network/**, android-app/feature/shell/**.

Verification target: authentic codec/error/tool/partial stream/cancel8calls50rows5s; no unsafe retry; actual eligibility unavailable shown

**Historical/current source-recorded observation:** Custom canonicalHTTPS OPENAI_CHAT text adapter, explicit exactprompt/destination/manualmodel/savedprofile-revision/private-data/possiblecharge/localhistory consent and durable coordinator actualAppGraph/UI integrated. Independently SOURCE/runtimeACK6519bff16b558356d5f1b3c62194c9b516525dcd Androidtree3cd772d0:79scopedhostcases11XML0f/e/s,69unchangedreused+10fresh/774pins. Durable PENDING before singlePOST, CAS/prefix/epoch, partial/final/cancel/uncertainoriginalID/noautomaticreplay; actualRoom/MainActivity proof synthetictransport and nativevault earlierseparate. PROVIDER-TEXT-APP-INTEGRATION-20261015.md preserves sourceHOLDs/config/cache/testproxyfailure lineage andexactreturns/drafts. Configuration79f9a96/history31bfe78 prioraccepted. No native/authenticprovider/billing/8cloud6localeligibility/fullH02-H08/releaseacceptance. Next bounded nativeAnthropic sourcefacts lookup fromR10; no chargedcalls, newdependencies or repins. NativeAnthropic boundedSOURCE/shared/runtimeACKd96da539/tree93b341e0:102hostcases14XML0f/e/s,23networkreused+79fresh/781pins. Explicitpositiveuserlimit/KNOWNversion/capturedGenerationRequest/transientscopedXApiKey/noautorereplay andactualRoom/PENDING/prefix/message_stop/usage12-4-read3/recreationnoreplay withsynthetictransport. ANTHROPIC-TEXT-APP-INTEGRATION-20261015.md preserves SOURCE01HOLDs/correctedSOURCE02/heldoriginalcf1e16d/exactreturns andnative/authentic/terms/H08componentretention/releaseholds. Next boundedResponses officialSDKsourcefacts.

### ai-history

Scope: **mixed**. Status: in_progress. Requirements: H08, H09.

Outcome: Persist per-deck conversations/status/partial replies/usage and prompt overrides with deletion and backup serializers.

Producer prerequisites: ai-prompts-grounding. Acceptance prerequisites: deck-ui.

Affected paths: android-app/feature/ai/**, android-app/data/storage/**, android-app/feature/shell/**.

Verification target: restart/interrupted vs final/deletion/stale deck/cancel/privacy/backup; actual provider path after ai-providers

**Historical/current source-recorded observation:** H09overridehostaccepted4ee30f7 report34tests; H08localhistory SOURCE/shared/runtimeACK31bfe7884db56b0f4b704e25a525ff7a08d6d2cf/tree553634f6:43hosttests9XML0f/e/s2m04/758pins. ActualRoom17to18 all50oldentities/physicalbytespreserved, explicitexactofflineprompt/reply/provenancecapture/reopen/recreation/perdeckbrowse/delete/privatepayloadclearing andpriorregressions. AI-HISTORY-APP-INTEGRATION-20261015.md preserves sourceHOLDs/compilefailure/fix/exactreturns/cleanoriginals/releasedclaims. Actualproviderstream/socket/native/backup/fullH08/Fold7/release remainsnamedconsumerwork; no fakeprovider or runtime Subsequent providertext6519bff1 boundedSOURCE/runtimeACK actualAppGraph/MainActivity/Room/clientcodec withsynthetictransport: PENDINGbeforePOST/STREAMINGprefix/COMPLETE/usageunknown/exactpromptprovenance/recreationnoreplay. Report PROVIDER-TEXT-APP-INTEGRATION-20261015.md; fullprovider/native/backup/Fold7/releaseholds unchanged. NativeAnthropic scopedhostd96da539 acceptedreportedinput12/output4/cacheRead3 withactualRoomandretainedUI/recreation no replay; inputtotalonlyallcomponentsknown, summedoverflowCORRUPT_DATA. Individualcachecreation/uncachedretention/fullH08/backup/native/releasestillheld.

### images

Scope: **mixed**. Status: planned. Requirements: A05, G08.

Outcome: Actual on-view bounded art cache and approved pinned resumable packs, readable life/card presentation.

Producer prerequisites: none declared. Acceptance prerequisites: data-finish, deck-ui.

Affected paths: android-app/feature/catalog/**, android-app/feature/shell/**, android-app/app/**.

Verification target: P06/P12 decoded/disk caps; pin survives eviction/no consent no pack; native image/cancel/offline fallback

**Historical/current source-recorded observation:** CardArtworkFrame is text fallback; Coil probe not production cache; G08 actual UI waits game path

### settings-onboarding

Scope: **mixed**. Status: planned. Requirements: K01, K02, A02, A03.

Outcome: Actual Full English first-run/offline path, consent/mode/retention/on-demand, module settings, notices and redacted diagnostics.

Producer prerequisites: data-finish. Acceptance prerequisites: deck-persistence, collection, images.

Affected paths: android-app/feature/settings/**, android-app/feature/catalog/**, android-app/feature/shell/**, android-app/app/**.

Verification target: Full/Lean switching interruption preserves all user state; no implicit foreign download; diagnostic redaction; actual offline notices

**Historical/current source-recorded observation:** DataStore theme/consent/removal requests and download UI exist; onboarding/FullLean consumption/removal coordinator incomplete; retention acceptance also needs deck-persistence/collection

### adaptive-input

Scope: **mixed**. Status: planned. Requirements: J01, J02, J03.

Outcome: Five width/height/posture safe-region layouts, preserved state, accessible keyboard/context/drag alternatives and shared tokens/resources.

Producer prerequisites: none declared. Acceptance prerequisites: deck-ui, life-service-ui, transfer-ui.

Affected paths: android-app/feature/shell/**, android-app/app/**.

Verification target: breakpoints +/-1/200%/compact height/fold/keyboard/focus/state; actual TalkBack/physical evidence separately

**Historical/current source-recorded observation:** Only3 WidthClass values; tokens/basic semantic controls/profile UI exist; no complete hinge/desktop acceptance; each feature must apply same tokens

### release-acceptance

Scope: **mixed**. Status: planned. Requirements: J03, J04, N01, N02, N03.

Outcome: Assembled complete retained user journeys, modern native remediation, physical Fold7/TalkBack/performance, canonical signed install/update.

Producer prerequisites: data-finish, rules-finish, query-context, deck-ui, collection, pricing, analysis, validation, links, life-service-ui, transfer-ui, images, settings-onboarding, adaptive-input, links-ui. Acceptance prerequisites: none separately declared.

Affected paths: android-app/**, docs/verification/**, .github/workflows/**.

Verification target: exact-head assembled checks; P01-P12 excluding D26 engines; source/secret/restore audit; physical screenshots/aesthetic review and canonical certificate; finishedAPKhold until passed

**Historical/current source-recorded observation:** K2/older PR21 accepted scoped evidence; frozen9c1573b native FAILED historical, local snapshot fix not native executed; no release claim

### engine-v1.1

Scope: **retained_later**. Status: approved_deferred_D26. Requirements: H03.

Outcome: On-device engines/runtime/model manager only, explicitly D26 deferred to1.1.

Producer prerequisites: none declared. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/inference/**.

Verification target: deferred P10/native benchmark/model lifecycle only

**Historical/current source-recorded observation:** No runtime/module; preserve retained other AI tasks

### links-ui

Scope: **mixed**. Status: planned. Requirements: F01.

Outcome: Bind actual CatalogPrintingLookup and links; ticketed selected-printing page/selection plus per-target unavailable/launch states in S03; persistent template editor requires reviewed settings addition.

Producer prerequisites: links. Acceptance prerequisites: none separately declared.

Affected paths: android-app/feature/shell/**, android-app/app/**, android-app/feature/settings/**, android-app/core/model/**.

Verification target: actual catalog→printing→selection→supplied links host/Compose checks; missing IDs visible; http(s) platform launch only explicit tap; preferences no secret/cross-origin bypass

**Historical/current source-recorded observation:** all consumers absent at baseline

## Appendix B: all 64 requirements

Registered acceptance state is not a new assertion of absence. See current source and observations for implemented subsets.

| ID | Capability | Scope | Screens | Task joins | Acceptance state |
|---|---|---|---|---|---|
| A01 | Full English catalog | mixed | S02,S03,S18 | data-finish | acceptance_open |
| A02 | Language consent | first_release | S01,S08,S18 | data-finish, settings-onboarding | acceptance_open |
| A03 | Storage modes | mixed | S01,S18 | settings-onboarding | acceptance_open |
| A04 | Offline rules | mixed | S03,S04,S18 | rules-finish | acceptance_open |
| A05 | Image packs/cache | mixed | S03,S18 | images | acceptance_open |
| A06 | Atomic updates | first_release | S18,S19 | data-finish | acceptance_open |
| A07 | Local query syntax | mixed | S02,S04 | query-execution, query-context | acceptance_open |
| A08 | Text search | first_release | S02,S04 | query-execution, query-context | acceptance_open |
| B01 | Deck boards/roles | first_release | S05,S06,S07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui | acceptance_open |
| B02 | Editing and organization | first_release | S05,S06,S07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui | acceptance_open |
| B03 | Deck analysis | first_release | S09 | analysis | acceptance_open |
| B04 | Draw/probability | retained_later | S09,S10 | playtest | acceptance_open |
| B05 | Versions/diff | first_release | S06,S11 | user-storage-contracts, deck-edits, deck-persistence, deck-ui | acceptance_open |
| B06 | All-format validation | mixed | S06,S09 | validation | acceptance_open |
| B07 | Native parity | mixed | S05-S11 | deck-persistence, deck-ui | acceptance_open |
| B08 | Deck imports/exports | mixed | S08,S17 | transfer-codecs, transfer-ui | acceptance_open |
| C01 | Inventory | first_release | S02,S08,S12 | user-storage-contracts, collection | acceptance_open |
| C02 | Acquisition n | mixed | S02,S03,S06,S12,S13,S16 | pricing | acceptance_open |
| C03 | Price source/currency | retained_later | S14,S18 | pricing | acceptance_open |
| C04 | Printing allocation/totals | mixed | S06,S12,S14 | pricing | acceptance_open |
| D01 | Budgets | retained_later | S13,S14 | recommendations | acceptance_open |
| D02 | Offline recommendations | retained_later | S13,S14 | recommendations | acceptance_open |
| E01 | Bracket rules data | retained_later | S03,S15,S18 | brackets | acceptance_open |
| E02 | Bracket estimates/targets | retained_later | S13,S15 | brackets | acceptance_open |
| F01 | External links | mixed | S03,S18 | links, links-ui | acceptance_open |
| G01 | Players/layout | mixed | S20,S21 | life-service-ui | acceptance_open |
| G02 | Life adjustments | first_release | S20,S21 | life-service-ui | acceptance_open |
| G03 | Commander damage | first_release | S21 | life-service-ui | acceptance_open |
| G04 | Counters | first_release | S21,S22 | life-service-ui | acceptance_open |
| G05 | Game designations | first_release | S21,S22 | life-service-ui | acceptance_open |
| G06 | Random tools | first_release | S22 | life-service-ui | acceptance_open |
| G07 | Timers | mixed | S21,S22 | life-service-ui | acceptance_open |
| G08 | Art/deck profiles | mixed | S20,S21 | life-service-ui, images | acceptance_open |
| G09 | Loss/end/stats | first_release | S21,S23 | life-service-ui | acceptance_open |
| G10 | Durability/undo/log | first_release | S21 | life-service-ui | acceptance_open |
| G11 | Life parity extras | mixed | S20-S23 | life-service-ui | acceptance_open |
| H01 | Modes/context | mixed | S16 | ai-prompts-grounding | acceptance_open |
| H02 | Cloud/custom providers | retained_later | S16,S24 | ai-providers | acceptance_open |
| H03 | On-device | retained_later | S16,S25 | engine-v1.1 | approved_deferred_D26 |
| H04 | Prompt export/pasteback | mixed | S16 | ai-prompts-grounding | acceptance_open |
| H05 | Grounding/reply validation | mixed | S16,S14 | ai-prompts-grounding | acceptance_open |
| H06 | Bounded tool calling | retained_later | S16,S24 | ai-providers | acceptance_open |
| H07 | Secrets | mixed | S24 | vault | acceptance_open |
| H08 | Streaming/history/cost | mixed | S16 | ai-providers, ai-history | acceptance_open |
| H09 | Prompts | mixed | S24 | ai-prompts-grounding, ai-history | acceptance_open |
| I01 | Lossless transfer | mixed | S08,S17 | transfer-codecs, transfer-ui, backup-restore | acceptance_open |
| I02 | Restore safety | mixed | S17 | user-storage-contracts, backup-restore | acceptance_open |
| J01 | Adaptive state | mixed | S01-S25 | adaptive-input | acceptance_open |
| J02 | Desktop input | mixed | S02-S25 | adaptive-input | acceptance_open |
| J03 | Accessibility/design | first_release | S01-S25 | adaptive-input, release-acceptance | acceptance_open |
| J04 | Performance/visual audit | mixed | S01-S25 | release-acceptance | acceptance_open |
| K01 | Onboarding/settings | mixed | S01,S18,S24 | settings-onboarding | acceptance_open |
| K02 | Attribution/diagnostics | first_release | S19 | settings-onboarding | acceptance_open |
| N01 | Architecture/CI gates | first_release | all | release-acceptance | acceptance_open |
| N02 | Network etiquette | first_release | S18 | release-acceptance | acceptance_open |
| N03 | Scope boundaries | first_release | all | release-acceptance | acceptance_open |
| B09 | Reusable packages | retained_later | S05,S06,S07,S08 | packages | acceptance_open |
| B10 | Multi-edit | first_release | S06,S07,S12 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, collection | acceptance_open |
| B11 | Printing optimizer | retained_later | S06,S14 | optimizer | acceptance_open |
| B12 | Interaction playtest | retained_later | S10 | playtest | acceptance_open |
| B13 | Local image/QR sharing | retained_later | S08,S17 | qr-sharing | acceptance_open |
| G12 | Variant modules | retained_later | S20,S21,S22 | life-source-variants | acceptance_open |
| G13 | Seat clocks/alerts | retained_later | S20,S21,S22 | life-service-ui | acceptance_open |
| G14 | Profile/game transport | retained_later | S17,S20,S23 | profile-game-transfer | acceptance_open |

## Appendix C: all 213 parity rows

The 19 explicit skips are scope decisions. All other rows inherit open whole-task acceptance, even where subsets work. Mixed rows need a D31 clause split.

| ID | Feature | Disposition | Scope | Requirements | Tasks |
|---|---|---|---|---|---|
| PX05-001 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-002 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-003 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-004 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-005 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-006 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-007 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-008 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-009 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-010 | [R-05](research/R-05-moxfield.md) | adapt | mixed | B07 | deck-persistence, deck-ui |
| PX05-011 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-012 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-013 | [R-05](research/R-05-moxfield.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-014 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-015 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-016 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-017 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-018 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-019 | [R-05](research/R-05-moxfield.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-020 | [R-05](research/R-05-moxfield.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-021 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-022 | [R-05](research/R-05-moxfield.md) | build | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX05-023 | [R-05](research/R-05-moxfield.md) | build | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX05-024 | [R-05](research/R-05-moxfield.md) | adapt | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX05-025 | [R-05](research/R-05-moxfield.md) | build | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX05-026 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX05-027 | [R-05](research/R-05-moxfield.md) | build | mixed | B06, B07 | deck-persistence, deck-ui, validation |
| PX05-028 | [R-05](research/R-05-moxfield.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX05-029 | [R-05](research/R-05-moxfield.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX05-030 | [R-05](research/R-05-moxfield.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX05-031 | [R-05](research/R-05-moxfield.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX05-032 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX05-033 | [R-05](research/R-05-moxfield.md) | build | first_release | B07 | deck-persistence, deck-ui |
| PX05-034 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B05, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-035 | [R-05](research/R-05-moxfield.md) | build | first_release | B05, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-036 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-037 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-038 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-039 | [R-05](research/R-05-moxfield.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-040 | [R-05](research/R-05-moxfield.md) | build | mixed | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-041 | [R-05](research/R-05-moxfield.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-042 | [R-05](research/R-05-moxfield.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-043 | [R-05](research/R-05-moxfield.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-044 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B08, I01 | approved skip |
| PX05-045 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-046 | [R-05](research/R-05-moxfield.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-047 | [R-05](research/R-05-moxfield.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX05-048 | [R-05](research/R-05-moxfield.md) | adapt | mixed | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX05-049 | [R-05](research/R-05-moxfield.md) | adapt | retained_later | E01, E02, B07 | deck-persistence, deck-ui, brackets |
| PX05-050 | [R-05](research/R-05-moxfield.md) | adapt | retained_later | B09 | packages |
| PX05-051 | [R-05](research/R-05-moxfield.md) | build | mixed | C01, C02, C03, C04, D01, B07 | user-storage-contracts, deck-persistence, deck-ui, collection, pricing, recommendations |
| PX05-052 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B07 | deck-persistence, deck-ui, collection, transfer-ui |
| PX05-053 | [R-05](research/R-05-moxfield.md) | adapt | retained_later | C01, C02, C03, C04, D01, B07 | user-storage-contracts, deck-persistence, deck-ui, collection, pricing, recommendations |
| PX05-054 | [R-05](research/R-05-moxfield.md) | adapt | mixed | B07 | deck-persistence, deck-ui |
| PX05-055 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B07 | approved skip |
| PX05-056 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B07 | approved skip |
| PX05-057 | [R-05](research/R-05-moxfield.md) | adapt | mixed | B07 | deck-persistence, deck-ui |
| PX05-058 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B07 | approved skip |
| PX05-059 | [R-05](research/R-05-moxfield.md) | adapt | retained_later | B07 | deck-persistence, deck-ui, recommendations |
| PX05-060 | [R-05](research/R-05-moxfield.md) | adapt | first_release | B07 | deck-persistence, deck-ui |
| PX05-061 | [R-05](research/R-05-moxfield.md) | build | first_release | A07, A08, B07 | query-execution, deck-persistence, deck-ui, query-context |
| PX05-062 | [R-05](research/R-05-moxfield.md) | adapt | mixed | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, query-execution |
| PX05-063 | [R-05](research/R-05-moxfield.md) | build | first_release | A04, B07 | rules-finish, deck-persistence, deck-ui |
| PX05-064 | [R-05](research/R-05-moxfield.md) | build | first_release | B07 | deck-persistence, deck-ui, adaptive-input |
| PX05-065 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B07 | approved skip |
| PX05-066 | [R-05](research/R-05-moxfield.md) | skip | existing_skip | B07 | approved skip |
| PX05-067 | [R-05](research/R-05-moxfield.md) | build | retained_later | C01, C02, C03, C04, D01, B07 | user-storage-contracts, deck-persistence, deck-ui, collection, pricing, recommendations |
| PX06-001 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-002 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-003 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-004 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-005 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-006 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-007 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-008 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-009 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-010 | [R-06](research/R-06-archidekt.md) | adapt | mixed | B07 | deck-persistence, deck-ui |
| PX06-011 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-012 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-013 | [R-06](research/R-06-archidekt.md) | build | first_release | B01, B06, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, validation |
| PX06-014 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-015 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-016 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-017 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-018 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-019 | [R-06](research/R-06-archidekt.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-020 | [R-06](research/R-06-archidekt.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-021 | [R-06](research/R-06-archidekt.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-022 | [R-06](research/R-06-archidekt.md) | build | retained_later | B07 | deck-persistence, deck-ui, playtest |
| PX06-023 | [R-06](research/R-06-archidekt.md) | build | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX06-024 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX06-025 | [R-06](research/R-06-archidekt.md) | build | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX06-026 | [R-06](research/R-06-archidekt.md) | build | first_release | B03, B07 | deck-persistence, deck-ui, analysis |
| PX06-027 | [R-06](research/R-06-archidekt.md) | build | mixed | B06, B07 | deck-persistence, deck-ui, validation |
| PX06-028 | [R-06](research/R-06-archidekt.md) | build | first_release | B07 | deck-persistence, deck-ui, validation |
| PX06-029 | [R-06](research/R-06-archidekt.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX06-030 | [R-06](research/R-06-archidekt.md) | build | first_release | B07 | deck-persistence, deck-ui, validation |
| PX06-031 | [R-06](research/R-06-archidekt.md) | build | first_release | B06, B07 | deck-persistence, deck-ui, validation |
| PX06-032 | [R-06](research/R-06-archidekt.md) | build | first_release | B07 | deck-persistence, deck-ui |
| PX06-033 | [R-06](research/R-06-archidekt.md) | build | first_release | B05, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-034 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B05, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-035 | [R-06](research/R-06-archidekt.md) | build | first_release | B07 | deck-persistence, deck-ui |
| PX06-036 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-037 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-038 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-039 | [R-06](research/R-06-archidekt.md) | build | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-040 | [R-06](research/R-06-archidekt.md) | build | mixed | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-041 | [R-06](research/R-06-archidekt.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-042 | [R-06](research/R-06-archidekt.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-043 | [R-06](research/R-06-archidekt.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-044 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-045 | [R-06](research/R-06-archidekt.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-046 | [R-06](research/R-06-archidekt.md) | build | first_release | B08, I01 | transfer-codecs, transfer-ui, backup-restore |
| PX06-047 | [R-06](research/R-06-archidekt.md) | adapt | mixed | B07 | deck-persistence, deck-ui |
| PX06-048 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B02, B07 | user-storage-contracts, deck-edits, deck-persistence, deck-ui |
| PX06-049 | [R-06](research/R-06-archidekt.md) | build | first_release | B10, C01 | user-storage-contracts, deck-edits, deck-persistence, deck-ui, collection |
| PX06-050 | [R-06](research/R-06-archidekt.md) | adapt | mixed | C01, C02, C03, C04, D01, B07 | user-storage-contracts, deck-persistence, deck-ui, collection, pricing, recommendations |
| PX06-051 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | C01, C02, C03, C04, D01, B07 | user-storage-contracts, deck-persistence, deck-ui, collection, pricing, recommendations |
| PX06-052 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | B11 | optimizer |
| PX06-053 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | E01, E02, B07 | deck-persistence, deck-ui, brackets |
| PX06-054 | [R-06](research/R-06-archidekt.md) | adapt | first_release | B07 | deck-persistence, deck-ui |
| PX06-055 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | B12 | playtest |
| PX06-056 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | B04, B07 | deck-persistence, deck-ui, playtest |
| PX06-057 | [R-06](research/R-06-archidekt.md) | build | first_release | B07 | deck-persistence, deck-ui |
| PX06-058 | [R-06](research/R-06-archidekt.md) | skip | existing_skip | B07 | approved skip |
| PX06-059 | [R-06](research/R-06-archidekt.md) | skip | existing_skip | B07 | approved skip |
| PX06-060 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | B13 | qr-sharing |
| PX06-061 | [R-06](research/R-06-archidekt.md) | adapt | mixed | B07 | deck-persistence, deck-ui, links, transfer-ui, pricing, links-ui |
| PX06-062 | [R-06](research/R-06-archidekt.md) | adapt | retained_later | D02, B07 | deck-persistence, deck-ui, recommendations |
| PX06-063 | [R-06](research/R-06-archidekt.md) | skip | existing_skip | B07 | approved skip |
| PX06-064 | [R-06](research/R-06-archidekt.md) | skip | existing_skip | B07 | approved skip |
| PX07-001 | [R-07](research/R-07-life-counters.md) | build | first_release | G01, G11 | life-service-ui |
| PX07-002 | [R-07](research/R-07-life-counters.md) | build | first_release | G01, G11 | life-service-ui |
| PX07-003 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G01, G11 | life-service-ui |
| PX07-004 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-005 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G02, G11 | life-service-ui |
| PX07-006 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-007 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-008 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-009 | [R-07](research/R-07-life-counters.md) | build | first_release | G03, G11 | life-service-ui |
| PX07-010 | [R-07](research/R-07-life-counters.md) | build | first_release | G03, G11 | life-service-ui |
| PX07-011 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G02, G11 | life-service-ui |
| PX07-012 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-013 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-014 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-015 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-016 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-017 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-018 | [R-07](research/R-07-life-counters.md) | build | first_release | G04, G11 | life-service-ui |
| PX07-019 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G04, G11 | life-service-ui |
| PX07-020 | [R-07](research/R-07-life-counters.md) | build | first_release | G05, G11 | life-service-ui |
| PX07-021 | [R-07](research/R-07-life-counters.md) | build | first_release | G05, G11 | life-service-ui |
| PX07-022 | [R-07](research/R-07-life-counters.md) | build | first_release | G05, G11 | life-service-ui |
| PX07-023 | [R-07](research/R-07-life-counters.md) | build | first_release | G05, G11 | life-service-ui |
| PX07-024 | [R-07](research/R-07-life-counters.md) | build | first_release | G06, G11 | life-service-ui |
| PX07-025 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G06, G11 | life-service-ui |
| PX07-026 | [R-07](research/R-07-life-counters.md) | build | first_release | G06, G11 | life-service-ui |
| PX07-027 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G06, G11 | life-service-ui |
| PX07-028 | [R-07](research/R-07-life-counters.md) | build | first_release | G06, G11 | life-service-ui |
| PX07-029 | [R-07](research/R-07-life-counters.md) | build | first_release | G07, G11 | life-service-ui |
| PX07-030 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G07, G11 | life-service-ui |
| PX07-031 | [R-07](research/R-07-life-counters.md) | build | first_release | G07, G11 | life-service-ui |
| PX07-032 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-033 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G08, G11 | life-service-ui, images |
| PX07-034 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G09, G11 | life-service-ui |
| PX07-035 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-036 | [R-07](research/R-07-life-counters.md) | build | first_release | G10, G11 | life-service-ui |
| PX07-037 | [R-07](research/R-07-life-counters.md) | build | first_release | G10, G11 | life-service-ui |
| PX07-038 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G10, G11 | life-service-ui |
| PX07-039 | [R-07](research/R-07-life-counters.md) | build | first_release | G10, G11 | life-service-ui |
| PX07-040 | [R-07](research/R-07-life-counters.md) | build | first_release | G01, G11 | life-service-ui |
| PX07-041 | [R-07](research/R-07-life-counters.md) | build | first_release | G01, G11 | life-service-ui |
| PX07-042 | [R-07](research/R-07-life-counters.md) | build | first_release | G02, G11 | life-service-ui |
| PX07-043 | [R-07](research/R-07-life-counters.md) | build | mixed | G01, G11 | life-service-ui |
| PX07-044 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G02, G11 | life-service-ui |
| PX07-045 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-046 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-047 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G07, G11 | life-service-ui |
| PX07-048 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G14 | profile-game-transfer |
| PX07-049 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G01, G11 | approved skip |
| PX07-050 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-051 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G08, G11 | life-service-ui, images |
| PX07-052 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G04, G11 | life-service-ui |
| PX07-053 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-054 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-055 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-056 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-057 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G02, G11 | life-service-ui |
| PX07-058 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-059 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-060 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G12 | life-source-variants |
| PX07-061 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G12 | life-source-variants |
| PX07-062 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G12 | life-source-variants |
| PX07-063 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G12 | life-source-variants |
| PX07-064 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G14 | profile-game-transfer |
| PX07-065 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-066 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G09, G11 | life-service-ui |
| PX07-067 | [R-07](research/R-07-life-counters.md) | adapt | retained_later | G13 | life-service-ui |
| PX07-068 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G01, G11 | life-service-ui |
| PX07-069 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G01, G11 | life-service-ui |
| PX07-070 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G08, G11 | life-service-ui, images |
| PX07-071 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-072 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G11 | life-service-ui |
| PX07-073 | [R-07](research/R-07-life-counters.md) | adapt | first_release | G06, G11 | life-service-ui |
| PX07-074 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G08, G11 | approved skip |
| PX07-075 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-076 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-077 | [R-07](research/R-07-life-counters.md) | adapt | mixed | G11 | life-service-ui, query-context, pricing, validation |
| PX07-078 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-079 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-080 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |
| PX07-081 | [R-07](research/R-07-life-counters.md) | build | retained_later | I02, G11 | user-storage-contracts, life-service-ui, backup-restore |
| PX07-082 | [R-07](research/R-07-life-counters.md) | skip | existing_skip | G11 | approved skip |

## Appendix D: all fetched named remote branches

Non-ancestor commits are reconciliation candidates, not proof of missing useful work; cherry-picked or corrected equivalents may already be integrated. No branch was deleted.

| Branch | Head | Commits not ancestral to continuation | Latest subject |
|---|---|---:|---|
| origin/agent/campaign-basic-legality-reader-source01-20261016 | 174f1c28f2b5 | 0 | Integrate independently reviewed actual source legality reader unchanged |
| origin/agent/campaign-basic-validation-engine-source01-20261016 | 3ff3be814054 | 1 | Preserve held basic validation engine SOURCE01 before multiface identity correction |
| origin/agent/campaign-deck-facts-source01-20261016 | a203b68e295d | 1 | Preserve frozen catalog deck facts SOURCE01 before bounded face preflight correction |
| origin/agent/campaign-deck-statistics-engine-source01-20261016 | 119f2d4103bb | 1 | Preserve held statistics engine SOURCE01 before cancellation correction |
| origin/agent/campaign-ordinary-life-ui-source01-20261016 | 8536b334825a | 1 | Preserve frozen ordinary life UI SOURCE01 before independent corrections |
| origin/agent/campaign-provider-anthropic-codec-source01-20261015 | cf1e16d381f4 | 1 | Preserve held untested native codec SOURCE01 before review corrections |
| origin/agent/campaign-provider-cc-codec-20261015 | 81001dedfb6a | 1 | Preserve unreviewed provider codec SOURCE01 for root handoff |
| origin/agent/campaign-provider-http-20261015 | 4b821d409cc4 | 1 | Preserve untested provider HTTP SOURCE02 for root handoff |
| origin/agent/campaign-provider-responses-codec-source01-20261016 | 55543376d6d4 | 1 | Preserve frozen Responses codec SOURCE01 before independent corrections |
| origin/agent/cc-gpt-k2-closeout-01 | 2e08e6964b0a | 0 | Make shell search tests deterministic on hosted emulators |
| origin/agent/cc-k2-integrate-01 | 9d976d9ab59b | 0 | Fix shell import action touch targets for emulator acceptance |
| origin/agent/cc-k2-qa-01 | 836e6dfe8f44 | 1 | [cc-k2-qa-01 step 1/1] K2 QA matrix, synthetic vectors, pinned contract review at 9681aec; READY-FOR-REVIEW |
| origin/agent/cc-k2-shell-01 | b1064f2129e4 | 0 | [cc-k2-shell-01 step 5/5] READY-FOR-REVIEW handoff |
| origin/agent/cc-k3-rules-parser-01 | 72a8f29323aa | 2 | [cc-k3-rules-parser-01 handoff] Record main's merge of 4971166, conflicts and defects still live on main |
| origin/agent/cc-m2-life-domain-01 | c4a27190d343 | 0 | [cc-m2-life-domain-01] Copy-ready Codex review and integration task |
| origin/agent/cc-plangaps-01 | ad717aaab0bb | 0 | [cc-plangaps-01 step 8/8] Apply review findings N1 to N13 and D-19 items 9 to 12; RK-33; shared-branch worktree rule; handoff to CONTINUE minimum |
| origin/agent/cc-settings-01 | 714cd7e92ba3 | 0 | [cc-settings-01 integrate] Merge main into agent/cc-settings-01 for hosted CI |
| origin/agent/cc-storage-01 | 09ea4ccf9e09 | 0 | [cc-storage-01 step 1/4] Module, Room3 schema v1 (seven catalog tables, FTS5 unicode61), newer-schema gate |
| origin/agent/cx-artifact-locator-01 | f8741ed990e9 | 9 | Record public open-flag API levels and lint correction |
| origin/agent/cx-catalog-k2-01 | 638cabaeb345 | 0 | Correct inclusive byte-limit test and record tested catalog evidence |
| origin/agent/cx-claude-life-handoff-01 | 354c1a0cf534 | 0 | Document full-app continuation recovery and agent routing plan |
| origin/agent/cx-english-downloads-integration-20261014 | d7e864f65e50 | 0 | fix(shell): align download status copy and record verified local integration |
| origin/agent/cx-english-downloads-integration-r1-20261014 | 12f898004c80 | 0 | fix(android): enable pinned desugaring in storage-consuming native tests |
| origin/agent/cx-fold7-handoff-01 | 707a271fdfb4 | 0 | Publish verified APK and hand off physical Fold7 validation |
| origin/agent/cx-full-v1-resume-01 | 315747dc44b1 | 0 | Route missing research through user regular-chat packets |
| origin/agent/cx-k3-acquisition-codec-01 | 4be4dfe62dee | 0 | Add strict versioned acquisition payload codec |
| origin/agent/cx-k3-download-engine-01 | e658091a7276 | 2 | Fence acquisition controls and authorize each physical wire dispatch |
| origin/agent/cx-k3-execute-01 | d334d9e3a60a | 0 | Checkpoint the real functional Fold7 candidate for native verification |
| origin/agent/cx-k3-full-bulk-import-01 | b6e74819b5b1 | 4 | Bound catalog activation acknowledgement after cancellation |
| origin/agent/cx-k3-full-framing-01 | 90374007f20c | 0 | Add bounded full-feed framers and record batches |
| origin/agent/cx-k3-locks-01 | c42da72c1e42 | 0 | [cx-k3-storage-03] Publish reviewed additive job schema ownership |
| origin/agent/cx-k3-network-01 | 7d94024f9fce | 1 | [cx-k3-network-01] Preserve JVM authenticator fixture across Android compile stubs |
| origin/agent/cx-k3-settings-removal-outbox-01 | d4d30f887f94 | 2 | Record bounded settings removal outbox verification handoff |
| origin/agent/cx-k3-storage-03 | 5617e02f640c | 0 | [K3 storage] Add durable acquisition schema and migration fixtures |
| origin/agent/cx-k3-storage-04 | 469340819eaa | 1 | [K3 storage] Add schema4 provenance and printing memberships |
| origin/agent/cx-life-closeout-01 | 1ff677f3ad4b | 0 | Publish life acceptance, rulings and new functional task lock |
| origin/agent/cx-life-domain-fix-01 | ce97e47e0d07 | 0 | Record clock undo correction evidence and provenance contract |
| origin/agent/cx-life-domain-integration-01 | 6fa6043ac98b | 0 | Integrate reviewed life domain while retaining network module |
| origin/agent/cx-life-hosted-gate-01 | 7569c81a62e0 | 0 | Add source-pinned hosted life domain JVM verification |
| origin/agent/cx-local-resume-20261013-public | d92ec315c59b | 1 | Retire old continuation directions and point to current published branch |
| origin/agent/cx-native-20261013 | 20a92836a11c | 2 | Add reviewed bounded private life wire primitives |
| origin/agent/cx-native-life-schema6-20261013 | c997c773466c | 1 | Run the focused Room schema6 integration verification |
| origin/agent/cx-native-life-service-20261014 | d3beaae07354 | 0 | docs(coordination): checkpoint verified persisted service and frozen PR merge hold |
| origin/agent/cx-native-life-service-source-20261014 | 1dc43908b81a | 0 | feat(life): implement reviewed persisted game service with real session and SQL readers |
| origin/agent/cx-native-vault-20261015 | 29f7b3f0919b | 0 | Install the demonstrated native emulator loader dependency |
| origin/agent/cx-professional-visual-foundation-01 | 8babcb2a49d6 | 6 | Record corrective visual review candidate and hosted gates |
| origin/agent/cx-reuse-compatibility-01 | 9e170fc5b0e2 | 7 | Validate Coil file decoding on actual Android instrumentation |
| origin/agent/cx-rulings-source-integration-20261014 | 341ed23a9eb1 | 0 | docs: checkpoint reviewed ruling UI and modern integration boundary |
| origin/agent/cx-rulings-source-integration-r1-20261014 | 4a4241be9e51 | 0 | feat(shell): add explicit ruling downloads and bounded offline card rulings |
| origin/agent/cx-rulings-source-integration-r2-20261014 | 9a2f6252789f | 1 | docs(android): record isolated ruling native verification snapshot |
| origin/agent/cx-search-rules-integration-r4-20261014 | 9c1573b201fc | 1 | docs(android): freeze current combined native verification snapshot |
| origin/agent/cx-settings-01 | 4f20764e3bc2 | 0 | Merge branch 'main' into agent/cx-settings-01 |
| origin/agent/cx-storage-01 | 080cd39145a4 | 0 | [cx-storage-01 S1 follow-up] Reject SQLite parenthesized parameter suffixes |
| origin/agent/cx-storage-recovery-01 | 985e03d91a7d | 0 | [K2 storage] Preserve callback ownership across nested coroutine scopes |
| origin/agent/cx-thread-recovery-20261013 | 7f822e6c746c | 0 | Claim bounded ordinary artwork producer and continue actual UI |
| origin/agent/gpt-doc-delta-01 | 6fc4a2cc7a9d | 0 | Require lightweight handoff delta after each action |
| origin/agent/gpt-docs-streamline-01 | 18897d6aafaf | 0 | Normalize documentation policy punctuation |
| origin/agent/gpt-k2-acceptance-01 | 56e2ffd1f8a6 | 0 | Correct K2 daily event archive typo |
| origin/agent/gpt-k2-closeout-01 | c91a348f6b5a | 0 | Stabilize shell UI waits on 16 KiB emulator |
| origin/agent/k2-contract-compile | 2ac4a302f683 | 2 | [K2 contracts] Separate printing assets and enforce rollback and query bounds |
| origin/agent/k2-core-contracts | 9681aec9866a | 2 | [K2 contracts] Separate printing assets and enforce rollback and query bounds |
| origin/agent/k3-catalog-framer-01 | 01e0c404b71e | 0 | Record catalog cleanup durability review request |
| origin/agent/k3-data-integration-01 | 46b7f5b9f9ea | 0 | [K3 candidate] Combine reviewed catalog parser and cleanup guard |
| origin/agent/k3-local-contracts-01 | 1bc6091c8233 | 1 | Add local rules import and snapshot lookup contracts |
| origin/agent/k3-rules-integration-01 | 9f88015bec0f | 0 | [K3 candidate] Wire private rules module into hosted verification |
| origin/agent/k3-shell-layout-01 | 33f79ae8ebf0 | 0 | Record expanded search diagnostic handoff |
| origin/agent/k3-shell-open-guard-01 | 3ffbb1f5a762 | 0 | Merge branch 'main' into agent/k3-shell-open-guard-01 |
| origin/ccr-b0894b79-qzhxhw | 797017a29ba0 | 0 | [gate-0] Record merge of Gate 0 into main |
| origin/ccr-d1e9ac59-b90cv2 | 6c9715be7910 | 6 | Checkpoint trusted resource profiles audit evidence (read-only) |
| origin/ci/android-pr-acceptance-20261013 | 19bbb8a9b5e1 | 1 | ci: gate Android PR validation and distinguish matrix-verified debug APKs |
| origin/component/catalog-binding-20261013 | a83d8db11b84 | 1 | feat(catalog): bind imports to genuine pinned Android artifacts |
| origin/component/english-profile-20261013 | 7cfc77c3bcc2 | 1 | Add reviewed fail-closed English search policy with141-test catalog proof |
| origin/component/life-history-audit-20261014 | a47d1cc804bf | 2 | Audit life history, undo, result, alert and clock-recovery coverage |
| origin/component/main-freeze-integration-20261014 | e9cb72b75bdd | 1 | Prepare frozen development integration with live coordination excluded |
| origin/component/rules-multiletter-20261013 | 3ed7237de73e | 1 | fix(rules): preserve multi-letter headings and references |
| origin/component/scryfall-integration-20261014 | ad3f9aa0cb21 | 3 | Close Scryfall integration task and release ownership |
| origin/component/scryfall-rulings-metadata-20261013 | 0db3298cc752 | 5 | Verify Scryfall metadata rejection boundaries and close task |
| origin/docs/pat-approved-first-release-cut-20261015 | cebf0755655a | 1 | docs(decisions): record Pat-approved first usable Fold7 release cut |
| origin/docs/pat-deck-tags-analytics-20261015 | 52335a0a6799 | 2 | docs(campaign): point next deck planning pass at tag design intake |
| origin/main | 4a14703fe548 | 4 | docs(integration): mark freeze preparation hold superseded by approved merge |

## Appendix E: commit-pinned sources and reproducibility

- [README.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/README.md)
- [AGENTS.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/AGENTS.md)
- [CONTINUE.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/CONTINUE.md)
- [coordination/state.json](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/coordination/state.json)
- [coordination/ORCHESTRATOR.json](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/coordination/ORCHESTRATOR.json)
- [coordination/tasks/orch-android-v1-campaign-01.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/coordination/tasks/orch-android-v1-campaign-01.md)
- [coordination/tasks/cx-campaign-ordinary-artwork-access-01.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/coordination/tasks/cx-campaign-ordinary-artwork-access-01.md)
- [docs/gauntlet/DECISIONS.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/gauntlet/DECISIONS.md)
- [docs/gauntlet/SPEC.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/gauntlet/SPEC.md)
- [docs/gauntlet/SCREENS.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/gauntlet/SCREENS.md)
- [docs/gauntlet/MILESTONES.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/gauntlet/MILESTONES.md)
- [docs/architecture/coverage.json](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/architecture/coverage.json)
- [docs/architecture/kotlin-registry.json](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/architecture/kotlin-registry.json)
- [docs/verification/full-v1-audit/FEATURE-AUDIT.md](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/docs/verification/full-v1-audit/FEATURE-AUDIT.md)
- [android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/AppGraph.kt](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/AppGraph.kt)
- [android-app/app/build.gradle.kts](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/app/build.gradle.kts)
- [.github/workflows/android-k2.yml](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/.github/workflows/android-k2.yml)

### Concrete source anchors

- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardDetail.kt:63](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardDetail.kt#L63): CardArtworkFrame()
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardArtworkFrame.kt:27](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardArtworkFrame.kt#L27): if (content == null) {
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt:31](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt#L31): public const val MAX_COMPRESSED_BYTES: Long = 3L * 1024 * 1024
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt:32](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt#L32): public const val MAX_EDGE: Int = 2048
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt:33](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/CardArtworkAccess.kt#L33): public const val MAX_PIXELS: Long = 2_000_000
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/Components.kt:23](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/Components.kt#L23): internal enum class WidthClass(val gutter: Dp) {
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt:26](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt#L26): * No IO on construction, automatic creation, action replay, timer or legacy watch.
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt:190](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt#L190): else -> return
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt:131](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/presentation/OrdinaryLifeController.kt#L131): when (val result = callPort(dispatcher) { service.history(GameHistoryFilter(), PageRequest(20, cursor), it) }) {
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardPrintings.kt:19](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardPrintings.kt#L19): internal fun CardPrintings(card: CardRecord, controller: PrintingSelectionController, onAddPrinting: ((PrintingRecord) -> Unit)? = null) {
- [android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardPrintings.kt:81](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/feature/shell/src/main/kotlin/com/architectpsg/commandlibrary/feature/shell/ui/CardPrintings.kt#L81): try { browser.openUri(uri.value) }
- [android-app/app/build.gradle.kts:14](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/app/build.gradle.kts#L14): versionCode = 1
- [android-app/app/build.gradle.kts:15](https://github.com/pemunson90-cmd/Command-Library/blob/7f822e6c746cf7411f1f7030b1d08a734b1694aa/android-app/app/build.gradle.kts#L15): versionName = "0.1.0-k2"

Re-fetch source before implementation. Follow-up work should update the existing task/report surfaces, not copy this whole audit into active coordination. No fresh upstream API/terms conclusion is asserted: those are research packets above.
