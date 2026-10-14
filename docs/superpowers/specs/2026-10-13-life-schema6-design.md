# Additive life storage schema6 design

This follows the reviewed life persistence and payload codec designs. Independent recovery review approved these six additive tables, with the concurrency/undo requirements below. No actual migration/service/device acceptance is implied by the design.

## Data authority

Game identity is the composite `(game_source, game_id)`, retaining provider/raw identity without ambiguous string concatenation. GameCheckpoint payload is authoritative current state including snapshot, settled event lineage and recovered process session. Indexed SQL fields are projections checked against that payload by later adapters; they never overwrite typed data. Creation payload bytes provide exact operation idempotence; SHA256 is only an optimization. The service packet must add a typed creation-input codec before binding.

User actions increment game revision once and append one immutable event archive. Timing settle/recovery changes checkpoint_sequence, not user event revision; every row update checks BOTH loaded revision and sequence to prevent same-revision timing writes overwriting one another. Guard sequence, revision and event counters before increment. A current checkpoint may replace its prior checkpoint atomically; original before/after/reading remains forever in action archives until explicit game deletion. Checkpoints do not become user undo actions.

## Tables and indexes

1. `life_game`: composite identity primary key; revision; globally unique creation_operation_id; creation_payload and creation_sha256; checkpoint_payload and checkpoint_sequence; status, started seconds/nano, ended optional seconds/nano, updated seconds/nano. Index status/start and stable start/nano/source/id ordering.
2. `life_event`: primary key game identity+event_id; parent game FK CASCADE; unique game+revision; group_id, event time, optional undo_of_event_id, archive_payload. Unique game+non-null undo target prevents undoing one event twice (ordinary NULL entries remain legal). Target existence/order/authenticity remains service validation. Parent PK prefix is indexed.
3. `life_seat_search`: primary key game identity+seat_id; parent FK CASCADE; original player_name and normalized player_name_folded, optional deck_source/deck_id, original/normalized recorded deck name. Elimination does not delete seats. Normalization is NFC then lowercase Locale.ROOT, with a versioned policy in the adapter; cursor contains full started seconds/nanos/source/id tuple and canonical filter.
4. `life_result`: primary key game identity; parent FK CASCADE; globally unique result_id; revision, finished seconds/nano, reason, retracted. Actual winners and typed result remain in authoritative checkpoint. Reopen retains and retracts the projection; subsequent finish stays one per-game result.
5. `life_alert`: primary key game identity+occurrence_id; parent FK CASCADE; target_kind/optional target_seat_id, fired seconds/nano, optional acknowledgement seconds/nano, delivery_mask. Index acknowledgement by game. Occurrence acknowledgement/delivery survives undo/restart; a genuine new crossing gets a new occurrence. Mask is not proof of exactly-once OS delivery.
6. `life_profile`: primary key profile_id; revision, original/normalized name, typed PlayerProfileDraft payload, updated seconds/nano. No game→profile FK; deleting reusable setup must preserve frozen game setup and referenced art/history.

All timestamp component pairs are null together or valid together; nanos0..999999999; counters nonnegative; known alert target kind with correct seat nullness; delivery mask only known bits. Where Room cannot declare SQL CHECK consistently on both fresh creation/migration, later checked adapters reject corrupt projections. No raw SQL projection certifies a valid service game.

## Migration and evidence

Root registers Migration5To6 and six entities, increments StorageSchema.VERSION to6, and retains actual KSP6 export. All eighteen prior entities and indexes remain byte-for-byte identical in exported metadata. No destructive fallback or legacy column rewrite. Only explicitly deleted game rows cascade to life children; profiles remain independent.

Local bundled SQLite tests create actual exported schema5, preserve synthetic prior rows/payloads/indexes through5→6/reopen, exercise FK and unique undo/revision constraints, CAS revision+sequence, explicit game cascade and profile preservation, and transaction rollback after forced failure. Actual export compared by script. Android-specific Room opening validation is separate: use one useful focused native test only when local evidence cannot cover Room Android integration; do not run the full emulator matrix per model/codec feature.
