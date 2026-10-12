# K3 bulk metadata probe

- Source: `GET https://api.scryfall.com/bulk-data`; HTTP 200.
- Retrieved: 2026-10-12T18:10:29Z (UTC, request start).
- Exact response body: `bulk-metadata.json`; 3,472 bytes.
- SHA-256: `f454b3f9df03ae0d4b5c7912ae6748d9d9791b509030a36d70a4e3f79950557b`.
- Top level is a JSON object with `object`, `has_more`, `data`; `data` is an array of seven entries.
- Every entry has `object`, `id`, `type`, `updated_at`, `uri`, `name`, `description`, `jsonl_download_uri`, and `compressed_size`.
- No entry contains fields named `mime_type`, `content_type`, `encoding`, `download_uri`, `files`, or `languages`.
- Each `jsonl_download_uri` points to a `data.scryfall.io` filename ending `.jsonl.gz`; metadata includes `compressed_size` (bytes value not otherwise qualified).
- The `default_cards` description says it contains every Scryfall card object in English or, if a card is available in only one language, its printed language.
- The `all_cards` description says every card object in every language. `oracle_cards` describes one object per Oracle ID, using selected sets for recognizable current versions.
- Candidate local importer: select `default_cards` if the goal matches that stated language scope; metadata advertises a gzip-suffixed JSONL resource, suggesting a gzip-decompress-then-record-stream pipeline. Actual HTTP headers, compression bytes, and line framing remain unverified because no download URL was fetched.
- English-only completeness is not established: the description explicitly allows a printed-language object when a card is only available in one language. Exact per-row language representation is unknown.
- No source rights are granted by accessible metadata.
- Continuation delta: exact catalog fields now establish the advertised URL suffix and documented default-card scope; old R02 assumptions alone were not used.
- Next action: verify compression/framing only through a separately scoped byte probe; source-specific network acquisition remains gated on rights/contract review. This metadata request downloaded no product corpus.
