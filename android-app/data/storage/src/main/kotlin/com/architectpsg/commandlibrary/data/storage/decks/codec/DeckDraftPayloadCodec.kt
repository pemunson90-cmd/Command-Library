package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException

private class UnsupportedDraft : IllegalArgumentException()

/** CLDF1 raw editor text preserves exact UTF16 units; structured fields retain CLDK1 wires. */
public class DeckDraftPayloadCodec : DeckDraftPayloadCodecPort {
    public override fun encodeDraft(value: DeckDraftBody): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        val writer = DeckWriter()
        writer.int(0x434c4446); writer.byte(0); writer.byte(1)
        writer.byte(when (value) { is DeckDraftBody.Details -> 0; is DeckDraftBody.Entry -> 1; is DeckDraftBody.Add -> 2; is DeckDraftBody.Remove -> 3 })
        writer.v(revisionWire, value.baseRevision)
        when (value) {
            is DeckDraftBody.Details -> {
                writer.raw(value.name, DeckDraftLimits.MAX_NAME_CODE_UNITS)
                writer.raw(value.description); writer.raw(value.primer)
            }
            is DeckDraftBody.Entry -> {
                writer.v(entityWire, value.entryId); writer.raw(value.quantity, DeckDraftLimits.MAX_QUANTITY_CODE_UNITS)
                writer.raw(value.notes); writer.choices(value.board, value.finish, value.labels)
            }
            is DeckDraftBody.Add -> {
                writer.raw(value.quantity, DeckDraftLimits.MAX_QUANTITY_CODE_UNITS); writer.raw(value.notes)
                writer.choices(value.board, value.finish, value.labels)
                writer.v(nullable(selectionWire), value.selection)
            }
            is DeckDraftBody.Remove -> writer.v(entityWire, value.entryId)
        }
        SnapshotBytes(writer.finish())
    }

    public override fun decodeDraft(payload: SnapshotBytes): Outcome<DeckDraftBody> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= DeckDraftLimits.MAX_ENCODED_BYTES)
        val reader = DeckReader(payload.copyBytes())
        require(reader.int() == 0x434c4446)
        val version = reader.ushort()
        if (version > 1) throw UnsupportedDraft()
        require(version == 1)
        val mode = reader.byte()
        val base = reader.v(revisionWire)
        val body = when (mode) {
            0 -> DeckDraftBody.Details(base, reader.raw(DeckDraftLimits.MAX_NAME_CODE_UNITS), reader.raw(), reader.raw())
            1 -> DeckDraftBody.Entry(base, reader.v(entityWire), reader.raw(DeckDraftLimits.MAX_QUANTITY_CODE_UNITS), reader.raw(),
                reader.v(enumeration<BoardRole>()), reader.v(knowledge(enumeration<Finish>())), reader.v(list(labelWire)))
            2 -> DeckDraftBody.Add(base, reader.raw(DeckDraftLimits.MAX_QUANTITY_CODE_UNITS), reader.raw(), reader.v(enumeration<BoardRole>()),
                reader.v(knowledge(enumeration<Finish>())), reader.v(list(labelWire)), reader.v(nullable(selectionWire)))
            3 -> DeckDraftBody.Remove(base, reader.v(entityWire))
            else -> badTag()
        }
        require(reader.remaining == 0)
        body
    }

    private fun DeckWriter.raw(value: String, cap: Int = DeckDraftLimits.MAX_TEXT_CODE_UNITS) {
        require(value.length <= cap)
        int(value.length)
        for (unit in value) { byte(unit.code ushr 8); byte(unit.code and 255) }
    }
    private fun DeckReader.raw(cap: Int = DeckDraftLimits.MAX_TEXT_CODE_UNITS): String {
        val count = int().toLong() and 0xffffffffL
        // Raw text is not a collection. Admit unsigned count before narrowing or allocating.
        require(count <= cap.toLong() && count <= remaining.toLong() / 2)
        return CharArray(count.toInt()) { ((byte() shl 8) or byte()).toChar() }.concatToString()
    }
    private fun DeckWriter.choices(board: BoardRole, finish: Knowledge<Finish>, labels: SnapshotList<EntryLabel>) {
        v(enumeration<BoardRole>(), board); v(knowledge(enumeration<Finish>()), finish); v(list(labelWire), labels)
    }
    private inline fun <T> guard(code: FailureCode, action: () -> T): Outcome<T> = try { Outcome.Success(action()) }
    catch (_: UnsupportedDraft) { Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer deck draft encoding") }
    catch (_: IllegalArgumentException) { failure(code) }
    catch (_: IOException) { failure(code) }
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(DomainFailure(code, "Invalid deck draft encoding", SnapshotList.empty()))

    private companion object {
        val selectionWire: DeckWire<DeckDraftSelection> = wire({ selection ->
            v(identityWire, selection.identity); v(nullable(generationWire), selection.generation)
        }, { DeckDraftSelection(v(identityWire), v(nullable(generationWire))) })
    }
}
