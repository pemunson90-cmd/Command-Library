# K3 source and fixture evidence

Checked 2026-10-12. This is a bounded source and fixture review for the active K3 task. It is not a legal opinion, a source permission grant, or a claim that product data bytes were acquired or validated. No corpus or rules text was downloaded. The only source payload fetched was Scryfall's small bulk-metadata JSON response (3,472 bytes, the size recorded by the existing K3 probe); policy pages were read as web pages. Repo inspection was read-only; this report is the sole file created.

## Governing K3 boundary

The active decision in `coordination/tasks/orch-kotlin-k3-offline-data-01.md` says K3 begins with user-selected local cards, separately dated card rulings, and rules TXT. File selection authorizes that import only; it does not grant network consent. Keep network acquisition and bundled datasets absent pending source-specific terms review, and reuse existing DataStore consent. The task also says source/version uncertainty cannot be replaced with dates inferred from filenames or local file metadata.

D-13 in `docs/gauntlet/DECISIONS.md` preserves full offline rules and a personal official-source download/cache where applicable, with local import fallback, while expressly not inferring public corpus redistribution permission. Pat's answer approved this approach and did not approve public corpus bundling. D-14 keeps English as the default and requires explicit approval before any non-English data download. These are product direction decisions, not determinations of upstream rights.

`docs/gauntlet/DOCUMENTATION_POLICY.md` makes DECISIONS the authority for durable rulings, the active task card the bounded execution authority, and old reports evidence rather than current truth. `AGENTS.md` and `CONTINUE.md` reinforce that user-selected files remain within scope while unresolved source/license changes receive review.

## Concrete evidence gaps

### E01: card source and Scryfall

The existing K3 probe (`docs/verification/k3-input-probe/README.md`) is a public metadata GET, not a card-data download. It records 3,472 response bytes and SHA-256 `f454b3f9df03ae0d4b5c7912ae6748d9d9791b509030a36d70a4e3f79950557b`; the metadata advertised seven gzip-suffixed JSONL feeds. It does not establish that any advertised URL returns gzip/JSONL bytes, that a particular feed contains all English printings, or that it can be bundled or cached under current terms.

I repeated only `GET https://api.scryfall.com/bulk-data` on 2026-10-12. It returned HTTP 200 and seven metadata objects. Current metadata says `default_cards` is “every card object on Scryfall in English or the printed language if the card is only available in one language”; `all_cards` says every language. `default_cards` therefore is not an English-only guarantee. Metadata exposes `compressed_size` and `jsonl_download_uri`; it does not prove actual compressed bytes, decompressed size, framing, current counts, completeness, or per-row language. `updated_at` is metadata time, not proof of a dataset's validated as-of date. No feed URL was followed.

The previous R-02 report states that Scryfall terms/API documentation requests returned 403 on 2026-10-11. That access condition is now stale for two directly checked pages: on 2026-10-12, `https://scryfall.com/docs/api` and `https://scryfall.com/docs/terms` returned HTTP 200. The API page currently states use is for additional Magic software/research/community content, says data may not be simply repackaged, republished, or proxied, and prohibits paywalling; it separately gives handling constraints for images. These terms are materially relevant to shipping an app corpus and require source/license review. This observation alone does not determine whether a particular local import, personal cache, transformed corpus, or app distribution complies. The Scryfall Terms page was reachable, but this bounded review found no clause resolving those specific scenarios. Do not carry forward R-02's older blanket “official pages returned 403” status for these two URLs; its other endpoint-specific and byte-measurement gaps remain.

R-02's reported 2026-10-11 metadata sizes/counts are yesterday's metadata observations, not current corpus proof. Today's metadata values differ. Current record counts, English printing coverage, actual bytes/hash, import limits/disk budget, and source-specific allowed use remain unverified.

### E02: Comprehensive Rules

