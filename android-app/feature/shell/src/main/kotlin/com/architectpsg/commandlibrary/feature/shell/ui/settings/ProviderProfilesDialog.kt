package com.architectpsg.commandlibrary.feature.shell.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.architectpsg.commandlibrary.core.model.ProviderDialect
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.*
import com.architectpsg.commandlibrary.feature.shell.ui.*

/** Raw key text exists only in this non-saveable composition and the explicit one-shot call. */
@Composable
internal fun ProviderProfilesDialog(controller: ProviderProfilesController, widthClass: WidthClass, onDismiss: () -> Unit) {
    val s by controller.state.collectAsState()
    if (!s.visible) return
    var keyText by remember(s.form?.id, s.selected?.revision) { mutableStateOf("") }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, s.form?.id, s.selected?.revision) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) keyText = "" }
        owner.lifecycle.addObserver(observer)
        onDispose { keyText = ""; owner.lifecycle.removeObserver(observer) }
    }
    val close = { keyText = ""; controller.dismiss(); onDismiss() }
    AlertDialog(onDismissRequest = close, modifier = Modifier.widthIn(max = ReadingWidth),
        title = { Text(stringResource(R.string.provider_profiles_manage)) }, text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = widthClass.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.provider_profiles_scope))
                if (s.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                ActionRow {
                    TextButton({ keyText = ""; controller.newProfile() }, enabled = !s.busy, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_new)) }
                    TextButton({ keyText = ""; controller.refresh() }, enabled = !s.busy, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_reload)) }
                }
                s.items.forEach { item ->
                    TextButton({ keyText = ""; controller.select(item.profile.id) }, enabled = !s.busy,
                        modifier = Modifier.fillMaxWidth().profileTouch()) {
                        Text(stringResource(R.string.provider_profiles_item, item.profile.name, item.profile.selectedModelId))
                    }
                }
                if (s.items.isEmpty() && !s.busy) Text(stringResource(R.string.provider_profiles_empty))
                if (s.nextCursor != null) TextButton({ controller.more() }, enabled = !s.busy, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_more)) }
                s.form?.let { f ->
                    val enabled = !s.busy && !s.reloadRequired
                    val revision = s.formRevision
                    ProfileField(f.name, R.string.provider_profiles_name, ProviderProfileField.NAME, f, enabled) { controller.edit(ProviderProfileField.NAME, it, revision) }
                    ProfileField(f.base, R.string.provider_profiles_base, ProviderProfileField.BASE, f, enabled) { controller.edit(ProviderProfileField.BASE, it, revision) }
                    Text(stringResource(R.string.provider_profiles_dialect))
                    ActionRow {
                        ProviderDialect.entries.forEach { dialect ->
                            FilterChip(selected = f.dialect == dialect, onClick = { controller.dialect(dialect, revision) }, enabled = enabled,
                                label = { Text(stringResource(dialectLabel(dialect))) }, modifier = Modifier.profileTouch())
                        }
                    }
                    ProfileField(f.model, R.string.provider_profiles_model, ProviderProfileField.MODEL, f, enabled) { controller.edit(ProviderProfileField.MODEL, it, revision) }
                    ProfileField(f.apiVersion, R.string.provider_profiles_api, ProviderProfileField.API_VERSION, f, enabled) { controller.edit(ProviderProfileField.API_VERSION, it, revision) }
                    TextButton({ keyText = ""; controller.save(revision) }, enabled = enabled, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_save)) }
                    s.selected?.let { selected ->
                        Text(stringResource(if (selected.profile.secretRef != null) R.string.provider_profiles_reference else R.string.provider_profiles_no_reference))
                        Text(stringResource(R.string.provider_profiles_key_help))
                        OutlinedTextField(keyText, { keyText = if (it.length <= 16384) it else "" },
                            enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            label = { Text(stringResource(R.string.provider_profiles_key)) }, modifier = Modifier.fillMaxWidth())
                        ActionRow {
                            TextButton({ val submitted = keyText; keyText = ""; controller.putKey(submitted, revision) }, enabled = enabled && keyText.isNotBlank(), modifier = Modifier.profileTouch()) {
                                Text(stringResource(if (selected.profile.secretRef == null) R.string.provider_profiles_add_key else R.string.provider_profiles_replace_key))
                            }
                            if (selected.profile.secretRef != null) TextButton({ keyText = ""; controller.requestConfirmation(ProviderProfileConfirmation.REMOVE_KEY, revision) },
                                enabled = enabled, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_remove_key)) }
                            TextButton({ keyText = ""; controller.requestConfirmation(ProviderProfileConfirmation.DELETE, revision) },
                                enabled = enabled && selected.profile.secretRef == null, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_delete)) }
                        }
                    }
                }
                if (s.reloadRequired) Text(stringResource(R.string.provider_profiles_reload_required))
                if (s.saved) Text(stringResource(R.string.provider_profiles_saved))
                if (s.settingsWarning) Text(stringResource(R.string.provider_profiles_settings_warning))
                s.problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(close, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_close)) } })
    s.confirmation?.let { kind ->
        val revision = s.formRevision
        AlertDialog(onDismissRequest = controller::cancelConfirmation,
            title = { Text(stringResource(if (kind == ProviderProfileConfirmation.DELETE) R.string.provider_profiles_delete else R.string.provider_profiles_remove_key)) },
            text = { Text(stringResource(if (kind == ProviderProfileConfirmation.DELETE) R.string.provider_profiles_delete_warning else R.string.provider_profiles_remove_warning)) },
            confirmButton = { TextButton({ keyText = ""; controller.confirm(revision) }, enabled = !s.busy, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_confirm)) } },
            dismissButton = { TextButton(controller::cancelConfirmation, modifier = Modifier.profileTouch()) { Text(stringResource(R.string.provider_profiles_cancel)) } })
    }
}

@Composable
private fun ProfileField(value: String, label: Int, field: ProviderProfileField, form: ProviderProfileForm, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, change, enabled = enabled, singleLine = true, isError = field in form.rejected,
        label = { Text(stringResource(label)) }, supportingText = {
            if (field in form.rejected) Text(stringResource(R.string.provider_profiles_invalid_field))
        }, modifier = Modifier.fillMaxWidth())
}
private fun Modifier.profileTouch() = sizeIn(minWidth = 48.dp, minHeight = 48.dp)
private fun dialectLabel(value: ProviderDialect): Int = when (value) {
    ProviderDialect.OPENAI_CHAT -> R.string.provider_profiles_chat
    ProviderDialect.OPENAI_RESPONSES -> R.string.provider_profiles_responses
    ProviderDialect.ANTHROPIC -> R.string.provider_profiles_anthropic
    ProviderDialect.GEMINI -> R.string.provider_profiles_gemini
    ProviderDialect.LOCAL -> R.string.provider_profiles_local
}
