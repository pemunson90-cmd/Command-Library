# R-03 Comprehensive Rules

## Question

Where are official Magic Comprehensive Rules (CR), what may Command Library store/display, and how should searchable rules, glossary, keyword links and card rulings be implemented?

All sources and failed access attempts below are dated 2026-10-11. Measurements are explicitly distinguished from browser-rendered text. No bulk file was saved. The role brief and research template were read; recommendations are proposed design, not vendor promises.

## Findings

### Official files and current version

The official rules page offers exactly three CR links in the inspected English page: DOCX, PDF and TXT. It describes CR as a reference for specific questions, rather than a beginner's book. No additional CR format or redistribution notice was present in that page's rendered content. Source: https://magic.wizards.com/en/rules (accessed 2026-10-11).

| Format | Exact currently linked URL | Verified status |
|---|---|---|
| TXT | https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt | Connector reads text; byte size/encoding UNVERIFIED. |
| PDF | https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.pdf | Effective September 25, 2026; connector reports 312 pages. |
| DOCX | https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.docx | Link verified; connector cannot decode this MIME type. |

Sources: linked files above and official rules page (accessed 2026-10-11). The version is the effective date, not a separately verified semantic version. File sizes for all three are UNVERIFIED: individual `curl -sI --max-time 10` requests returned exit 7 and no headers because shell network/proxy access failed. Do not confuse the connector's 8,017 rendered TXT lines with original bytes or physical line count. BOM, charset and CRLF/LF counts remain UNVERIFIED: the connector normalizes text and exposes no raw-byte response. The file's HTTP charset was not measured.

Reproducible measurements once shell access works, not successful measurements in this run:

```bash
cr_url='https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt'
curl -fsSI "$cr_url"
curl -fsS "$cr_url" | python -c 'import sys; b=sys.stdin.buffer.read(); print("bytes",len(b),"prefix",b[:4].hex(),"CRLF",b.count(b"\r\n"),"LF",b.count(b"\n")); b.decode("utf-8",errors="strict"); print("strict UTF-8 accepted")'
```

Repeat HEAD for the PDF/DOCX URLs. A missing Content-Length means stream and count actual bytes, not estimate from page count. Source for measurement targets: the exact official links above (access attempt 2026-10-11).

### Update history and detection

Official evidence establishes multiple rules updates across the last two years; it does not establish a fixed interval or exhaustive version archive.

| Evidence | Date | What is verified |
|---|---|---|
| Foundations update bulletin | November 14, 2024 | Article publication, not CR effective date. https://magic.wizards.com/en/news/announcements/foundations-update-bulletin |
| Tarkir: Dragonstorm bulletin | April 9, 2025 | Article publication. https://magic.wizards.com/en/news/announcements/tarkir-dragonstorm-update-bulletin |
| Archived official CR PDF | June 6, 2025 | CR effective date. https://media.wizards.com/2025/downloads/MagicCompRules%2020250606.pdf |
| Edge of Eternities bulletin | August 21, 2025 | Article publication. https://magic.wizards.com/en/news/announcements/edge-of-eternities-update-bulletin |
| Teenage Mutant Ninja Turtles bulletin | March 11, 2026 | Article publication. https://magic.wizards.com/en/news/announcements/teenage-mutant-ninja-turtles-update-bulletin |
| Archived official CR PDF | April 17, 2026 | CR effective date. https://media.wizards.com/2026/downloads/MagicCompRules%2020260417.pdf |
| Marvel Super Heroes bulletin | June 30, 2026 | Article publication. https://magic.wizards.com/en/news/announcements/marvel-super-heroes-update-bulletin |
| Reality Fracture bulletin | September 21, 2026 | Article publication. https://magic.wizards.com/en/news/announcements/reality-fracture-update-bulletin |
| Current linked CR | September 25, 2026 | CR effective date; URLs above. |

All table sources accessed 2026-10-11. Exhaustive 2024-10-11 through 2026-10-11 version/effective-date list is UNVERIFIED after official bulletin searches and archive-PDF searches. Article dates are deliberately not substituted for version dates. Do not present this sample as every release.

Proposed client detection: fetch the official landing page and extract the TXT anchor associated with Comprehensive Rules, storing resolved URL, effective date, content hash and fetch time. The observed URL embeds year and YYYYMMDD; discover its replacement rather than generate future URLs. Re-download on link changes, and occasionally compare a content hash in case a file changes under the same URL. Conditional ETag/Last-Modified support and a rules-specific feed are UNVERIFIED: HEAD failed, and no feed was found on the inspected page. Keep a previously valid version when download or parsing fails. Source for observed structure: https://magic.wizards.com/en/rules (accessed 2026-10-11); polling strategy is design guidance.