R-03 records an official rules listing and a dated text URL, but explicitly says raw bytes, encoding, rule/glossary counts, and parser/catalog mismatch measurements were not acquired. Its displayed listing is evidence of an official download location, not a raw fixture. On 2026-10-12, `https://magic.wizards.com/en/rules` returned HTTP 200 and exposed official links to the Wizards Fan Content Policy and Terms. The official policy URL `https://company.wizards.com/fancontentpolicy` returned HTTP 200 and says Fan Content does not include verbatim copying and reposting of Wizards IP, including free distribution of rules content/books. The linked current terms `https://company.wizards.com/legal/terms` returned HTTP 200 and describes a limited, personal, non-exclusive, non-transferable, revocable license for individual, non-commercial use of the Websites and Services. These public clauses do not settle whether a privately held personal download, automated cache, user-imported file, or transformed searchable local display is permitted. Do not convert the personal-use wording into an affirmative permission claim.

Current effective rules version, exact source bytes, encoding, byte count, SHA-256, counts, parser behavior against genuine text, version binding, and local personal-use scope remain unverified. A current page response is not the rules corpus. No Wizards contact was made, and no actual rules TXT was fetched.

### Fixture publication risk

`docs/gauntlet/RISKS.md` RK-19 identifies the missing public-repository policy for committed authentic fixtures; current CI wording only says “small authentic sanitized fixtures” and excludes bulk data. It does not define a size ceiling, allowed content classes, derivation procedure, attribution/notice requirements, or an approval gate. RK-11 and RK-13 separately leave Wizards/Scryfall scope questions unresolved. A tiny excerpt is not automatically permitted merely because it is small, tests need it, or it is publicly accessible. Public repository visibility makes every committed byte a republication surface.

## Safe fixture publication policy draft

Adopt this as a conservative project rule, subject to the independent source/license review required by the repo:

1. **Default:** keep genuine third-party source bytes out of this public repository unless a reviewer records source-specific permission or a clearly applicable license that covers the precise copied material and its public test-fixture use. “Small”, “sanitized”, “publicly downloadable”, or “needed for tests” is not itself authorization.
2. **Synthetic tests:** use clearly labeled synthetic fixtures for parser edge cases, stress, malformed input, and bounds. They can establish parser behavior, never source completeness, real-world format shape, upstream counts, or performance on a genuine corpus.
3. **Genuine evidence without publication:** acquire only through the approved personal/user-initiated route below; keep full source bytes in ignored local storage or an access-controlled evidence artifact, never in Git/history/logs. Record source URL, user-observed acquisition date, source/version claim exactly as supplied (or `unknown`), exact byte count and SHA-256, and parser/tool revision. A hash proves byte identity only; it proves neither authorization nor semantic completeness.
4. **Public derived fixture:** publish only after source-specific review approves the exact selected bytes and required notices/attribution. Prefer a minimally sufficient record or redacted structural derivative when that derivation itself is allowed. Keep the provenance link and derivation recipe next to it; label it `genuine-derived` and state that it cannot prove the full source. Never silently sanitize or rewrite and then describe the result as raw upstream bytes.
5. **No fixture laundering:** do not commit complete feeds, full rules documents, representative “small samples” copied from sources with unresolved rights, or source payloads embedded in tests, snapshots, logs, screenshots, generated databases, APKs, or release artifacts. No fixtures containing personal data or user collection/deck data.
6. **Review gate:** before any real-source fixture enters the public repo, document exact URL, access date, exact selected bytes/lines and hash, applicable license/terms evidence, required notices, intended distribution surface, and independent reviewer disposition. If evidence is ambiguous, leave fixture local-only and use synthetic committed tests.

This policy closes RK-19's process gap without claiming the unresolved CR or Scryfall bytes are eligible for publication.

## Minimal genuine personal-acquisition validation route

This route validates the implementation's local-file contract while preserving the current no-network-app boundary:

