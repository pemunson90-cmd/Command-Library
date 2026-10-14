package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

/** Explicit user-authored execution configuration; does not authenticate official rules or install source data. */
public data class CustomVariantManifest(public val definition: VariantDefinition, public val initialState: VariantState,
    public val transitions: SnapshotList<CustomVariantTransition>)
public sealed interface CustomVariantSelection {
    public data object None : CustomVariantSelection
    public data object Shuffle : CustomVariantSelection
    public data class Top(public val count: Int) : CustomVariantSelection
    public data class Explicit(public val minimum: Int, public val maximum: Int) : CustomVariantSelection
}
public enum class CustomVariantInsertion { TOP, BOTTOM }
public data class CustomVariantTransition(public val actionId: String, public val phases: SnapshotList<String>,
    public val fromZone: String?, public val toZone: String?, public val selection: CustomVariantSelection = CustomVariantSelection.None,
    public val insertion: CustomVariantInsertion = CustomVariantInsertion.BOTTOM, public val appendDrawn: Boolean = false,
    public val appendDiscarded: Boolean = false, public val nextPhase: String? = null,
    public val properties: SnapshotList<VariantProperty> = SnapshotList.empty(), public val reset: Boolean = false)
