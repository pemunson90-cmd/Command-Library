package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Bounded application text export; never a restore or inventory-write authority. */
public fun interface CollectionTextRendering {
    public fun render(snapshot: InventorySnapshot, includeNotes: Boolean, includeLocations: Boolean,
        cancel: CancelToken): Outcome<ByteArtifact>
}
