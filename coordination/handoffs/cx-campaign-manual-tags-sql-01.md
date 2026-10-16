# Manual personal vocabulary persistence — SOURCE01 frozen NEW5

## Latest continuation delta

- Worktree `/workspace/command-library-manual-tags-sql-01`; branch `agent/campaign-manual-tags-sql-20261016`.
- Unchanged base HEAD `7c5095a4ef11dcea9838456547267b1b5f6b65f4`; reviewed root contract/schema SOURCE03 remains untouched.
- Authored exactly the locked NEW5 paths below. No existing/shared files, deck codecs, schema, DI, shell or other worker paths changed.
- Verification: source inspection only; compiler/build/tests/network **NOT_RUN** as delegated. No children, commit or push.
- Next action: root independent source/durability review, byte-check these NEW5 files, integrate, then run the coherent assembled persistence/planner/app gate using the root build slot. The worker claims no runtime acceptance.

## Implementation

`SqlLocalPersonalTags(db: Database, clock: Clock): LocalPersonalTags` implements only `load` and whole ordered `replace(expected,tags,cancel)`. Never-stored load returns revision0/empty/null. Complete strict UTF8/list/payload preflight precedes IO, normalization and hashing/map work. Exact IDs are nonblank/distinct and <=1024 UTF8 bytes; exact nonblank names <=256 bytes exclude ISO control characters. Active uniqueness uses public `PersonalTagPolicy.searchKey`: NFKC, Kotlin trim(), then Locale.ROOT lowercase. Retired identities remain counted/reserved and can reactivate; a replacement cannot drop any stored identity. No hidden seeds or ID/name normalization.

Within one real Database transaction, read and exact CAS precede no-op comparison. No-op returns unchanged state without clock/write, including never-stored empty replacement. A real change advances once; revision exhaustion is rejected before clock/write. Time cannot move backwards. Writes use singleton1 and an expected-revision predicate; non-single affected-row acknowledgment fails. No internal retry, operation receipt or automatic write replay exists. Cancellation propagates unchanged; callers reload after lost acknowledgment.

`PersonalTagPoolWire` is private to the decks implementation (internal declaration): PTAG/version1, revision and timestamp, count, ordered length-prefixed ID/name UTF8 strings and exact 0/1 retirement flags. A 32-byte header plus every field is preflighted against2MiB. SQL retains length/SHA256/version and matching scalar revision/time; load validates SQL types, singleton identity, row count, version, byte length/type and bounded checksum before fetching BLOBs. Metadata reads intentionally have no singleton filter and a two-row lookahead, so extra/invalid rows cannot disappear. Decoder admits lengths/counts before allocation, rejects malformed UTF8/trailing bytes/invalid flags/IDs/names/active collisions, and verifies decoded scalar projections. Future scalar or wire versions return UNSUPPORTED. Static diagnostics strip underlying adapter text/personal names.

Pool changes never access saved decks, versions, undo, drafts, catalog or network. Pool save and an eventual deck-label application remain separate acknowledged transactions. Manual-default/suppression/provenance and full B02 completion remain root's named following work.

## Authored verification (19 tests; all NOT_RUN)

`SqlLocalPersonalTagsTest` uses the existing HostLifeDatabase over actual bundled SQLite with actual Migration18To19 table SQL. It does not claim full Room schema/native migration acceptance; root owns the broad52-old-entity preservation fixture.

Cases cover never-stored/no-op/no-clock; create/rename/reorder/retire/reactivate/reopen and exact Unicode/IDs; stale-before-no-op; permanent retired IDs; normalized active collisions/search; invalid Unicode/controls/counts/oversized ID and payload before IO; exact1024-byte IDs/256-byte names/4096 lifetime IDs; exact2MiB payload and+1 byte; scalar preflight without BLOB reads; unexpected second singleton row; checksums/revision/time projection mismatch; checksummed malformed wire/UTF8/flags/trailing bytes/duplicate IDs/active names; future versions; coherent revision exhaustion; backward/wrapped timestamp; cancellation before IO/during write rollback and cancellation after commit requiring reload; static adapter diagnostics; independent two-connection same-revision CAS.

## Frozen source SHA-256

- `android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/persistence/SqlLocalPersonalTags.kt`: `78c60f5b67e86bee956d876d7b10f6e1bcd629b4b3ae9fec7051e1c7cda41a28`
- `android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/persistence/PersonalTagPoolWire.kt`: `c9cd60ca3baeea8f1228bdabed3a17978f2e85cd6ceff7e539174a1914086c60`
- `android-app/feature/decks/src/main/kotlin/com/architectpsg/commandlibrary/feature/decks/PersonalTagPolicy.kt`: `379b1a1eff6a5960c835a32118734f5b182869584c8a31f79d3837d4af913fb4`
- `android-app/data/storage/src/test/kotlin/com/architectpsg/commandlibrary/data/storage/decks/SqlLocalPersonalTagsTest.kt`: `cc5d997dca93e025634d1ab3c70895e637cd2ac0690abb5f102ddfb863eaab3f`

The fifth hash (this handoff itself) is returned externally to avoid a recursive manifest.
