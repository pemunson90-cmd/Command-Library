export const meta = {
  name: 'trusted-profile-evidence',
  description: 'Gather code-contract, official-source and repo-ruling evidence for three trusted resource profiles (read-only)',
  phases: [
    { title: 'Evidence', detail: 'code contracts, Scryfall search, Scryfall rulings, Wizards CR, repo rulings/measurements' },
  ],
}

const SCRATCH = '/tmp/claude-0/-home-user-Command-Library/84fae32b-1749-4220-9bf3-a878c67ec6fa/scratchpad'
const ROOT = SCRATCH + '/pkt'
const A = ROOT + '/android-app'
const CAT = A + '/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog'
const MODEL = A + '/core/model/src/main/kotlin/com/architectpsg/commandlibrary/core/model'
const NET = A + '/data/network/src/main/kotlin/com/architectpsg/commandlibrary/data/network'
const RULES = A + '/feature/rules/src/main/kotlin/com/architectpsg/commandlibrary/feature/rules'

const CTX = `
CONTEXT (read carefully):
- You are one evidence agent in a READ-ONLY implementation-readiness audit for Command Library (Kotlin Android MTG companion). Today is 2026-10-13.
- Repository snapshot to read: ${ROOT} (detached worktree of origin/agent/cx-full-v1-resume-01 at 315747d). Android sources: ${A}.
- NEVER modify, create, commit or delete anything inside ${ROOT} or /home/user/Command-Library. No git writes. No builds.
- The audit makes three trusted, user-downloadable resource profiles concrete: (1) CARDS = strict English cards via the existing fixed Scryfall /cards/search lang:en traversal (EnglishSearchPageCodec.INITIAL_URI, never default_cards); (2) RULINGS = Scryfall rulings bulk file (dataset is not guaranteed all-English); (3) RULES = Wizards Comprehensive Rules TXT. Output is evidence for Codex, NOT permission to implement.
- Tag every finding: VERIFIED (directly observed: code file:line, or an official page/endpoint you fetched today with exact quote and UTC fetch time), INFERRED_POLICY (a design choice or derived bound; give rationale), UNRESOLVED (cannot be established; say what would settle it).
- Use profile = cards | rulings | rules | cross (applies to several).
- Network etiquette (only agents told to use the network): use curl through the preconfigured proxy; never unset HTTPS_PROXY or disable TLS verification (if TLS/proxy trouble: see /root/.ccr/README.md). Send a truthful User-Agent "CommandLibrary-source-audit/1.0 (read-only source audit)" plus an explicit Accept header. Space requests to api.scryfall.com at least 1 second apart. Record UTC time of each fetch (date -u +%FT%TZ). NO full corpus downloads beyond the per-agent limits below.
- Downloaded bytes go ONLY into your own new empty directory ${SCRATCH}/probes/<your-agent-name>/ ; put any helper scripts in ${SCRATCH}/scripts/<your-agent-name>/ ; run Python that reads downloads with python3 -I and pass file paths as arguments. Never copy downloads into the repo.
- Be exhaustive within your scope, terse in wording, quote exact text. Your final structured output is data for a synthesizer, not prose for a human.
`

const EVIDENCE = {
  type: 'object',
  properties: {
    summary: { type: 'string' },
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          profile: { type: 'string', enum: ['cards', 'rulings', 'rules', 'cross'] },
          topic: { type: 'string' },
          claim: { type: 'string' },
          evidence: { type: 'string', description: 'file:line with short code excerpt, or URL + UTC fetch time + exact quote/header' },
          status: { type: 'string', enum: ['VERIFIED', 'INFERRED_POLICY', 'UNRESOLVED'] },
        },
        required: ['id', 'profile', 'topic', 'claim', 'evidence', 'status'],
      },
    },
    discrepancies: { type: 'array', items: { type: 'string' } },
    unknowns: { type: 'array', items: { type: 'string' } },
  },
  required: ['summary', 'findings', 'discrepancies', 'unknowns'],
}

