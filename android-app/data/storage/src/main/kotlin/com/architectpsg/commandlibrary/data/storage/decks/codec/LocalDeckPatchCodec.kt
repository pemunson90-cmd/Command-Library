package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException

private class UnsupportedPatch : IllegalArgumentException()
private val patchEditWire: DeckWire<DeckEdit> = wire({ edit ->
    when (edit) {
        is DeckEdit.Rename -> { byte(0); v(textWire, edit.name) }
        is DeckEdit.Description -> { byte(1); v(textWire, edit.text) }
        is DeckEdit.Primer -> { byte(2); v(textWire, edit.markdown) }
        is DeckEdit.ReorderEntries -> { byte(3); v(list(entityWire, DeckPayloadLimits.MAX_ENTRIES), edit.entryIds) }
        is DeckEdit.Categories -> { byte(4); v(list(labelWire, DeckPayloadLimits.MAX_CATEGORIES), edit.categories) }
        is DeckEdit.Format -> { byte(5); v(knowledge(formatWire), edit.format) }
        is DeckEdit.Metadata -> {
            byte(6); v(knowledge(textWire), edit.folder); v(list(textWire), edit.tags)
            v(knowledge(amountWire), edit.budget); v(nullable(entityWire), edit.profileId)
        }
        is DeckEdit.Add -> { byte(7); v(entryWire, edit.entry) }
        is DeckEdit.Replace -> { byte(8); v(entryWire, edit.entry) }
        is DeckEdit.Remove -> { byte(9); v(entityWire, edit.entryId) }
        is DeckEdit.RestoreVersion, is DeckEdit.ApplyPackage -> throw UnsupportedPatch()
    }
}, {
    when (byte()) {
        0 -> DeckEdit.Rename(v(textWire))
        1 -> DeckEdit.Description(v(textWire))
        2 -> DeckEdit.Primer(v(textWire))
        3 -> DeckEdit.ReorderEntries(v(list(entityWire, DeckPayloadLimits.MAX_ENTRIES)))
        4 -> DeckEdit.Categories(v(list(labelWire, DeckPayloadLimits.MAX_CATEGORIES)))
        5 -> DeckEdit.Format(v(knowledge(formatWire)))
        6 -> DeckEdit.Metadata(v(knowledge(textWire)), v(list(textWire)), v(knowledge(amountWire)), v(nullable(entityWire)))
        7 -> DeckEdit.Add(v(entryWire))
        8 -> DeckEdit.Replace(v(entryWire))
        9 -> DeckEdit.Remove(v(entityWire))
        else -> badTag()
    }
})

/** CLDP1 uses the frozen CLDK1 nested field encodings without changing snapshot bytes. */
public class LocalDeckPatchCodec : LocalDeckPatchCodecPort {
    public override fun encodePatch(value: DeckPatch): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        val writer = DeckWriter()
        writer.int(0x434c4450); writer.byte(0); writer.byte(1)
        writer.v(list(patchEditWire, LocalDeckEditLimits.MAX_EDITS), value.edits)
        writer.v(textWire, value.reason)
        SnapshotBytes(writer.finish())
    }
    public override fun decodePatch(payload: SnapshotBytes): Outcome<DeckPatch> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= DeckPayloadLimits.MAX_ENCODED_BYTES)
        val reader = DeckReader(payload.copyBytes())
        require(reader.int() == 0x434c4450)
        val version = reader.ushort()
        if (version > 1) throw UnsupportedPatch()
        require(version == 1)
        val value = DeckPatch(reader.v(list(patchEditWire, LocalDeckEditLimits.MAX_EDITS)), reader.v(textWire))
        require(reader.remaining == 0)
        value
    }
    private inline fun <T> guard(code: FailureCode, action: () -> T): Outcome<T> = try {
        Outcome.Success(action())
    } catch (_: UnsupportedPatch) {
        Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Deck patch requires an unsupported capability")
    } catch (_: IllegalArgumentException) {
        failure(code)
    } catch (_: IOException) {
        failure(code)
    }
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid deck patch", SnapshotList.empty()),
    )
}
