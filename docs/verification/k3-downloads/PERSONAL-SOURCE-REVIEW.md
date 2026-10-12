# Independent personal-source download/API review

Reviewed 2026-10-12, approximately 21:58–22:00 UTC. Read-only repository review; output and genuine evidence are outside Git. Reviewed AGENTS.md, CONTINUE.md, D-13/D-14, SOURCE-EVIDENCE.md, SOURCE-REVIEW.md and GENUINE-INPUT-EVIDENCE.md. This is an implementation/source-contract disposition, not an upstream permission grant or a guarantee about legal interpretation.

## Disposition for the requested implementation

**Proceed with explicit in-app Download actions for personal official-source acquisition, on-device caching, and offline consultation/search.** The current evidence does not justify retaining a generic “personal cache rights unresolved” barrier for these scoped actions. Scryfall explicitly supports additional Magic software and encourages caching/local processing; Wizards explicitly offers its Comprehensive Rules as TXT for consultation and grants individual noncommercial website/service use. Those facts support ordinary retrieval of the offered file for that purpose. The public app/APK must contain downloader/parser functionality, not the downloaded corpus. Raw data export/rehosting/public fixture publication is a different use and remains outside this disposition.

The earlier SOURCE-EVIDENCE/REVIEW reports were intentionally inconclusive and preceded this narrower assessment. Their unresolved-public-republication conclusion must not be applied as a blanket ban on a user's private download/cache. No new user permission question is required to implement the personal download buttons already explicitly requested. Existing per-device durable network consent and D-14 language consent still apply when the app actually downloads.

### Scryfall: direct API acquisition and private cache

Sources, checked HTTP 200 today:

- https://scryfall.com/docs/api — data is provided for “creating additional Magic software”; “Your software must create additional value for end-users.” Restrictions include no paywall, no false endorsement, and no simple repackaging/republication/proxying.
- https://scryfall.com/docs/api/rate-limits — “We encourage you to cache the data you download from Scryfall or process it locally in your own system, at least for 24 hours.” Search/named/random/collection are currently **2 requests/second (500 ms)**, manifest 10/minute, other API methods 10/second. File origins `*.scryfall.io` are not API-rate-limited. On 429, access is limited for 30 seconds; clients must reduce overages.
- https://scryfall.com/docs/terms — automated means must not place undue burden on Scryfall.

Command Library's offline search, collection/deck integration and rules/ruling navigation supply additional end-user value. Download directly from upstream to the user's device, attribute source, avoid suggesting endorsement, and keep access to source data free. Local user cache/search is expressly supported by the cache guidance. Do not build a public proxy or redistribution endpoint.

**English default acquisition route:**

`https://api.scryfall.com/cards/search?q=lang%3Aen&unique=prints&include_extras=true&include_variations=true&include_multilingual=false&order=set&dir=asc&page=1`

- https://scryfall.com/docs/api/cards/search documents `unique=prints` as all matched prints (disables rollup), extras and variations flags, 175 rows/page, and current search rate limit.
- https://scryfall.com/docs/syntax documents the `lang:` language filter. `include_multilingual=false` alone is not an adequate language assertion: explicitly constrain `lang:en` server-side and validate every returned `lang` as `en`.
- https://scryfall.com/docs/api/lists documents `next_page`, `has_more`, `total_cards`, and warnings. Follow validated same-origin next URLs, preserving English query scope. Treat warnings as incomplete input, not silent success.
- Use one sequential request at a time with at least 600 ms between starts, accurate app/version User-Agent and Accept header. On 429 pause at least 30 seconds (or a longer supplied retry interval), then reduce rate; allow cancellation. Do not spin or retry forbidden responses.
- This is a bounded, user-triggered acquisition traversal; app searches after acquisition use the cache. The rate page says bulk must be used for **rapid** card-name/price lookups or large image resolution. Do not use this route as continuous online lookup, parallel rapid acquisition, per-launch refresh or image crawling. Normal refresh should reuse data at least 24 hours; gameplay weekly/after releases is upstream's suggested cadence.
- This route has a real cost: current total implies 663 pages and at least 6.6 minutes of request spacing at 600 ms, before transfer/import. That is an estimate, not measured import performance. No English-only bulk feed is advertised. This is the concrete supported server-side English filter route without transferring a mixed-language corpus.

**Actual bounded probe:** first page returned 175 rows, all `lang=en`, `total_cards=116009`, `has_more=true`; page 663 returned 159 rows, all `lang=en`, `has_more=false`, same total. Middle pages were not downloaded, so this is endpoint-contract evidence, not full catalog validation. The live API offers no immutable snapshot token in these responses. Stage by stable card ID, detect duplicate/missing/count drift, retain previous active generation on incomplete acquisition, and describe data as retrieved over a time interval. Do not label it an atomic source snapshot or infer a bulk `updated_at` for it.

### Bulk metadata and D-14

https://api.scryfall.com/bulk-data and https://scryfall.com/docs/api/bulk-data currently advertise **gzipped JSON Lines**, `.jsonl.gz`, using `jsonl_download_uri` and `compressed_size`. Old JSON-array/download_uri expectations must not be hard-coded.

Today's metadata: default_cards 78,862,047 compressed bytes; all_cards 396,238,170; rulings 5,422,271. These are advertised values, not measured entire transfers. `default_cards` explicitly includes English **or printed language where a card is available only in one language**. Therefore neither default_cards nor all_cards may be silently fetched as the D-14 English-only default; filtering after downloading foreign rows does not satisfy consent-before-download. Oracle/unique-artwork files also provide no English-only guarantee. Optional mixed/all-language bulk download requires clear prior explicit non-English consent; retain English-only acquisition above as the default.

