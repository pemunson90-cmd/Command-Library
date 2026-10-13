package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.architectpsg.commandlibrary.feature.shell.R
import androidx.compose.ui.unit.dp

/** Content is supplied by an image owner, never fetched here. Full cards must use fit scaling.
 * Attribution stays outside the image and remains readable; null content is a truthful fallback.
 */
@Composable
internal fun CardArtworkFrame(
    modifier: Modifier = Modifier,
    attribution: String? = null,
    content: (@Composable () -> Unit)? = null,
) {
    require(content == null || !attribution.isNullOrBlank())
    FeaturePanel(modifier) {
        if (content == null) {
            Text(stringResource(R.string.artwork_not_available), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.artwork_text_available), style = MaterialTheme.typography.bodyMedium)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().aspectRatio(5f / 7f)) { content() }
                attribution?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
