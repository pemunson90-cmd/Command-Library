# R-16 Deck and collection formats

Path: `docs/gauntlet/research/R-16-deck-formats.md`. Status: complete with explicit verification gaps. Access date for all sources: 2026-10-11. Samples below are short source excerpts; none is an invented vendor export. Proposed native grammar is labelled separately.

## Question

Which deck and collection formats are in common use, what grammar is actually verified, and how can import/export preserve cards, boards, printings and finishes without guessing?

## Findings

### F1. Source register and evidence categories

All source codes used below resolve to these URLs, accessed 2026-10-11:

| Code | Primary source or authentic artifact | Verification scope |
|---|---|---|
| ARENA | https://mtgarena-support.wizards.com/hc/en-us/articles/360049857771-Importing-a-Deck | Official import steps and simple list example, updated July 2026 |
| MTGO | https://www.mtgo.com/getting-started/getting-started-deckbuilding | Official text/DEK import/export existence; no full wire grammar |
| MB-D | https://manabox.app/guides/decks/import-export/ | Official optional set/number line sample and text export |
| MB-C | https://manabox.app/guides/collection/import-export/ | Official accepted CSV fields and identity requirements |
| DB | https://deckbox.org/help/exports_and_imports | Official comma CSV and UTF-8 guidance |
| GF | https://www.mtggoldfish.com/help/import_formats | Official Goldfish importer schemas, including other vendors; not proof of their current native export versions |
| GF-DECK | https://www.mtggoldfish.com/deck/arena_download/7938593 | Real Arena-target deck text displayed by Goldfish |
| TO | https://tappedout.net/mtg-decks/06-07-18-qcj-ad-nauseum/ | Real TappedOut Arena-copy widget |
| ARCH | https://archidekt.com/faq | Official Import menu points users to live Formatting Examples |
| MOX | https://github.com/moxfield/moxfield-public/wiki/Features | Historical official wiki, edited 2020; export existence |
| MOX-E | https://moxfield.nolt.io/2064 | Maintainer reply confirms Moxfield/MTGO/Arena variants and Bulk Edit tags |
| COD-D | https://cockatrice.github.io/docs/de/de9/classDeckLoader.html | Current official loader docs and extensions |
| COD-F | https://github.com/Naereen/My-Magic-the-Gathering-decks/blob/main/Multi-Colored%20Elves%20Pauper%20%28from%20MTGGoldFish%29.cod | Real user's COD artifact, not a canonical schema specification |
| FORGE | https://raw.githubusercontent.com/Card-Forge/forge/master/forge-core/src/main/java/forge/deck/io/DeckSerializer.java | Official serializer/parser source |
| FORGE-I | https://github.com/Card-Forge/forge/issues/5261 | User-supplied example in upstream issue; author explicitly warns some properties may be invalid |
| XMAGE | https://github.com/magefree/mage/issues/14422 | Upstream import interoperability issue; no complete DCK contract |
| HV | https://www.vitorcesco.com/helvault/faq.html | Official Helvault native columns and PRO distinction |
| ECHO | https://github.com/EchoMTG/mtg-csv-reader | Official Echo CSV reader, not its current native export schema |
| POOL | https://support.manapool.com/hc/en-us/articles/26131255560855-CSV-Inventory-Export-ManaBox-Format | Publisher interoperability/condition mapping |
| CSV | https://www.rfc-editor.org/info/rfc4180/ | CSV format reference |
| CARD | https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Card/CardFields.ts | Official identity/face/flavor name fields |
| SET | https://raw.githubusercontent.com/scryfall/api-types/main/src/objects/Set/Set.ts | Official separate regular/Arena/MTGO set code fields |
| A-OLD | https://magic.wizards.com/en/news/mtg-arena/mtg-arena-state-game-alchemy-2021-12-02 | Historical rebalanced name prefix |
| A-NOW | https://magic.wizards.com/en/news/mtg-arena/state-of-the-formats-2026 | Official September 2026 migration announcement |
| LTR | https://magic.wizards.com/en/news/feature/the-lord-of-the-rings-tales-of-middle-earth-release-notes | Official accented names |
| IKO | https://media.wizards.com/2020/wpn/marketing_materials/iko/iko_eac_tn_en.pdf | Official Godzilla/Zilortha name example |