### Rulings

Use the metadata object with `type=rulings`, validate its official `https://data.scryfall.io/...jsonl.gz` URI, stream gzip + JSONL, and join by `oracle_id`. https://scryfall.com/docs/api/rulings documents `source` (`wotc` or `scryfall`), `published_at` and `comment`; preserve all three, not just text. Published date is each ruling's publication date, not corpus freshness. Rulings have no `lang` field; the corpus is rulings prose, not a foreign printing download. Do not infer a foreign-card download requirement merely because it contains Oracle references to cards absent from the English catalog. There is no explicit whole-rulings language assertion in the reviewed schema, so do not claim that a metadata field certifies all comments as English.

A bounded 65,536-byte range probe of `https://data.scryfall.io/rulings/rulings-20261012210033.jsonl.gz` returned HTTP 206, Content-Range `bytes 0-65535/5422271`, application/gzip. Its gzip prefix decompressed to 305,885 bytes containing 918 complete JSONL ruling objects, with expected fields. This establishes actual gzip/JSONL framing for this feed, not full gzip CRC, completeness, final counts, or every card feed's framing. No full corpus was downloaded.

### Wizards Comprehensive Rules

- https://magic.wizards.com/en/rules — describes the CR as a reference to consult for rules questions and directly offers DOCX/PDF/TXT.
- Current offered TXT: https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt . Existing genuine local bytes start “These rules are effective as of September 25, 2026” and state that the latest version can be downloaded from the rules website.
- https://company.wizards.com/legal/terms §2.1 — limited personal individual noncommercial use license. §2.2 restricts **unauthorized** automated access/data mining and derivative works; §6.2 reserves rights not expressly granted.
- https://company.wizards.com/fancontentpolicy — excludes verbatim copying **and reposting** (example: freely distributing D&D rules/books), and says not to remove legal notices. This does not say a person cannot privately read/search a legitimately downloaded MTG reference file.

**Scope supported:** on explicit Download, retrieve the official offered TXT directly once, preserve source text/notices and its effective date, parse/index locally for private offline consultation, and retain original source identification. An app-side index for private searching is not public reposting. These clauses do not create a demonstrated requirement to email Wizards before ordinary retrieval/consultation of its offered downloadable document. This disposition does not assert an express separate license to distribute a rewritten rules database or operate a scraper/mirror.

Use the verified official TXT URL for the initial version; do not invent future dated URLs. Dynamic version discovery, if implemented, must be a bounded retrieval of the official rules listing and extraction of its offered TXT link, with host/path validation, not crawling unrelated Wizards pages. If it cannot establish a link, show the failure and retain the existing version/import fallback. A pinned URL must be labeled by actual effective date, never “latest” without checking. A terms/access change or an explicit refusal for the precise download operation is a true new barrier; absent one, do not manufacture an approval barrier from public-republication ambiguity.

## Provenance, security and acceptance limits

Store source URL, redirect destination, retrieval start/end time, source-provided version/update values, HTTP ETag/Last-Modified when present, exact received-byte hash/size, selected language scope, parser version and accepted counts. Distinguish hash over compressed transfer bytes from decoded/import bytes. Hashes prove identity, not completeness/rights. Validate TLS, HTTPS and allowed origins at each redirect/next-page step. Enforce compressed and expanded size/row limits, free-space checks, cancellation and transactional activation without deleting the prior generation. Existing 8 MiB local-input limits cannot be reused blindly for an approximately 116k-print catalog. Metadata transfer size is not final database size or disk budget proof.

For CR, prefer the actual document effective-date statement; filename, fetch time and Last-Modified are not effective date. Existing CR exact input is 977,752 bytes, SHA-256 `8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca`, per GENUINE-INPUT-EVIDENCE and local source inspection. No redownload performed here.

Scratch evidence: `/workspace/scratch/k3-source-review-pages/` contains fetched pages, byte measurements/headers and bounded genuine probes. Keep all genuine bodies outside Git/artifacts. Metadata 3,472 bytes SHA-256 `b1626592ba84e33ed75900e391b153fbde9b7968e7bdaf3840c6e9c847a19c7f`; English page1 860,496 bytes SHA-256 `af6d437e061995fbea596b9ce3cee52129cd88f4bde7a64e6f7f9fd1e8474bd1`; page663 866,303 bytes SHA-256 `b6c5aa960c815f77872efd001d354a23fc6c7ee1d0ef7772eda1412fb63adce8`; rulings compressed-prefix SHA-256 `dfbaf160b6da7b74d5f23aa3a971ef3875fb4460cd95c52a7ef32b7f1de50f2b` (prefix only). Proxy/TLS settings preserved; no credentials required; current enforced network policy was unrestricted.

True remaining acceptance work: implement and test complete bounded downloads and cancel/retry handling; measure full English coverage/count drift, full rulings parsing, staging/disk cost and offline functionality on device. This review did not run application builds/tests and does not close full functionality acceptance. Public corpus bundling/reposting is not approved; optional non-English transfer still requires D-14 consent. Neither is a reason to withhold the requested personal English download implementation.

## Latest continuation delta

Completed narrow independent source/API review and bounded official probes on 2026-10-12. Repository unchanged; no branch/commit. Verification actually run: HTTP page/metadata fetches, first/last English search page structural/language checks, bounded rulings gzip/JSONL prefix decode, existing CR header inspection. Next: orchestrator records this scoped disposition and implements explicit personal download actions using the reviewed source/language/rate contracts, then performs full-transfer and device acceptance checks.
