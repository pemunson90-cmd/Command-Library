package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*

internal data class ProfileCommanderEditor(val original: CommanderSlot, val label: String = original.label, val invalidLabel: Boolean = false)

/** Literal local-catalog identity; a selected oracle does not imply a selected printing. */
internal fun CardRecord.profileCommanderIdentity(): CardIdentity = CardIdentity(
    Knowledge.Known(oracleId), Knowledge.Absent("No printing selected"), providerIdentities.firstOrNull(), Knowledge.Known(name), language,
)