UNVERIFIED is not a negative capability claim. Searches and attempted source reads are stated per family below. An importer contract is verified only for the fields actually documented or present in a real artifact; an extension or another application's compatibility claim is insufficient.

### F2. Deck format inventory and grammar coverage

For every family, the task asks about five board headers, quantity syntax, set/collector selectors, foil/etched markers, tags/categories, comments, blank lines, multi-face spelling and encoding. Any unlisted component is **UNVERIFIED** for that family, including absence of a feature. No vendor profile should be promoted as complete until those probes pass.

| Family | Verified grammar/evidence | Explicit outstanding grammar and exact sample probe |
|---|---|---|
| Plain N Card Name | ARENA sample uses integer, space, full name; names include commas/apostrophes | No universal standard for headers, comments, x suffix, finishes or encodings. Our strict baseline proposed in F7 |
| Arena import/export | ARENA shows simple integer/name input; GF-DECK shows About/Name/Commander/Deck; TO shows Deck/Sideboard and Nx quantities; MB-D shows optional `(SET) number` | Companion/Maybeboard handling, native export bytes, blank-line significance, foil/etched encoding, comments/tags and multi-face spelling UNVERIFIED. Obtain actual client exports and reimport them |
| MTGO TXT | MTGO confirms text file and clipboard import/export | Exact SIDEBOARD syntax, commander/companion representation, quantity/print/finish/comments/blank lines/name/encoding all UNVERIFIED. Export native client text with each board and a foil |
| MTGO DEK | MTGO confirms game-specific DEK | XML root/attributes, CatID namespace, commander/sideboard mapping, quantity/name/foil behavior and encoding UNVERIFIED. Obtain one native DEK and validate against current client; do not derive from unrelated XML snippets |
| Moxfield variants | MOX confirms Arena/MTGO; MOX-E confirms Moxfield format and Bulk Edit tag copy | Exact native and target text grammar, board markers, printing/finish markers, categories/comments/blanks/face names/encoding UNVERIFIED. User-initiated export fixture for each variant required |
| Archidekt text | ARCH directs users to Formatting Examples in Import menu | Entire exact extended text grammar/sample UNVERIFIED, including category punctuation and foil markers. Copy current official UI examples and actual export, then round-trip |
| MTGGoldfish text | GF-DECK real Arena-target list has Commander and Deck; names/quantities visible | Other native text variants, Companion/Sideboard/Maybeboard, printing/finish/tags/comments/encoding and exact clipboard line endings UNVERIFIED. Capture each current export option |
| TappedOut text | TO real Arena-copy widget has quantity x, `(set) number`, Deck/Sideboard | Native non-Arena export, Commander/Companion/Maybeboard, foil/etched marker semantics, tags/comments and encoding UNVERIFIED. Current widget is evidence of output, not evidence Arena accepts every represented card |
| Deckstats text | https://deckstats.net/upload-deck/en/ search snippet advertises Arena/MTGO imports; direct open failed | Entire exact grammar/sample UNVERIFIED. Retrieve current site's text-format help and actual user export; do not infer from collection CSV |
| Cockatrice COD | COD-F shows XML root `cockatrice_deck`, version 1, main/side zones and card number/name attributes; COD-D confirms COD loader | Commander/Companion/Maybeboard zones, printing/finish data, tags/comments, full schema versions, face spelling and encoding UNVERIFIED. Verify current serializer and client-produced artifact |
| Forge DCK | FORGE emits `[metadata]`, equals key/value metadata, bracketed section names and CardPool card lists; reads legacy general section | CardPool exact grammar, case/blank/comment rules, all section names, etched flags and multi-face names/encoding UNVERIFIED. Read CardPool/FileSection/FileUtil and capture native current export |
| XMage DCK | XMAGE upstream issue confirms format/interoperability concerns | Entire exact DCK grammar/sample UNVERIFIED. Inspect current upstream DCK importer/exporter and native saved deck; never route solely by .dck extension |
| ManaBox text, added | MB-D documents text file export and optional printing selectors | Added because it is a real mobile migration source. Native headers, finishes/tags/comments/blanks/face spelling/encoding UNVERIFIED; obtain actual export, separate grouped Share from reimportable Export |
| Magic Workstation/DEC, added | COD-D lists .dec and .mwDeck as accepted loader extensions | Historical interoperability leads, not complete format proof. Exact grammar/sample UNVERIFIED; obtain current real artifact before promising support |

