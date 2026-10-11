# COVERAGE

Gate 2 draft coverage. Every baseline requirement is in SPEC; every approved parity row will be inventoried in PARITY_ACCEPTANCE. No unmapped item is silently omitted. Latest English-default D-14 supersedes automatic all-language download proposal.

## Baseline trace

| Brief source | SPEC IDs | Components/screens | Disposition |
|---|---|---|---|
| 2.A card fields/layouts/printings/rulings | A01,A02 | catalog/S02,S03,S18 | Required; English default, other languages approved before download |
| 2.A rules/glossary/cross-links | A04 | rules/S03,S04 | Full offline corpus, never link-only |
| 2.A Full/Lean switch/new cards | A03 | catalog/S01,S18 | Required without user data loss |
| 2.A image cache/packs/resume/Wi-Fi | A05,A06 | catalog/S18 | On-view cache default; optional pack consent |
| 2.A atomic update/manual/date | A06,K02 | catalog/settings/S18,S19 | Required |
| 2.A local syntax/autocomplete/online fallback | A07,A08 | search/S02,S04 | Explicit unsupported/online distinction |
| 2.B native parity/boards/roles | B01,B07 | decks/S05-S11 | Complete approved matrix |
| 2.B categories/tags/printing/quantity/notes/primer | B01,B02 | decks/S06,S07 | Required |
| 2.B all analysis/probability/tokens | B03,B04 | decks/S09,S10 | Required; deterministic semantics |
| 2.B all-format validation/live warnings | B06 | validation/S06,S09 | Required, Commander first |
| 2.B versions/diff/duplicate/folders/search/sort | B02,B05 | decks/S05,S11 | Required |
| 2.B imports/exports/text/CSV/guided flow | B08,I01 | transfer/S08,S17 | Required; vendor adapters authentic fixtures |
| 2.B/C inline n everywhere | C02,C04 | pricing/S02,S03,S06,S12-S16 | Required |
| 2.C owned printing/finish/add/deck/CSV | C01 | collection/S08,S12 | Required |
| 2.C source/currency/total/market | C02-C04 | pricing/S14,S18 | Approved USD default, alternatives selectable |
| 2.D budget caps/owned-free/source | D01 | recommendations/S14 | Required |
| 2.D offline synergy/roles/override/explanations | D02 | recommendations/S13 | Required; permitted enrichers only |
| 2.E criteria/Game Changers/combos/date | E01 | brackets/S15,S18 | Reviewed data and reconciliation |
| 2.E evidence/confidence/intent/target changes | E02 | brackets/S13,S15 | Required, no false official certainty |
| 2.E non-Commander legality | B06,E02 | validation/S06,S09 | All-format registry |
| 2.F seven links/user tokens | F01 | links presentation in shell/S03,S18 | Required; supplied URI/fallback and safe encoding |
| 2.G 1-6 players/seat/presets/tap/hold | G01,G02 | life/S20,S21 | Required |
| 2.G opposing commander/partners/tax/deduction | G03,G04 | life/S21,S22 | Required |
| 2.G every named/custom/hideable counter | G04 | life/S21,S22 | Required |
| 2.G Monarch/Initiative/DayNight/Blessing | G05 | life/S21,S22 | Required |
| 2.G dice/coin/first player/seat randomizer | G06 | life/S22 | Required |
| 2.G turns/turn+game timers | G07 | life/S21,S22 | Required |
| 2.G backgrounds/art/linked deck/statistics | G08,G09 | life/S20,S21,S23 | Required |
| 2.G loss/undo/autosave/log/awake/offline/fold | G01,G09,G10 | life/S21 | Required |
| 2.G additional approved reference parity | G11 | life/S20-S23 | All build/adapt rows; optional modules user-enabled |
| 2.H four modes/roast intensity | H01 | ai/S16 | Required |
| 2.H eight direct providers/local six/custom | H02 | ai/S16,S24 | Required candidate support; contracts/eligibility evidence explicit |
| 2.H on-device/model downloads | H03 | inference/S16,S25 | llama.cpp baseline |
| 2.H prompt export/pasteback | H04 | ai/S16 | No provider account required |
| 2.H listing/manual IDs | H02 | ai/S24 | Always manual ID |
| 2.H grounding/parsed suggestions/tappable n | H01,H05 | ai/S16 | Local deterministic validation |
| 2.H optional tool calls/limit | H06 | ai/S16 | Max8 read-only calls,50 rows,5s |
| 2.H encrypted keys/no logging/plaintext export | H07 | vault/S24 | Required |
| 2.H stream/cancel/retry/history/token/cost | H08 | ai/S16 | Cost hidden when unavailable |
| 2.H repo versioned/editable prompts | H09 | ai/S24 | Required |
| 2.I text/CSV/native backup/restore | I01,I02 | transfer/S08,S17 | Keys excluded, atomic |
| 2.I sync | N03 | POST_RELEASE | Explicit deferral |
| 2.J tokens/components/themes/identity/cards/icons | J03 | DESIGN_SYSTEM/all screens | Required UI audit |
| 2.J widths/panes/postures/state | J01 | shell/all screens | Five widths and height override |
| 2.J desktop keys/hover/context/drop/resize | J02 | shell/all screens | Required |
| 2.J TalkBack/Narrator/text/contrast/targets | J03 | shell/all screens | Actual reader tests required |
| 2.J numbered performance/screenshot auditor | J04 | PERFORMANCE/SCREENS | Proposed targets, not measurements |
| 2.K first-run/mode/estimate/download/resume | K01 | settings/S01,S18 | Required |
| 2.K module settings/about/attribution/dates | K01,K02 | settings/S18,S19,S24 | Required |
| 3 architecture/repositories/pure domain/cancel | N01 | COMPONENTS/DATA_MODEL | No cycles; ARCHITECT before app code |
| 3 data/index/stream/batches/migration tests | A06,A08,I02,N01 | storage/PERFORMANCE | Required |
| 3 shared client/politeness/retry/offline | N02 | network/all online paths | Required |
| 3 zero-placeholder/testing/security/pins | N01,H07,J04 | Gate3/CI acceptance | Required, no fake exceptions |
| Environment phone/Actions/Android+Windows | N01,J04 | hosted CI; signed APK/EXE/ZIP | No local developer machine dependency |
| D03 existing key/single secret/backups | N01 | CI Gate3/M0 | Preserve key and latest backup ruling; never commit sheets |
| D14 English and explicit other-language approval | A02,K01,I02 | catalog/transfer/settings | No silent foreign-language/restore-triggered transfer |

