# GATE-2: Specification filed for ruling

Date: 2026-10-11. Orchestrator: Codex, sole through Gate 3. Status: ready for Pat's ruling; Gate 3 not started. All Gate 1 decisions including D04 are approved.

## Delivered

| Artifact | Final content |
|---|---|
| SPEC.md |64 feature contracts with observable acceptance, pricing/state/query semantics and all-format registry inventory |
| DESIGN_SYSTEM.md | Light/dark/high contrast tokens, component states, card/mana/identity rendering, motion, desktop/reader/fold rules |
| COVERAGE.md | Baseline and12 intake traces,64 named acceptance/milestone mappings, source follow-ups E01-E13 |
| PARITY_ACCEPTANCE.md | All213 approved rows:194 build/adapt obligations,19 skips, unchanged feature/disposition/reason |
| DATA_MODEL.md | Catalog/user/rules/price/game/AI/transfer entities, atomic generations/restore, package/variant/clock/share records |
| COMPONENTS.md |20-component acyclic conceptual dependency inventory, pure domain/ports and shared-file boundaries |
| SCREENS.md |25 screens with five width layouts, tabletop/book, height override and offline/error/accessibility behavior |
| PERFORMANCE.md |12 numerical proposed budgets, reference hardware/corpus/sample method and bounded decoded-art/import/restore behavior |
| GATE-2-REVIEW.md | Independent review and rechecks; all five findings resolved; ready to file for Pat |
| GATE-2-CHECKS.md | Document/graph/ID/link and static-token contrast checks; no runtime test claim |

## Independent review dispositions

Initial review found material missing milestone/test trace and incomplete contracts for approved extras. Corrections added64 AC mappings and explicit packages, deck+collection multi-edit, printing optimization, interaction playtest, image/multiframe QR, Planechase/Archenemy/Bounty/custom modules, seat clocks/alerts and local profile/game exchange contracts. Data entities and actual screen controls accompany them.

Nonblocking all-format inventory, decoded-art RAM and detailed parity-family trace clarifications were also corrected. Reviewer independently re-read the latest snapshot and confirmed all five findings resolved,64 exact requirement/test joins and unchanged213 parity rows. Root verified owners/screens/links, graph and static contrast. The reviewer did not inspect remote commit equality; root verifies publication separately.

## Proposed rulings

1. D-15: approve the full specification/design/data/screen/coverage package. Preserves all mandatory features and approved extras; Full English by default and non-English download consent. Proposed detailed policies include any-eligible-owned-printing allocation unless explicitly pinned, immutable package copy/linked modes, manual interaction/variant adjudication and locally portable QR/profile/game exchange. Tradeoff: breadth requires staged delivery and authentic-source fixtures, rather than claiming complete vendor/runtime support now.
2. D-16: approve proposed performance targets/reference method: cold-start p95<=2.5s; DB search p95<=150ms and visible<=250ms after debounce; full local import<=10min excluding transfer; base steady RSS<=300MiB/import peak<=450MiB excluding loaded LLM only; decoded-art64MiB steady/96MiB transient. Twelve full budgets and disk/model targets are in PERFORMANCE. Tradeoff: physical reference-hardware/corpus measurements must demonstrate targets; missing measurements are not passes and failures require reported tuning or a ruled budget change.

## Material limits carried forward

Current source terms/bytes/English-printing coverage, rules encoding/reference integrity, bracket set agreement, vendor import/link fixtures, provider account eligibility/contracts, local model performance/licenses, stable vault/Flutter engine configuration, physical TalkBack/Narrator/fold tests, variant data rights/QR/clock behavior and signed package install remain explicit E01-E13 work. Nothing here is a new permission to republish proprietary corpora or a reason to cut personal functionality.

No app code, CI build or target-device test at Gate 2. Dependency pin tuples, exact public signatures, exclusive path globs, physical schema, implemented tests, final milestones and concurrency remain Gate 3 deliverables. Local filesystem is a text snapshot without .git; expected-head GitHub connector is publication transport.

## Checkpoints and next

3180f09 applied final Gate 1 approval;6b67f3a published full draft/reviewer brief;b6f903d saved material corrections. This final Gate 2 commit publishes the independent review, report and final handoff. Verify current main head before future changes.

STOP for D15/D16. After Pat rules, record verbatim and proceed to Gate 3 architecture and plan with independent zero-overlap/DAG/coverage review, then stop at Gate 3. No component code until its ARCHITECT is merged and implementation authorized.