Source codes and URLs above were accessed 2026-10-11. Moxfield current main/help pages returned JavaScript shells during the research; no undocumented service was used. XMage search did not retrieve a complete current serializer. Forge issue examples are explicitly non-authoritative and must not define parsing rules by themselves.

### F3. Authentic short deck excerpts

These are excerpts, not full valid decks. They demonstrate only observed syntax. Web extraction sometimes collapses clipboard content to one line, so no claimed original line endings are reconstructed.

**Arena official import sample**, copied contiguous lines from ARENA:

```text
1 Liliana, Death Mage

1 Liliana's Standard Bearer

3 Grim Physician
```

Source: ARENA (accessed 2026-10-11). This proves names with punctuation and plain quantities in the official example, not advanced export grammar.

**ManaBox official text example**, exact short excerpt:

```text
3 Verdant Catacombs (MH2) 260
```

Source: MB-D (accessed 2026-10-11). The docs explicitly make set and number optional. The rest of its sample is omitted; use of blank lines as a board delimiter remains UNVERIFIED.

**Goldfish real Arena-target excerpt**, as flattened by browser extraction:

```text
Commander 1 Kutzil, Malamet Exemplar Deck 1 Akroma's Will
```

Source: GF-DECK (accessed 2026-10-11). Original clipboard line endings were not exposed, so this is not a byte-faithful fixture. The deck's name mentioning Moxfield does not make this a Moxfield export.

**TappedOut real Arena-copy excerpt**, as flattened by extraction:

```text
Deck 1x Ad Nauseam (ALA) 63
```

Source: TO (accessed 2026-10-11). Another observed line uses collector number `259★` and set `000`; those are imported as unresolved source tokens, not automatically valid Scryfall identifiers. Neither indicates an authenticated native Arena export.

**Cockatrice real XML excerpt**:

```xml
<zone name="main">
<card number="3" name="Birchlore Rangers"/>
```

Source: COD-F (accessed 2026-10-11). The real file's side section contains malformed attributes in some rows. An actual artifact may itself be invalid; reject malformed XML rather than silently repairing it into a purported canonical sample.

**Forge upstream issue example excerpt**, not an endorsed serializer fixture:

```text
[main]
4 Attended Socialite|SNC|1
```

Source: FORGE-I (accessed 2026-10-11). Meaning of the trailing art index and finish suffixes requires CardPool source verification. This is a copied issue example, explicitly lower confidence than current serializer source.

**MTGO TXT/DEK, Moxfield, Archidekt, Deckstats, XMage, native ManaBox export, DEC/MWDeck samples:** UNVERIFIED, none copied. Exact required user-export/code probes appear in F2. No synthetic sample is substituted.

### F4. Collection CSV inventory

Native exact exported header rows require actual vendor documentation or artifacts. An importer allowing a flexible header set is not proof of a single mandatory export ordering. For every collection family below, unverified pieces include header order, column meanings not explicitly listed, complete condition/language enumerations, foil/etched encoding, card identity namespace, set-code namespace, collector number preservation and actual row fixture.

