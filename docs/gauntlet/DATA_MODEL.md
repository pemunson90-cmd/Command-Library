# DATA MODEL: Gate 2 logical contract

Logical entities and transaction rules, not a physical schema or ORM pin. SQLite/FTS5 via Flutter-compatible access is selected in principle; Gate3 storage ARCHITECT defines migrations/indexes/queries and orchestrator-owned schema registry. User tables are never discarded when source data refreshes.

## Identity, provenance and relationships

| Entity | Identity/key and core fields | Relationships/constraints |
|---|---|---|
| CatalogGeneration | generation_id, provider, source/version/date/URI/checksum, language scope, format, counts, status | staging/validated/active/retired; one active pointer per source dataset; old data retained until successful activation |
| OracleCard | source-qualified oracle_id, name, mana_cost/mv, colors/identity, types, text, stats including defense, keywords, layout, reserved, normalized search text | multi-face child entities; source null/unknown distinct from zero/empty; preserve provider ID mappings |
| CardFace | oracle_id+face_index/provider_face_id, face name/text/mana/type/stats/image refs | split/adventure/transform/meld semantics explicit; meld parts/result references not guessed by same name |
| Printing | source-qualified printing_id, oracle_id, set/collector_number, language, rarity, finish support, image URI, supplied links and vendor IDs | collector_number TEXT preserves leading zeros/suffix; finish availability relation; no unique(name,set) shortcut |
| Legality / FormatDefinition | oracle_id+format_id+generation; status. format version, sizes/boards/identity/singleton/banned/restricted/companion predicates | all source-exposed formats registry; manual/intent predicates produce unknown/review, not automatic legal |
| Ruling | source-qualified ruling_id or deterministic content/date key, oracle_id, ruling date/text/provenance | source date versus fetched_at distinct; local Lean fetched rulings stay available |
| RulesCorpus / RulesEntry | corpus version/effective date/hash/source/license; entry_id/type/number/title/text/ordinal | glossary/example/notices and numbered cross-reference edges, unresolved references retained; one validated active corpus |
| KeywordRuleLink | keyword+corpus, target entry/evidence | reviewed mapping, not invented official coverage |
| BracketRuleSet / GameChangerMember | ruleset_id/version/date/source criteria/intent flags; oracle membership with per-source evidence | source-set discrepancies block silent activation; deck assessment records exact version |
| Combo / ComboCard | source-qualified combo_id, prerequisites/results/requirements/evidence/license/date; oracle role/count | versioned attributed permitted local cache; unresolved IDs retained |
| Deck | UUID, name/format, description, folder/tags, created/modified, revision, budget/profile refs | stable user identity; snapshot analyses pinned to deck revision/catalog generation |
| DeckEntry / BoardRole / Category | UUID, deck_id, oracle/raw identity, printing_id optional, finish/language, positive quantity, board, role, notes; many-to-many labels | roles include commander/partner/background/companion; labels never multiply quantity. Unresolved raw row remains editable |
| DeckVersion / VersionEntry | version UUID, immutable deck manifest and exact entries, label/time/source revision | restoring creates new working revision and recoverable predecessor; copies are independent |
| OwnedLot | UUID, printing/raw identity, oracle mapping optional, finish/language/condition/location, quantity, notes | quantities>=0; destructive adjustments logged; foreign language ID can exist without data pack |
| Quote | provider+printing/finish+medium+statistic+native_unit+as_of, decimal amount nullable, fetched_at/source | different price statistics never merge; stale marker, confidence and optional provider quote ID retained |
| FxRate | base/quote/day/provider, decimal rate, source/fetched_at | fiat only; TIX never an assumed currency. Conversion stores rate date independently |
| AcquisitionPlan | transient deck revision+policy+quote snapshot, demand allocations and unknown rows | q/o/remaining per actual entry; owned physical quantity allocated once in one plan; no global reservation implied |
| RoleOverride / Recommendation | override deck-entry/card role; recommendation analysis revision/evidence/score/delta | automatic tag version separate; overrides survive catalog refresh; results stale when revision changes |
| Game / Seat / CommanderSlot | game UUID/settings/format/start/end, seat UUID/player/profile/deck ref; commander slots by source identity | stable IDs independent of seat order/rotation; deleted linked deck remains tombstoned reference |
| GameEvent / GameSnapshot / Result | ordered monotonic event id, group id, action/payload/undo lineage/time; checkpoint; unique game result | reducer validates events, append and snapshot transactional; undo/reopen adjusts result once; timings use monotonic/persisted anchors |
| CounterDefinition / CounterState | stable type/custom UUID, label/range/loss policy, hide flag/value by seat/slot | hidden not deleted; unknown custom loss policy none; game version preserves definitions |
| Conversation / Turn | conversation UUID/deck id/mode, prompt/version/context revision/provider/model, status/content/usage | separate conversations per deck; user-deletable; partial interrupted distinct complete; grounded card references separate raw prose |
| ProviderProfile / KeyReference | UUID/type/base/model/capabilities/dialect settings; vault reference only | no plaintext key in DB/backup; key bound to provider/origin, redact diagnostics |
| PromptTemplate | ID/shipped version/override/variables | effective prompt preview deterministic; template parse errors block sending, not unrelated app |
| DownloadJob / PackMembership | UUID/type/source/ETag/hash/offset/expected bytes/language/consent/Wi-Fi state; pinned items | approval scoped to language/pack/model/version intent; redirect/content change invalidates resume; no restore auto-download |
| Settings / Consent / SourceNotice | versioned user choices/theme/storage/cache/budget; approved scope/time; attribution/license/version/date | explicit non-English consent; consent revocation stops future matching transfer, downloaded data removal optional and previewed |
| TransferManifest / ImportIssue | schema/app version, hashes/counts/license decisions; raw row/location/issue/resolution | keys excluded; user-data restore transactional; imported IDs retain unresolved provenance |

