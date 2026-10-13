# Life payload codec recovery design

Recovered from committed persistence requirements and the user's screenshot; the inaccessible other thread's unpublished text is not claimed recovered verbatim. Independent recovery reviewer approved the binary design and clarified roots on2026-10-13.

## Intent and placement

Persist complete typed life payloads without erasing unknown metadata or silently truncating histories. Implement in `:data:storage` package `life.codec`; storage already depends on core:model, and cannot depend on private feature:life reducers. Core:model remains platform-independent. No additional library or reflection serializer.

Public `LifePayloadCodec` provides typed encode/decode for GameSnapshot, PlayerProfileDraft, GameEvent, GameEventArchive and GameCheckpoint. Return Outcome<SnapshotBytes> or Outcome<T>; invalid caller payloads return INVALID_INPUT, malformed/newer/wrong-root stored bytes return CORRUPT_DATA. This is not ProfileExchange/preferences/whole-history transfer support, schema6, service or UI acceptance.

## Wire rules and retained fields

Big-endian binary frame begins magic CLIF, Int codecVersion1, byte root tag(snapshot1/profile2/event3/archive4/checkpoint5). DTO snapshot/archive versions and Room schema versions remain separate. Stable explicit tags cover every existing enum/sealed branch; never use reflection/class names or enum ordinal. Future tags/versions fail visibly; never infer defaults for missing fields.

Every currently reachable field is explicit: all snapshot/settings/seat/counter/clock/elapsed/designation/result/alert state; complete card identities with language, raw providers, Knowledge branches and Evidence; appearances/local-art/checksum/crop; variant definitions/state/actions/cards/zones/properties/provenance/notices; event actions and actual recorded random values; before/after provenance and original reading; profile setup and checkpoint lineage. No snapshot contains archives. Roundtrip preserves metadata and equality.

Strings are strict UTF8 with byte-length Int prefix<=65536; malformed UTF8 and unpaired UTF16 surrogates fail rather than replace data. Booleans and nullable markers are exactly0/1. Lengths must be nonnegative, bounded and fit remaining input before allocation. Collection readers allocate incrementally. Constructors run on decode; no bypass of their invariants. Require exact frame EOF; no trailing data.

Durations must be finite. Encode Long whole-seconds plus signed Int subsecond nanoseconds from toComponents; reconstruct with seconds.seconds+nanos.nanoseconds and require exact component equality. This preserves positive/negative largest finite values without saturating inWholeNanoseconds. Life timing infinity is not persistent state even where legacy constructors are lax; encoder rejects it visibly. Finite overtime remains representable. Timestamps preserve epochSeconds+nanos including extreme UTC seconds.

## Bounds

- Snapshot/profile frame:4MiB. Event/archive frame:16MiB. Checkpoint frame:4MiB+64KiB envelope allowance.
- Every archive before/after snapshot is a separately framed4MiB payload, with independent definition/list scopes; do not aggregate their counter counts.
- Every definitions collection<=128; event flat Group1..1024, no nested groups. Dice batches<=10000 with existing typed validation.
- Generic list count cannot exceed remaining bytes on decode; each codec list element has at least one byte. Encode is constrained by its complete frame byte budget. No whole-history collection or silent truncation.

## Validation boundary and evidence

Codec validates structural/local invariants and finite timing, retaining incomplete legacy Knowledge and unspecified roles. Creation/import/resume must separately validate complete game graphs, role/clock bindings and actual service historical lineage. Byte structure cannot prove authenticated actions, random history or transaction durability. Schema6 migration, reopen/crash/service and Fold7 remain later gates.

Tests must cover metadata-rich equality for each root, every sealed branch, finite Duration extremes and unit boundaries, deterministic bytes, strict malformed lengths/tags/UTF8/EOF, too-large strings/frames, flat group limits, archive snapshots independently at limits, and INVALID_INPUT versus CORRUPT_DATA. Source review and local storage/model/life tests precede checkpoint.
