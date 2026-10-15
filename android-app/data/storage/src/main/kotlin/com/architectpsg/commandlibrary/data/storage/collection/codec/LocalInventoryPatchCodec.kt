package com.architectpsg.commandlibrary.data.storage.collection.codec

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.decks.codec.*
import java.io.IOException

private class UnsupportedInventoryPatch : IllegalArgumentException()
private val collectionEditWire: DeckWire<CollectionEdit> = wire({ edit ->
    when (edit) {
        is CollectionEdit.Add -> { byte(0); v(ownedLotWire, edit.lot) }
        is CollectionEdit.Replace -> { byte(1); v(ownedLotWire, edit.lot) }
        is CollectionEdit.Adjust -> { byte(2); v(entityWire, edit.lotId); int(edit.delta); v(textWire, edit.reason) }
        is CollectionEdit.Remove -> { byte(3); v(entityWire, edit.lotId); v(textWire, edit.reason) }
    }
}, {
    when (byte()) {
        0 -> CollectionEdit.Add(v(ownedLotWire))
        1 -> CollectionEdit.Replace(v(ownedLotWire))
        2 -> CollectionEdit.Adjust(v(entityWire), int(), v(textWire))
        3 -> CollectionEdit.Remove(v(entityWire), v(textWire))
        else -> badTag()
    }
})

/** CLIP1 preserves exact edit order/reasons and reuses frozen CLOL1 nested fields. */
public class LocalInventoryPatchCodec : LocalInventoryPatchCodecPort {
    public override fun encodePatch(value: CollectionPatch): Outcome<SnapshotBytes> = guard(FailureCode.INVALID_INPUT) {
        val writer = DeckWriter()
        writer.int(0x434c4950); writer.byte(0); writer.byte(1)
        writer.v(list(collectionEditWire, InventoryProjectionLimits.MAX_EDITS), value.edits)
        SnapshotBytes(writer.finish())
    }
    public override fun decodePatch(payload: SnapshotBytes): Outcome<CollectionPatch> = guard(FailureCode.CORRUPT_DATA) {
        require(payload.size <= DeckPayloadLimits.MAX_ENCODED_BYTES)
        val reader = DeckReader(payload.copyBytes())
        require(reader.int() == 0x434c4950)
        val version = reader.ushort()
        if (version > 1) throw UnsupportedInventoryPatch()
        require(version == 1)
        val value = CollectionPatch(reader.v(list(collectionEditWire, InventoryProjectionLimits.MAX_EDITS)))
        require(reader.remaining == 0)
        value
    }
    private inline fun <T> guard(code: FailureCode, action: () -> T): Outcome<T> = try {
        Outcome.Success(action())
    } catch (_: UnsupportedInventoryPatch) {
        Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Inventory patch requires a newer codec")
    } catch (_: IllegalArgumentException) {
        failure(code)
    } catch (_: IOException) {
        failure(code)
    }
    private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid inventory patch payload", SnapshotList.empty()),
    )
}
