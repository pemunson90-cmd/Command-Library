package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.architectpsg.commandlibrary.core.model.Color
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val KIB = 1024L
private const val MIB = 1024L * 1024L

@Composable
internal fun formatBytes(bytes: Long): String {
    val locale = LocalLocale.current.platformLocale
    return when {
        bytes < KIB -> pluralStringResource(R.plurals.shell_size_bytes, bytes.toInt(), formatCount(bytes))
        bytes < MIB -> stringResource(R.string.shell_size_kib, oneDecimal(bytes.toDouble() / KIB, locale))
        else -> stringResource(R.string.shell_size_mib, oneDecimal(bytes.toDouble() / MIB, locale))
    }
}

private fun oneDecimal(value: Double, locale: Locale): String =
    DecimalFormat("0.#", DecimalFormatSymbols.getInstance(locale)).format(value)

@Composable
internal fun formatCount(count: Long): String = NumberFormat.getIntegerInstance(LocalLocale.current.platformLocale).format(count)

@Composable
internal fun formatTimestamp(timestamp: UtcTimestamp): String {
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, LocalLocale.current.platformLocale)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return stringResource(R.string.shell_timestamp_utc, format.format(Date(timestamp.epochSeconds * 1000L)))
}

/** Known values render through [render]; absent and unknown values never become blanks or zeroes. */
@Composable
internal fun <T> knowledgeText(value: Knowledge<T>, render: @Composable (T) -> String): String = when (value) {
    is Knowledge.Known -> render(value.value)
    is Knowledge.Absent -> stringResource(R.string.shell_value_not_supplied)
    is Knowledge.Unknown -> stringResource(R.string.shell_value_unknown)
}

@Composable
internal fun languageName(tag: LanguageTag): String =
    if (tag == LanguageTag.ENGLISH) stringResource(R.string.shell_language_english) else tag.value

@Composable
internal fun colorNames(colors: List<Color>): String =
    if (colors.isEmpty()) {
        stringResource(R.string.detail_colorless)
    } else {
        val separator = stringResource(R.string.detail_list_separator)
        colors.map { stringResource(colorName(it)) }.joinToString(separator)
    }

@StringRes
private fun colorName(color: Color): Int = when (color) {
    Color.WHITE -> R.string.color_white
    Color.BLUE -> R.string.color_blue
    Color.BLACK -> R.string.color_black
    Color.RED -> R.string.color_red
    Color.GREEN -> R.string.color_green
}

@StringRes
internal fun problemText(problem: Problem): Int = when (problem) {
    Problem.INVALID_INPUT -> R.string.shell_problem_invalid_input
    Problem.STALE_REVISION -> R.string.shell_problem_stale_revision
    Problem.STALE_GENERATION -> R.string.shell_problem_stale_generation
    Problem.CORRUPT_DATA -> R.string.shell_problem_corrupt_data
    Problem.PERSISTENCE -> R.string.shell_problem_persistence
    Problem.PERMISSION -> R.string.shell_problem_permission
    Problem.SOURCE_MISMATCH -> R.string.shell_problem_source_mismatch
    Problem.TRANSPORT -> R.string.shell_problem_transport
    Problem.INTERNAL -> R.string.shell_problem_internal
    Problem.OFFLINE -> R.string.shell_problem_offline
    Problem.NOT_INSTALLED -> R.string.shell_problem_not_installed
    Problem.NOT_FOUND -> R.string.shell_problem_not_found
    Problem.CONSENT_REQUIRED -> R.string.shell_problem_consent_required
    Problem.REVOKED -> R.string.shell_problem_revoked
    Problem.UNSUPPORTED -> R.string.shell_problem_unsupported
    Problem.SOURCE_UNAVAILABLE -> R.string.shell_problem_source_unavailable
    Problem.KEY_MISSING -> R.string.shell_problem_key_missing
    Problem.UNKNOWN_RESULT -> R.string.shell_problem_unknown_result
    Problem.UNEXPECTED -> R.string.shell_problem_unexpected
}