| Collection source | Verified information | Exact header/sample and unresolved codes |
|---|---|---|
| Moxfield | Maintainer MOX-E confirms text exports; native collection CSV not established there | UNVERIFIED native CSV header and every code/identity field. Primary main app was JS shell. Obtain owned collection and binder exports; keep any customized-column version distinct |
| Archidekt | ARCH verifies import UI, not collection wire format | UNVERIFIED header, codes, ID/finish semantics and sample. Obtain current collection CSV, including optional exported columns |
| Deckbox | DB specifies comma CSV and UTF-8; GF documents a supported Deckbox importer profile | Current full native header/sample UNVERIFIED. Edition name and foil token in GF are a consumer contract, not guaranteed present native format; verify exact current Deckbox export |
| ManaBox | MB-C accepts name plus set name/code or Scryfall ID, with quantity, foil, collector number, language, condition, purchase fields, altered/misprint; exports include binder/list | Native ordered header/sample and complete enum values UNVERIFIED. POOL documents condition translations but these are that integration's semantics, not a universal grading table |
| Dragon Shield | Search of publisher card-manager/export docs did not establish Magic CSV schema | UNVERIFIED header, profile variants, condition/language/finish/identity fields and sample. Obtain actual Magic app export; do not confuse another game's CSV or its Moxfield-compatible profile with Moxfield native |
| Delver Lens | Publisher-targeted CSV export search did not yield a current schema | UNVERIFIED native/custom templates, delimiter, headers/codes/identity and sample. Obtain current app export with recorded settings; distinguish proprietary backup from CSV |
| TCGplayer app | GF supplies one accepted app profile; MB-C instructs enabling TCGplayer ID and Product ID | Current platform-specific native headers and codes UNVERIFIED. Obtain iOS/Android exports with selected-column settings; product ID is not a Scryfall ID |
| Helvault | HV explicitly lists extras/name/scryfall_id/quantity; PRO CSV may add price/set/oracle fields | Column set verified, exact serialized order/header line/sample, extras grammar and finish/condition/language enums UNVERIFIED. Capture standard, PRO and Legacy separately |
| Echo MTG | ECHO official reader accepts name plus set selector and flexible headers, acquisition fields, condition/language, quantities | Native export header/sample UNVERIFIED. Reader demonstrates conditions such as NM and language EN but not full exported enum spec. Capture inventory CSV and current documentation |
| MTGGoldfish, added | GF gives its own six-column importer schema and foil states | Native live exporter settings/sample UNVERIFIED; schema excerpt below is official documented profile |
| MTGO collection, added | GF documents accepted MTGO CSV profile and Premium Yes/No | Current client native header byte details and sample UNVERIFIED; obtain current collection export |
| Deckstats collection, added | GF documents amount/name/foil/pinned/set ID/code columns | Native exact header/current enum details UNVERIFIED; don't treat numerical set_id as Scryfall UUID |
| MTG Studio/Decked Builder/MTGStocks/Pucatrade, added | GF lists supported profiles | Added as actual documented interoperability formats. Exact current native exports and fixture support UNVERIFIED; handle through column mapping until separately verified |

All source codes were accessed 2026-10-11. No closed-app collection data was accessed. Exact samples absent here remain blocked research evidence, not release-tested importers.

### F5. Documented CSV profile excerpts, meanings and CSV mechanics

Goldfish's own documented header (not a live native export fixture):

```csv
Card,Set ID,Set Name,Quantity,Foil,Variation
```

GF documents foil values REGULAR, FOIL and FOIL_ETCHED. Its printed sample has inconsistent field counts, so it is not adopted as a passing fixture. GF's Deckstats profile uses amount/card_name/is_foil/is_pinned/set_id/set_code, with integer-looking flags in samples. Its TCGplayer profile includes product/SKU, printing, condition/language and set/number fields. Its Deckbox example uses edition names, descriptive condition/language and a foil token. GF says its set codes follow MTGO, which can differ from other sites. Source: GF (accessed 2026-10-11). These are documented importer facts, not a current proof of each producer's full export grammar.

Helvault's documented column set, in the FAQ's listed order, is `extras`, `name`, `scryfall_id`, `quantity`. It is UNVERIFIED whether this precise order/comma serialization is its default emitted header. Source: HV (accessed 2026-10-11).

ManaBox's official accepted field list is not presented as one exact ordered export header. It supports name plus Scryfall set name/code, or Scryfall ID instead; collector number narrows printings; errors are reported with input line information. Missing set/number can therefore yield weaker identity. TCGplayer exports require selected ID columns for its import flow. Source: MB-C (accessed 2026-10-11).