### Terms and licensing

The Fan Content Policy states that fan content excludes "the verbatim copying and reposting of Wizards’ IP". It requires free access and an unofficial notice, preservation of existing notices, and compliance with the overriding Wizards Terms. Its FAQ discusses personal enjoyment but does not establish a full-CR republication license. Quote is 10 words; other clauses are paraphrased. Source: https://company.wizards.com/en/legal/fancontentpolicy (accessed 2026-10-11).

Wizards Terms grant use "solely for your individual and non-commercial use" under section 2.1. This limited website/services license is not a redistribution grant for an app's full rules corpus. Source: https://company.wizards.com/en/legal/terms (accessed 2026-10-11).

Conclusion: permission to publicly bundle, mirror or re-display the entire CR through an app is UNVERIFIED, and the general fan policy cannot honestly be cited as granting it. An official download being available does not settle republication rights. Personal downloading/reading through the official source has stronger support than public redistribution; whether an automated personal cache or transformed full-text viewer falls within the intended license is still UNVERIFIED. A user-imported local copy is an implementation option, not a claim of blanket legal exemption. Preserve credits/copyright and source attribution if storing an authorized copy. No Wizards contact was made. Source basis: the two policy URLs above and https://magic.wizards.com/en/rules (accessed 2026-10-11; conclusion is interpretation).

### Structure and parser

CR has nine numbered major sections, followed by glossary and credits. Rule identifiers include chapter headings (`100.`), rules (`100.1.`) and lettered subrules (`100.1a`); letters l and o are skipped. Examples and continuation text belong to their preceding rule. Contents repeat headings, so counting the whole file produces duplicates. The glossary uses term headings and following prose, including aliases. Observed references include `see rule 702.19`, lettered targets, chapter numbers and section references. Source: https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt (accessed 2026-10-11).

Proposed parsing, not an executed or validated parser:

1. Detect the body after Contents, then exact standalone Glossary and Credits boundaries after the body starts. Retain introduction and notices separately.
2. Recognize major sections with `^[1-9]\. ` and rule starts with `^(\d{3})(?:\.(\d+)([a-km-np-z])?)?\.?\s+`. Preserve original identifiers and text. Do not require every possible numeric identifier to exist.
3. Append non-heading paragraphs to their current entry; mark Example paragraphs as children. Store original order and paragraphs, not only flattened text.
4. Parse glossary using term/definition blocks and alias pointers; validate ambiguous blocks before indexing. Keep credits out of glossary counts.
5. Use a tokenizer for contextual `rule`, `rules`, and `section` references, covering lists and shorthand letter ranges. Resolve only against the parsed ID index. Unknown targets remain plain text and enter validation output. Never link arbitrary decimal numbers.
6. Store `(documentVersion, ruleId)` targets. Rebuild links and keyword maps atomically with each version. Search indexes retain rule text, title and glossary aliases.

This design follows the structure observed in the official TXT above (accessed 2026-10-11). Current rule and glossary counts are UNVERIFIED: shell download failed and rendered text cannot satisfy the requested byte-stream measurement. Suggested streamed rule-ID audit command, with body/contents boundaries first confirmed:

```bash
curl -fsS "$cr_url" | tr -d '\r' | sed -n '/^1\. Game Concepts$/,$p' | grep -Eo '^[0-9]{3}(\.[0-9]+[a-km-np-z]?)?' | sort -u | wc -l
```

This counts unique chapter/rule/subrule IDs and avoids duplicate contents headings; report those categories separately. Glossary measurement should isolate the body glossary up to Credits, split blank-line blocks, then count heading lines, checking continuation paragraphs and aliases. A simplistic grep count of all short lines is not trustworthy. The exact current glossary-separator grammar and a reliable pure-grep entry count are UNVERIFIED until raw TXT can be measured. Do not treat the proposed command as a recorded numeric result.

### Keywords and card rulings

Observed CR distinguishes 701 Keyword Actions and 702 Keyword Abilities. A glossary term may share a parent target: Trample and Trample Over Planeswalkers both refer to 702.19. Therefore a one-name-to-one-heading match is insufficient even before comparing Scryfall. Source: https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt (accessed 2026-10-11).