## Appendix A intake rulings trace

| Intake answer | Mapping |
|---|---|
| 1 separate orchestrators/component ARCHITECT/checkpoint/isolation | N01; coordination protocol and Gate3 component owned paths |
| 2 researched stack with working fallback | D07 Flutter approved; COMPONENTS retains runtime acceptance/fallback evidence |
| 3 personal first/public later | N03 |
| 4 Full+Lean offline rules/Oracle/art opt-in | A01-A06 |
| 5 file/text/CSV now, sync later | B08,I01,I02,N03 |
| 6 native as far as possible | B01-B08 and approved R05/R06 parity |
| 7 collection and n owned zero | C01-C04 |
| 8 as many local/API/prompt AI options as possible | H01-H09, inference |
| 9 all formats/Commander first | B06,E01,E02 |
| 10 entire life baseline plus extras; shared-phone later | G01-G11,N03 and R07 parity |
| 11 gated passes/always filed | STATE/JOURNAL/GATE reports and handoffs |
| 12 Command Library/application ID | D01 com.architectpsg.commandlibrary; N01 packaging |

## Evidence follow-up register

These are verification obligations, not unmapped features. Acceptance cannot claim them passed until source/runtime evidence exists.

| Evidence ID | Remaining verification | Acceptance IDs |
|---|---|---|
| E01 | Current Scryfall contracts/terms/English all-printing/language coverage and real bytes/counts/images | A01-A03,A05,A06,C03 |
| E02 | Actual official rules bytes/encoding/counts and parser/glossary/reference integrity; personal acquisition versus public republication | A04 |
| E03 | Current exact criteria/Game Changer set equality and Spellbook authenticated bulk mappings/license notices | E01,E02,D02 |
| E04 | Authentic Moxfield/Archidekt/vendor text/CSV/import+export round trips; permission conflict before automatic retrieval | B08,I01 |
| E05 | Exact card-link vendor ID/URL/HTTP behavior and applicable source policy | F01 |
| E06 | Cloud schemas/rate/error/price units/tool/stream/auth contracts and account-specific applicable terms including Gemini use eligibility | H02,H06,H08 |
| E07 | Local server version/port/models/auth/tool/parser configuration | H02 |
| E08 | Model license/hash/bytes/Range support, native ABI/load/cancel/quality and target memory/speed | H03/P10 |
| E09 | Stable Flutter vault bridge isolation, backup/reinstall/corruption and origin-bound auth | H07 |
| E10 | Pinned Flutter engine MSAA/UIA settings, actual packaged TalkBack/Narrator and physical fold sensors | J01,J03 |
| E11 | SQLite FTS5 behavior/migrations/bounded import, full corpus perf measurements on actual reference hardware | A06,A08,I02,J04 |
| E12 | Hosted signed APK via ANDROID_SIGNING, certificate verification and real Android/Windows install/launch | N01 |
| E13 | Authentic current variant definitions/card data/instructions and applicable personal acquisition/redistribution rights, plus native QR scanner/export and clock background behavior before claims; source uncertainty never silently defers approved modules | G12,G13,B13 |

