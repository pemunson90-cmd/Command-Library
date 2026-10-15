package com.architectpsg.commandlibrary.core.model

/** Complete bounded owned-lot content, including zero quantities and unresolved foreign identity. */
public interface OwnedLotPayloadCodecPort {
    public fun encodeOwnedLot(value: OwnedLot): Outcome<SnapshotBytes>
    public fun decodeOwnedLot(payload: SnapshotBytes): Outcome<OwnedLot>
}
