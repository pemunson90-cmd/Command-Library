# Deck organization and tag-aware analysis — Pat's design direction (2026-10-15)

**Status:** User-approved product direction staged for **root-orchestrator review at the next eligible deck B02/B03 planning boundary**. This document is *not* a worker claim, a new active task, a merged change, a source/test acceptance result, or permission to interrupt running H08/other integration. No current implementation deadline or extra test matrix is imposed.

**Authority/provenance:** Pat confirmed the existing Moxfield-parity coverage and endorsed tag-aware analysis, role-targets, what-if comparison and other proposed improvements, then specified actual tagging usage: functional roles first; custom deck/synergy/context annotations; multiple simultaneous classifications; and reusable tags in a persistent pool. Pat explicitly requested intelligent, preferably automatic generic classification, adjustable as cards/decks evolve, **without holding up feature completion for a sophisticated inference system**. Preserve this as user direction, distinguishing it from Moxfield's documented behavior and the implementation recommendations below.

## Existing contracts and observed gap

- Required baseline: `SPEC.md` B01/B02/B03/B04/B05/B07/B09/B10/B12, SCREENS S05–S11, `PARITY_ACCEPTANCE.md` PX05-008/009/017–026/034–040/048 and matching PX06 rows. `DATA_MODEL.md` has DeckEntry multi-label, Category, RoleOverride, Deck metadata and versioning. `kotlin-registry.json.execution` tasks `deck-ui`, `analysis`, `playtest`, `packages`, `recommendations` and consumers remain authoritative for scheduling.
- At inspection, `DeckPatchReducer` knows ordered category definitions and many-to-many entry labels, and deck editor Compose screens can toggle **existing** labels. This does not establish complete create/rename/order/reuse/manage/global-default/bulk-tag UX; do not mark B02/PX05-008/009 accepted prematurely.
- `LocalDrawProbabilities` and the draw-probability dialog are real, scoped host-tested features, **not** mana curve/type/color-source analysis or opening-hand/manual goldfish.
- The Moxfield research R-05 documents historical features but cannot establish the competitor's latest exact behavior; do not claim unverified parity or access its site automatically. Native local capability matters more than visual imitation.

## Functional contract — preserve all these independent dimensions

1. **Reusable personal tag pool**: Create a custom named tag once and choose it from a persistent, searchable picker across decks. Rename, reorder and manage it without repeatedly typing it. Stable tag identity, normalized search, Unicode input and defined rename/delete behavior; no accidental loss of existing deck annotations. Useful built-in function tags may seed the list but can be hidden/adjusted. **Do not hard-code a closed taxonomy.**
2. **Many labels per actual card entry**: “Removal” + “Artifact Synergy” + “Better With Commander” can all apply simultaneously, alongside notes/free-text personal meaning. Tags are not boards, don't duplicate card entries/quantities, and aren't mutually exclusive. Preserve intentional entry-level overrides even when the same Oracle card has different roles in different decks.
3. **Separate information layers**: (a) sourced card facts (types/Oracle text/mana etc.), (b) reusable personal/global labels or defaults, (c) deck-/entry-specific labels and explicit suppressions/overrides, and (d) computed suggestions/analysis. Track provenance and exact card/catalog generation; label origin is visible or inspectable. Personal changes always outrank derived guesses.
4. **Editable bulk workflow**: Accessible assign/remove tag(s) to selected entries, create a tag from the same workflow, reorder/group by tags without disturbing board/role/printing, show cards with multiple roles, and retain grouped undo, saved versions, restoration and native backup semantics. Dragging has touch/keyboard/menu alternatives.
5. **Display filter**: A simple persistent **Show auto tags** toggle, with a manual/custom-only presentation when off. The switch changes visibility, not actual user annotations, card counts, or the analysis' underlying source evidence. Custom tags remain editable whether automation is enabled or hidden.

## Tag-aware deterministic deck evaluation

- Complete baseline B03 first: mana value curves; demand by colored mana symbols versus usable/conditional land sources; type distribution; average mana value with explicit denominator; land guidance; token references. Account for multi-face/MDFC/adventure/hybrid/X and uncertain values; never turn unknown into zero.
- Enable analysis filtering by any tag, multiple tags (AND/OR), **intersections** (“Ramp AND Card Draw”), boards and user-selected card sets. Show matching count and full-deck reference side by side; add an accessible textual chart/table alternative.
- A dual-role card may contribute to **both** functional role totals while its physical copies remain counted **once** in overall deck size, probability, cost, ownership and zone conservation. Warn explicitly that tag bucket totals overlap.
- User-defined role coverage targets (e.g. 10 ramp, 8 removal) should be editable per deck/template, descriptive rather than prescribing a universal ideal. Distinguish unconditional versus conditional contributions where source/evidence permits.
- Proposed, non-destructive what-if evaluation: stage cuts/adds and preview changes in curve, functional coverage, overlap, mana demand, cost/owned allocation, color identity and legality where those consumers exist; never claim unknown costs/legalities as certain. Apply only through the existing reviewed versioned deck patch/undo path.
- Keep tag-aware analysis offline and deterministic. It must function without network, keys, installed AI models or pricing; missing optional sources are reported as partial, not blockers to base curve/tag reports.

