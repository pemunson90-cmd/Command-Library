package com.architectpsg.commandlibrary.feature.shell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CatalogTextField
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.presentation.CatalogStatusUiState
import com.architectpsg.commandlibrary.feature.shell.presentation.QueryProblem
import com.architectpsg.commandlibrary.feature.shell.presentation.SearchController
import com.architectpsg.commandlibrary.feature.shell.presentation.SearchLoad
import com.architectpsg.commandlibrary.feature.shell.presentation.SearchUiState
import com.architectpsg.commandlibrary.feature.shell.presentation.RulingsController
import com.architectpsg.commandlibrary.feature.shell.presentation.PrintingSelectionController
import com.architectpsg.commandlibrary.feature.shell.presentation.installed

/**
 * Expanded width shows results and details side by side. Narrower widths show one pane; details
 * replace the results and Back (button or system back) returns to them with the list position kept.
 */
@Composable
internal fun CardsScreen(
    controller: SearchController,
    statusState: CatalogStatusUiState,
    widthClass: WidthClass,
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    rulings: RulingsController? = null,
    printings: PrintingSelectionController? = null,
    onManageRulings: (() -> Unit)? = null,
    onLookupKeyword: ((String) -> Unit)? = null,
) {
    val state by controller.state.collectAsState()
    val listState = rememberLazyListState()
    val catalogMissing = statusState.status?.installed == false
    val selected = state.selected
    if (widthClass == WidthClass.EXPANDED && LocalDensity.current.fontScale < 1.5f) {
        Row(modifier.fillMaxSize()) {
            Box(Modifier.weight(0.45f).fillMaxHeight().testTag(ShellTags.RESULTS_PANE)) {
                SearchPane(controller, state, catalogMissing, widthClass, showSelection = true, onOpenCatalog, listState)
            }
            VerticalDivider()
            Box(Modifier.weight(0.55f).fillMaxHeight().testTag(ShellTags.DETAIL_PANE)) {
                if (selected != null) {
                    CardDetail(selected, onBack = null, widthClass = widthClass, rulings = rulings, printings = printings, onOpenCatalog = onManageRulings, onLookupKeyword = onLookupKeyword)
                } else {
                    Text(
                        text = stringResource(R.string.detail_select_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(widthClass.gutter),
                    )
                }
            }
        }
    } else {
        BackHandler(enabled = selected != null) { controller.clearSelection() }
        if (selected != null) {
            CardDetail(selected, onBack = controller::clearSelection, widthClass = widthClass, modifier = modifier.testTag(ShellTags.DETAIL_PANE), rulings = rulings, printings = printings, onOpenCatalog = onManageRulings, onLookupKeyword = onLookupKeyword)
        } else {
            Box(modifier.fillMaxSize().testTag(ShellTags.RESULTS_PANE)) {
                SearchPane(controller, state, catalogMissing, widthClass, showSelection = false, onOpenCatalog, listState)
            }
        }
    }
}

@Composable
private fun SearchPane(
    controller: SearchController,
    state: SearchUiState,
    catalogMissing: Boolean,
    widthClass: WidthClass,
    showSelection: Boolean,
    onOpenCatalog: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = widthClass.gutter, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search-form") { SearchForm(controller, state) }
        if (state.suggestionsLoading) item(key = "suggestions-loading") { Busy(stringResource(R.string.search_suggestions_loading)) }
        if (state.suggestionProblem != null) item(key = "suggestions-problem") {
            MessageBlock(stringResource(R.string.search_suggestions_failed), listOf(stringResource(problemText(state.suggestionProblem))), tone = MessageTone.INFO)
        }
        if (state.suggestions.isNotEmpty()) item(key = "suggestions-title") { SectionHeading(stringResource(R.string.search_suggestions_title)) }
        items(state.suggestions, key = { "suggestion:${it.oracleId.source.value}:${it.oracleId.raw}" }) { card ->
            OutlinedButton(onClick = { controller.selectSuggestion(card) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .testTag("name-suggestion-${card.oracleId.raw}")) { Text(card.name) }
        }
        item(key = "search-status") { SearchStatus(state, catalogMissing, onOpenCatalog, controller) }
        items(state.results, key = { "${it.oracleId.source.value}:${it.oracleId.raw}" }) { card ->
            ResultRow(
                card = card,
                selected = showSelection && state.selected?.oracleId == card.oracleId,
                showSelection = showSelection,
                onClick = { controller.select(card) },
            )
        }
        item(key = "search-more") { MoreFooter(state, controller) }
    }
}

@Composable
private fun SearchForm(controller: SearchController, state: SearchUiState) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit: () -> Unit = {
        controller.submit()
        keyboard?.hide()
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.search_eyebrow), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        SectionHeading(stringResource(if (controller.supportsOracleText) R.string.search_text_title else R.string.search_idle_title), style = MaterialTheme.typography.headlineMedium)
        if (controller.supportsOracleText) ActionRow {
            CatalogTextField.entries.forEach { field ->
                FilterChip(selected = controller.fieldInput == field, onClick = { controller.onFieldChange(field) },
                    label = { Text(stringResource(if (field == CatalogTextField.NAME) R.string.search_field_label else R.string.search_oracle_text_label)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("search-field-${field.name}"))
            }
        }
        val problem = state.queryProblem
        OutlinedTextField(
            value = controller.queryInput,
            onValueChange = controller::onQueryChange,
            label = { Text(stringResource(if (controller.fieldInput == CatalogTextField.NAME) R.string.search_field_label else R.string.search_oracle_text_label)) },
            supportingText = {
                Text(
                    when (problem) {
                        QueryProblem.EMPTY -> stringResource(if (controller.fieldInput == CatalogTextField.NAME) R.string.search_problem_empty else R.string.search_oracle_text_empty)
                        QueryProblem.TOO_LONG -> pluralStringResource(
                            R.plurals.search_problem_too_long,
                            SearchController.MAX_QUERY_CODE_POINTS,
                            SearchController.MAX_QUERY_CODE_POINTS,
                        )
                        null -> stringResource(if (controller.fieldInput == CatalogTextField.NAME) R.string.search_field_help else R.string.search_oracle_text_help)
                    },
                )
            },
            isError = problem != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = submit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.search_action)) }
    }
}

