package com.architectpsg.commandlibrary

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.data.storage.StorageDatabase
import com.architectpsg.commandlibrary.data.storage.decks.codec.DeckPayloadCodec
import com.architectpsg.commandlibrary.di.AppGraph
import com.architectpsg.commandlibrary.di.RandomUuidIdSource
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalDeckLibrary
import com.architectpsg.commandlibrary.feature.decks.persistence.SqlLocalPersonalTags
import com.architectpsg.commandlibrary.feature.shell.ShellPorts
import com.architectpsg.commandlibrary.feature.shell.R as ShellR
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.annotation.Config

/** Real MainActivity/AppGraph/pool/planner/deck edits/Room. Card identities are synthetic fixtures. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], shadows = [RealHostDirectoryLinux::class, RealHostBlockGuardOs::class])
class ManualTagsAppIntegrationTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    private lateinit var app: CommandLibraryApplication
    private lateinit var ports: ShellPorts
    private lateinit var first: DeckSnapshot
    private lateinit var second: DeckSnapshot
    private val names = listOf("Removal", "ArtifactSynergy", "BetterWithCommander")
    private fun <T> ok(value: Outcome<T>): T {
        assertTrue("Expected success: $value", value is Outcome.Success)
        return (value as Outcome.Success).value
    }
    private val seed = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            runBlocking {
                app = ApplicationProvider.getApplicationContext()
                ports = ok(app.graph.ports())
                val time = checkNotNull(ports.utcClock).utcNow()
                val source = PlainDeckExportBoundaryFixture.source(DeckId(LocalDeckLibraryLimits.SOURCE, "tags-first"), "Manual tags first", time)
                val a = source.entries.first()
                val bId = EntityId("tags-side-entry")
                val b = a.copy(id = bId, quantity = 4, board = BoardRole.SIDEBOARD, notes = "Distinct private sideboard note Ω",
                    roleOverrides = SnapshotList.copyOf(a.roleOverrides.map { it.copy(entryId = bId) }))
                first = source.copy(entries = SnapshotList.of(a, b))
                val cId = EntityId("tags-second-entry")
                val c = a.copy(id = cId, quantity = 3, labels = SnapshotList.empty(),
                    roleOverrides = SnapshotList.copyOf(a.roleOverrides.map { it.copy(entryId = cId) }))
                second = source.copy(id = source.id.copy(raw = "tags-second"), name = "Manual tags second",
                    entries = SnapshotList.of(c), categoryOrder = SnapshotList.empty())
                val decks = checkNotNull(ports.localDecks)
                first = ok(decks.create(first, EntityId("tags-first-seed"), token))
                second = ok(decks.create(second, EntityId("tags-second-seed"), token))
                assertEquals(first.entries[0].identity, first.entries[1].identity)
                assertEquals(PersonalTagPool(Revision(0), SnapshotList.empty(), null), ok(checkNotNull(ports.personalTags).load(token)))
                checkNotNull(ports.manualTagPlanning) // Actual AppGraph planner binding, never injected.
            }
            base.evaluate()
        }
    } }
    @get:Rule val chain: RuleChain = RuleChain.outerRule(seed).around(compose)

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    private fun await(check: () -> Boolean) = compose.waitUntil(20_000) { compose.waitForIdle(); check() }
    private fun pool(): PersonalTagPool = runBlocking { ok(checkNotNull(ports.personalTags).load(token)) }
    private fun deck(id: DeckId): DeckSnapshot = runBlocking { ok(checkNotNull(ports.localDecks).load(id, token)) }
    private fun dialogClick(tag: String) {
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
    }
    private fun edit(tag: String, value: String) {
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }
    private fun textClick(resource: Int, vararg arguments: Any) {
        val label = compose.activity.getString(resource, *arguments)
        compose.onNodeWithText(label).performScrollTo().performClick()
    }
    private fun overviewClick(tag: String) {
        await { exists("deck-library-overview") }
        compose.onNodeWithTag("deck-library-overview").performScrollToNode(hasTestTag(tag))
        await { compose.onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performClick()
    }
    private fun selectDeck(id: DeckId) {
        await { exists("shell-nav-decks") || exists("shell-nav-more") }
        if (!exists("shell-nav-decks")) compose.onNodeWithTag("shell-nav-more").performClick()
        compose.onNodeWithTag("shell-nav-decks").performClick()
        await { exists("deck-library-list") || exists("deck-library-overview") }
        if (!exists("deck-library-list")) {
            val back = compose.activity.getString(ShellR.string.decks_back)
            compose.onNodeWithTag("deck-library-overview").performScrollToNode(hasText(back))
            compose.onNodeWithText(back).performClick()
        }
        await { exists("deck-library-list") }
        val row = "deck-open-${id.raw}"
        compose.onNodeWithTag("deck-library-list").performScrollToNode(hasTestTag(row))
        compose.onNodeWithTag(row).performClick()
        await { exists("deck-library-overview") }
    }
    private fun openTags() {
        overviewClick("deck-manual-tags")
        await { exists("manual-tags-name") }
    }
    private fun closeTags() {
        textClick(ShellR.string.manual_tags_close)
        await { !exists("manual-tags-name") }
    }
    private fun savePool(revision: Long): PersonalTagPool {
        dialogClick("manual-tags-save")
        await { pool().revision == Revision(revision) }
        await { compose.onAllNodes(hasTestTag("manual-tags-name") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        await { compose.onAllNodes(hasTestTag("manual-tags-save") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty() }
        return pool()
    }
    private fun assign(base: DeckSnapshot, tags: List<PersonalTag>, entryIds: List<EntityId>): DeckSnapshot {
        for (tag in tags) dialogClick("manual-tag-${tag.id.value}")
        for (id in entryIds) dialogClick("manual-tag-entry-${id.value}")
        assertEquals(base, deck(base.id)) // Selection itself never mutates the deck.
        dialogClick("manual-tags-assign")
        await { !exists("manual-tags-name") && deck(base.id).revision.value == base.revision.value + 1 }
        return deck(base.id)
    }
    private fun expected(base: DeckSnapshot, saved: DeckSnapshot, labels: List<EntryLabel>, entries: Set<EntityId>): DeckSnapshot =
        base.copy(revision = saved.revision, modifiedAt = saved.modifiedAt,
            categoryOrder = SnapshotList.copyOf(checkNotNull(base.categoryOrder) + labels),
            entries = SnapshotList.copyOf(base.entries.map { if (it.id in entries) it.copy(labels = SnapshotList.copyOf(it.labels + labels)) else it }))
    private fun independent(expectedPool: PersonalTagPool, vararg expectedDecks: DeckSnapshot) = runBlocking {
        ok(StorageDatabase.open(app, AppGraph.DATABASE_FILE)).use { db ->
            val clock = checkNotNull(ports.utcClock)
            assertEquals(expectedPool, ok(SqlLocalPersonalTags(db, clock).load(token)))
            val reader = SqlLocalDeckLibrary(db, DeckPayloadCodec(), clock, RandomUuidIdSource)
            for (saved in expectedDecks) assertEquals(saved, ok(reader.load(saved.id, token)))
        }
    }

    @Test fun actualReusablePoolBulkAssignmentUndoRenameRetirementAndSecondDeckReusePersistWithoutQuantityChanges() {
        selectDeck(first.id)
        openTags()
        for (name in names) {
            edit("manual-tags-name", name)
            dialogClick("manual-tags-put")
        }
        assertTrue(pool().tags.isEmpty()) // Pool edits remain a draft until explicit Save.
        val created = savePool(1)
        assertEquals(names, created.tags.map { it.name })
        assertEquals(3, created.tags.map { it.id }.distinct().size)
        assertTrue(created.tags.none { it.retired })
        val labels = created.tags.map { EntryLabel(it.id, it.name) }
        val selected = first.entries.map { it.id }
        val assigned = assign(first, created.tags, selected)
        assertEquals(expected(first, assigned, labels, selected.toSet()), assigned)
        assertEquals(6, assigned.entries.sumOf { it.quantity })
        independent(created, assigned, second)
        overviewClick("deck-undo")
        await { deck(first.id).revision.value == assigned.revision.value + 1 }
        val undone = deck(first.id)
        assertEquals(first.copy(revision = undone.revision, modifiedAt = undone.modifiedAt), undone)
        assertEquals(created, pool()) // One deck undo does not undo separately saved vocabulary.
        independent(created, undone, second)
        openTags()
        val reassigned = assign(undone, created.tags, selected)
        assertEquals(expected(undone, reassigned, labels, selected.toSet()), reassigned)

        openTags()
        edit("manual-tags-search", "ArtifactSynergy")
        textClick(ShellR.string.manual_tags_edit) // Search isolates one row; no assumed generated UUID.
        edit("manual-tags-name", "ArtifactEngine")
        dialogClick("manual-tags-put")
        val renamed = savePool(2)
        assertEquals(created.tags.map { if (it.name == "ArtifactSynergy") it.copy(name = "ArtifactEngine") else it }, renamed.tags.toList())
        edit("manual-tags-search", "Removal")
        textClick(ShellR.string.manual_tags_retire)
        val retired = savePool(3)
        val removalId = created.tags.single { it.name == "Removal" }.id
        assertEquals(renamed.tags.map { if (it.id == removalId) it.copy(retired = true) else it }, retired.tags.toList())
        assertEquals(reassigned, deck(first.id))
        independent(retired, reassigned, second)
        textClick(ShellR.string.manual_tags_restore, "Removal")
        val restored = savePool(4)
        assertEquals(renamed.tags, restored.tags)
        assertEquals(created.tags.map { it.id }, restored.tags.map { it.id })
        edit("manual-tags-search", "")
        dialogClick("manual-tags-reload")
        await { compose.onAllNodes(hasTestTag("manual-tags-name") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(restored, pool())
        independent(restored, reassigned, second)
        compose.activityRule.scenario.recreate()
        await { exists("manual-tags-name") }
        for (tag in restored.tags) compose.onNodeWithTag("manual-tag-${tag.id.value}").assertExists()
        independent(restored, reassigned, second) // Retained clean dialog/recreation performs no writes.
        closeTags()

        selectDeck(second.id)
        openTags()
        val engine = restored.tags.single { it.name == "ArtifactEngine" }
        val secondSaved = assign(second, listOf(engine), second.entries.map { it.id })
        assertEquals(expected(second, secondSaved, listOf(EntryLabel(engine.id, "ArtifactEngine")), second.entries.map { it.id }.toSet()), secondSaved)
        assertEquals(reassigned, deck(first.id))
        assertEquals("ArtifactSynergy", reassigned.categoryOrder!!.single { it.id == engine.id }.name)
        assertEquals("ArtifactEngine", secondSaved.entries.single().labels.single().name)
        assertEquals(restored, pool())
        independent(restored, reassigned, secondSaved)
    }
}