POOL maps ManaBox condition strings mint/near_mint/excellent/good/light_played/played/poor to its own inventory grades. That integration maps near_mint to LP and excellent/good to MP; never equate strings to conventional grades solely by their apparent English meaning. Retain source profile and original grade before a user-confirmed conversion. Source: POOL (accessed 2026-10-11).

ECHO's reader requires card name plus a set selector and recognizes multiple aliases for fields. Its example warns that MIR is Mirage rather than Mirrodin; matching a name and unrelated set can fail. Exact native export language/condition enums remain UNVERIFIED. Source: ECHO (accessed 2026-10-11).

CSV mechanics verified from RFC 4180: records have comma-separated fields; fields containing commas, newlines or quotes are quoted; embedded quotes are doubled; a header is optional at the general CSV level and present only if the specific application/profile requires it. Source: CSV (accessed 2026-10-11). Deckbox specifically requests UTF-8 for names with special characters. Source: DB (accessed 2026-10-11). Recommendations: accept UTF-8 BOM and LF/CRLF input, preserve quoted multiline values, and make delimiter/encoding recovery visible. Do not split CSV on commas or infer one record per physical line.

### F6. Name and identifier hazards

- **Split/adventure/DFC**: CARD documents concatenated names with ` // ` and separate faces. Preserve whole names and face aliases; never interpret `//` inside a recognized name as an inline comment. Split and adventure text may expose only one face, and front-face-only versus concatenated importer acceptance is UNVERIFIED for each vendor. Example of official split naming: Breaking // Entering in A-NOW. Source: CARD/A-NOW (accessed 2026-10-11).
- **Alchemy prefixes and migration**: A-OLD documents the historical A- prefix for rebalanced identities. A-NOW says rebalanced tabletop cards reverted on 2026-09-22 across formats and Alchemy is scheduled to retire on 2027-02-02. Never globally strip A-: retain source spelling and date, consult current identity/migration data and show ambiguous or stale matches. Sources: A-OLD/A-NOW (accessed 2026-10-11).
- **Accents and punctuation**: official LTR notes include Éowyn, Shieldmaiden. Keep Unicode spelling and use normalization only to find candidates; preserve original bytes and avoid collapsing genuinely distinct identities. CSV commas in such names need quoted fields. Sources: LTR/DB/CSV (accessed 2026-10-11).
- **Alternate/Universes Beyond/Secret Lair names**: IKO shows Godzilla, King of the Monsters alongside Zilortha, Strength Incarnate. CARD has flavor_name separate from gameplay name. Resolve aliases to verified printing/Oracle identity rather than assuming every alternate name is a separate card or a cosmetic synonym. Exact current Secret Lair alias exports are UNVERIFIED; probe a live dual-name printing and a mechanically distinct Universes Beyond card. Sources: IKO/CARD (accessed 2026-10-11).
- **Set namespaces**: SET distinguishes code, arena_code and mtgo_code; GF explicitly uses MTGO codes. UNVERIFIED: a freshly checked concrete Arena/Scryfall mismatch such as DAR/DOM. Required probe: fetch current Dominaria set object and retain all codes rather than hard-code a remembered example. Source: SET/GF (accessed 2026-10-11).
- **Printing is richer than name/set**: collector_number is a string, vendor IDs use different namespaces, and Oracle identity is not printing identity. Numeric conversion loses suffixes, stars and leading zeros. Finish is separate from printing and foreign language may refer to a different record. Source: CARD (accessed 2026-10-11). Proposed resolution must prefer a valid source-scoped printing ID, then a validated set/collector/language tuple, then name/face aliases, with user review of conflicts.

### F7. Recommended parser behavior and proposed native interchange

The following is a design proposal for Command Library, not external grammar research.