## Intelligent automatic classification — deliberately phased, not a critical-path trap

**Phase A — required now when B02 deck organization becomes eligible:** deliver personal tag-pool CRUD/search/reuse, many-to-many manual edits, deck-specific overrides, bulk assignment and stable persistence/undo/version/backup behavior. These are baseline Moxfield-style requirements, not speculative research.

**Phase B — alongside B03 when its real catalog/analysis input is ready:** deliver baseline curve/pip/type/source statistics and tag-filtered/intersection views. No machine learning or graph engine is needed. The tag-aware analysis is an approved enhancement to a required feature, not permission to postpone the feature.

**Phase C — bounded, useful auto-tagging (preferred for v1 if it fits naturally):** add conservative, versioned offline rules from *actual* Oracle text, keyword/typing and basic effect patterns. Candidate generic functions include Ramp, Card Draw, Conditional Draw, Removal, Board Wipe, Protection, Recursion, Tutor and Token Generation. Cards may receive several proposed roles. A derived “Conditional Draw” distinction is better than a false definite “Card Draw.” Attach reason/evidence and uncertainty to each suggestion; expose review/edit/reject controls and Show auto tags. Never rewrite user tags, assume a missing effect is absent, or pretend regex alone fully understands conditional effects.

**Phase D — context-aware refinements after feature completion where worthwhile:** recompute when deck contents, commander(s), manual overrides, tag rules or catalog generation change. Make “Artifact Synergy”/“Better With Commander” suggestions only where justified by explicit local relationships; otherwise they remain ordinary manually selectable tags. Recompute affected entries/summary caches incrementally and bound evaluation; don't run unlimited recursive passes or create auto-tags from prior inferred auto-tags. Advanced synergy/commander graph inference is an optional refinement; it must not block tags, B03, remaining v1 features or final release. If the initial conservative auto-tag implementation itself proves costly, record the smallest useful supported subset and an explicit later checkpoint rather than quietly deleting the user-approved direction.

Suggested implementation layering: compute auto role suggestions independently of personal tags; persist manual selections/overrides separately; cache derived results keyed by card/generation, deck revision, rule-set version and override revision where appropriate. Prefer lazy/on-add computation to tagging the entire catalog up front; invalidation must not mutate deck edits. Do not add AI-model/provider dependency.

## Light, meaningful acceptance checks

At the appropriate implementation boundaries (not after every micro-edit), exercise:
- Create “Artifact Synergy” once, assign it in several decks without retyping; rename it, restart, verify identity/annotations; combine it with “Removal” and “Better With Commander” on one card.
- Tag a card Ramp + Draw; overall deck count remains unchanged, both tag filters include it, intersection shows it once, total price/owned copies do not double. User corrections and explicit suppressions survive restart/catalog refresh.
- Hide and re-show auto labels; manual categories/notes/entry roles are unchanged. An unsupported/ambiguous classifier rule produces an uncertain/unclassified outcome, not silently authoritative tags.
- Tagged mana curves sum each matching card/copy according to the documented policy; face/conditional/missing-data fixtures remain truthful. Compare staged changes without a deck write, then apply/undo through existing saved-deck revisions.
- Preserve relevant tag identities and provenance through named versions, durable editor, supported native interchange and previewed restore. Legacy lossy text export explicitly warns when it cannot carry them.
- Verify an actual app route plus focused domain/storage tests before claiming the integrated feature; use D27/D28 minimum sufficient testing and defer broad device/polish acceptance to final assembly.

## Integration/ownership instructions for surviving root

1. **Do not preempt active H08/AI-history workers, change global next-task or claim their files.** Process this proposal the next time deck organization/analysis tasks become runnable, or at an existing campaign replan boundary.
2. This is a proposed doc/decision delta against `agent/cx-thread-recovery-20261013`; root alone reviews/rebases it, records the user ruling in the canonical `DECISIONS.md` using the next unclaimed identifier, and updates `kotlin-registry.json.execution` / coverage/acceptance links only as needed. Distinguish baseline B02/B03 from enhancements; preserve 64/213 trace invariants rather than inventing counts.
3. Carve separately owned feature work only after accepted interfaces and exact branch/lock; root retains schema/DI/shell coordination. Avoid creating a new agent process or a separate live queue just for tags.
4. **Completion priority:** manual functional tags and analysis first, conservative auto labels next if cheap, advanced commander/synergy classification and freeform spatial brewing as later bounded refinements. No automatic full-corpus inference, broad retest ritual or interruption of unrelated work.

**Other approved design directions from this conversation:** functional category targets, what-if comparisons, owned-card/collection-aware analysis and optionally a freeform visual brewing table. These are product-enhancement directions to schedule against existing deck, pricing, recommendation and UI dependencies, not claims that they are currently implemented, not authority to delay required features, and not permission to scrape Moxfield.
