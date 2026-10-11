# cx-gate1-review: independent research review

Date: 2026-10-11. Status: preliminary review complete; final Gate 1 synthesis not reviewed yet.

## Scope and independence

Read GAUNTLET_CODEX.md, the original task cards, research template and finalized R02-R07/R11-R15. Audited reporting, coverage and internal consistency. No app code or research files changed. This review does not independently reproduce external measurements or inspect signing secrets.

R08 and R13 are excluded from this agent's independent approval because this agent authored them. The orchestrator reports independently reviewing their final scope and recommendations. References to R13 below identify cross-report consistency only.

R01, R09, R10, R16 and R17 remain active and **not reviewed yet**. Their existing drafts, if any, are not approved by this handoff. RESEARCH_SUMMARY.md and the final Gate 1 report also require a follow-up review. This is not permission to pass Gate 1 or start implementation.

## Findings requiring correction or explicit ruling

| ID | Severity | Finding | Required disposition |
|---|---|---|---|
| RV01 | Material consistency | R02 F1/F2 and Risks preserve a same-day seven-export, gzipped JSONL Scryfall contract. R14 F5 recommends root-array streaming approaches across stacks, including Kotlin ARRAY_WRAPPED. That is not an importer for this reported primary feed. | Add bounded decompression plus line-wise JSON object decoding for Scryfall. Keep root-array/other document readers for producers that actually use them. Define feed format from retrieved metadata/content, not one global assumption. |
| RV02 | Coverage consistency | R15 E proposes compact/medium/expanded screenshot fixtures only. Current adaptive research R13 includes large and extra-large classes. The CI promise is screenshots per supported class. | Include large/extra-large fixtures in the architecture's planned coverage, or explicitly state which render behavior shares a baseline and test its breakpoint boundaries. This is a planned-coverage fix, not evidence that tests have run. |
| RV03 | Ruling needed | R03 identifies a real distinction between individual use and public republication, but its recommendation leaves automatic personal caching unresolved and offers official links/user import. Links alone do not meet mandatory full offline rules. Public release is expressly post-release and cannot cut or delay personal functionality. | Gate 1 summary must preserve the full local searchable rules requirement, distinguish personal acquisition/cache from public bundling, and seek a concrete acquisition decision. Do not silently turn a public-release licensing question into a source-links-only product. |
| RV04 | Ruling needed | R02's language-scope recommendation requires agreement, while Full mode requires every card and printings. default_cards scope is not verified as every-language completeness. | State the proposed Full-mode scope and resulting excluded language printings explicitly for Pat. Do not call a narrower catalog complete without that ruling. Preserve foreign owned printing identity even under an English display policy. |
| RV05 | Ruling needed | R07's Lotus/Mox selection uses an analyst-chosen established-app threshold, rather than literal highest star rating. Auditor/Rhystic have higher reported stars in the disclosed sample. | Carry this transparent sampling interpretation into the gate ruling. Do not advertise the selected apps as the exhaustive global top two. All mandatory baseline rows remain build/adapt irrespective of sample choice. |

These findings rely on the named reports and local brief as read on 2026-10-11. RV01 concerns their documented contracts, not independent verification of the inherited live response.

## Evidence integrity and scope results

