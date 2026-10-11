# R-02 Card data sources

Path: `docs/gauntlet/research/R-02-card-data.md`. Task card: `coordination/tasks/cc-research-02.md`. Author: researcher (cc-research-02). Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for everything below is 2026-10-11 unless stated otherwise (clock from `date -u +%F`).

Status: IN PROGRESS. Sections are appended as research completes.

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
