# K2 functional foundation design

Status: draft for independent Astra architecture/durability review before consumer implementation. Accepted K1 supplies candidate dependencies and ownership, not compiled APIs or a database. Pure contract draft is isolated on agent/k2-core-contracts; Claude Code QA is isolated on agent/cc-k2-qa-01.

## Consumed capabilities

Core declares the complete twenty-component port inventory against real typed DTOs. Future aggregate ports have no registered implementations in K2. The consumed local split is CatalogLookup (bounded lookupName), CatalogStatus (persisted source/generation/date/language/count state), and CatalogImport (caller-owned byte stream, import source, progress and structured cancellation). The worker proposes exact Kotlin declarations; review covers those actual declarations, not just these names. The complete CatalogRepository remains declaration-only until all six methods work. No future-method Unsupported implementation is a foundation dependency.

Settings uses actual persisted read/update/consent semantics; an approval record stores the explicit intent but starts no job. Default Full English does not mean data is installed. Only implemented settings controls are displayed; Lean switching, downloads, vault and remote transport remain later functionality rather than pretend-success controls. If the full SettingsRepository cannot yet be truthfully implemented, use the same reviewed capability segregation and retain its aggregate declaration. Root assembly owns app wiring; all domain signatures stay free of Android/SQLite/Compose types.

## Storage schema version 1

