package com.architectpsg.commandlibrary.core.model

/** Bounded typed persistence serialization; decoding does not validate a service graph. */
public interface LifePayloadCodecPort {
    public fun encodeCreation(value: GameCreation): Outcome<SnapshotBytes>
    public fun decodeCreation(bytes: SnapshotBytes): Outcome<GameCreation>
    public fun encodeSnapshot(value: GameSnapshot): Outcome<SnapshotBytes>
    public fun decodeSnapshot(bytes: SnapshotBytes): Outcome<GameSnapshot>
    public fun encodeProfile(value: PlayerProfileDraft): Outcome<SnapshotBytes>
    public fun decodeProfile(bytes: SnapshotBytes): Outcome<PlayerProfileDraft>
    public fun encodeEvent(value: GameEvent): Outcome<SnapshotBytes>
    public fun decodeEvent(bytes: SnapshotBytes): Outcome<GameEvent>
    public fun encodeArchive(value: GameEventArchive): Outcome<SnapshotBytes>
    public fun decodeArchive(bytes: SnapshotBytes): Outcome<GameEventArchive>
    public fun encodeCheckpoint(value: GameCheckpoint): Outcome<SnapshotBytes>
    public fun decodeCheckpoint(bytes: SnapshotBytes): Outcome<GameCheckpoint>
}
