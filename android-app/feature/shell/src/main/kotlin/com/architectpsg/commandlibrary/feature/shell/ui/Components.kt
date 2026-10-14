package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layout follows the width actually available to the shell, not the device type. */
internal enum class WidthClass(val gutter: Dp) {
    COMPACT(16.dp),
    MEDIUM(24.dp),
    EXPANDED(32.dp),
    ;

    companion object {
        val MEDIUM_MIN: Dp = 600.dp
        val EXPANDED_MIN: Dp = 840.dp

        fun of(width: Dp): WidthClass = when {
            width < MEDIUM_MIN -> COMPACT
            width < EXPANDED_MIN -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** Reading measure cap so text lines stay near 80 characters on wide windows. */
internal val ReadingWidth: Dp = 680.dp

@Composable
internal fun SectionHeading(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium) {
    Text(text = text, style = style, color = MaterialTheme.colorScheme.onBackground, modifier = modifier.semantics { heading() })
}

/** A label and its value, read by screen readers as one item. */
@Composable
internal fun DetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

internal enum class MessageTone { INFO, ERROR }

/**
 * A titled message with optional actions. Errors carry a text title, never color alone. Messages that
 * report an outcome are announced politely to screen readers.
 */
@Composable
internal fun MessageBlock(
    title: String?,
    body: List<String>,
    tone: MessageTone,
    modifier: Modifier = Modifier,
    announce: Boolean = true,
    actions: @Composable () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (tone == MessageTone.ERROR) colors.errorContainer else colors.surfaceContainerHigh,
        contentColor = if (tone == MessageTone.ERROR) colors.onErrorContainer else colors.onSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .then(if (announce) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) SectionHeading(title, style = MaterialTheme.typography.titleMedium)
            body.forEach { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
            ActionRow(actions)
        }
    }
}

/** Buttons wrap onto further lines at large text sizes instead of being squeezed or clipped. */
@Composable
internal fun ActionRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/** Informational badge deliberately has no click or button semantics. */
@Composable
internal fun ReadOnlyBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier.semantics(mergeDescendants = true) {}, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