Orchestrator owns android-app/database/registry/** and global migration order. Room3/bundled SQLite is the candidate. The initial physical schema contains only consumed catalog tables; no destructive fallback or empty future tables:

| Table | Identity and fields | Integrity |
|---|---|---|
| catalog_generation | generated UUID; provider; source URI/display name; optional truthful source version/date; observed import UTC; actual SHA-256; language scope; schema/parser version; oracle/printing counts; staging/active/retired state | Never infer upstream date from file modification/retrieval time. Separate source and observed time. UUID/provider/raw IDs remain unchanged through queries. |
| catalog_active | dataset key to generation UUID | Single persisted active pointer. Activation and state/count changes in one transaction. |
| oracle_card | generation + provider + oracle ID + language; original name/text, normalized sort/search name, complete canonical typed-record payload | One canonical row per oracle/language, not per printing. Consistent repeated oracle fields required; malformed/conflicting source fails activation. Exact fields/null/unknown/faces follow DATA_MODEL and core declarations. |
| card_face | generation + oracle identity + face index; original typed face data and supplied identities | Foreign key to canonical record; original face order and multi-face identities retained. |
| printing | generation + provider + printing ID; canonical oracle relation; set; collector number TEXT; language; rarity; supported finishes; image/source links and typed original metadata | No name/set shortcut identity, guessed face identity or collector-number integer coercion. Exact duplicate printing identity is rejected. |
| catalog_name_fts | FTS5 row for canonical oracle/language name/text, generation and canonical identity join | One row per canonical record. Explicit unicode61 tokenizer policy; bundled driver required. No platform SQLite assumption. |

Version1 creates these tables and indexes through real Room-generated schema plus the reviewed registry. Export actual Room schema for subsequent migration tests. Opening a newer unsupported schema fails with a visible recoverable error; never clear data. Future user tables have a separate lifecycle and are not owned by catalog generation cleanup. Later migrations must preserve records and consent; no schema or dataset version conflation.

## Local-file import protocol

1. User explicitly selects a local UTF-8 Scryfall JSON-array file using SAF. No product-data network transfer occurs. K2 accepts English records only; foreign printing IDs in later user-owned records remain preserved under DATA_MODEL without causing downloads. File selection is not broad pack/download consent.
2. Reader enforces maximum8MiB uncompressed input, maximum10000 printing objects, maximum128KiB per record, bounded nesting32 and maximum128 rows per staging batch. UI discloses the K2 foundation limits; full corpus streaming comes in K3. Reject invalid UTF-8, unsupported framing/schema, missing identity, invalid numeric/relationship fields and partial/truncated JSON. Do not invent absent upstream fields. Limits are deterministic failures before activation, not silently truncated success.
3. Hash exact incoming file bytes and record parser/source provenance. Allocate a new staging generation; parse incrementally and insert bounded batches. Validate canonical equality, printing/face relations, required field interpretation, counts and English scope. Large source acquisition/terms research remains with ordinary chat; executor still verifies fixture bytes and material schema claims actually consumed here.
4. Acquire a single import mutex. Queries continue against the committed active generation while staging is written; no partially initialized catalog is exposed. On cancellation/failure, retain the old active pointer and release/clean this staging generation only. CancellationException is rethrown, never reported as successful or generic source-unavailable import. Bounded NonCancellable cleanup may finish its transaction, then cancellation propagates. If cleanup fails, the abandoned staging row remains visibly recoverable and never active.
5. A final transaction validates counts/relationships, marks the previous generation retired, marks the new one active and swaps the pointer. Cancellation before commit rolls back. Cancellation arriving after durable commit cannot undo the completed activation; a later status read is authoritative. Close/reopen and process recreation confirm persisted truth. On startup, discard only abandoned staging rows; retain previous active/retired data until a later reviewed retention policy. No user table is deleted.

## Bound local lookup

Name input is trimmed with a maximum128 Unicode code points; empty input returns a truthful bounded empty page/status, not a remote fallback. Literal token/phrase/prefix behavior is documented and tested with apostrophes, punctuation, quotes, diacritics, Unicode and FTS keywords. Construct only the escaped FTS literal expression; bind it and the generation/cursor/limit as SQL parameters. Never interpolate SQL identifiers or raw user syntax.

Results join canonical records against one active generation, ordered deterministically by normalized name then producer ID. Page size1..100; fetch limit+1 to expose a genuine continuation cursor. Cursor contains generation and final sort key; validate its bounded encoding. A changed generation yields a typed stale-cursor result, not a page mixing snapshots. Page/source provenance stays pinned, source date unknown stays unknown, and original Unicode data remains available.

## Settings and UI

DataStore persists immutable versioned settings and scope-specific consent records atomically. Updates compare expected revision inside the update transaction; each committed change increments once. English is the shipped UI/data default. Restoring preferences never restores effective acquisition permission or triggers transfers. No plaintext credentials, source response logs or optional pack jobs exist in this section.

Compose presents only local catalog status, working file import/cancel, bounded lookup/results/details and implemented settings/about information. Every string is externalized English. Missing source date reads unknown; imported counts and generation are real. No card art request or hidden automatic transfer occurs. Compact/medium/expanded layouts keep controls reachable with labels and large-text support; future tabs are absent. ViewModel jobs own import cancellation; process recreation reads committed state rather than inventing previous success.

## Required acceptance evidence

Actual all-port Kotlin compile and resolved version evidence precede consumers. JVM checks cover source-qualified foreign identity, null/unknown, exact decimal/TIX units, bounded pages, stale revisions and cancellation propagation. Android instrumentation covers generated Room3/FTS5 bound MATCH, quoted/punctuation input, staging failure/cancel after writes, atomic activation, close/reopen, unsupported schema and settings revision/consent persistence. UI checks cover empty/failed/successful import, cancel, lookup, recreation and adaptive widths. Retain the hosted debug APK, checksum, tested commit and emulator install/launch results. Check packaged ELF and ZIP16KiB alignment; static AAR findings or an API36 emulator alone are insufficient 16KiB runtime evidence.

User's real research answers are integrated before affected consumers ship. Genuine fixture bytes/date/URL/hash and parser lineage must be observed by the executor; synthetic QA vectors are always labelled. Full corpus, full product coverage, physical Fold7/TalkBack/performance, restore/vault and permanent-key signed release remain later acceptance, explicitly unrun. K2 is not complete until its working finish line, all mandatory checks and independent implementation review pass.
