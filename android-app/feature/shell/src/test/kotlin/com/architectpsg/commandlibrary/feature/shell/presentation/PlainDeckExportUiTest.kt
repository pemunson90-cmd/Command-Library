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
import com.architectpsg.commandlibrary.feature.shell.document.DocumentRef
import com.architectpsg.commandlibrary.feature.shell.document.PlainDeckWriteResult
import com.architectpsg.commandlibrary.feature.shell.ui.decks.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real surface/controller, owned scheduler and explicit ports. No provider/SQL/native evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlainDeckExportUiTest {
    @get:Rule val rule = createComposeRule()
    private val scope = TestScope()
    private val f = ExportUiPorts(scope)
    private var pending by mutableStateOf(false)
    private var pickerTicket: String? = null
    private val chosen = mutableListOf<PlainDeckExportPreview>()
    @After fun close() { scope.cancel(); scope.testScheduler.runCurrent() }
    private fun choose(preview: PlainDeckExportPreview) {
        // Faithful root boundary: surface never launches or creates a controller ticket itself.
        val ticket = f.controller.requestDestination(preview) ?: return
        chosen += preview; pickerTicket = ticket; pending = true
    }
    private fun show() {
        rule.setContent { MaterialTheme { PlainDeckExportSurface(f.controller, ::choose, pending) } }
        rule.waitForIdle()
    }
    private fun pump() { rule.runOnIdle { scope.runCurrent() }; rule.waitForIdle() }
    private fun start() {
        rule.runOnIdle { f.controller.prepare(f.head.id, f.head.revision) }; pump()
    }
    private fun reveal(tag: String) {
        rule.onNodeWithTag("plain-export-list").performScrollToNode(hasTestTag(tag)); rule.waitForIdle()
    }
    private fun click(tag: String, inList: Boolean = false) {
        if (inList) reveal(tag)
        rule.onNodeWithTag(tag).assertIsEnabled().performClick(); pump()
    }
    private fun finish(uri: String?) {
        rule.runOnIdle {
            val ticket = checkNotNull(pickerTicket)
            pickerTicket = null; pending = false
            f.controller.pickerResult(ticket, uri?.let(::DocumentRef))
        }
        pump()
    }
    private fun text(id: Int, vararg args: Any): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun originalSourceAllLossLabelsAndExactNamesAreReviewedBeforeOneExactPreviewChoice() {
        show(); rule.onNodeWithTag("plain-export-list").assertDoesNotExist(); assertTrue(f.loads.isEmpty())
        start(); val preview = checkNotNull(f.controller.state.value.preview)
        reveal("plain-export-source"); rule.onNodeWithTag("plain-export-source").assertTextEquals(f.head.name).assertIsDisplayed()
        reveal("plain-export-revision"); rule.onNodeWithTag("plain-export-revision").assertTextEquals(text(R.string.plain_export_revision, 7L))
        reveal("plain-export-summary"); rule.onNodeWithTag("plain-export-summary").assertTextEquals(text(R.string.plain_export_summary, 2, 4_000_000_000L))
        reveal("plain-export-format"); rule.onNodeWithTag("plain-export-format").assertTextEquals(text(R.string.plain_export_format)).assertIsDisplayed()
        reveal("plain-export-loss-summary"); rule.onNodeWithTag("plain-export-loss-summary").assertTextEquals(text(R.string.plain_export_loss_summary, 34))
        assertEquals(PlainDeckListLossField.entries.toSet(), preview.export.losses.map { it.field }.toSet())
        for (loss in preview.export.losses) {
            val id = loss.entryId
            val key = if (id == null) "plain-export-loss-deck-${loss.field.name}" else "plain-export-loss-entry-${id.value}-${loss.field.name}"
            // The outer list owns this exact key; never scroll a nested raw-name ancestor.
            rule.onNodeWithTag("plain-export-list").performScrollToKey(key)
            rule.onNodeWithTag(key).assertTextEquals(text(lossResource(loss.field))).assertIsDisplayed()
            if (id != null) {
                val original = preview.source.entries.single { it.id == id }
                rule.onNodeWithTag("$key-name").assertTextEquals((original.identity.originalName as Knowledge.Known).value).assertIsDisplayed()
            }
        }
        assertEquals(1, f.loads.size); assertEquals(listOf(f.head), f.validated); assertEquals(listOf(f.head), f.rendered)
        assertTrue(chosen.isEmpty()); assertTrue(f.writes.isEmpty())
        click("plain-export-choose")
        assertEquals(1, chosen.size); assertSame(preview, chosen.single()); assertTrue(pending)
        assertTrue(f.writes.isEmpty())
    }

    @Test fun preparationStopTypedErrorAndEmptyDeckWidgetsUseRealAdmissionWithoutHiddenFailureBanner() {
        show(); val read = CompletableDeferred<Unit>(); f.readGate = read
        start(); assertTrue(f.controller.state.value.preparing)
        reveal("plain-export-preparing"); rule.onNodeWithTag("plain-export-preparing").assertTextEquals(text(R.string.plain_export_preparing))
        click("plain-export-stop-preparation", true)
        read.complete(Unit); pump(); assertNull(f.controller.state.value.preview)
        f.validation = { _, _ -> Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Private backend message", SnapshotList.empty())) }
        start(); assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        reveal("plain-export-problem"); rule.onNodeWithTag("plain-export-problem").assertExists()
        rule.onNodeWithText("Private backend message").assertDoesNotExist()
        rule.onNodeWithTag("plain-export-choose").assertDoesNotExist()
        click("plain-export-hide"); rule.onNodeWithTag("plain-export-resume").assertDoesNotExist()
        f.validation = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
        f.head = f.head.copy(entries = SnapshotList.empty()); start()
        assertFalse(f.controller.state.value.preparing); assertNotNull(f.controller.state.value.preview)
        reveal("plain-export-summary"); rule.onNodeWithTag("plain-export-summary").assertTextEquals(text(R.string.plain_export_summary, 0, 0L))
        rule.onNodeWithTag("plain-export-choose").assertIsEnabled()
        assertTrue(chosen.isEmpty()); assertTrue(f.writes.isEmpty())
    }

    @Test fun widgetBusinessCallbacksFenceHiddenNewFramesExternalPickerAndLaterAttemptEndWarning() {
        show(); start(); val preview = checkNotNull(f.controller.state.value.preview); val ticket = f.controller.state.value.formTicket
        // These factories are the exact business callbacks the surface passes to its widgets.
        val oldChoose = plainExportChooseAction(f.controller, ticket, preview, { pending }, ::choose)
        val oldHide = plainExportHideAction(f.controller, ticket)
        rule.runOnIdle { pending = true; oldChoose() }; pump(); assertTrue(chosen.isEmpty())
        rule.runOnIdle { pending = false }; pump()
        click("plain-export-hide"); rule.runOnIdle { oldChoose() }; pump(); assertTrue(chosen.isEmpty())
        click("plain-export-resume")
        click("plain-export-end", true)
        val frame = plainExportEndFrame(f.controller.state.value, preview)
        val oldEnd = plainExportEndAction(f.controller, frame) { pending }
        // A queued genuine Choose business callback arrives after the warning opened.
        rule.runOnIdle { oldChoose() }; pump(); finish("content://selected/after-warning")
        assertSame(preview, f.controller.state.value.preview); assertEquals(1, f.controller.state.value.attempts)
        rule.onNodeWithTag("plain-export-end-confirm").assertIsNotEnabled()
        rule.runOnIdle { oldEnd() }; pump(); assertSame(preview, f.controller.state.value.preview)
        click("plain-export-end-cancel")
        click("plain-export-end", true); click("plain-export-end-confirm")
        assertNull(f.controller.state.value.preview); rule.onNodeWithTag("plain-export-resume").assertDoesNotExist()
        start(); val next = checkNotNull(f.controller.state.value.preview)
        rule.runOnIdle { oldChoose(); oldEnd(); oldHide() }; pump()
        assertTrue(f.controller.state.value.visible); assertSame(next, f.controller.state.value.preview)
        assertEquals(1, chosen.size); assertEquals(1, f.writes.size)
        click("plain-export-choose"); assertSame(next, chosen.last())
    }

    @Test fun hidingPendingPickerThenGlobalResumePermanentlyRevokesItsResultUntilNewExplicitChoice() {
        show(); start(); val preview = checkNotNull(f.controller.state.value.preview)
        click("plain-export-choose"); val originalTicket = pickerTicket
        rule.onNodeWithTag("plain-export-choose").assertIsNotEnabled()
        reveal("plain-export-end"); rule.onNodeWithTag("plain-export-end").assertIsNotEnabled()
        click("plain-export-hide"); rule.onNodeWithTag("plain-export-resume").assertIsDisplayed()
        assertEquals(originalTicket, f.controller.state.value.pendingPicker); assertFalse(f.controller.state.value.pickerAuthorized)
        click("plain-export-resume"); reveal("plain-export-pending")
        rule.onNodeWithTag("plain-export-pending").assertTextEquals(text(R.string.plain_export_pending_revoked))
        finish("content://selected/revoked")
        assertTrue(f.writes.isEmpty()); assertEquals(0, f.controller.state.value.attempts); assertSame(preview, f.controller.state.value.preview)
        click("plain-export-choose"); finish("content://selected/explicit-new")
        reveal("plain-export-written"); rule.onNodeWithTag("plain-export-written").assertTextEquals(text(R.string.plain_export_written))
        assertEquals(1, f.writes.size); assertSame(preview.export.bytes, f.writes.single().bytes)
        assertEquals(1, f.loads.size); assertEquals(1, f.rendered.size)
    }

    @Test fun truthfulRecoveryAndNoncooperativeCleanupKeepBusyUntilClosureThenLimitNeedsExplicitEnd() {
        show(); start(); val preview = checkNotNull(f.controller.state.value.preview)
        f.writer = { _, _, _ -> PlainDeckWriteResult.NotStarted(Problem.PERMISSION) }
        click("plain-export-choose"); finish("content://selected/not-started")
        reveal("plain-export-not-started"); rule.onNodeWithTag("plain-export-not-started").assertTextEquals(text(R.string.plain_export_not_started))
        f.writer = { _, _, _ -> PlainDeckWriteResult.PossiblyModified(Problem.PERSISTENCE) }
        click("plain-export-choose"); finish("content://selected/partial")
        reveal("plain-export-possibly-modified"); rule.onNodeWithTag("plain-export-possibly-modified").assertTextEquals(text(R.string.plain_export_possibly_modified))
        val work = CompletableDeferred<Unit>(); val closing = CompletableDeferred<Unit>()
        var cleanup = false; var writerJob: Job? = null
        f.writer = { _, _, _ ->
            writerJob = currentCoroutineContext().job
            try { withContext(NonCancellable) { work.await() }; PlainDeckWriteResult.Written }
            finally { withContext(NonCancellable) { closing.await(); cleanup = true } }
        }
        click("plain-export-choose"); finish("content://selected/cancelled")
        reveal("plain-export-writing"); rule.onNodeWithTag("plain-export-writing").assertTextEquals(text(R.string.plain_export_writing))
        click("plain-export-cancel-write", true)
        assertFalse(checkNotNull(writerJob).isActive); assertFalse(cleanup)
        reveal("plain-export-cancelling"); rule.onNodeWithTag("plain-export-cancelling").assertTextEquals(text(R.string.plain_export_cancelling))
        rule.onNodeWithTag("plain-export-choose").assertIsNotEnabled()
        reveal("plain-export-end"); rule.onNodeWithTag("plain-export-end").assertIsNotEnabled()
        click("plain-export-hide"); rule.onNodeWithTag("plain-export-cancelling").assertExists()
        assertEquals(3, f.writes.size); click("plain-export-resume")
        work.complete(Unit); pump(); assertFalse(cleanup); assertEquals(PlainDeckExportWritePhase.CANCELLING, f.controller.state.value.writePhase)
        closing.complete(Unit); pump(); assertTrue(cleanup)
        assertEquals(PlainDeckWriteResult.PossiblyModified(Problem.UNKNOWN_RESULT), f.controller.state.value.lastWrite)
        f.writer = { _, _, _ -> PlainDeckWriteResult.Written }
        click("plain-export-choose"); finish("content://selected/complete")
        reveal("plain-export-written"); rule.onNodeWithTag("plain-export-written").assertTextEquals(text(R.string.plain_export_written))
        assertEquals(4, f.writes.size); assertTrue(f.writes.all { it.bytes === preview.export.bytes })
        assertEquals(1, f.loads.size); assertEquals(1, f.rendered.size)
        // Actual retained producer callbacks fill the remaining bounded slots; no state injection.
        repeat(28) { index -> rule.runOnIdle { choose(preview) }; finish("content://selected/remaining-$index") }
        assertEquals(32, f.controller.state.value.attempts)
        rule.onNodeWithTag("plain-export-choose").assertIsNotEnabled()
        reveal("plain-export-limit"); rule.onNodeWithTag("plain-export-limit").assertTextEquals(text(R.string.plain_export_limit))
        click("plain-export-end", true); click("plain-export-end-cancel")
        assertSame(preview, f.controller.state.value.preview)
        click("plain-export-end", true); click("plain-export-end-confirm")
        assertNull(f.controller.state.value.preview); rule.onNodeWithTag("plain-export-resume").assertDoesNotExist()
        assertEquals(32, f.writes.size)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class ExportUiPorts(test: TestScope) : LocalDeckLibrary, PlainDeckListCodec {
    data class Write(val destination: DocumentRef, val bytes: SnapshotBytes)
    var head = exportUiDeck()
    var readGate: CompletableDeferred<Unit>? = null
    val loads = mutableListOf<DeckId>()
    val validated = mutableListOf<DeckSnapshot>()
    val rendered = mutableListOf<DeckSnapshot>()
    val writes = mutableListOf<Write>()
    var validation: (DeckSnapshot, CancelToken) -> Outcome<Unit> = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
    var writer: suspend (DocumentRef, SnapshotBytes, CancelToken) -> PlainDeckWriteResult = { _, _, cancel -> cancel.throwIfCancelled(); PlainDeckWriteResult.Written }
    val controller = PlainDeckExportController(test.backgroundScope, this, this,
        { deck, cancel -> validated += deck; validation(deck, cancel) },
        { destination, bytes, cancel -> writes += Write(destination, bytes); writer(destination, bytes, cancel) },
        StandardTestDispatcher(test.testScheduler), "export-ui-owner")
    override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> {
        loads += id
        val captured = head
        val gate = readGate; readGate = null; gate?.await()
        cancel.throwIfCancelled(); return Outcome.Success(captured)
    }
    override fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport> {
        cancel.throwIfCancelled(); rendered += deck
        val deckFields = PlainDeckListLossField.entries.take(16)
        val entryFields = PlainDeckListLossField.entries.drop(16)
        val body = if (deck.entries.isEmpty()) "Command Library Deck List 1\n" else
            "Command Library Deck List 1\nDeck\n2000000000 Same  exact 😃\nSideboard\n2000000000 Long exact name " + "a".repeat(400) + "\n"
        return Outcome.Success(PlainDeckListExport(SnapshotBytes(body.toByteArray()),
            SnapshotList.copyOf(deckFields.map { PlainDeckListLoss(null, it) } + deck.entries.flatMap { entry -> entryFields.map { PlainDeckListLoss(entry.id, it) } })))
    }
    override suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList> = error("No parse")
    override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No deck mutation")
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = error("No implicit list")
    override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No duplicate mutation")
}

private fun exportUiDeck(): DeckSnapshot {
    fun entry(id: String, name: String, board: BoardRole) = DeckEntry(EntityId(id), CardIdentity(Knowledge.Absent("oracle"), Knowledge.Absent("printing"), null,
        Knowledge.Known(name), LanguageTag("ja")), 2_000_000_000, board, Knowledge.Known(Finish.FOIL), "Notes", SnapshotList.empty(), SnapshotList.empty())
    return DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "export-ui-deck"), "  Exact saved deck 😃  ", Knowledge.Absent("format"), "Description",
        Knowledge.Known("Folder"), SnapshotList.of("Tag"), UtcTimestamp(1), UtcTimestamp(2), Revision(7),
        SnapshotList.of(entry("original-main", "Same  exact 😃", BoardRole.MAIN),
            entry("original-side", "Long exact name " + "a".repeat(400), BoardRole.SIDEBOARD)),
        Knowledge.Absent("budget"), null, SnapshotList.empty(), SnapshotList.empty(), "Primer")
}

/** Independent explicit resource oracle for all 25 labels; not the production mapping helper. */
private fun lossResource(field: PlainDeckListLossField): Int = when (field) {
    PlainDeckListLossField.DECK_ID -> R.string.plain_export_loss_deck_id
    PlainDeckListLossField.DECK_NAME -> R.string.plain_export_loss_deck_name
    PlainDeckListLossField.FORMAT -> R.string.plain_export_loss_format
    PlainDeckListLossField.DESCRIPTION -> R.string.plain_export_loss_description
    PlainDeckListLossField.FOLDER -> R.string.plain_export_loss_folder
    PlainDeckListLossField.TAGS -> R.string.plain_export_loss_tags
    PlainDeckListLossField.CREATED_AT -> R.string.plain_export_loss_created_at
    PlainDeckListLossField.MODIFIED_AT -> R.string.plain_export_loss_modified_at
    PlainDeckListLossField.REVISION -> R.string.plain_export_loss_revision
    PlainDeckListLossField.BUDGET -> R.string.plain_export_loss_budget
    PlainDeckListLossField.PROFILE_ID -> R.string.plain_export_loss_profile_id
    PlainDeckListLossField.CATALOG_REFERENCES -> R.string.plain_export_loss_catalog_references
    PlainDeckListLossField.PACKAGE_INSTANCES -> R.string.plain_export_loss_package_instances
    PlainDeckListLossField.PRIMER -> R.string.plain_export_loss_primer
    PlainDeckListLossField.CATEGORY_ORDER -> R.string.plain_export_loss_category_order
    PlainDeckListLossField.ENTRY_ORDER -> R.string.plain_export_loss_entry_order
    PlainDeckListLossField.ENTRY_ID -> R.string.plain_export_loss_entry_id
    PlainDeckListLossField.ORACLE_ID -> R.string.plain_export_loss_oracle_id
    PlainDeckListLossField.PRINTING_ID -> R.string.plain_export_loss_printing_id
    PlainDeckListLossField.RAW_IDENTITY -> R.string.plain_export_loss_raw_identity
    PlainDeckListLossField.LANGUAGE -> R.string.plain_export_loss_language
    PlainDeckListLossField.FINISH -> R.string.plain_export_loss_finish
    PlainDeckListLossField.NOTES -> R.string.plain_export_loss_notes
    PlainDeckListLossField.LABELS -> R.string.plain_export_loss_labels
    PlainDeckListLossField.ROLE_OVERRIDES -> R.string.plain_export_loss_role_overrides
}
