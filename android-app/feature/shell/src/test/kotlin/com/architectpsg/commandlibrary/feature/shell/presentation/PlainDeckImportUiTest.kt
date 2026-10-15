package com.architectpsg.commandlibrary.feature.shell.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.R
import com.architectpsg.commandlibrary.feature.shell.ui.decks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real dialog/controller over explicit small codec/library port fixtures; not SAF/SQL evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlainDeckImportUiTest {
    @get:Rule val rule = createComposeRule()
    private val scope = TestScope()
    private val f = ImportUiPorts(scope)
    private var pending by mutableStateOf(false)
    private var chooses = 0
    @After fun close() { scope.cancel(); scope.testScheduler.runCurrent() }
    private fun show() {
        rule.setContent { MaterialTheme { PlainDeckImportSurface(f.controller, { chooses++; pending = true }, pending) } }
        rule.waitForIdle()
    }
    private fun pump() { rule.runOnIdle { scope.runCurrent() }; rule.waitForIdle() }
    private fun start(id: String = "source", parsed: PlainDeckList = importRows()) {
        // Models root's selected-file callback; this consumer has no startup/open/empty-parse command.
        rule.runOnIdle { f.controller.parse(f.input(id, parsed)) }; pump()
    }
    private fun reveal(tag: String) { rule.onNodeWithTag("plain-import-list").performScrollToNode(hasTestTag(tag)); rule.waitForIdle() }
    private fun click(tag: String, inList: Boolean = false) {
        if (inList) reveal(tag)
        rule.onNodeWithTag(tag).assertIsEnabled().performClick(); pump()
    }
    private fun name(raw: String) { reveal("plain-import-name"); rule.onNodeWithTag("plain-import-name").performTextReplacement(raw); pump() }
    private fun text(id: Int, vararg args: Any): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun everyPhysicalRowAndExactNameAreReviewedBeforeOneNewUnresolvedDeckAndSavedNotice() {
        show(); rule.onNodeWithTag("plain-import-list").assertDoesNotExist()
        val parsed = importRows(); start(parsed = parsed)
        assertFalse(f.controller.state.value.parsing)
        assertEquals(parsed, f.controller.state.value.input)
        for (row in parsed.rows) {
            val tag = "plain-import-row-${row.location.rowNumber}"; reveal(tag)
            rule.onNodeWithTag(tag).assertTextEquals(if (row.rawText.isEmpty()) text(R.string.plain_import_blank_line) else row.rawText)
        }
        reveal("plain-import-unresolved"); rule.onNodeWithTag("plain-import-unresolved").assertTextEquals(text(R.string.plain_import_unresolved))
        val rawName = "  Exact imported deck 😃  "
        name(rawName); assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids); assertEquals(0, f.clocks)
        click("plain-import-preview")
        val preview = checkNotNull(f.controller.state.value.preview)
        reveal("plain-import-preview-summary")
        rule.onNodeWithTag("plain-import-preview-summary").assertTextEquals(text(R.string.plain_import_preview_summary, 3, 7L))
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids); assertEquals(0, f.clocks)
        click("plain-import-confirm")
        val initial = f.creates.single().initial
        assertEquals(rawName, initial.name)
        assertEquals(listOf(2, 2, 3), initial.entries.map { it.quantity })
        assertEquals(listOf("Same", "Same", "Side"), initial.entries.map { (it.identity.originalName as Knowledge.Known).value })
        assertEquals(listOf(BoardRole.MAIN, BoardRole.MAIN, BoardRole.SIDEBOARD), initial.entries.map { it.board })
        assertTrue(initial.entries.all { it.identity.language == LanguageTag("und") && it.identity.oracleId is Knowledge.Unknown &&
            it.identity.printingId is Knowledge.Unknown && it.identity.raw == null && it.finish is Knowledge.Unknown })
        assertEquals(preview.name, initial.name); assertEquals(1, f.groups); assertEquals(listOf(initial), f.notices)
        assertEquals(1, f.clocks)
        rule.onNodeWithTag("plain-import-saved").assertTextEquals(text(R.string.plain_import_saved, rawName))
        rule.onNodeWithTag("plain-import-resume").assertDoesNotExist()
        click("plain-import-dismiss")
        rule.onNodeWithTag("plain-import-saved").assertDoesNotExist()
        assertEquals(initial, f.heads[initial.id]); assertEquals(1, f.creates.size) // Dismiss clears display, not the deck.
    }

    @Test fun actualWidgetBusinessActionsRejectUnseenPreviewAndOldFormNameCallbacks() {
        show(); start(); name("First"); click("plain-import-preview")
        val first = checkNotNull(f.controller.state.value.preview)
        val ticket = f.controller.state.value.formTicket
        // The factories below are exactly those consumed by TextField and Confirm, not detached Material semantics wrappers.
        val oldName = plainImportNameAction(f.controller, ticket)
        val oldConfirm = plainImportConfirmAction(f.controller, ticket, first) { pending }
        name("Latest"); assertNull(f.controller.state.value.preview); click("plain-import-preview")
        val newer = f.controller.state.value.preview
        assertNotSame(first, newer)
        rule.runOnIdle { oldConfirm() }; pump()
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids)
        start("replacement")
        val current = f.controller.state.value
        assertTrue(current.formTicket > ticket)
        rule.runOnIdle { oldName("Stale callback must not replace"); oldConfirm() }; pump()
        assertEquals("Latest", f.controller.state.value.name)
        assertEquals(EntityId("replacement"), f.controller.state.value.input!!.inputId)
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids)
        click("plain-import-preview"); click("plain-import-confirm")
        assertEquals("Latest", f.creates.single().initial.name); assertEquals(1, f.groups)
    }

    @Test fun invalidRowsStayVisibleWithStaticIssuesAndWarningsNeverImportAValidSubset() {
        show(); val parsed = importRows(invalid = true); start(parsed = parsed)
        // Wait for real controller admission, rather than treating a temporarily disabled button as readiness.
        assertFalse(f.controller.state.value.parsing); assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        name("Keep all rows")
        for (row in parsed.rows) {
            val tag = "plain-import-row-${row.location.rowNumber}"; reveal(tag)
            rule.onNodeWithTag(tag).assertTextEquals(if (row.rawText.isEmpty()) text(R.string.plain_import_blank_line) else row.rawText)
            for (issue in row.issues) {
                val label = when (issue.code) {
                    PlainDeckListIssueCodes.INVALID_QUANTITY -> R.string.plain_import_invalid_quantity
                    PlainDeckListIssueCodes.INVALID_MARKER -> R.string.plain_import_invalid_marker
                    else -> R.string.plain_import_invalid_row
                }
                rule.onNodeWithText(text(label)).assertExists()
                rule.onNodeWithText(issue.description).assertDoesNotExist()
            }
        }
        rule.onNodeWithTag("plain-import-list").performScrollToIndex(0)
        reveal("plain-import-invalid"); rule.onNodeWithTag("plain-import-invalid").assertTextEquals(text(R.string.plain_import_invalid_rows))
        rule.onNodeWithTag("plain-import-preview").assertIsNotEnabled()
        rule.onNodeWithTag("plain-import-confirm").assertDoesNotExist()
        click("plain-import-choose", true); click("plain-import-replace-cancel")
        assertEquals(0, chooses); assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        click("plain-import-discard", true); click("plain-import-discard-cancel")
        assertEquals("Keep all rows", f.controller.state.value.name)
        assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        click("plain-import-choose", true); click("plain-import-replace-confirm")
        assertEquals(1, chooses); assertTrue(pending)
        assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        reveal("plain-import-choose"); rule.onNodeWithTag("plain-import-choose").assertIsNotEnabled()
        rule.onNodeWithTag("plain-import-discard").assertIsNotEnabled()
        rule.runOnIdle { pending = false }; pump() // Picker cancellation supplies no new parse/stream.
        assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        assertEquals(1, f.collections); assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids); assertEquals(0, f.clocks)
    }

    @Test fun hideResumeRetainsUncertainExactRequestThenKnownAckRetriesOnlyReads() {
        show(); start(); name("Retained deck")
        f.afterUnknown = true
        click("plain-import-preview"); click("plain-import-confirm")
        val exact = f.creates.single(); val ids = f.ids; val reads = f.loads.size
        assertTrue(f.controller.state.value.uncertain)
        reveal("plain-import-uncertain"); rule.onNodeWithTag("plain-import-uncertain").assertTextEquals(text(R.string.plain_import_uncertain))
        val input = f.controller.state.value.input
        click("plain-import-close"); click("plain-import-resume")
        assertEquals(input, f.controller.state.value.input); assertEquals("Retained deck", f.controller.state.value.name)
        assertEquals(listOf(exact), f.creates); assertEquals(reads, f.loads.size); assertEquals(ids, f.ids)
        reveal("plain-import-choose"); rule.onNodeWithTag("plain-import-choose").assertIsNotEnabled()
        rule.onNodeWithTag("plain-import-discard").assertIsNotEnabled()
        f.failRead = true
        click("plain-import-retry")
        assertEquals(listOf(exact, exact), f.creates)
        assertTrue(f.controller.state.value.awaitingFresh); assertFalse(f.controller.state.value.uncertain)
        assertEquals(exact.initial.id, f.controller.state.value.acknowledgedId); assertNull(f.controller.state.value.currentHead)
        reveal("plain-import-awaiting-fresh")
        rule.onNodeWithTag("plain-import-awaiting-fresh").assertTextEquals(text(R.string.plain_import_awaiting_fresh))
        rule.onNodeWithTag("plain-import-confirm").assertDoesNotExist()
        click("plain-import-retry"); assertEquals(listOf(exact, exact), f.creates)
        f.failRead = false; click("plain-import-retry")
        assertEquals(listOf(exact, exact), f.creates); assertEquals(ids, f.ids); assertEquals(1, f.clocks)
        assertEquals(1, f.groups); assertEquals(listOf(exact.initial), f.notices)
        rule.onNodeWithTag("plain-import-saved").assertTextEquals(text(R.string.plain_import_saved, "Retained deck"))
    }

    @Test fun replacementAndDiscardWarningsFenceExactFrameAndStopReadingOnlyCancelsRead() {
        show(); start(); name("Warned name"); click("plain-import-preview")
        click("plain-import-choose", true)
        val frame = plainImportWarningFrame(f.controller.state.value)
        val staleReplace = plainImportReplaceAction(f.controller, frame, { pending }) { chooses++ }
        val staleDiscard = plainImportDiscardAction(f.controller, frame) { pending }
        val currentName = plainImportNameAction(f.controller, frame.ticket)
        rule.runOnIdle { currentName("Later exact name") }; pump() // A delayed real text business callback races with the warning.
        rule.onNodeWithTag("plain-import-replace-confirm").assertIsNotEnabled()
        rule.runOnIdle { staleReplace(); staleDiscard() }; pump()
        assertEquals(0, chooses); assertEquals("Later exact name", f.controller.state.value.name)
        assertNotNull(f.controller.state.value.input)
        click("plain-import-replace-cancel")
        click("plain-import-preview")
        click("plain-import-discard", true)
        val warned = plainImportWarningFrame(f.controller.state.value)
        val oldDiscard = plainImportDiscardAction(f.controller, warned) { pending }
        val gate = f.gateParse("reading")
        start("reading") // Controlled selected-file event arrives after a prior warning; never a fake empty parse.
        val ticket = f.controller.state.value.formTicket
        rule.runOnIdle { oldDiscard() }; pump()
        assertEquals(ticket, f.controller.state.value.formTicket); assertTrue(f.controller.state.value.parsing)
        click("plain-import-stop-reading", true)
        gate.complete(Unit); pump()
        assertFalse(f.controller.state.value.parsing); assertNull(f.controller.state.value.input)
        assertEquals("Later exact name", f.controller.state.value.name)
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids); assertEquals(0, f.clocks)
        start("fresh")
        click("plain-import-discard", true)
        assertNotNull(f.controller.state.value.input)
        click("plain-import-discard-confirm")
        assertNull(f.controller.state.value.input); assertFalse(f.controller.state.value.visible)
        rule.onNodeWithTag("plain-import-resume").assertDoesNotExist()
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.ids)
    }
}

