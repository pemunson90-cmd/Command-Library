# R-02 Card data sources

Path: `docs/gauntlet/research/R-02-card-data.md`. Task card: `coordination/tasks/cc-research-02.md`. Author: researcher (cc-research-02). Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for everything below is 2026-10-11 unless stated otherwise (clock from `date -u +%F`).

Status: Research complete with explicitly bounded verification gaps. Prior live measurements in F1 were preserved; resumed browsing returned 403 for most Scryfall documentation and its API. No bulk archive was downloaded during resume.

## Question

Which card data sources should Command Library use for Full and Lean storage modes, what do they contain, how big are they today, how often do they change, and what do their terms allow?

## Findings

### F1. Scryfall bulk data index (`GET https://api.scryfall.com/bulk-data`)

Measured with `curl -sS -H 'User-Agent: CommandLibraryGauntlet/0.1 (research; ...)' -H 'Accept: application/json' https://api.scryfall.com/bulk-data | jq`. HTTP 200, `object: list`, `has_more: false`, 7 entries.

- Current bulk object fields are exactly: `object, id, type, updated_at, uri, name, description, jsonl_download_uri, compressed_size`. There is NO `size`, NO `download_uri`, NO `content_type`, NO `content_encoding` in the live response (all absent, so `null` under jq). Source: https://api.scryfall.com/bulk-data (accessed 2026-10-11) and the docs "Fields" table at https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11), which lists id, uri, type, name, description, updated_at, jsonl_download_uri, compressed_size.
- The page states: "Each bulk file is a gzipped JSONL (JSON Lines) archive: You will specifically download a jsonl.gz archive and need to decompress or stream it on disk. They are not wrapped in tarballs (it's just .gz, not .tar.gz)". Source: https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11).
- Consequence: the card's wording "`size`, `compressed_size`, ... download URIs" maps today to `compressed_size` and `jsonl_download_uri` only. An uncompressed `size` is not published; it must be measured by streaming (see F2).

| type | id | updated_at (UTC) | compressed_size (bytes) | jsonl_download_uri |
|---|---|---|---|---|
| oracle_cards | 27bf3214-1271-490b-bdfe-c0be6c23d02e | 2026-10-10T21:01:55.638 | 24,605,753 | https://data.scryfall.io/oracle-cards/oracle-cards-20261010210155.jsonl.gz |
| unique_artwork | 6bbcf976-6369-4401-88fc-3a9e4984c305 | 2026-10-10T21:02:46.915 | 37,863,388 | https://data.scryfall.io/unique-artwork/unique-artwork-20261010210246.jsonl.gz |
| default_cards | e2ef41e3-5778-4bc2-af3f-78eca4dd9c23 | 2026-10-10T21:06:04.155 | 78,798,080 | https://data.scryfall.io/default-cards/default-cards-20261010210604.jsonl.gz |
| all_cards | 922288cb-4bef-45e1-bb30-0c2bd3d3534f | 2026-10-10T21:18:37.214 | 395,466,044 | https://data.scryfall.io/all-cards/all-cards-20261010211837.jsonl.gz |
| rulings | 06f54c0b-ab9c-452d-b35a-8297db5eb940 | 2026-10-10T21:00:33.367 | 5,422,271 | https://data.scryfall.io/rulings/rulings-20261010210033.jsonl.gz |
| art_tags | 48da5752-eeb6-4126-bf97-8829e20ad14f | 2026-10-10T21:01:21.181 | 13,025,174 | https://data.scryfall.io/art-tags/art-tags-20261010210121.jsonl.gz |
| oracle_tags | bd8df61e-5d0a-47a2-9086-40137a645b98 | 2026-10-10T21:00:37.906 | 6,016,975 | https://data.scryfall.io/oracle-tags/oracle-tags-20261010210037.jsonl.gz |

Source for the table: https://api.scryfall.com/bulk-data (accessed 2026-10-11). The docs page shows the same files in MiB-style units (23.5 MB, 36.1 MB, 75.1 MB, 377 MB, 5.17 MB, 12.4 MB, 5.74 MB), which match the byte values divided by 1,048,576. Source: https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11).

