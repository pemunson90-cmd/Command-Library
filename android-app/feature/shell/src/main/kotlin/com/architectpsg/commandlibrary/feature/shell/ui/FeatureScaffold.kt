package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared editorial panel; grows with text rather than prescribing a fixed height. */
@Composable
internal fun FeaturePanel(modifier: Modifier = Modifier, accent: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (accent) colors.primaryContainer else colors.surface,
        contentColor = if (accent) colors.onPrimaryContainer else colors.onSurface,
        border = BorderStroke(1.dp, if (accent) colors.primary else colors.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