/** Small scripted codec data + actual receipt semantics; no source URI, real parser or SQL adapter. */
private class ImportUiPorts(private val test: TestScope) : PlainDeckListCodec, LocalDeckLibrary {
    data class Create(val initial: DeckSnapshot, val operation: EntityId)
    val creates = mutableListOf<Create>(); val loads = mutableListOf<DeckId>(); val notices = mutableListOf<DeckSnapshot>()
    val heads = mutableMapOf<DeckId, DeckSnapshot>()
    private val receipts = mutableMapOf<EntityId, Create>()
    private val parsed = mutableMapOf<EntityId, PlainDeckList>()
    private val gates = mutableMapOf<EntityId, CompletableDeferred<Unit>>()
    var ids = 0; var clocks = 0; var collections = 0; var groups = 0
    var afterUnknown = false; var failRead = false
    val controller = PlainDeckImportController(test, this, this, StandardTestDispatcher(test.testScheduler),
        { EntityId("generated-${++ids}") }, { clocks++; UtcTimestamp(10) },
        { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }, { notices.add(it) })
    fun input(id: String, value: PlainDeckList): TransferInput {
        val inputId = EntityId(id); parsed[inputId] = value.copy(inputId = inputId)
        val bytes = value.rows.joinToString("\n") { it.rawText }.toByteArray(Charsets.UTF_8)
        var consumed = false
        return TransferInput(inputId, "plain.txt", Knowledge.Known("text/plain"), Knowledge.Known(bytes.size.toLong()), flow {
            check(!consumed); consumed = true; collections++
            emit(ByteChunk(0, 0, SnapshotBytes(bytes), null, true))
        })
    }
    fun gateParse(id: String) = CompletableDeferred<Unit>().also { gates[EntityId(id)] = it }
    override suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList> {
        input.chunks.collect { cancel.throwIfCancelled(); it.bytes.copyBytes() }
        val result = parsed.getValue(input.id)
        gates.remove(input.id)?.await()
        return Outcome.Success(result)
    }
    override fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport> = error("No export")
    override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
        val call = Create(initial, operationId); creates.add(call)
        val old = receipts[operationId]
        if (old != null && old != call) return Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT, "Static fixture refusal", SnapshotList.empty()))
        if (old == null) { check(initial.id !in heads); receipts[operationId] = call; heads[initial.id] = initial; groups++ }
        if (afterUnknown) { afterUnknown = false; return Outcome.Unknown("No backend detail should be shown", SnapshotList.empty()) }
        return Outcome.Success(receipts.getValue(operationId).initial)
    }
    override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> {
        loads.add(id)
        return if (failRead) Outcome.Unknown("provider://backend/private", SnapshotList.empty()) else Outcome.Success(heads.getValue(id))
    }
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = error("No listing")
    override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No existing-deck mutation")
}

