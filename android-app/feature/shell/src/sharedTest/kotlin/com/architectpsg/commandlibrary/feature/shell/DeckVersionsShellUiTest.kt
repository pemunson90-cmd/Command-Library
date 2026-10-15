package com.architectpsg.commandlibrary.feature.shell

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.presentation.Problem
import com.architectpsg.commandlibrary.feature.shell.ui.decks.DeckVersionsTags
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Actual ShellRoot/controller navigation over explicit test-only ports; real Room has separate probes. */
@RunWith(AndroidJUnit4::class)
class DeckVersionsShellUiTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var shell: ShellUiHarness
    private val fixture = Fixture()
    private class Fixture : LocalDeckLibrary, LocalDeckVersions {
        val initial = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "shell-versions"), "Shell version deck", Knowledge.Absent("none"), "",
            Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(0), SnapshotList.empty(),
            Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
        @Volatile var head = initial
        @Volatile var failReads = false
        @Volatile var loseRestoreAcknowledgement = false
        private var receipt: Pair<List<Any>, DeckRestoreResult>? = null
        val restoreAttempts = AtomicInteger()
        @Volatile var recovery: DeckVersion? = null
        val writes = AtomicInteger()
        private val epoch = AtomicLong(2) // Existing create and named capture in this test-only fixture.
        val checkpoint = DeckVersion(EntityId("checkpoint"), initial.id, "Initial checkpoint", UtcTimestamp(2), Revision(0), initial, null)
        override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("Unexpected library creation")
        override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("Unexpected duplication")
        override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> = if (failReads)
            Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Synthetic read failed", SnapshotList.empty())) else Outcome.Success(head)
        override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = Outcome.Success(Page(
            SnapshotList.of(DeckSummary(head.id, head.name, head.revision, head.createdAt, head.modifiedAt, 0, 0)), null,
            SnapshotReference(LocalDeckLibraryLimits.SOURCE, null, Revision(epoch.get()), null)))
        override suspend fun capture(id: DeckId, expected: Revision, label: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckVersion> {
            writes.incrementAndGet(); error("Navigation must not create checkpoints")
        }
        override suspend fun list(id: DeckId, page: PageRequest, cancel: CancelToken): Outcome<Page<DeckVersionSummary>> {
            fun summary(v: DeckVersion, kind: DeckVersionKind, target: EntityId?) = DeckVersionSummary(v.id, v.deckId, v.label, v.createdAt,
                v.sourceRevision, v.predecessorVersionId, kind, target)
            return Outcome.Success(Page(SnapshotList.copyOf(listOfNotNull(recovery?.let { summary(it, DeckVersionKind.RECOVERY, checkpoint.id) },
                summary(checkpoint, DeckVersionKind.USER, null))), null, SnapshotReference(LocalDeckLibraryLimits.SOURCE, null, Revision(epoch.get()), null)))
        }
        override suspend fun load(id: DeckId, versionId: EntityId, cancel: CancelToken): Outcome<DeckVersion> =
            listOfNotNull(checkpoint, recovery).firstOrNull { it.id == versionId }?.let { Outcome.Success(it) }
                ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Synthetic missing version")
        override suspend fun previewRestore(id: DeckId, versionId: EntityId, expected: Revision, cancel: CancelToken): Outcome<DeckRestorePreview> =
            Outcome.Success(DeckRestorePreview(head, checkpoint, DeckDifference(head, checkpoint.manifest, SnapshotList.empty(), SnapshotList.empty())))
        override suspend fun restore(id: DeckId, versionId: EntityId, expected: Revision, recoveryLabel: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckRestoreResult> {
            restoreAttempts.incrementAndGet()
            val frozen = listOf<Any>(id, versionId, expected, recoveryLabel, operationId)
            receipt?.let { (request, result) ->
                check(request == frozen) { "Retry changed the frozen request" }
                return if (loseRestoreAcknowledgement) Outcome.Unknown("Synthetic lost acknowledgement", SnapshotList.empty()) else Outcome.Success(result)
            }
            writes.incrementAndGet()
            val before = head
            val saved = checkpoint.manifest.copy(revision = Revision(expected.value + 1), modifiedAt = UtcTimestamp(10))
            val recovery = DeckVersion(EntityId("recovery"), id, recoveryLabel, UtcTimestamp(10), expected, before, checkpoint.id)
            this.recovery = recovery; head = saved; epoch.incrementAndGet()
            val result = DeckRestoreResult(saved, recovery)
            receipt = frozen to result
            return if (loseRestoreAcknowledgement) Outcome.Unknown("Synthetic lost acknowledgement", SnapshotList.empty()) else Outcome.Success(result)
        }
    }
    @Before fun setup() {
        profileHostIdleBudget(300)
        shell = ShellUiHarness(rule, decks = fixture, deckVersions = fixture)
    }
    @After fun close() { shell.close(); profileHostIdleBudget(60) }
    private fun openPreview() {
        shell.show(900.dp, 800.dp)
        rule.onNodeWithTag("shell-nav-decks").performClick()
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.items.isNotEmpty() }
        rule.onNodeWithTag("deck-open-shell-versions").performScrollTo().performClick()
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.selected != null }
        rule.onNodeWithTag("deck-open-versions").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        shell.awaitState { shell.controllers.deckVersions!!.state.value.let { !it.loadingHead && it.items.isNotEmpty() } }
        rule.onNodeWithTag("deck-version-checkpoint").performScrollTo().performClick()
        shell.awaitState { shell.controllers.deckVersions!!.state.value.selected != null }
        rule.onNodeWithTag(DeckVersionsTags.PREVIEW).performScrollTo().performClick()
        shell.awaitState { shell.controllers.deckVersions!!.state.value.preview != null }
    }
    @Test fun rootVersionsSubrouteRetainsRecoveryDraftAcrossNavbarResizeAndConfirmedBack() {
        openPreview()
        rule.onNodeWithTag(DeckVersionsTags.RECOVERY_LABEL).performScrollTo().performTextReplacement("Keep this recovery name")
        rule.onNodeWithTag("shell-nav-settings").performClick()
        rule.onNodeWithTag("shell-nav-decks").performClick()
        shell.resize(360.dp, 800.dp, 2f)
        assertEquals("Keep this recovery name", shell.controllers.deckVersions!!.state.value.recoveryLabel)
        rule.onNodeWithTag(DeckVersionsTags.RECOVERY_LABEL).performScrollTo().assertTextContains("Keep this recovery name")
        shell.pressAppBackAfterKeyboardDismissal()
        rule.onNodeWithText(shell.string(R.string.deck_versions_keep)).performClick()
        assertEquals("Keep this recovery name", shell.controllers.deckVersions!!.state.value.recoveryLabel)
        shell.pressAppBackAfterKeyboardDismissal()
        rule.onNodeWithText(shell.string(R.string.deck_versions_discard)).performClick()
        rule.onNodeWithTag("deck-open-versions").performScrollTo().assertIsDisplayed()
        assertEquals(0, fixture.writes.get())
    }
    @Test fun unresolvedRestoreBlocksLibraryNavigationUntilIdenticalRetryResolves() {
        openPreview()
        rule.onNodeWithTag(DeckVersionsTags.RECOVERY_LABEL).performScrollTo().performTextReplacement("Before uncertain restore")
        fixture.loseRestoreAcknowledgement = true
        rule.onNodeWithTag(DeckVersionsTags.RESTORE).performScrollTo().performClick()
        shell.awaitState { shell.controllers.deckVersions!!.state.value.let { it.pending && !it.mutationBusy } }
        rule.onNodeWithText(shell.string(R.string.deck_versions_close)).performClick()
        rule.onNodeWithTag("deck-open-versions").assertDoesNotExist()
        shell.pressAppBackAfterKeyboardDismissal()
        rule.onNodeWithTag("deck-open-versions").assertDoesNotExist()
        rule.onNodeWithTag("shell-nav-settings").performClick()
        rule.onNodeWithTag("shell-nav-decks").performClick()
        rule.onNodeWithTag(DeckVersionsTags.RETRY).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("deck-open-versions").assertDoesNotExist()
        assertEquals(1, fixture.writes.get())
        assertEquals(1, fixture.restoreAttempts.get())
        fixture.loseRestoreAcknowledgement = false
        rule.onNodeWithTag(DeckVersionsTags.RETRY).performClick()
        shell.awaitState { !shell.controllers.deckVersions!!.state.value.pending && !shell.controllers.deckVersions!!.state.value.mutationBusy }
        assertEquals(1, fixture.writes.get())
        assertEquals(2, fixture.restoreAttempts.get())
        assertEquals(Revision(1), fixture.head.revision)
        rule.onNodeWithText(shell.string(R.string.deck_versions_close)).performClick()
        rule.onNodeWithTag("deck-open-versions").performScrollTo().assertIsDisplayed()
    }
    @Test fun knownRestoreAcknowledgementInvalidatesSelectedLibraryEvenWhenReloadFails() {
        openPreview()
        rule.onNodeWithTag(DeckVersionsTags.RECOVERY_LABEL).performScrollTo().performTextReplacement("Before restore")
        fixture.failReads = true
        rule.onNodeWithTag(DeckVersionsTags.RESTORE).performScrollTo().performClick()
        shell.awaitState { fixture.writes.get() == 1 && !shell.controllers.deckVersions!!.state.value.mutationBusy }
        shell.awaitState { shell.controllers.deckLibrary!!.state.value.detailProblem == Problem.TRANSPORT }
        assertNull(shell.controllers.deckLibrary!!.state.value.selected)
        assertFalse(shell.controllers.deckVersions!!.state.value.pending)
        assertEquals(Revision(1), fixture.head.revision)
        assertEquals(fixture.initial, fixture.recovery!!.manifest)
    }
}