1. The person obtains the intended file themselves from its official source in a separately authorized personal action. The app offers an explicit file picker for a local card feed, dated rulings feed, or rules TXT. Selecting a file grants permission to import that file only; it must not trigger another URL, background refresh, or language download. K3's existing task decision already authorizes this local-first input shape.
2. Before parser acceptance, retain a local evidence manifest with the literal source URL, observed download date/time, actual local byte count, SHA-256 of the exact input bytes, supplied source/version fields verbatim (unknown remains unknown), media/format observation, tool/parser revision, and chosen language scope. Distinguish source-provided `updated_at` from local retrieval time. Do not infer license, completeness, or source as-of from a filename, file timestamp, HTTP metadata description, or hash.
3. Run strict byte/encoding/framing checks and bounded import into a staging generation. Record actual accepted/rejected counts, unknown fields, language mix, malformed records, and rules/rulings references. Keep a prior active generation intact; cancellation or malformed input must leave it intact. Verify reopen/persistence and lookups from the committed local generation. A successful small sample validates only that sample and the local mechanism; it cannot close complete English printing coverage or full CR availability.
4. Keep genuine source bytes and any source-derived database local-only unless fixture review separately approves publication. Commit synthetic tests for repeatable edge cases. If the user cannot provide a genuine file, report the genuine-source validation as blocked; do not substitute the metadata response or synthetic fixtures.
5. For any future in-app network path, first obtain a separate source-specific review of current terms and endpoint contract, explicit scoped durable consent semantics, actual byte-format probe, verified source/version behavior, and reviewed size/disk/storage budgets. A successful personal import does not approve or establish a network-acquisition path.

## Current official page observations

All observations below were made 2026-10-12 through the configured HTTPS proxy with TLS verification enabled. These are page/metadata fetches only, not corpus-byte validation:

- Scryfall API documentation: https://scryfall.com/docs/api — HTTP 200; page text includes non-repackaging/non-republication and paywall restrictions.
- Scryfall Terms: https://scryfall.com/docs/terms — HTTP 200; inspected but no determination made for the acquisition/cache/import cases above.
- Scryfall bulk metadata: https://api.scryfall.com/bulk-data — HTTP 200; seven objects; no advertised file URL followed.
- Wizards rules listing: https://magic.wizards.com/en/rules — HTTP 200; official page links to the Fan Content Policy and Terms.
- Wizards Fan Content Policy: https://company.wizards.com/fancontentpolicy — HTTP 200; policy text excludes verbatim copying/reposting, including free distribution of rules content/books.
- Wizards Terms: https://company.wizards.com/legal/terms — HTTP 200; terms state personal, individual, non-commercial use license for Websites and Services.

Exact payload-byte measurements and SHA-256 were intentionally not claimed for these HTML pages; only the Scryfall metadata response size is independently recorded by the existing probe. HTTP 200 and page text are not permission determinations.

## Exact next closure actions

1. Keep K3 local-file import as the only active acquisition path; do not wire network calls or ship source corpus bytes based on this report.
2. Have the independent source/license reviewer assess the current cited Scryfall and Wizards wording against the precise planned use: personal file import/cache, transformation/search display, app bundle, and public test artifact. Record which use cases remain unresolved. Resolve public redistribution separately from personal acquisition.
3. Request one genuine, user-acquired representative card/rulings input and one genuine rules TXT input only when available through the approved personal route. Measure their exact bytes/hash/version claims and run parser checks locally. Update source counts, language coverage, encoding and schema evidence from these exact bytes; preserve unknowns.
4. Have the storage owner review K3's byte/disk budget and staging/migration against measured input, not the old 8 MiB K2 cap or metadata `compressed_size` alone. Keep public fixtures synthetic until step 2 explicitly approves exact fixture material.

## Repository evidence consulted

- `AGENTS.md`, `CONTINUE.md`, `docs/gauntlet/DOCUMENTATION_POLICY.md`
- `coordination/tasks/orch-kotlin-k3-offline-data-01.md` (local-first input decision and latest continuation delta)
- `docs/gauntlet/COVERAGE.md` evidence rows E01/E02 (card-source and rules-source gaps; these evidence IDs are distinct from requirement IDs)
- `docs/gauntlet/RISKS.md` RK-11, RK-12, RK-13, RK-19
- `docs/gauntlet/DECISIONS.md` D-13, D-14 and Pat's Gate 1 answers
- `docs/gauntlet/research/R-02-card-data.md`, `docs/gauntlet/research/R-03-comprehensive-rules.md`
- `docs/verification/k3-input-probe/README.md`