| Report | Coverage/evidence result | Limit that must survive synthesis |
|---|---|---|
| R02 card data | Requested metadata, schema, layouts, formats, filtering, images, Lean contracts, terms, MTGJSON and refresh covered by evidence or explicit unknown/probe. | F1 is inherited same-day live evidence, not repeated measurement. Counts and 20-card image sample not measured; image budgets illustrative. Current bulk presence, endpoint contracts and Scryfall terms remain unknown. Seven-file JSONL observation must not be replaced with remembered old array contract. |
| R03 rules | Source/version, history, encoding/sizes, structure/counts, keyword mapping/rulings, related documents and rights covered with bounded gaps. | Rendered lines/pages are not byte/encoding/count measurements. History is a sample, not all releases. Current catalog mismatch audit unperformed. Offline acquisition remains RV03. |
| R04 brackets/combos | Rules/history, automation, machine-readable candidates, effects, Spellbook routes/bulk/schema/terms, estimators and refresh covered. | Full current dynamically rendered criteria and official membership/count unknown. The request for every criterion verbatim is explicitly unmet, with linked summaries. Three-source agreement test is proposed, not run. Exact bulk bytes/cadence unknown. |
| R05 Moxfield | Required parity rows present; extra features, paid tiers, access policy, fallback and fixture gaps documented. | 2020 wiki is historical primary evidence. Main UI is a JS shell. Exact export/CSV bytes and controls unknown; R16 fixture dependency open. Restrictive terms are served by a Moxfield-owned related property, with current main-site equivalence unknown. |
| R06 Archidekt | Required parity rows present; sourced shipped features, historical API welcome versus terms conflict, fallback and fixture gaps documented. | Historic developer permission is meaningful counterevidence, but current scoped contract remains unresolved. Documented import example is not a captured current export. Authentic CSV/complete grammar await R16. |
| R07 life counters | Every mandatory baseline row present with build/adapt; extra features, metadata, paid/OS differences and source disagreements covered. | No app installed or offline/posture operation exercised. iOS evidence is not Android evidence. U means unknown, not missing. Sample selection requires RV05. |
| R11 local LLM | Requested runtimes, model choices, platform interop, packaging, benchmarks and download facts covered with explicit unknowns. | Inherited package/version/size observations broadly downgraded to unverified. ExecuTorch benchmark is named flagship vendor evidence, not a mid-range/full-deck result. No target APK/package, runtime performance or Range test passed. |
| R12 storage | Native APIs, lifecycle, six-stack paths, limits, threat model, backup exclusions and licenses covered. | Same-user Windows malware is outside storage encryption protection. Flutter development source is not proven stable-package parity. Actual packaged runtime, update/reinstall and backup tests remain future work. |
| R14 database | Engines, FTS, six-stack packages, scale, parser candidates, streaming and atomic activation covered. | Prior native sizes/flags are inherited unverified observations despite locally precise wording in F1. No application corpus timing/memory benchmark. Room 3 FTS5 recommendation is sourced; chosen targets still need compile-and-probe. Resolve RV01. |
| R15 CI | Six-stack packaging/testing, existing-key mechanics, billing, code signing, scanner sketch and pinning covered. | No workflow or package was run. Scanner explicitly misses semantic/multiline stubs. Eligibility and code-signing terms do not guarantee acceptance. Generic Tauri/package sizes are not application sizes. Resolve RV02. |
| R08 pricing | Orchestrator-reviewed, excluded from this independent approval. | Keep actual currency/provider distinctions, nullable quote behavior and printing/finish ownership policy in synthesis. |
| R13 adaptive | Orchestrator-reviewed, excluded from this independent approval. | Keep packaged Windows Narrator verification as a mandatory S1 selection condition, not a silent swap to another reader. |

All inspected reports contain Question, Findings, Options, Recommendation, Risks and Confidence. Global access-date conventions and source registers legitimately supply dates to individual linked claims/matrix cells; a fresh date does not make a historical announcement current hands-on proof. No report establishes completed app builds or hardware measurements. The research-complete label means evidence inventory plus explicit unknowns, not every requested measurement completed.

## Product and matrix audit

R05/R06 cover every required deckbuilding scope row, including companion handling, notes/Markdown, probability, mulligan/playtesting, tokens, validation, versions/diff, organization and imports/exports. R05 skips automated Moxfield retrieval but supplies guided export, which the brief expressly allows. R06 preserves historical API permission rather than calling access definitively forbidden. Both keep native required features where competitor evidence is unknown. Proposed social/paid-infrastructure exclusions are scope recommendations requiring the gate ruling, not approved product cuts.

R07 keeps the entire 1-6-seat baseline, including partner damage, all counters/hiding, shared states, arbitrary dice, seat randomization, all timers, art, loss/undo/autosave/log, awake, deck stats, tabletop and offline operation. Optional extra-module dispositions are separate from that mandatory baseline. No competitor documentation gap justifies cutting a baseline feature.

Cross-topic principles are consistent: nullable/unknown data is not zero or false; provider licenses do not license all third-party art/model weights; community estimates are not official rules; hardware/backend support requires actual target validation; inherited measurements are not newly rerun probes. R04 correctly refuses to hardcode 53 and includes both Farewell and Biorhythm in the February update. The final summary must preserve those corrections.

R01's whole scored stack matrix is not yet available for this review. Criterion-specific numeric scores in R11/R12 are engineering judgments with stated uncertainty, not measured performance. They cannot alone settle the stack. In particular, S1 fallback preference must carry R13's unresolved Windows Narrator condition, and S2 remains a credible alternative. Do not let packaging feasibility imply accessibility or foldable success.

## Follow-up / next

Orchestrator corrects RV01/RV02 or records an explicit supported design. Gate 1 summary carries RV03-RV05 as concrete decisions, the inherited-evidence limits and remaining source probes. Then provide finalized R01/R09/R10/R16/R17, RESEARCH_SUMMARY.md and gates/GATE-1.md for final coverage/consistency review. Check that each of 17 topics has one ruling, full parity tables survive, unsupported facts remain unknown and no report claims unrun validation. Check R16 specifically against authentic-export unknowns in R05/R06 and R17 against every mandatory external-link vendor.
