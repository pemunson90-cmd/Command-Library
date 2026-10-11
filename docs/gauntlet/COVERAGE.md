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

## Open product questions

No baseline item is unmapped. Gate2 proposed budgets/design/policy details require Pat's gate ruling after independent review. Exact platform dependency pins, signatures, exclusive owned paths and implementer concurrency are intentionally Gate3 deliverables. Unknown source facts above remain explicit; no silent scope cuts or fake passing checks.

## Exhaustive parity trace

See [PARITY_ACCEPTANCE](PARITY_ACCEPTANCE.md). Imported source feature counts: R-05: 67, R-06: 64, R-07: 82. Every build/adapt row is an acceptance obligation; every skip retains approved reason. These counts are matrix rows, not competitor measurements.
