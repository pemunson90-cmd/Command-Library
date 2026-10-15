package com.architectpsg.commandlibrary.data.storage.collection.codec

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.*
import java.io.IOException

private class UnsupportedLotVersion : IllegalArgumentException()

/** CLOL1 follows OwnedLot declaration order and reuses frozen CLDK1 nested encodings. */
internal val ownedLotWire: DeckWire<OwnedLot> = wire({ lot ->
    v(entityWire, lot.id); v(identityWire, lot.identity); v(knowledge(enumeration<Finish>()), lot.finish)
    v(enumeration<CardCondition>(), lot.condition); v(textWire, lot.location); int(lot.quantity)
    v(textWire, lot.notes); v(revisionWire, lot.revision); v(knowledge(timestampWire), lot.acquiredAt)
}, {
    OwnedLot(v(entityWire), v(identityWire), v(knowledge(enumeration<Finish>())), v(enumeration<CardCondition>()),
        v(textWire), int(), v(textWire), v(revisionWire), v(knowledge(timestampWire)))
})

/** Structural serialization only; grants no merge, catalog, collection or persistence authority. */
public class OwnedLotPayloadCodec : OwnedLotPayloadCodecPort {
    public override fun encodeOwnedLot(value: OwnedLot): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        val writer = DeckWriter()
        writer.int(0x434c4f4c); writer.byte(0); writer.byte(1)
        writer.v(ownedLotWire, value)
        SnapshotBytes(writer.finish())
    }
    public override fun decodeOwnedLot(payload: SnapshotBytes): Outcome<OwnedLot> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= DeckPayloadLimits.MAX_ENCODED_BYTES)
        val reader = DeckReader(payload.copyBytes())
        require(reader.int() == 0x434c4f4c)
        val version = reader.ushort()
        if (version > 1) throw UnsupportedLotVersion()
        require(version == 1)
        val value = reader.v(ownedLotWire)
        require(reader.remaining == 0)
        value
    }
    private inline fun <T> guard(code: FailureCode, action: () -> T): Outcome<T> = try {
        Outcome.Success(action())
    } catch (_: UnsupportedLotVersion) {
        Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Owned lot requires a newer codec")
    } catch (_: IllegalArgumentException) {
        failure(code)
    } catch (_: IOException) {
        failure(code)
    }
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid owned lot payload", SnapshotList.empty()),
    )
}
