package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException

private const val MAGIC = 0x434c444b // CLDK
private const val VERSION = 1
private class UnsupportedDeckVersion : IllegalArgumentException()

/** Complete bounded model serialization; no persistence, source or graph authority. */
public class DeckPayloadCodec : DeckPayloadCodecPort {
    public override fun encodeSnapshot(value: DeckSnapshot): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        val writer = DeckWriter()
        writer.int(MAGIC)
        writer.byte(VERSION ushr 8)
        writer.byte(VERSION and 0xff)
        writer.v(snapshotWire, value)
        SnapshotBytes(writer.finish())
    }

    public override fun decodeSnapshot(payload: SnapshotBytes): Outcome<DeckSnapshot> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= DeckPayloadLimits.MAX_ENCODED_BYTES)
        val reader = DeckReader(payload.copyBytes())
        require(reader.int() == MAGIC)
        val version = reader.ushort()
        if (version > VERSION) throw UnsupportedDeckVersion()
        require(version == VERSION)
        val value = reader.v(snapshotWire)
        require(reader.remaining == 0)
        value
    }

    private inline fun <T> guard(code: FailureCode, operation: () -> T): Outcome<T> = try {
        Outcome.Success(operation())
    } catch (_: UnsupportedDeckVersion) {
        Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Deck payload requires a newer codec")
    } catch (_: IllegalArgumentException) {
        failure(code)
    } catch (_: IOException) {
        failure(code)
    }

    // Exception messages can contain enum values or constructor input: never disclose user text.
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid deck payload", SnapshotList.empty()),
    )
}