@Composable
private fun SearchStatus(state: SearchUiState, catalogMissing: Boolean, onOpenCatalog: () -> Unit, controller: SearchController) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val firstPageSettled = state.searched && state.loading != SearchLoad.FIRST_PAGE && state.failedLoad != SearchLoad.FIRST_PAGE
        if (state.searched && controller.supportsOracleText) Text(stringResource(R.string.search_matching_field,
            stringResource(if (state.queryField == CatalogTextField.NAME) R.string.search_field_label else R.string.search_oracle_text_label)))
        when {
            state.loading == SearchLoad.FIRST_PAGE -> Busy(stringResource(R.string.search_loading))
            state.failedLoad == SearchLoad.FIRST_PAGE && state.problem != null -> MessageBlock(
                title = stringResource(R.string.search_failed),
                body = listOf(stringResource(problemText(state.problem))),
                tone = MessageTone.ERROR,
            ) {
                OutlinedButton(onClick = controller::retry) { Text(stringResource(R.string.shell_action_retry)) }
            }
            catalogMissing && (!state.searched || state.results.isEmpty()) -> MessageBlock(
                title = null,
                body = listOf(stringResource(R.string.search_no_catalog)),
                tone = MessageTone.INFO,
                announce = state.searched,
            ) {
                Button(onClick = onOpenCatalog) { Text(stringResource(R.string.search_action_open_catalog)) }
            }
            firstPageSettled && state.results.isEmpty() -> MessageBlock(
                title = null,
                body = listOf(stringResource(R.string.search_no_results, state.query.orEmpty())),
                tone = MessageTone.INFO,
            )
        }
        if (state.stale) {
            MessageBlock(
                title = stringResource(R.string.search_stale_title),
                body = listOf(stringResource(R.string.search_stale_body)),
                tone = MessageTone.INFO,
            ) {
                Button(onClick = controller::searchAgain) { Text(stringResource(R.string.search_again)) }
            }
        }
        if (firstPageSettled && state.results.isNotEmpty()) {
            val count = state.results.size
            Text(
                text = pluralStringResource(R.plurals.search_results_count, count, formatCount(count.toLong())),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.nextCursor != null && !state.stale) {
                Text(
                    text = stringResource(R.string.search_more_available),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MoreFooter(state: SearchUiState, controller: SearchController) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            state.loading == SearchLoad.NEXT_PAGE -> Busy(stringResource(R.string.search_loading_more))
            state.failedLoad == SearchLoad.NEXT_PAGE && state.problem != null -> MessageBlock(
                title = stringResource(R.string.search_more_failed),
                body = listOf(stringResource(problemText(state.problem))),
                tone = MessageTone.ERROR,
            ) {
                OutlinedButton(onClick = controller::retry) { Text(stringResource(R.string.shell_action_retry)) }
            }
            state.canLoadMore -> OutlinedButton(onClick = controller::loadMore) { Text(stringResource(R.string.search_load_more)) }
        }
    }
}

@Composable
private fun Busy(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(Modifier.padding(4.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultRow(card: CardRecord, selected: Boolean, showSelection: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) colors.primaryContainer else colors.surface)
            .clickable(onClickLabel = stringResource(R.string.search_result_open), role = Role.Button, onClick = onClick)
            .then(if (showSelection) Modifier.semantics { this.selected = selected } else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionRow {
            Text(card.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.fillMaxWidth())
            (card.manaCost as? Knowledge.Known)?.value?.takeIf(String::isNotEmpty)?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
            }
        }
        if (selected) Text(stringResource(R.string.search_selected), style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        (card.types as? Knowledge.Known)?.value?.original?.takeIf(String::isNotEmpty)?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