1. Parse into a lossless intermediate representation first: original source text, profile, encoding, row/record coordinates, raw identity tokens, quantities, boards, tags/notes, finish/language/condition and unresolved columns. Resolve card identities separately; conflicting ID and name is an error for review, not silent correction.
2. Quantity is a positive integer for deck/ownership rows. Zero may be an explicit metadata row in a confirmed profile; never accept negative/fractional quantities accidentally. Repeated rows can merge only on equal board, printing, finish, language, condition and relevant inventory location, with a preview of the total.
3. Keep commander entries as a list, so partner/background pairs survive. Companion is a distinct role, even if a producer encodes it in sideboard. Maybeboard/considering is outside active deck totals. Do not infer commander or companion solely from a creature's name.
4. Candidate detection proceeds by content: XML root and namespace, native schema/version header, distinctive verified producer CSV header signature, verified DCK section signature, verified extended text suffixes, then simple quantity/name text. File extension is a hint only; Forge and XMage both use DCK. A URL is a link/import fallback, never a text decklist.
5. Headers alone such as Deck/Sideboard are not enough to uniquely identify a provider. Show candidate profile and unresolved lines. Do not auto-promote ambiguous plaintext to a source-specific profile. CSV with unknown headers opens column mapping with a small record preview.
6. Proposed simple text input grammar: one positive decimal integer, whitespace, then the entire nonempty card name; tolerate blank lines without changing board. Our section-aware variant explicitly recognizes Commander, Companion, Deck, Sideboard and Maybeboard as standalone headers. Unknown headings remain unresolved. No implicit x marker, tags, comments or printing suffix stripping without an explicitly selected grammar.
7. Proposed native collection/deck CSV is UTF-8 with a schema version and documented columns for entity/board, quantity, source-scoped printing ID, Oracle ID, name, set code, collector number, language, finish, condition, location, notes and tags. Use nonfoil/foil/etched as our own declared enum. Arrays should be unambiguous structured strings with normal CSV escaping. This is not labelled ManaBox/Moxfield compatible without fixture testing.
8. Use native full backup for full app round-trip, including UUIDs, history, metadata and settings. Plain text/third-party CSV are portability paths with an explicit field-loss report, not universal lossless backups. Inventory acquisition prices/currency remain separate from current market prices.

These design choices respond to verified field/CSV ambiguity in CARD/SET/CSV/MB-C/ECHO (all accessed 2026-10-11); they are not claims that any vendor applies our exact rules.

### F8. Import/export support and round-trip acceptance

| Proposed support | Import | Export | Evidence and release prerequisite |
|---|---|---|---|
| Plain quantity/name and our explicit boards | Yes | Yes | ARENA demonstrates baseline names/quantities; our extended grammar must be documented and tested |
| Arena style | Yes after profile fixtures | Yes after real client test | ARENA, MB-D, GF-DECK/TO confirm partial grammar only |
| MTGO TXT/DEK | Yes after native fixtures | Yes after current client test | MTGO confirms existence; ID/board mapping remains unverified |
| Moxfield/Archidekt/Goldfish/TappedOut/Deckstats variants | Yes for verified profiles | Only formats whose destination round-trip passes | MOX-E/ARCH/GF-DECK/TO, with gaps explicitly listed in F2 |
| Cockatrice/Forge/XMage | Import useful for migration after fixture/parser verification | Optional after client load validation | COD-D/FORGE/XMAGE; DCK ambiguity prohibits generic exporter |
| Named collection CSV profiles | Yes when verified header and codes match | Native documented CSV first; vendor profiles conditional | MB-C/HV/GF/DB/ECHO, with F4 fixture gaps |
| Unknown CSV | Column mapping with preview | Not a claimed vendor exporter | CSV and documented alias diversity in MB-C/ECHO |
| Full native backup | Lossless restore | Lossless backup | Product recommendation, separate from portable lists |

Sources above accessed 2026-10-11. Prerequisites mean unsupported exact profiles are not release-ready merely because a parser could be written.

Required round-trip corpus: actual source-produced artifacts for each promised profile and version; one main/side/maybe deck; two commanders or commander/background; a companion; quantities; foil/nonfoil/etched; specific printing IDs; split, adventure and DFC names; A- historical input; accented/punctuated/alternate names; collector suffixes; CSV embedded commas/quotes/newlines; empty optional values; contradictory ID/name; duplicate inventory rows; unresolved names; and malformed XML/CSV. These are proposed acceptance fixtures, not tests claimed run.