`all_cards` is reported as metadata only and was NOT downloaded (per the card).



### F2. Counts, sizes, freshness, and verification boundary

- UNVERIFIED: current `oracle_cards` and `default_cards` record counts and decompressed sizes. The inherited shell proxy is unreachable; resumed browser access to https://api.scryfall.com/bulk-data returned 403. Required probe after connectivity is restored: obtain each current `jsonl_download_uri`, then run `set -o pipefail; curl -fLsS -H 'User-Agent: CommandLibraryGauntlet/0.1 (research)' -H 'Accept: application/octet-stream' "$bulk_uri" | gzip -dc | wc -l`. Run a separate `wc -c` stream for decompressed bytes. A failed curl/decompression must never be reported as a zero count. Only stream oracle/default, never all_cards. Source attempted: https://api.scryfall.com/bulk-data (accessed 2026-10-11).
- UNVERIFIED: current daily bulk cadence wording. The bulk documentation returned 403 during resume, so the preserved F1 observation verifies one snapshot rather than a schedule. Required probe: reopen https://scryfall.com/docs/api/bulk-data and record its exact cadence statement. Client design recommendation: compare `(type, updated_at, jsonl_download_uri)` with the last successfully installed generation, rather than constructing date-based URLs. Source attempted: https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11); metadata source: https://api.scryfall.com/bulk-data (accessed 2026-10-11, prior run).
- F1's seven types and compressed bytes remain prior same-day evidence, not a fresh resumed HTTP measurement. Meanings of each export, including which language fallback default_cards uses and which printing oracle_cards chooses, are UNVERIFIED during resume. Required probe: read all seven live `description` strings and preserve them beside the metadata. Source attempted: https://api.scryfall.com/bulk-data (accessed 2026-10-11).

### F3. Field inventory and bulk coverage

Official Scryfall type definitions confirm these API card field names. Presence in today's bulk archives is separately UNVERIFIED because no archive could be streamed. Documentation is not a substitute for an observed bulk row. Source for all rows below: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts (accessed 2026-10-11).

| Requested inventory | Confirmed API schema | Today's bulk observation |
|---|---|---|
| Identity and text | `oracle_id`, `name`, `mana_cost`, `cmc`, `colors`, `color_identity`, `type_line`, `oracle_text` | UNVERIFIED |
| Stats and structure | `power`, `toughness`, `loyalty`, `defense`, `keywords`, `card_faces`, `layout` | UNVERIFIED |
| Legality and flags | `legalities`, `reserved` | UNVERIFIED |
| Printing | `set`, `collector_number`, `rarity`, `finishes`, `prices`, `image_uris` | UNVERIFIED |
| External identifiers | `multiverse_ids`, `tcgplayer_id`, `cardmarket_id` | UNVERIFIED |
| Enrichment and links | `edhrec_rank`, `penny_rank`, `produced_mana`, `all_parts`, `related_uris`, `purchase_uris` | UNVERIFIED |

Optional stats, prices, vendor IDs and ranking data need nullable storage. Reversible objects can have `oracle_id` on faces. The type file does not contain `game_changer`, so it cannot establish its current spelling or availability. Sources: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts and https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFace.ts (accessed 2026-10-11).

UNVERIFIED: exact current Game Changer field is expected to be `game_changer`, but primary live confirmation is outstanding. Required probe: stream oracle/default through a line-wise JSON parser, count rows with this key, true/false/null/missing values, and retain a non-secret representative card ID for each. Also union keys across all rows to distinguish absent fields from optional ones. Source attempted: https://scryfall.com/docs/api/cards (accessed 2026-10-11, 403).

Rulings are a separate observed bulk type in F1. UNVERIFIED: today's ruling row schema, expected candidate keys `object`, `oracle_id`, `source`, `published_at`, `comment`; do not implement those as confirmed until a live sample is parsed. The card schema exposes `rulings_uri` rather than inline ruling text. Required probe: stream the current rulings archive through a line-wise key union and sample parser, then verify joins on Oracle identity. Sources: https://api.scryfall.com/bulk-data (accessed 2026-10-11, prior run); https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts (accessed 2026-10-11); https://scryfall.com/docs/api/rulings (accessed 2026-10-11, failed fetch).