## Open product questions

No baseline item is unmapped. Gate2 proposed budgets/design/policy details require Pat's gate ruling after independent review. Exact platform dependency pins, signatures, exclusive owned paths and implementer concurrency are intentionally Gate3 deliverables. Unknown source facts above remain explicit; no silent scope cuts or fake passing checks.

## Exhaustive parity trace

See [PARITY_ACCEPTANCE](PARITY_ACCEPTANCE.md). Imported source feature counts: R-05: 67, R-06: 64, R-07: 82. Every build/adapt row is an acceptance obligation; every skip retains approved reason. These counts are matrix rows, not competitor measurements.

## Explicit parity-extra trace

| Approved source rows | SPEC | Component/screens |
|---|---|---|
| PX05-050 packages | B09 | decks/S05-S08 |
| PX06-049 multi-edit | B10,C01 | decks + collection/S06,S07,S12 |
| PX06-052 printing optimizer | B11 | pricing/S06,S14 |
| PX06-055 interaction simulator | B12 | decks/S10 |
| PX06-060 image/QR sharing | B13 | transfer/S08,S17 |
| PX07-060 through PX07-063 variants | G12 | life/S20-S22 |
| PX07-067 countdown/chess/alerts | G13 | life/S20-S22 |
| PX07-048 profiles; PX07-064 game CSV/JSON | G14 | transfer/S17,S20,S23 |

E13: verify authentic current variant definitions/card data/instructions and applicable personal acquisition/redistribution rights, plus native QR scanner/export and clock background behavior before claims. Covers G12/G13/B13; source uncertainty never silently defers approved modules.

## Requirement to milestone and named acceptance test

Provisional milestone grouping follows GAUNTLET section6; final order/steps are Gate3. AC-ID is a named acceptance case set with exact observable fixture contract in SPEC row ID, not a claim tests are implemented. UI/a11y checks run at each feature milestone and comprehensive regression at M8/M9, not postponed entirely to polish. Source/parser/DB failure and schema migration cases accompany the affected feature.