Scryfall primary documentation access failed with 403 at https://scryfall.com/docs/api/catalogs , https://scryfall.com/docs/api/cards , https://scryfall.com/docs/api/rulings and the individual keyword catalog documentation pages (attempted 2026-10-11). Direct catalog API access was unavailable through the connector; shell networking also failed. Consequently current `/catalog/keyword-abilities`, `/catalog/keyword-actions`, `/catalog/ability-words` values/counts, card `keywords` semantics, and an exhaustive mismatch report are UNVERIFIED. Searches surfaced third-party client schemas but those were not promoted to verified primary API facts.

Proposed integration to verify: collect the three catalogs and card `keywords`, normalize case/Unicode only for comparison, and match 701/702 titles plus glossary aliases. Produce explicit unmatched names, many-to-one mappings and CR-only names. Ability words require a separate category and must not be forced into a 702 ability. Never synthesize a rule number for an unmatched keyword; show a safe search link instead. Confirm the exact endpoint response and licenses before shipping.

Card ruling fields requested by the task (`oracle_id`, `source`, `published_at`, `comment`, and object type), `wotc` versus `scryfall` source values, and the card's `rulings_uri` are UNVERIFIED in this run due to failed primary access. Design if confirmed: fetch the supplied rulings URI rather than infer one; join rulings by Oracle identity, share them across printings, retain publication date/source, and distinguish Scryfall notes from Wizards rulings. Do not join by card name or assume one printing ID equals an Oracle ID. Refresh rulings independently of CR versions. Verification targets: https://scryfall.com/docs/api/rulings and https://scryfall.com/docs/api/cards (attempted 2026-10-11).

### Related official documents and secondary sites

WPN currently links the English Magic Tournament Rules PDF dated February 27, 2026: https://media.wizards.com/ContentResources/WPN/MTG_MTR_2026_Feb27_EN.pdf . Connector reports 56 pages. Tournament policy is distinct from game rules. Keep an official link; optional authorized local document storage should have separate version metadata and permission evaluation. No TXT/DOCX MTR link was established on the inspected listing. Source: https://wpn.wizards.com/en/rules-documents (accessed 2026-10-11).

Commander official HTML page: https://magic.wizards.com/en/formats/commander . It provides format introduction, bracket and Game Changer links. Use CR 903 for detailed game mechanics; the marketing summary is not a substitute for exact rule parsing. No separate downloadable Commander rulebook was verified there. Keep the link and independently sourced format data, rather than mirror the full page. Source: the Commander page above (accessed 2026-10-11).

Secondary hyperlinked CR examples: https://blogs.magicjudges.org/rules/comprehensive-rules/ (current displayed effective date matches September 25, 2026), https://m.mtg.wtf/help/rules and https://crypticstudy.com/rules/ (accessed 2026-10-11). These help assess navigation patterns; their hosting is not evidence of Wizards redistribution permission and they should not replace official version authority.

## Options

- Official links plus a local user-import parser: supports implementation without publicly hosting a full corpus; rights still depend on the user's authorized copy.
- Automatic official download/cache: attractive for offline use, but permission and raw-byte verification remain unresolved.
- App-bundled searchable full CR: best first-run offline experience, but no confirmed redistribution authorization.

## Recommendation

Implement a versioned document/parser boundary with rule, example, glossary, notice and reference entities. Keep official source navigation available. Do not state public full-CR bundling is licensed by the Fan Content Policy. Resolve that narrow permission question before distributing the full corpus; document a source-link/local-import fallback if permission cannot be established. Obtain actual bytes to validate encoding, parser counts and catalog mismatch output before Gate 2 architecture treats those as settled facts. Do not silently drop unknown references or keywords.

## Risks

Permission uncertainty affects the offline full-rules brief; source links alone do not fulfill its complete offline requirement. Parser drift, duplicate contents, renamed/renumbered rules, glossary aliases, malformed downloads and stale card rulings can all cause incorrect links. Current Scryfall verification and exact byte measurements remain blocked by access failures, explicitly listed above.

## Confidence

High for current official URLs/effective date and the fan-policy limitation. Medium for parser design because byte-level validation has not run. Low for exhaustive historical versions, encoding/sizes/counts and current Scryfall mappings/ruling schema, all UNVERIFIED. No contradiction with GAUNTLET section 10's existing facts; GAUNTLET 2.A and Appendix A's full offline rules request need the permission and measurement work recorded here.