### F4. Layouts, faces, and format keys

The official type repository lists `normal`, `split`, `flip`, `transform`, `modal_dfc`, `meld`, `leveler`, `class`, `saga`, `adventure`, `mutate`, `prototype`, `battle`, `planar`, `scheme`, `vanguard`, `token`, `double_faced_token`, `emblem`, `augment`, `host`, `art_series`, `reversible_card`, `case`. This is the repository enum, not a verified exhaustive list in today's bulk. Required completeness probe: stream distinct root and face `layout` values from oracle/default and compare with current docs. Source: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/values/Layout.ts (accessed 2026-10-11).

The repository format keys are `standard`, `future`, `historic`, `gladiator`, `pioneer`, `explorer`, `modern`, `legacy`, `pauper`, `vintage`, `penny`, `commander`, `oathbreaker`, `brawl`, `alchemy`, `paupercommander`, `duel`, `oldschool`, `premodern`, `predh`, `timeless`, `standardbrawl`. Today's exhaustive legality key set is UNVERIFIED. Required probe: union `legalities` keys from live bulk, preserve unknown keys in a table rather than rejecting them. Source: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/values/Format.ts (accessed 2026-10-11).

Confirmed repository representation: split, adventure and flip have multiple faces on one side; transform and MDFC have front/back faces. Face-specific text, mana cost and stats belong to `card_faces`; double-sided faces add colors and side imagery. Root legalities and related parts remain card-level. Reversible faces additionally carry independent Oracle identities. Sources: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFace.ts and https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/Card.ts (accessed 2026-10-11).

UNVERIFIED: current battle and meld payloads. The official repository classifies battle and meld as single-faced, which must not be used to assume that all present-day battles lack `card_faces`. Required probe: inspect one live representative for every layout, especially battle, meld, reversible, new split variants; inspect `all_parts` component roles for tokens, meld ingredients and result. Source attempted: https://scryfall.com/docs/api/layouts (accessed 2026-10-11, 403). Design recommendation: ingest by actual field presence, preserve ordered faces and related IDs, and render unknown layouts with a generic face viewer.

### F5. Playable-card filtering

The schema exposes `layout`, `games`, `digital`, `oversized`, `lang`, `set_type`, `border_color`, `security_stamp` and legalities, giving independent filtering axes. Source: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts (accessed 2026-10-11).

Design recommendation: keep collectible objects in the database and apply a separate format-specific deck eligibility view. Keep tokens and emblems for related-card displays; exclude them and art_series from deck slots. Decide paper versus digital availability independently. Exclude oversized/memorabilia from ordinary deck slots without deleting collection records. Do not exclude an entire funny set, because eligibility should follow individual legality and acorn status. Keep non-English printings attached to Oracle identities; do not let an English display filter erase owned foreign printings. These are product recommendations, not claims that a single Scryfall filter already does all of them. Source basis: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts (accessed 2026-10-11).

UNVERIFIED: exact current token, memorabilia, acorn/funny and non-English query operators and the interaction of border/stamp/set fields. Required probe: consult current syntax and sample each excluded category, comparing official format legality with the proposed view; include banned cards as collection entries. Sources attempted: https://scryfall.com/docs/syntax and https://scryfall.com/docs/api/cards (accessed 2026-10-11; cards page returned 403, syntax was not retrieved).

### F6. Images and pack budgets

Official repository image enum confirms six variants, with indicative historical sizes rather than measurements. Source for this table: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/values/ImageSize.ts (accessed 2026-10-11).

| Variant | Dimensions | Repository approximate bytes, interpreted in decimal units |
|---|---|---|
| small | 146 x 204 | 10,000 |
| normal | 488 x 680 | 60,000 |
| large | 672 x 936 | 100,000 |
| png | 745 x 1040 | 1,000,000 |
| art_crop | varies | 50,000 to 100,000 |
| border_crop | 480 x 680 | 60,000 |