## Additional approved parity entities

| Entity | Identity/fields | Integrity/transaction contract |
|---|---|---|
| CardPackage / PackageVersion / PackageInstance | UUID/version, entries/boards/printing/notes, linked-or-copy instance policy, entry contribution lineage | Immutable package versions; linked update diff, copied instance independent; remove contributions without deleting unrelated same-card quantities |
| PrintingOptimizationPlan | deck revision/quote generation/source/unit/ownership/pinning policy, eligible choices/objective/conflicts | Deterministic optimum over known eligible choices; unknown quotes explicitly incomplete; preview/apply atomic deck version |
| PlaytestScenario / PlaytestEvent | UUID/version/seed/deck snapshot, disruption input/manual zone event/order | No hidden opponent rules automation; conserve cards, grouped undo/replay/reset |
| VariantDefinition / VariantCard / VariantState | module id/version/source/notice, instructions/allowed actions/zones/limits, scenario card IDs/text; per-game deck/order/drawn/discarded/phase/custom state | Planechase/Archenemy/Bounty/custom namespaces; imported manifest validates before use; reducer executes only declared local actions, persists with game event/snapshot and undo. Exact source rules/rights need E13 verification |
| SeatClockState / ClockAlert | mode/initial/remaining per seat/active seat/monotonic anchor/paused, zero policy/alert-fired transition | Restart and wall-clock-safe accounting; explicit undo switch policy, alert idempotence; background OS delivery separately validated |
| ShareManifest / QrFrame | native interchange version, allowed fields/privacy choices, content hash/frame index/count | No backend link required; bounded multi-frame assembly then standard import preview; never embed keys/private histories by default |
| ProfileExchange / GameExchange | schema version/stable IDs/preferences/local art refs; game manifest/events/results/clock/variant version | Local transport reuses transfer validation; CSV lossy flattening labeled; import doesn't duplicate existing result or start optional downloads |

## Storage and indexes

Canonical Oracle FTS once per language/canonical record, not copied per printing. Independent rules FTS. Relational indexes on normalized name, color/identity bitsets, numeric stats where meaningful, legalities, printing set/number/language/finish, owned identity, deck references and quote dimensions. Typed AST selects parameterized indexed filters and explicit multi-face semantics; raw user query never interpolated into SQL. FTS syntax escapes/literal semantics documented separately from online Scryfall syntax.

Preserve original Unicode names/text and exact producer IDs alongside normalized lookup keys. Price decimal amounts are integer minor units with explicit scale or decimal text representation; TIX precision not forced to cents. No binary floating-point budget comparisons. UTC timestamps with distinct source/effective/fetched times; timer elapsed uses monotonic clock plus restart anchors. Schema version not source-data version.

## Atomicity, concurrency and cancellation

User edit transactions increment revision once; concurrent stale analyses cannot overwrite new edits. Bulk staging outside active user references, bounded streaming batches, relationship/count validation and transactional pointer activation. Reference deletion marks unresolved/tombstone instead of deleting decks/owned lots. Catalog cancellation abandons staging safely; migration never partially exposes schema. Game event group persistence and resulting derived state commit together. Restore creates pre-restore recovery point and atomically changes selected user dataset; imported preferences do not start jobs.

## Backup and retention

One-file native archive contains manifest, user tables, recoverable game/conversations, approved prompt overrides and settings; keys excluded always in initial implementation. Optional personal rules corpus inclusion respects acquisition/source notices; image/model/catalog payloads separate optional attachments with size preview, not necessary to reconstruct user state. Key references remapped to absent on another install. Base rebuild/re-download is explicit; no optional language consent inferred from restored settings. Users can delete history, images, model or language pack without deleting relevant deck/owned identities. Never retain hidden secrets in log/manifest/raw HTTP blobs.

## Future interfaces

Repositories expose immutable snapshots/results and typed errors; adapters own SQL/network/vault. Rule/validation/recommendation/life reducers operate on domain entities, no Flutter/native objects. Public signatures and path ownership are Gate3 deliverables, not invented implementation here.
