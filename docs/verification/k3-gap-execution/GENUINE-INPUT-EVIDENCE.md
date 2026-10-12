# Genuine-source local validation inputs

Acquired 2026-10-12 through the configured HTTPS proxy for local validation under the user's explicit request. Exact source payloads are held only in `/workspace/scratch/k3-genuine-inputs/`; do not copy them into Git, logs, public fixtures, build artifacts, or releases. No non-English bulk or full card corpus was downloaded.

## Files and measurements

| Local file | Official source | HTTP / type | Exact bytes | SHA-256 | Observation |
|---|---|---:|---:|---|---|
| `scryfall-time-vault-card.json` | `https://api.scryfall.com/cards/named?exact=Time%20Vault` | 200 / `application/json; charset=utf-8` | 4,968 | `51ac804f34d76edda2aad7d7f984312b4cce7806d5d9fec5ba42cd60049f8e32` | Card name Time Vault; object `lang` is `en`; Oracle ID `99d4d99d-cf56-45aa-aa39-a250695612f2`; ETag recorded in manifest. |
| `scryfall-time-vault-rulings.json` | `https://api.scryfall.com/cards/99d4d99d-cf56-45aa-aa39-a250695612f2/rulings` (copied from card object's `rulings_uri`) | 200 / `application/json; charset=utf-8` | 969 | `8d39ad6f5a2996909d04ae02be11080d4511b2cd0232aa85125f3c878728cd64` | Three entries, all associated with the same Oracle ID; `published_at` values are 2008-10-01 (two entries) and 2022-12-08. ETag recorded in manifest. |
| `wizards-comprehensive-rules-20260925.txt` | `https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt`, linked by `https://magic.wizards.com/en/rules` | 200 / `text/plain` | 977,752 | `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca` | Strict UTF-8 decode passed. Official listing/version date is 2026-09-25. HTTP Last-Modified is 2026-08-17 and is not used as the document effective date. |

Request User-Agent was `CommandLibraryGauntlet/0.1 (genuine-input-validation)`; API requests used `Accept: application/json`. No HTTP Content-Length was supplied for Scryfall API JSON; actual body byte counts above were measured after reading each response. Wizards supplied Content-Length 977752, matching the exact body byte count. Full headers, exact URLs, fetch timestamps, hashes and bounded text line measurements are in `evidence-manifest.json`.

## Scope and limits

The card's `lang=en` field is the evidence that this representative card record is English. Its returned `rulings_uri` produced three records with matching `oracle_id` and dated `published_at` values. This establishes only a genuine local card/rulings example; it does not show every English ruling is in this one-card endpoint, nor complete card, printing, or rulings coverage. The CR bytes are from the TXT URL listed on Wizards' rules page; the file decodes strictly as UTF-8.

A simple line scan found 9,410 normalized lines and 3,459 numbered-heading-like lines, with 3,312 distinct labels. Those are raw-input observations only, not accepted parser counts, glossary coverage, reference integrity, or correctness. No application parser, import, persistence, or search validation was run here.

The metadata file distinguishes fetch time, HTTP Last-Modified/ETag, card `lang`, ruling `published_at`, and the CR listing/version date. It makes no source-rights determination. The bytes are retained for the requested personal local validation and must not be treated as authorized public fixtures or redistribution material. Existing project decisions D-13/D-14 remain in force: non-English downloads still require explicit user approval; public CR redistribution permission is not inferred.