UNVERIFIED: required 20-card-per-variant `curl -sI` measurement. No sample was measured because the inherited shell proxy is unreachable. Required probe: select and report at least 20 diverse live printing IDs, including double-sided examples; use actual root/face `image_uris`, then `curl -fLsSI -H 'User-Agent: CommandLibraryGauntlet/0.1 (research)' -H 'Accept: image/*' "$image_uri"`. Record final `Content-Length`, status and cache headers per variant/face; an absent length is missing evidence, not zero. Do not fabricate image URLs. Source attempted: https://scryfall.com/docs/api/imagery (accessed 2026-10-11, failed fetch).

Illustrative arithmetic only, UNVERIFIED as actual download budgets: let O be the measured Oracle count, P the actual printing count for the chosen language scope, and count every required face. Deduplicate repeated cards before packing. The following ignores multiple faces and overhead, using repository indicative bytes. Source basis: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/values/ImageSize.ts (accessed 2026-10-11).

| Variant | 100 card images | 1,000 | 5,000 | One printing per Oracle | Every chosen printing |
|---|---|---|---|---|---|
| small | 1 MB | 10 MB | 50 MB | O x 10,000 B | P x 10,000 B |
| normal | 6 MB | 60 MB | 300 MB | O x 60,000 B | P x 60,000 B |
| large | 10 MB | 100 MB | 500 MB | O x 100,000 B | P x 100,000 B |
| png | 100 MB | 1 GB | 5 GB | O x 1,000,000 B | P x 1,000,000 B |
| art_crop | 5 to 10 MB | 50 to 100 MB | 250 to 500 MB | O x 50,000 to 100,000 B | P x 50,000 to 100,000 B |
| border_crop | 6 MB | 60 MB | 300 MB | O x 60,000 B | P x 60,000 B |

No numerical O/P totals are claimed. P from default_cards cannot automatically stand for every language printing. Replace each indicative mean with measured means, report sample uncertainty, and add all back faces and filesystem overhead before a Gate 2 budget is approved. Source attempted for export scope: https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11, 403).

Image host limits: the official FAQ says `*.scryfall.io` origins are outside the cited API limit; this is not evidence of unrestricted service usage or a promised unlimited rate. Bulk metadata is preferred for large image lookups. MTGJSON's FAQ says Scryfall asks heavy image consumers to cache imagery. Exact Scryfall image caching duration, replacement policy and redistribution permission remain UNVERIFIED until the imagery/API pages can be read. Sources: https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17 and https://mtgjson.com/faq/ (accessed 2026-10-11).

### F7. Lean fetch contracts and GAUNTLET section 10 recheck

Verified from the official access FAQ: relevant application-specific User-Agent, explicit Accept and HTTPS are required for well-formed API requests; sustained API traffic must stay below 10 requests/second; excessive 429 responses require reducing load, not retrying through them. Large lookups/image metadata collection should use bulk files. Direct quotes totaling 21 words: "under 10 requests per second", "set a relevant `User-Agent` header", "set an `Accept` header", "do not have these limits". The last phrase refers to other origins such as `*.scryfall.io`. This confirms the cited section 10 access facts; it does not verify any newer endpoint-specific stricter limits. Source: https://scryfall.com/docs/faqs/i-m-having-trouble-accessing-the-scryfall-api-or-i-m-blocked-17 (accessed 2026-10-11).

| Lean endpoint topic | Current primary verification | Required probe |
|---|---|---|
| `/cards/collection`: HTTP method, identifier types, max batch, not_found behavior | UNVERIFIED, official page failed; do not adopt remembered 75 as verified | Read official page, enumerate identifier forms and limits, then test one permitted request and an explicit missing identifier |
| `/cards/named`: exact/fuzzy names, set disambiguation, ambiguous match errors | UNVERIFIED, official page failed | Read official page and test exact versus ambiguous fuzzy input |
| `/cards/:id`: printing identity, errors, current replacement behavior | UNVERIFIED, not live tested | Follow an actual card object's `uri` and compare stored printing ID |
| Endpoint-specific rate limits | UNVERIFIED, rate-limits page 403 | Read current rate-limits page, apply strictest relevant limit |

