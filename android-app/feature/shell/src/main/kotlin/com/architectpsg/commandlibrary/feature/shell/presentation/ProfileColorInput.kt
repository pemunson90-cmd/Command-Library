package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.DisplayColor
import com.architectpsg.commandlibrary.core.model.SeatAppearance
import com.architectpsg.commandlibrary.core.model.SeatOrientation
import java.util.Locale

internal const val DefaultProfileColor = "#FF1976D2"
private val ProfileArgbSyntax = Regex("#[0-9a-fA-F]{8}")
internal fun DisplayColor.profileColorText(): String = String.format(Locale.ROOT, "#%08X", argb)
internal fun parseProfileColor(value: String): DisplayColor? =
    if (ProfileArgbSyntax.matches(value)) DisplayColor(value.substring(1).toLong(16)) else null

internal data class ProfileAppearanceEditor(
    val useColor: Boolean,
    val color: String,
    val orientation: SeatOrientation,
    val invalidColor: Boolean = false,
)
internal fun SeatAppearance.draftAppearanceEditor(): ProfileAppearanceEditor =
    ProfileAppearanceEditor(color != null, color?.profileColorText() ?: DefaultProfileColor, orientation)
