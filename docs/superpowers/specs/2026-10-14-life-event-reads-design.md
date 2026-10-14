# Checked immutable event/archive reads

Scope cx-m2-life-event-reads-01, root-owned life/storage tests; continuous local-first execution authorized by CONTINUE.md. Prerequisite for transactional dispatch/undo, not a partial GameService registration.

## Interface and boundary

Public concrete SqlLifeEventReader(Database, LifePayloadCodecPort), matching existing store helper testing visibility; no new core port:
- suspend archive(id: GameId, eventId: Long, cancel: CancelToken): Outcome<GameEventArchive>
- suspend events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>>

Each operation uses one Database transaction. Parent game must exist: check bounded scalar revision only, not unrelated full current graph/payload. Missing game/event NOT_FOUND. Invalid caller eventID/cursor INVALID_INPUT. Malformed stored projection/payload CORRUPT_DATA; unsupported archive model version UNSUPPORTED; propagate codec unavailable/fail/cancellation unchanged. Read-only helper has no writes and is not a semantic undo/replay/source authentication certificate. Historical provenance snapshots remain codec-checked, without forcing legacy roles/current mutable definitions through current snapshot guard.

## Metadata and consistency

Metadata selection excludes archive blob; SQL CASE bounds/types before driver retrieval. Check event_id >=0, revision >=1 and <= current parent revision; groupID nonblank strict UTF8 <=65536bytes; at UTC pair typed nanos0..999999999; undo nullable typed nonnegative and != own eventID. Blob type/length checked via SQL scalar <=16MiB. Requested source/raw IDs use existing strict65KiB nonblank game identity validators and bound parameters. Parent revision must be typed/nonnegative; no eventID==revision/checkpointsequence assumption.

Fetch exact selected blobs one at a time, verify size against metadata, decode archive, archive.version==1, exact event identity/game/revision/group/time/knownUndo projection match. Model constructors already check before/after game/event and consecutive revisions. Flat Group action's groupId must equal event.groupId. Preserve original action reading.wall independently from event.at; equality is not established by the existing archive contract. Clock anchors may differ and wall chronology is not inferred. Known undo target must exist in same game at an earlier revision; query metadata only, validate it, no recursive decode or claim that target is semantically undoable. Unique schema index remains distinct from service choice authentication. Null undo projection preserves Absent/Unknown branches; it does not certify their operational completeness. No random replay generated or changed.

## Pages and limits

Ascending (revision,eventID) keyset order, revision unique index. Query at most limit+1 <=101 metadata rows, validate ALL queried metadata and strict ordering before any payload fetch. Select a prefix <=limit and <=32MiB total encoded selected archive bytes; every single16MiB archive fits, so pages never stall. Decode selected only, return immutable full GameEvent values. If rows remain, nextCursor binds exact game identity digest and last RETURNED tuple; byte-budget prefix is ordinary pagination, not history deletion or omitted remainder. More pages preserve all history; no unbounded list. Empty history success with no cursor. Page SnapshotReference uses local-life-events provider and parent revision observed in that transaction; no cross-page snapshot stability promise. Concurrent new appends may appear on subsequent pages; mutation/removal/wholehistory authority handled later.

Cursor le1:<SHA256>:<revision>:<eventID>. Digest domain-separated UTF8 source/raw identities with explicit byte lengths prevents concatenation alias and accommodates imported65KiB IDs within2048byte cursor bound. Canonical lowercase64hex/nonnegative decimal without leadingzero (revision>=1); validate exact game digest and roundtrip spelling, reject trailing/extra parts. SHA is identity binding/integrity, not secret/authentication. No java.util.Base64 dependency needed.

## Alternatives and next boundary

Returning all events is unbounded; fetching whole selected row blobs ignores metadata-first budgets. Prefer concrete checked reader, reusable metadata/transaction-local helpers later for mutation guard. Do not change SqlLifeGameStore current/settled pointer validation in this unit: Unknown/Absent/current checkpoint vs system/recovery lineage needs separate reviewed semantic policy; examining one archive/page cannot certify it. Future CAS/event/result/alert writes, currentpointer/undo selection, recovery and actual service/UI remain pending.

## Verification

Independent design/source review. Actual APIabsence and declaration-only behavior RED, then real bundledSQLite/JVM GREEN. Synthetic archives contain legacy OTHER role metadata and arbitrary nonmonotonic IDs with consecutive revisions, random values and exact provenance. Verify reopen, source/raw SQL punctuation, missing/empty, deterministic keysetlimit pages/cursor binding, mismatched every projection/UTF8/type/oversize before blob fetch, distinct provenance reading preserved/group mismatch, knownUndo target missing/later/self, unsupported archive model, cancel propagation, and32MiB byte boundary resumes last returned event. Final coherent local check/debug and continuity/Python checks; no routine hosted emulator. No service/UI/Room/concurrency/force-stop/source authority acceptance.

Independent design review APPROVE after removing invented event.at==reading.wall constraint.32MiB limits encoded selected bytes, not a measured JVM heap cap. Source review and actual tests remain pending.