Sources attempted: https://scryfall.com/docs/api/cards/collection, https://scryfall.com/docs/api/cards/named, https://scryfall.com/docs/api/cards, https://scryfall.com/docs/api/rate-limits (accessed 2026-10-11). Design recommendation: one shared queue with descriptive headers, conservative request spacing, backoff and cache; batch additions only after collection contract verification. Lean stores successfully fetched cards permanently for offline use and preserves unresolved additions visibly.

### F8. Terms and licensing

UNVERIFIED: current Scryfall clauses for data use, required attribution, paywalling, image modification/use, cache retention and bundling/redistributing bulk data inside an app. Direct retrieval of both https://scryfall.com/docs/api and https://scryfall.com/docs/terms returned 403 (accessed 2026-10-11). Search returned third-party copies but these were not treated as current primary permission. Required probe: reopen official API Additional Terms, imagery page and Terms, quote short applicable clauses separately, and distinguish offline device caching from app/server redistribution. No affirmative redistribution permission is established here. A public app's attribution and rights review remains pending this evidence.

MTGJSON publishes MIT licensing, grants software use/distribution subject to preserving notices, and disclaims warranties. Short quotes: "Permission is hereby granted, free of charge" and `THE SOFTWARE IS PROVIDED "AS IS"`. The complete legal text is linked rather than copied. Design implication: include the upstream copyright and permission notice with redistributed covered material; this license alone does not establish rights to third-party card art or override supplier terms. Source: https://mtgjson.com/license/ (accessed 2026-10-11).

### F9. MTGJSON files, formats, sizes and cross-mapping

The official file catalog describes AllPrintings as set-organized printings, AtomicCards as name-organized Oracle-like cards, AllIdentifiers as UUID-organized cards/tokens, Keywords and CardTypes as vocabularies, and AllPrices as 90-day UUID-keyed price history. CSV and Parquet archives exist for AllPrintings. Source: https://mtgjson.com/downloads/all-files/ (accessed 2026-10-11).

The following are publisher directory values observed on 2026-10-11 for files dated 2026-Oct-10, not `curl -sI` measurements. Exact header byte sizes are UNVERIFIED because the shell proxy is unreachable. Required probe for each file: `curl -fLsSI -H 'User-Agent: CommandLibraryGauntlet/0.1 (research)' -H 'Accept: application/octet-stream' 'https://mtgjson.com/api/v5/FILE'`, recording final Content-Length and Last-Modified. Source: https://mtgjson.com/api/v5/ (accessed 2026-10-11).

| File | JSON | JSON gzip | JSON xz |
|---|---|---|---|
| AllPrintings | 644.1 MiB | 174.3 MiB | 94.1 MiB |
| AtomicCards | 154.9 MiB | 49.6 MiB | 24.5 MiB |
| AllIdentifiers | 619.2 MiB | 222.6 MiB | 109.1 MiB |
| AllPrices | 1.2 GiB | 141.6 MiB | 45.7 MiB |
| Keywords | 4.3 KiB | 2.1 KiB | 2.0 KiB |
| CardTypes | 6.7 KiB | 2.9 KiB | 2.6 KiB |

The directory also confirms JSON compression via bz2/zip, SQL and PostgreSQL AllPrintings outputs, and CSV/Parquet packs. FAQ confirms SQL/SQLite exports; exact per-file SQLite availability and bytes are UNVERIFIED in this resumed pass and need file-server/HEAD inspection. Sources: https://mtgjson.com/api/v5/ and https://mtgjson.com/faq/ (accessed 2026-10-11).

MTGJSON says builds begin 1:00 AM EST and publish 9:00 AM EST, with upstream delays possible. Each file has a `.sha256` companion; Meta provides generation metadata. It does not directly provide card images. Sources: https://mtgjson.com/faq/ and https://mtgjson.com/downloads/all-files/ (accessed 2026-10-11).

The identifiers model exposes `scryfallId`, `scryfallOracleId`, `scryfallIllustrationId`, vendor IDs and MTGJSON finish-specific identifiers. Multiple faces can share `scryfallId`, so UUID-to-printing mapping is not necessarily one-to-one. Source: https://mtgjson.com/data-models/identifiers/ (accessed 2026-10-11).