Define round-trip equality by semantic row identity, board, counts, printing, finish, language/condition/location and supported metadata, not whitespace. Reject silent row loss or unwanted ownership increments. For lossy destination formats, report each omitted field and check the surviving quantities/roles; vendor rendering or successful parsing alone does not prove fidelity. Import save is atomic and undoable. Reimporting the same collection file must offer explicit merge/replace semantics rather than silently doubling ownership.

### F9. Terms and licensing boundary

This report concerns user-selected local files/pasted text; support for a grammar does not authorize scraping an export endpoint or copying entire hosted collections. Moxfield-owned terms prohibit automated site access: short excerpt, "Use any robot, spider or other automatic device". Source: https://sorcery.moxfield.com/help/terms (accessed 2026-10-11). Guided user export is the recommendation until provider authorization exists.

UNVERIFIED: license implications of incorporating Cockatrice/Forge/XMage/Echo parser implementation code. No code was copied into app source; before reuse, read the exact file/repository license and choose either compliant reuse or an independently implemented documented parser. Sources investigated: COD-D/FORGE/XMAGE/ECHO (accessed 2026-10-11). Data formats and short factual schema excerpts here are research, not approval to redistribute hosted deck primers or proprietary app backups.

## Options

1. Strict known profiles plus generic CSV mapping and unresolved-row review. Recommended because it can preserve information without assuming a complete vendor contract. Evidence: MB-C/HV/ECHO/CSV (accessed 2026-10-11).
2. Broad heuristic parser that guesses missing printings and boards. Not recommended: unsupported guesses can alter collections, paired leaders and costs. Evidence of ambiguity: SET/CARD/TO/GF (accessed 2026-10-11).
3. Native full backup plus separate portable exports. Recommended alongside option 1 for lossless app migration; external text cannot be presumed to encode all metadata. This is a product architecture proposal grounded in the partial external grammar inventory.

## Recommendation

Implement the plain baseline, explicit board model, lossless import intermediate representation and a robust CSV reader/mapping preview first. Add named external adapters as authentic fixtures establish exact headers, flags and identity mappings. Support UTF-8 and quoted/multiline CSV correctly. Never silently select a printing, remove A-, truncate a collector number, discard unresolved rows or treat a foil flag as etched. Source basis: ARENA/MB-D/MB-C/DB/CSV/CARD/SET/A-OLD/A-NOW (accessed 2026-10-11).

Export documented native text/CSV plus full backup immediately in the spec; gate vendor-branded exporters on actual destination round-trip checks. Preserve all required collection attributes in native export, and disclose field losses for common text. Cross-reference R-05/R-06 export investigations through the orchestrator; no exact Moxfield/Archidekt fixture was fabricated here.

## Risks

- Exact producer samples are missing for several common families; each is UNVERIFIED with the required probe in F2/F4. Primary product docs often document the UI rather than grammar.
- Goldfish's consumer schema page contains malformed-looking examples and header punctuation. It is not a canonical proof of every native export. Source: GF (accessed 2026-10-11).
- Real public artifacts can be malformed, and web extraction can collapse whitespace. Samples in F3 are excerpts, not byte-faithful test fixtures. Sources: COD-F/GF-DECK/TO (accessed 2026-10-11).
- Provider/code versions and set aliases change. September 2026 Arena changes make blind historical A- rewriting especially unsafe. Sources: A-NOW/SET (accessed 2026-10-11).
- Collection header identity alone does not prove the app/version/profile; source-specific conditions and flags require explicit mappings. Sources: MB-C/HV/POOL/ECHO (accessed 2026-10-11).

## Confidence

Medium for the portable baseline, official partial schemas and conservative resolution strategy. Low for complete per-vendor grammar and exact native CSV fixtures, explicitly marked UNVERIFIED. All requested deck/collection families and hazard categories are accounted for; no absent fixture or round-trip test is claimed verified. All template sections are present and the file contains zero em dashes.
