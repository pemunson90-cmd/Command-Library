package com.architectpsg.commandlibrary.feature.shell.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.SeatOrientation
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

internal object ProfileAppearanceTags {
    const val MANAGE = "profile-appearance"
    const val EDITOR = "profile-appearance-editor"
    const val USE_COLOR = "profile-appearance-use-color"
    const val COLOR = "profile-appearance-color"
    const val UPRIGHT = "profile-appearance-upright"
    const val CLOCKWISE = "profile-appearance-clockwise"
    const val INVERTED = "profile-appearance-inverted"
    const val COUNTERCLOCKWISE = "profile-appearance-counterclockwise"
    const val APPLY = "profile-appearance-apply"
    const val CANCEL = "profile-appearance-cancel"
}

@Composable
internal fun ProfileAppearanceScreen(state: ProfilesUiState, editor: ProfileEditor, controller: ProfilesController, widthClass: WidthClass) {
    val appearance = editor.appearanceEditor ?: return
    val enabled = !state.busy
    Column(Modifier.widthIn(max = ReadingWidth).fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = widthClass.gutter, vertical = 16.dp).testTag(ProfileAppearanceTags.EDITOR),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeading(stringResource(R.string.profile_appearance_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.profile_appearance_draft_notice))
        ProfileProblem(state.problem)
        ProfileToggle(appearance.useColor, controller::changeAppearanceUseColor,
            stringResource(R.string.profile_appearance_use_color), ProfileAppearanceTags.USE_COLOR, enabled)
        if (appearance.useColor) {
            ProfileField(appearance.color, controller::changeAppearanceColor, stringResource(R.string.profile_counter_color),
                ProfileAppearanceTags.COLOR, enabled,
                if (appearance.invalidColor) stringResource(R.string.profile_counter_invalid_color) else null, KeyboardType.Ascii)
            parseProfileColor(appearance.color)?.let { color ->
                val description = stringResource(R.string.profile_appearance_preview)
                Box(Modifier.size(32.dp).background(Color(color.argb.toInt())).border(1.dp, MaterialTheme.colorScheme.outline)
                    .semantics { contentDescription = description })
            }
        } else Text(stringResource(R.string.profile_appearance_unset))
        Text(stringResource(R.string.profile_appearance_orientation), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileRadioChoice(appearance.orientation == SeatOrientation.UPRIGHT, { controller.changeAppearanceOrientation(SeatOrientation.UPRIGHT) },
                stringResource(R.string.profile_appearance_upright), ProfileAppearanceTags.UPRIGHT, enabled)
            ProfileRadioChoice(appearance.orientation == SeatOrientation.CLOCKWISE, { controller.changeAppearanceOrientation(SeatOrientation.CLOCKWISE) },
                stringResource(R.string.profile_appearance_clockwise), ProfileAppearanceTags.CLOCKWISE, enabled)
            ProfileRadioChoice(appearance.orientation == SeatOrientation.INVERTED, { controller.changeAppearanceOrientation(SeatOrientation.INVERTED) },
                stringResource(R.string.profile_appearance_inverted), ProfileAppearanceTags.INVERTED, enabled)
            ProfileRadioChoice(appearance.orientation == SeatOrientation.COUNTERCLOCKWISE, { controller.changeAppearanceOrientation(SeatOrientation.COUNTERCLOCKWISE) },
                stringResource(R.string.profile_appearance_counterclockwise), ProfileAppearanceTags.COUNTERCLOCKWISE, enabled)
        }
        if (editor.appearance.art != null) Text(stringResource(R.string.profile_appearance_kept))
        ActionRow {
            Button(onClick = controller::applyAppearance, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileAppearanceTags.APPLY)) { Text(stringResource(R.string.profile_counter_apply)) }
            OutlinedButton(onClick = controller::closeAppearanceEditor, enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(ProfileAppearanceTags.CANCEL)) { Text(stringResource(R.string.profiles_cancel)) }
        }
    }
}