MTGJSON CardSet additionally documents explicit parsed `types`/`subtypes`/`supertypes`, `leadershipSkills`, `edhrecSaltiness`, `foreignData`, `otherFaceIds`, `sourceProducts` and `isGameChanger`; its `manaValue`, `setCode` and `number` correspond conceptually to differently named Scryfall fields. UNVERIFIED: absolute absence of those concepts from every present-day Scryfall payload. Required probe: compare current complete schemas and actual rows before claiming exclusive coverage. Source: https://mtgjson.com/data-models/card/card-set/ (accessed 2026-10-11).

## Options

1. Scryfall primary in both modes; MTGJSON optional identifier/structured enrichment. Lowest conceptual split between Full/Lean; live contracts and distribution terms still need verification. Source basis: F1, F3, F7, F9 (URLs and access dates above).
2. MTGJSON Full primary, Scryfall Lean/images. Convenient exported database formats, but requires face/UUID joins and separate data generations. Source basis: https://mtgjson.com/faq/ and https://mtgjson.com/data-models/identifiers/ (accessed 2026-10-11).
3. Bulk Oracle-only Full. Smaller download but cannot satisfy the brief's every-printing requirement alone. This is a design conclusion conditional on verifying oracle export scope. Source attempted: https://scryfall.com/docs/api/bulk-data (accessed 2026-10-11, 403).

## Recommendation

Recommend Scryfall as the primary provider for both modes, conditional on the remaining contract/terms probes. Full should ingest verified Oracle identities, printings and rulings with language scope explicitly agreed; default_cards alone must not be called every-language completeness. Lean should use verified on-demand endpoints and retain all fetched gameplay/ruling data offline. Use MTGJSON as secondary enrichment and cross-mapping, not an automatic authority override when sources disagree. Source basis: F1, F3, F7, F9, all accessed 2026-10-11.

Proposed atomic refresh: check bulk metadata; stream and validate a new generation into staging; verify counts, required keys and relationship integrity; build indexes; atomically change the active generation pointer only after the whole chosen dataset validates. Store source timestamps and checksums alongside generation metadata. Keep decks/collection ownership outside replaceable provider tables. Retain the previous generation through interruption/recovery, and treat disappeared printing IDs as unresolved references rather than deleting user inventory. MTGJSON checksums can validate its optional enrichment before the same switch. These are implementation recommendations, not claims about a provider-supplied delta API. Source basis: https://api.scryfall.com/bulk-data (accessed 2026-10-11, prior run) and https://mtgjson.com/faq/ (accessed 2026-10-11).

## Risks

- Count and image measurements are blocked; Gate 2 storage/download budgets need actual probes rather than the indicative image arithmetic. Sources attempted: https://api.scryfall.com/bulk-data and https://scryfall.com/docs/api/imagery (accessed 2026-10-11).
- Repository enums are not exhaustive live schema proof: Game Changer is missing there and battle grouping merits checking. Preserve unknown layout/format fields. Sources: https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts and https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/values/Layout.ts (accessed 2026-10-11).
- No verified current Scryfall redistribution or paywall permission; terms quotations are outstanding, explicitly UNVERIFIED, not silently granted. Sources attempted: https://scryfall.com/docs/api and https://scryfall.com/docs/terms (accessed 2026-10-11).
- F1 metadata now uses JSONL gzip and seven files. Any old JSON-array parser or assumed `size`/`download_uri` key needs updating to the preserved same-day live contract. Source: https://api.scryfall.com/bulk-data (accessed 2026-10-11, prior run).

## Confidence

Medium for provider architecture and official FAQ/MTGJSON facts; low for current bulk field completeness, record counts, exact endpoint contracts, terms and image budgets because they could not be remeasured. Every task scope item is covered either by evidence or an explicit UNVERIFIED status with the required follow-up probe. Sources and access dates are attached throughout. This satisfies bounded research reporting, not a claim that blocked measurements passed.