private fun importRows(invalid: Boolean = false): PlainDeckList {
    val lines: List<Pair<String, PlainDeckListEntry?>> = if (invalid) listOf(
        PlainDeckListLimits.MARKER to null, "" to null, "Deck" to null, "2 Good" to PlainDeckListEntry(2, "Good", BoardRole.MAIN),
        "bad row" to null, "0 Invalid" to null, "Command Library Deck List bad" to null)
    else listOf(PlainDeckListLimits.MARKER to null, "" to null, "Deck" to null,
        "2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN), "2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN),
        "Sideboard" to null, "3 Side" to PlainDeckListEntry(3, "Side", BoardRole.SIDEBOARD))
    var offset = 0L
    return PlainDeckList(EntityId("source"), SnapshotList.copyOf(lines.mapIndexed { index, (raw, entry) ->
        val location = RowLocation("plain.txt", index + 1L, offset, null)
        offset += raw.toByteArray(Charsets.UTF_8).size + 1L
        val issue = if (!invalid || index < 4) null else when (index) {
            4 -> PlainDeckListIssueCodes.INVALID_ROW
            5 -> PlainDeckListIssueCodes.INVALID_QUANTITY
            else -> PlainDeckListIssueCodes.INVALID_MARKER
        }
        PlainDeckListRow(location, raw, entry, if (issue == null) SnapshotList.empty() else SnapshotList.of(ImportIssue(
            location, issue, IssueSeverity.ERROR, "provider://backend/private", Knowledge.Absent("No automatic correction"))))
    }), !invalid)
}
