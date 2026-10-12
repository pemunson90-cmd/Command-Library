package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CardStats
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.feature.shell.R

/** Oracle text and game fields only; no card art is requested. Absent stats are omitted, unknown ones say so. */
@Composable
internal fun CardDetail(card: CardRecord, onBack: (() -> Unit)?, widthClass: WidthClass, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = widthClass.gutter, vertical = 16.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack) {
                Icon(painterResource(R.drawable.shell_ic_back), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.detail_back))
            }
        }
        Column(Modifier.widthIn(max = ReadingWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionHeading(card.name, style = MaterialTheme.typography.headlineMedium)
            DetailField(stringResource(R.string.detail_mana_cost), knowledgeText(card.manaCost) { it })
            DetailField(stringResource(R.string.detail_mana_value), knowledgeText(card.manaValue) { it.stripTrailingZeros().toPlainString() })
            DetailField(stringResource(R.string.detail_type), knowledgeText(card.types) { it.original })
            DetailField(stringResource(R.string.detail_text), knowledgeText(card.originalText) { it })
            StatFields(card.stats)
            DetailField(stringResource(R.string.detail_colors), knowledgeText(card.colors) { colorNames(it) })
            DetailField(stringResource(R.string.detail_color_identity), knowledgeText(card.colorIdentity) { colorNames(it) })
            val separator = stringResource(R.string.detail_list_separator)
            val none = stringResource(R.string.shell_value_none)
            DetailField(stringResource(R.string.detail_keywords), knowledgeText(card.keywords) { if (it.isEmpty()) none else it.joinToString(separator) })
            DetailField(stringResource(R.string.detail_layout), knowledgeText(card.layout) { it })
            val yes = stringResource(R.string.shell_value_yes)
            val no = stringResource(R.string.shell_value_no)
            DetailField(stringResource(R.string.detail_reserved), knowledgeText(card.reserved) { if (it) yes else no })
            DetailField(stringResource(R.string.detail_language), languageName(card.language))
            if (card.faces.size > 1) {
                SectionHeading(stringResource(R.string.detail_faces_title), modifier = Modifier.padding(top = 16.dp))
                card.faces.forEachIndexed { index, face ->
                    SectionHeading(
                        stringResource(R.string.detail_face_heading, index + 1, face.name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    DetailField(stringResource(R.string.detail_mana_cost), knowledgeText(face.manaCost) { it })
                    DetailField(stringResource(R.string.detail_type), knowledgeText(face.types) { it.original })
                    DetailField(stringResource(R.string.detail_text), knowledgeText(face.text) { it })
                    StatFields(face.stats)
                }
            }
            SectionHeading(stringResource(R.string.detail_source_title), modifier = Modifier.padding(top = 16.dp))
            val provenance = card.provenance
            DetailField(stringResource(R.string.catalog_field_source), provenance.provider.value)
            DetailField(stringResource(R.string.catalog_field_source_date), knowledgeText(provenance.sourceDate) { formatTimestamp(it) })
            DetailField(stringResource(R.string.catalog_field_imported), formatTimestamp(provenance.fetchedAt))
            provenance.generation?.let { DetailField(stringResource(R.string.catalog_field_generation), it.value) }
        }
    }
}

@Composable
private fun StatFields(stats: CardStats) {
    val power = stats.power
    val toughness = stats.toughness
    if (power !is Knowledge.Absent || toughness !is Knowledge.Absent) {
        val unknown = stringResource(R.string.shell_value_unknown)
        val value = stringResource(
            R.string.detail_power_toughness_value,
            (power as? Knowledge.Known)?.value ?: unknown,
            (toughness as? Knowledge.Known)?.value ?: unknown,
        )
        DetailField(stringResource(R.string.detail_power_toughness), value)
    }
    if (stats.loyalty !is Knowledge.Absent) DetailField(stringResource(R.string.detail_loyalty), knowledgeText(stats.loyalty) { it })
    if (stats.defense !is Knowledge.Absent) DetailField(stringResource(R.string.detail_defense), knowledgeText(stats.defense) { it })
}