const TASKS = [
  {
    key: 'code-engine',
    prompt: `${CTX}
YOUR SCOPE: acquisition engine + network contract map (no network use).
Read fully: ${MODEL}/AcquisitionContracts.kt, ${MODEL}/InfrastructureContracts.kt, ${MODEL}/Values.kt, ${CAT}/LocalAcquisition.kt, ${CAT}/EnglishSearchPageCodec.kt, ${CAT}/AcquisitionPayloadCodec.kt, ${CAT}/DurableArtifactFiles.kt, ${CAT}/AndroidArtifactFiles.kt, ${NET}/OkHttpNetworkClient.kt, ${NET}/WireDispatchGuard.kt. Search the whole ${A} tree (main sources, including app module and AppGraph) for any production implementation or wiring of validateTrustedRequest, endpointFor, connection, LocalAcquisition construction, OkHttpNetworkClient construction, userAgent value, BuildConfig/versionName. Skim ${A}/feature/catalog/src/test/kotlin/**/LocalAcquisitionTest.kt only to see how test profiles/limits are constructed (note any accept-all test validators).
Answer with findings (file:line each):
1. For EVERY field of DownloadIntent, AcquisitionRequest, AcquisitionSource.RemoteFile, AcquisitionSource.EnglishSearch, AcquisitionLimits, EndpointProfile, RetryPolicy, RequestSpec, AcquisitionProvenance, VerifiedArtifact: which code validates or consumes it (init block, LocalAcquisition.validate/decode/authorizeWireDispatch/transfer/remote/search/page/complete, OkHttp client), and which fields are NOT enforced by the engine (left entirely to the trusted validateTrustedRequest/endpointFor adapters).
2. Exact semantics: how OkHttp applies RequestSpec.timeout (call vs connect/read timeout); whether EndpointProfile.requestsPerSecond is enforced anywhere; RetryPolicy initialDelay/maximumDelay/honorRetryAfter usage; redirect following (who follows, max hops, how EndpointProfile.allowedRedirectOrigins vs RemoteFile.allowedRedirectOrigins vs endpointFor(actualUri) interact, header stripping across origins, https downgrade); Accept and User-Agent (is Accept hardcoded to application/json? can a RequestSpec header override it? how is userAgent supplied); Accept-Encoding/transparent gzip behaviour and the engine's Content-Encoding identity requirement; maxResponseBytes enforcement; chunk sizes emitted vs maxChunkBytes; HttpMetadata.retryAfter parsing; 429 handling (waitRateLimit) and what state the job ends in; charset handling for search pages (strict UTF-8).
3. Transfer budget arithmetic: reserve() for search pages (maxPageBytes per page) and for remote files (allowance = maxArtifactBytes or expectedBytes), what happens to transferred_bytes on success, 429, restart (MAX_ATTEMPTS), crash/interruption and resume. Derive formulas: minimum maxTransferBytes so that a profile can still complete after k interruptions/429s, for (a) search traversal with P pages of actual total bytes T, (b) remote file of size S with expectedBytes Known vs Unknown. Cite lines.
4. Provenance flow: where sourceVersion/sourceDate/notice/languageScope/consistency/etag/lastModified/finalUri/retrieval times come from; what is Absent for search; what RemoteFile records.
5. Language-scope rules in validate (ENGLISH_CARDS/ENGLISH_RULES/RULINGS_LANGUAGE_NOT_ASSERTED kind/language constraints); whether RemoteFile requires any languageScope; whether a rulings or rules RemoteFile request with given kind/language passes engine validation.
6. Consent: ConsentRecord.permits exact DownloadIntent equality; consequence when resource URI or expectedBytes changes between refreshes (dated file URLs); where consent is checked (queue, execute, every reserve/transfer/monitor, authorizeWireDispatch).
7. Gaps: any engine behaviour a profile cannot express (e.g. two-step metadata->file discovery, HTML link discovery, per-profile Accept header, decoded-size enforcement for gzip RemoteFile, expected checksum unknown, free-space preflight).`,
  },
  {
    key: 'code-consumers',
    prompt: `${CTX}
YOUR SCOPE: downstream consumer contracts for each profile (no network use).
Read: ${MODEL}/FullCatalogContracts.kt, ${MODEL}/LocalRulesContracts.kt, ${MODEL}/CatalogContracts.kt, ${CAT}/LocalBulkCatalog.kt, ${CAT}/FullCatalogDecodedInput.kt, ${CAT}/JsonLinesFramer.kt, ${CAT}/JsonArrayFramer.kt, ${CAT}/BoundedRecordBatches.kt, ${CAT}/SourceMetadataCodec.kt, ${CAT}/ScryfallCodec.kt (bounds only), ${CAT}/AcquisitionArtifactProvider.kt and ${CAT}/AndroidVerifiedArtifactLocator.kt (read-only; owned by Codex, do not propose edits to them), ${RULES}/LocalRules.kt, ${RULES}/RulesCodec.kt, all of ${RULES}/parser/*.kt, ${A}/data/storage/src/main/kotlin/com/architectpsg/commandlibrary/data/storage/schema/RulesTables.kt, ${A}/feature/settings/src/main/kotlin/com/architectpsg/commandlibrary/feature/settings/SettingsStoreFormat.kt, and grep ${A} main sources for 'ruling', 'SourceNotice', 'notice', 'freeSpace|usableSpace|StatFs|getAvailableBytes'.
Answer with findings (file:line each):
1. CARDS: exact requirements CatalogBulkImport/LocalBulkCatalog place on a VERIFIED_ARTIFACT source (provider, language, scope, framing JSONL, compression, expected bytes/checksum, sourceDate/sourceUri/notice equality, consent id), how CatalogBulkImportLimits (raw/decoded/records/record/batch/chunk/nesting) map onto AcquisitionLimits for the search profile, what provenance the installed generation persists.
2. RULES: what the rules import contract accepts today (user content URI only? any VerifiedArtifact/ENGLISH_RULES acquisition binding?), required source metadata, where effective date comes from (parsed text? caller?), source version/version label semantics, exact parser limits (max input bytes, line bytes, rule count, nesting, anything else) with numbers, encoding policy (strict UTF-8? BOM accepted/stripped? CRLF/CR/LF? control chars? NUL?), what the parser requires structurally (effective-date sentence? Contents/Glossary/Credits?). Is there any production path from a downloaded TXT artifact into rules install? If absent say so precisely.
3. RULINGS: does ANY consumer/import contract, model type, storage table or setting exist for Scryfall rulings (oracle_id, source, published_at, comment)? CachePolicy.retainRulings semantics? Any JSONL+gzip decoder reusable for rulings and its bounds? If none exists, state exactly what is missing.
4. NOTICE/ATTRIBUTION: where SourceNotice is created/persisted/displayed; any existing Scryfall or Wizards notice/attribution text; whether CatalogBulkImportSource.notice must equal artifact provenance notice.
5. DISK: any free-space preflight, staging or WAL accounting code; any existing measured staging/WAL numbers referenced in code comments.
6. Gaps: precise missing contracts/bindings per profile.`,
  },
  {
    key: 'src-cards',
    model: 'sonnet',
    prompt: `${CTX}
YOUR SCOPE: official Scryfall sources for the strict English CARDS search profile. You MAY use the network.
Read first (no network): ${CAT}/EnglishSearchPageCodec.kt (INITIAL_URI, scope map, pageNumber and decode rules).
Fetch and quote exactly (record UTC times): https://scryfall.com/docs/api (required headers, use/permission policy, attribution/endorsement rules), https://scryfall.com/docs/api/rate-limits, https://scryfall.com/docs/api/cards/search (every parameter, page size, errors), https://scryfall.com/docs/api/lists (List object fields: data, has_more, next_page, total_cards, warnings), https://scryfall.com/docs/api/errors, https://scryfall.com/docs/syntax (lang: operator), https://scryfall.com/docs/api/cards (lang field), https://scryfall.com/docs/api/languages, https://scryfall.com/docs/terms, https://scryfall.com/docs/faqs (anything on data use, attribution, caching, Fan Content Policy). Use curl -sS (or WebFetch) and strip HTML locally if needed.
Live bounded probe, AT MOST 1 request to api.scryfall.com: GET the exact INITIAL_URI once with headers Accept: application/json, Accept-Encoding: identity and the truthful UA, WITHOUT following redirects (curl without -L, -D headers file, -w '%{http_code} %{redirect_url} %{size_download}'). Save body to your probe dir. Report: status, every response header verbatim (Content-Type/charset, Content-Encoding, Content-Length, ETag, Last-Modified, Cache-Control, Vary, any rate-limit headers), any redirect, byte size, sha256, object, total_cards, has_more, len(data), all data[].object=='card' and lang=='en', duplicate ids, presence/shape of warnings, and the exact next_page string. Then evaluate next_page against the codec rules with a python3 -I script: scheme https, host api.scryfall.com, port default/443, raw path /cards/search, no userinfo/fragment, raw query length <= 2048, split on & and =, URL-decode, no duplicate keys, keys subset of {q, unique, include_extras, include_variations, include_multilingual, order, dir, page, format}, scope values exactly q=lang:en unique=prints include_extras=true include_variations=true include_multilingual=false order=set dir=asc, format absent or json, page matches [1-9][0-9]{0,6} and equals 2. Report accept/reject verbatim with the reason. Compute ceil(total_cards/175).
Also establish from docs: does search expose any snapshot/version/date or pagination-consistency guarantee? Is there any English-only bulk file? Is gzip/compression offered or required on api.scryfall.com? Required headers wording (User-Agent/Accept)? What 429 says (duration, consequences of continued overage)? Any statement about caching for 24 hours, data freshness cadence?`,
  },
  {
    key: 'src-rulings',
    model: 'sonnet',
    prompt: `${CTX}
YOUR SCOPE: official Scryfall sources for the RULINGS bulk profile. You MAY use the network.
Fetch and quote exactly (record UTC times): https://scryfall.com/docs/api/bulk-data (Bulk Data object: every field, file formats, update cadence, URL stability, compression/gzip statements, JSON vs JSONL), https://scryfall.com/docs/api/rulings (Ruling object fields: object, oracle_id, source, published_at, comment; any language statement), https://scryfall.com/docs/api/rate-limits (bulk-data endpoint limit, data.scryfall.io / file origin statements), https://scryfall.com/docs/api (use policy, required headers).
API (AT MOST 2 requests to api.scryfall.com, >= 1 s apart): GET https://api.scryfall.com/bulk-data and GET https://api.scryfall.com/bulk-data/rulings with Accept: application/json and the truthful UA. Save bodies. Report the full rulings bulk object verbatim (all fields and values, e.g. id, type, updated_at, uri, name, description, size, download_uri, content_type, content_encoding, and any jsonl_download_uri/compressed_size or similar), plus response headers (Content-Type, ETag, Last-Modified, Cache-Control).
File origin (AT MOST 4 requests to data.scryfall.io): for each distinct rulings file URL advertised (JSON and/or JSONL), one HEAD with Accept-Encoding: identity, no redirect following: status, Content-Type, Content-Encoding, Content-Length, ETag (strong or weak), Last-Modified, Accept-Ranges, Cache-Control, Server, any Location. Then ONE bounded GET of the gzip JSONL file (or whichever the profile would use) with Range: bytes=0-65535 and Accept-Encoding: identity: status (206?), Content-Range total, gzip magic bytes, decompress the prefix with python3 -I (zlib.decompressobj(47)), count complete lines, list keys of the first 3 objects, the set of 'object' and 'source' values seen, whether any 'lang' or language field exists, max line bytes in the prefix. Also one HEAD of the superseded URL https://data.scryfall.io/rulings/rulings-20261012210033.jsonl.gz (status only) to learn whether yesterday's dated file is still served.
Establish: does Scryfall publish any checksum for bulk files? Does updated_at match the filename timestamp? Is size the compressed or uncompressed size (compare with HEAD Content-Length)? Is the file URL stable or dated/rotating? Any statement that rulings text is English or language-neutral? Any statement on whether superseded files are retained?`,
  },
  {
    key: 'src-rules',
    model: 'sonnet',
    prompt: `${CTX}
YOUR SCOPE: official Wizards sources for the Comprehensive Rules TXT profile. You MAY use the network (AT MOST 8 requests to Wizards hosts total).
1. Fetch https://magic.wizards.com/en/rules (no -L first to record any redirect; then follow if needed). Extract every link to media.wizards.com (txt/pdf/docx) with its visible label and any effective-date text near it; also any other-language CR offerings. Identify the CURRENT offered TXT URL. Compare with the previously verified https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt . If the page is JS-rendered and links are not in HTML, try WebFetch or Firecrawl scrape and say which method found the link.
2. HEAD the current TXT URL with Accept-Encoding: identity, no redirect following: status, Content-Type (charset?), Content-Length, Content-Encoding, ETag (strong/weak), Last-Modified, Accept-Ranges, Cache-Control, Server/CDN, any Location.
3. ONE full GET of that TXT (about 1 MB) with Accept-Encoding: identity into your probe dir. With a python3 -I script report: byte size, sha256, compare to the recorded prior evidence (977,752 bytes, sha256 8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca), first 3 bytes (UTF-8 BOM EF BB BF?), strict UTF-8 validity (and if invalid, whether cp1252 decodes), count and top 15 non-ASCII code points, any C0 control chars other than CR/LF/TAB, NUL bytes, line-ending counts (CRLF, lone LF, lone CR), total lines, longest line in bytes, the exact effective-date sentence and its line number, presence of a 'latest version' / download statement, every line containing copyright, trademark, Wizards, Hasbro or 'All rights reserved' (quote), presence of Contents, Glossary and Credits headings, number of numbered rule lines (regex ^\\d{3}\\.\\d+[a-z]?\\.? ), and whether the date in the filename equals the effective date in the text.
4. Fetch and quote current terms: https://company.wizards.com/en/legal/terms (sections granting personal noncommercial use, restricting automated access/data mining/derivative works, reserving rights) and https://company.wizards.com/en/legal/fancontentpolicy (verbatim copying/reposting, legal notices). Note section numbers as they appear today and the UTC fetch time. Also any use statement on the rules page itself.
5. Establish: is there any official checksum, version number or machine-readable release metadata for the CR? Is the dated URL pattern documented anywhere? Are superseded dated TXT URLs still served (only if you can learn this without guessing URLs, otherwise UNRESOLVED)?`,
  },
  {
    key: 'repo-rulings',
    model: 'sonnet',
    prompt: `${CTX}
YOUR SCOPE: repository rulings, requirements and prior measured evidence that constrain the three profiles (no network use).
Read: ${ROOT}/docs/gauntlet/DECISIONS.md (all of D-13, D-14, D-17, D-22, D-24 and any line mentioning rulings, rules, language, consent, refresh, Scryfall, Wizards, attribution, notice), ${ROOT}/docs/gauntlet/COVERAGE.md (E01/E02 and any rules/rulings/download rows), ${ROOT}/docs/gauntlet/research/R-03-comprehensive-rules.md, ${ROOT}/docs/verification/k3-downloads/PERSONAL-SOURCE-REVIEW.md, FULL-SOURCE-MEASUREMENTS.md, WIRE-CONSENT-DESIGN-REVIEW.md, FULL-IMPORT-IDENTITY-REVIEW.md, ACQUISITION-DESIGN.md, ACQUISITION-INDEPENDENT-REVIEW.md, DOWNLOAD-ENGINE-REVIEW.md (download/profile/limit sections), the input drafts ${ROOT}/docs/verification/reuse-research/ENGLISH-PROFILE-DRAFT.md, ANDROID-BINDING-DRAFT.md, ANDROID-BINDING-DRAFT-REVIEW.md, EXECUTION-PREFLIGHT.md, ${ROOT}/docs/architecture/rules/ARCHITECT.md (acquisition/import/effective-date/notice sections), and find (git -C ${ROOT} ls-files | grep -iE 'GENUINE|SOURCE-EVIDENCE|SOURCE-REVIEW|SPEC|REQUIREMENT') then read the relevant rules/rulings/download portions. Also grep ${ROOT}/docs for 'ruling' to find any requirement that rulings ship in v1 and how they are displayed. Read ${ROOT}/coordination/state.json and the task card named by next_task only to learn which task owns trusted profiles (do not follow further).
Report findings with file:line and verbatim quotes:
1. Every recorded Pat ruling or requirement constraining CARDS, RULINGS, RULES acquisition: language/consent (D-14), public bundling vs personal download (D-13), refresh/automatic behaviour, notices/attribution, v1 scope (on-device AI v1.1), Fold7/signing pending.
2. Every measured value with its source doc and date: English total cards, pages, page sizes (first, last, largest), full traversal bytes, derived JSONL bytes and sha, max record bytes, retrieval interval; rulings compressed/decoded bytes, record count, max record; CR bytes/sha/effective date; any staging/WAL/disk/heap measurements (or explicit statement that none exist).
3. Discrepancies: places where the input drafts conflict with measurements or with each other (e.g. ENGLISH-PROFILE-DRAFT says only two pages were measured while FULL-SOURCE-MEASUREMENTS reports a full traversal; page cap vs largest page; anything else).
4. Existing statements about rulings language (no lang field; RULINGS_LANGUAGE_NOT_ASSERTED), rules effective date vs filename, never relabelling local policy version as producer release.
5. Which task/owner is expected to implement profiles, and any recorded acceptance cases already demanded.`,
  },
]

phase('Evidence')
async function pool(thunks, n) {
  const results = new Array(thunks.length)
  let next = 0
  async function worker() {
    while (next < thunks.length) {
      const k = next++
      try { results[k] = await thunks[k]() } catch (e) { results[k] = null }
    }
  }
  await Promise.all(Array.from({ length: Math.min(n, thunks.length) }, worker))
  return results
}

const results = await pool(TASKS.map(t => () => agent(t.prompt, {
  label: t.key, phase: 'Evidence', schema: EVIDENCE, ...(t.model ? { model: t.model } : {}),
})), 4)

const out = {}
TASKS.forEach((t, i) => { out[t.key] = results[i] })
const missing = TASKS.filter((t, i) => !results[i]).map(t => t.key)
if (missing.length) log('Missing evidence from: ' + missing.join(', '))
return out