| SPEC ID | Primary component | Provisional milestone | Named acceptance case and required test kind |
|---|---|---|---|
| A01 | catalog | M1 | AC-A01: Full English catalog; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A02 | catalog | M1 | AC-A02: Language consent; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A03 | catalog | M1 | AC-A03: Storage modes; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A04 | rules | M1 | AC-A04: Offline rules; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A05 | catalog | M1 cache/M8 packs | AC-A05: Image packs/cache; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A06 | catalog | M1 | AC-A06: Atomic updates; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A07 | search | M1 | AC-A07: Local query syntax; source/parser/DB/query fixtures, interruption/offline UI and profile |
| A08 | search | M1 | AC-A08: Text search; source/parser/DB/query fixtures, interruption/offline UI and profile |
| B01 | decks | M3 | AC-B01: Deck boards/roles; domain fixtures, state/undo persistence and task UI |
| B02 | decks | M3 | AC-B02: Editing and organization; domain fixtures, state/undo persistence and task UI |
| B03 | decks | M5 | AC-B03: Deck analysis; domain fixtures, state/undo persistence and task UI |
| B04 | decks | M5 | AC-B04: Draw/probability; domain fixtures, state/undo persistence and task UI |
| B05 | decks | M3 | AC-B05: Versions/diff; domain fixtures, state/undo persistence and task UI |
| B06 | validation | M5 | AC-B06: All-format validation; domain fixtures, state/undo persistence and task UI |
| B07 | decks | M3 | AC-B07: Native parity; domain fixtures, state/undo persistence and task UI |
| B08 | transfer | M3 | AC-B08: Deck imports/exports; authentic text/CSV/native fixture round trips, failure atomicity and UI |
| C01 | collection | M4 | AC-C01: Inventory; domain arithmetic/constraint fixtures, DB and task UI |
| C02 | pricing | M4 | AC-C02: Acquisition n; domain arithmetic/constraint fixtures, DB and task UI |
| C03 | pricing | M4 | AC-C03: Price source/currency; domain arithmetic/constraint fixtures, DB and task UI |
| C04 | pricing | M4 | AC-C04: Printing allocation/totals; domain arithmetic/constraint fixtures, DB and task UI |
| D01 | recommendations | M6 | AC-D01: Budgets; domain arithmetic/constraint fixtures, DB and task UI |
| D02 | recommendations | M6 | AC-D02: Offline recommendations; domain arithmetic/constraint fixtures, DB and task UI |
| E01 | brackets | M5 | AC-E01: Bracket rules data; domain arithmetic/constraint fixtures, DB and task UI |
| E02 | brackets | M5 | AC-E02: Bracket estimates/targets; domain arithmetic/constraint fixtures, DB and task UI |
| F01 | links | M5 | AC-F01: External links; domain fixtures, state/undo persistence and task UI |
| G01 | life | M2 | AC-G01: Players/layout; pure reducer/time/seed fixtures, persistence and offline UI, incl seat eliminate/restore, eligible-seat random pick and keyboard seat reorder |
| G02 | life | M2 | AC-G02: Life adjustments; pure reducer/time/seed fixtures, persistence and offline UI |
| G03 | life | M2 | AC-G03: Commander damage; pure reducer/time/seed fixtures, persistence and offline UI |
| G04 | life | M2 | AC-G04: Counters; pure reducer/time/seed fixtures, persistence and offline UI |
| G05 | life | M2 | AC-G05: Game designations; pure reducer/time/seed fixtures, persistence and offline UI |
| G06 | life | M2 | AC-G06: Random tools; pure reducer/time/seed fixtures, persistence and offline UI |
| G07 | life | M2 | AC-G07: Timers; pure reducer/time/seed fixtures, persistence and offline UI |
| G08 | life | M2 | AC-G08: Art/deck profiles; pure reducer/time/seed fixtures, persistence and offline UI |
| G09 | life | M2 | AC-G09: Loss/end/stats; pure reducer/time/seed fixtures, persistence and offline UI |
| G10 | life | M2 | AC-G10: Durability/undo/log; pure reducer/time/seed fixtures, persistence and offline UI |
| G11 | life | M2 | AC-G11: Life parity extras; pure reducer/time/seed fixtures, persistence and offline UI, incl game-history filter by player/deck/date |
| H01 | ai | M7 | AC-H01: Modes/context; codec/SSE/cancel fixture, domain reply tests and UI, incl Surprise named-angle prompt, stated angle and H05 grounding |
| H02 | ai | M7 | AC-H02: Cloud/custom providers; codec/SSE/cancel fixture, domain reply tests and UI |
| H03 | inference | M7 | AC-H03: On-device; native model fixture/profile/hash/resume/cancel |
| H04 | ai | M7 | AC-H04: Prompt export/pasteback; codec/SSE/cancel fixture, domain reply tests and UI |
| H05 | ai | M7 | AC-H05: Grounding/reply validation; codec/SSE/cancel fixture, domain reply tests and UI |
| H06 | ai | M7 | AC-H06: Bounded tool calling; codec/SSE/cancel fixture, domain reply tests and UI |
| H07 | vault | M7 | AC-H07: Secrets; native vault integration, corruption/backup and redaction |
| H08 | ai | M7 | AC-H08: Streaming/history/cost; codec/SSE/cancel fixture, domain reply tests and UI |
| H09 | ai | M7 | AC-H09: Prompts; codec/SSE/cancel fixture, domain reply tests and UI |
| I01 | transfer | M3/M9 | AC-I01: Lossless transfer; authentic text/CSV/native fixture round trips, failure atomicity and UI |
| I02 | transfer | M3/M9 | AC-I02: Restore safety; authentic text/CSV/native fixture round trips, failure atomicity and UI |
| J01 | shell | each milestone + M8 | AC-J01: Adaptive state; layout golden, task UI, physical reader/posture and profile |
| J02 | shell | each milestone + M8 | AC-J02: Desktop input; layout golden, task UI, physical reader/posture and profile |
| J03 | shell | each milestone + M8 | AC-J03: Accessibility/design; layout golden, task UI, physical reader/posture and profile, incl English-only externalized-string scan |
| J04 | shell | each milestone + M8 | AC-J04: Performance/visual audit; layout golden, task UI, physical reader/posture and profile |
| K01 | settings | M0/M1 + module milestones | AC-K01: Onboarding/settings; domain fixtures, state/undo persistence and task UI |
| K02 | settings | M0/M1 + module milestones | AC-K02: Attribution/diagnostics; domain fixtures, state/undo persistence and task UI |
| N01 | shell | M0 + every merge/M9 | AC-N01: Architecture/CI gates; hosted CI/scan/package and interface verification |
| N02 | network | M0 + every merge/M9 | AC-N02: Network etiquette; hosted CI/scan/package and interface verification |
| N03 | shell | M0 + every merge/M9 | AC-N03: Scope boundaries; scope/document audit |
| B09 | decks | M3 | AC-B09: Reusable packages; domain fixtures, state/undo persistence and task UI |
| B10 | decks | M3 | AC-B10: Multi-edit; domain fixtures, state/undo persistence and task UI |
| B11 | pricing | M5 | AC-B11: Printing optimizer; domain fixtures, state/undo persistence and task UI |
| B12 | decks | M5 | AC-B12: Interaction playtest; domain fixtures, state/undo persistence and task UI |
| B13 | transfer | M3 | AC-B13: Local image/QR sharing; authentic text/CSV/native fixture round trips, failure atomicity and UI |
| G12 | life | M2 | AC-G12: Variant modules; pure reducer/time/seed fixtures, persistence and offline UI |
| G13 | life | M2 | AC-G13: Seat clocks/alerts; pure reducer/time/seed fixtures, persistence and offline UI |
| G14 | transfer | M2/M3 | AC-G14: Profile/game transport; pure reducer/time/seed fixtures, persistence and offline UI |

Every parity PX-ID additionally requires feature-specific scenario evidence with its named behavior/reason and mapped SPEC tests. Skip rows require scope audit, not fake runtime tests. All P01-P12 budgets map to AC-J04 profile suite and affected feature AC-ID; all S01-S25 map to AC-J01/J02/J03/J04 screenshots/task/accessibility suite.

Reviewer clarifications: B06 all-format inventory/limited-custom-team rules added to SPEC; P06/P12 decoded artwork bounded and total RSS includes art; B10 collection OwnedLot co-owner and standalone G14 profile collision policy explicit. Named AC-B10 covers both deck and collection transaction/restart fixtures.
