package com.architectpsg.commandlibrary

import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.architectpsg.commandlibrary.core.model.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual UI/activity/application/Room composition, on host SDK34 or packaged device runner. */
@RunWith(AndroidJUnit4::class)
class ProfileCounterUiIntegrationTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Before fun hostBudget() = idleBudget(300)
    @After fun restoreBudget() = idleBudget(60)
    private fun idleBudget(seconds: Long) {
        if (Build.FINGERPRINT.contains("robolectric")) Class.forName("androidx.test.espresso.IdlingPolicies")
            .getMethod("setMasterPolicyTimeout", Long::class.javaPrimitiveType, TimeUnit::class.java)
            .invoke(null, seconds, TimeUnit.SECONDS)
    }
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun <T> ok(result: Outcome<T>): T {
        assertTrue("Expected success: $result", result is Outcome.Success)
        return (result as Outcome.Success).value
    }
    private fun click(tag: String) { rule.onNodeWithTag(tag).performScrollTo().performClick() }
    private fun text(tag: String, value: String) { rule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value) }
    private fun managerClick(tag: String) {
        rule.onNodeWithTag("profile-counters-list").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).performClick()
    }
    private fun waitSaved() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("profile-name").fetchSemanticsNodes().isEmpty() &&
                rule.onAllNodesWithTag("profiles-refresh").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
        }
    }
    private fun revealProfileAction(tag: String) {
        repeat(10_000) {
            try {
                rule.onNodeWithTag("profiles-list").performScrollToNode(hasTestTag(tag))
                return
            } catch (notInLoadedPage: AssertionError) {
                if (rule.onAllNodesWithTag("profiles-more").fetchSemanticsNodes().isEmpty()) throw notInLoadedPage
                rule.onNodeWithTag("profiles-more").assertIsEnabled().performClick()
                rule.waitUntil(20_000) {
                    rule.onAllNodesWithTag("profiles-more").fetchSemanticsNodes().none { it.config.contains(SemanticsProperties.Disabled) }
                }
            }
        }
        fail("Profile was not found within the integration probe paging budget")
    }
    private suspend fun ownedRows(repo: PlayerProfileRepository, name: String): List<PlayerProfileSnapshot> {
        var cursor: String? = null
        val cursors = mutableSetOf<String?>()
        val result = mutableListOf<PlayerProfileSnapshot>()
        do {
            assertTrue("Real cursor must advance", cursors.add(cursor)); assertTrue(cursors.size <= 10_000)
            val page = ok(repo.list(PageRequest(100, cursor), token))
            result += page.items.filter { it.value.name == name }
            cursor = page.nextCursor
        } while (cursor != null)
        return result
    }

    @Test fun commanderRenameAndConfirmedRemovalPreserveRestOfActualRoomProfile() {
        val name = "Commander probe ${UUID.randomUUID()}"
        val app = rule.activity.application as CommandLibraryApplication
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("shell-nav-profiles").fetchSemanticsNodes().isNotEmpty() }
        val repo = runBlocking { requireNotNull(ok(app.graph.ports()).playerProfiles) }
        try {
            val card = CardIdentity(Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"),
                ProviderIdentity(ProviderId("synthetic"), "card", "one"), Knowledge.Known("Commander"), LanguageTag.ENGLISH)
            val life = CounterDefinition(EntityId("life"), "Life", null, null, 40, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
            val original = PlayerProfileDraft(name, 40, SnapshotList.of(life), SeatAppearance(DisplayColor(0xffffffff),
                GameArtReference.CachedCard(card, EntityId("cached-art")), ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED),
                SnapshotList.of(CommanderSlot(EntityId("partner"), card, "Partner")), 3)
            val created = runBlocking { ok(repo.create(original, token)) }
            rule.onNodeWithTag("shell-nav-profiles").performClick(); waitSaved(); click("profiles-refresh"); waitSaved()
            revealProfileAction("profile-edit-${created.id.value}"); click("profile-edit-${created.id.value}"); click("profile-commanders")
            rule.onNodeWithTag("profile-commanders-list").performScrollToNode(hasTestTag("profile-commander-edit-partner"))
            click("profile-commander-edit-partner"); text("profile-commander-label", "Primary"); click("profile-commander-apply")
            assertEquals(created, runBlocking { ok(repo.read(created.id, token)) })
            rule.onNodeWithTag("profile-commanders-list").performScrollToNode(hasTestTag("profile-commanders-back"))
            click("profile-commanders-back"); click("profile-save"); waitSaved()
            val renamed = runBlocking { ok(repo.read(created.id, token)) }
            assertEquals(created.revision.next(), renamed.revision)
            assertEquals(original.copy(commanders = SnapshotList.of(original.commanders.single().copy(label = "Primary"))), renamed.value)
            revealProfileAction("profile-edit-${created.id.value}"); click("profile-edit-${created.id.value}"); click("profile-commanders")
            rule.onNodeWithTag("profile-commanders-list").performScrollToNode(hasTestTag("profile-commander-delete-partner"))
            click("profile-commander-delete-partner"); rule.onNodeWithTag("profile-commander-delete-confirm").performClick()
            assertEquals(renamed, runBlocking { ok(repo.read(created.id, token)) })
            rule.onNodeWithTag("profile-commanders-list").performScrollToNode(hasTestTag("profile-commanders-back"))
            click("profile-commanders-back"); click("profile-save"); waitSaved()
            val removed = runBlocking { ok(repo.read(created.id, token)) }
            assertEquals(renamed.revision.next(), removed.revision); assertEquals(original.copy(commanders = SnapshotList.empty()), removed.value)
        } finally {
            runBlocking {
                for (row in ownedRows(repo, name)) {
                    val latest = repo.read(row.id, token)
                    if (latest is Outcome.Success) ok(repo.delete(row.id, latest.value.revision, token))
                }
            }
        }
    }

    @Test fun appearanceEditorChangesOnlyColorAndOrientationThroughRealAppGraphRoom() {
        val name = "Appearance probe ${UUID.randomUUID()}"
        val app = rule.activity.application as CommandLibraryApplication
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("shell-nav-profiles").fetchSemanticsNodes().isNotEmpty() }
        val repo = runBlocking { requireNotNull(ok(app.graph.ports()).playerProfiles) }
        try {
            val card = CardIdentity(Knowledge.Absent("Synthetic"), Knowledge.Absent("Synthetic"),
                ProviderIdentity(ProviderId("synthetic"), "card", "one"), Knowledge.Known("Commander"), LanguageTag.ENGLISH)
            val life = CounterDefinition(EntityId("life"), "Life", null, null, 40, CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE)
            val charge = CounterDefinition(EntityId("charge"), "Charge", -10, 10, 3, CounterLossPolicy.None, true,
                CounterRole.OTHER, DisplayColor(0xff112233), CounterPreset.CHARGE, CounterResetPolicy.ON_TURN_START)
            // Stored metadata exercises preservation; this probe makes no art-loading claim.
            val appearance = SeatAppearance(DisplayColor(0x01234567), GameArtReference.CachedCard(card, EntityId("cached-art")),
                ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED)
            val created = runBlocking { ok(repo.create(PlayerProfileDraft(name, 40, SnapshotList.of(life, charge), appearance,
                SnapshotList.of(CommanderSlot(EntityId("partner"), card, "Partner")), 3), token)) }
            rule.onNodeWithTag("shell-nav-profiles").performClick()
            rule.waitUntil(20_000) {
                rule.onAllNodesWithTag("profiles-refresh").fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) }
            }
            click("profiles-refresh"); waitSaved()
            revealProfileAction("profile-edit-${created.id.value}")
            click("profile-edit-${created.id.value}"); click("profile-appearance")
            click("profile-appearance-use-color"); click("profile-appearance-counterclockwise"); click("profile-appearance-apply")
            assertEquals(created, runBlocking { ok(repo.read(created.id, token)) })
            click("profile-save"); waitSaved()
            val updated = runBlocking { ok(repo.read(created.id, token)) }
            assertEquals(created.revision.next(), updated.revision)
            assertEquals(created.value.copy(appearance = appearance.copy(color = null, orientation = SeatOrientation.COUNTERCLOCKWISE)), updated.value)
        } finally {
            runBlocking {
                for (row in ownedRows(repo, name)) {
                    val latest = repo.read(row.id, token)
                    if (latest is Outcome.Success) ok(repo.delete(row.id, latest.value.revision, token))
                }
            }
        }
    }

    @Test fun actualCounterEditorSavesAndEditsExactCounterThroughRealAppGraphRoom() {
        val name = "Counter probe ${UUID.randomUUID()}"
        val app = rule.activity.application as CommandLibraryApplication
        rule.waitUntil(20_000) { rule.onAllNodesWithTag("shell-nav-profiles").fetchSemanticsNodes().isNotEmpty() }
        val repo = runBlocking { requireNotNull(ok(app.graph.ports()).playerProfiles) }
        try {
            rule.onNodeWithTag("shell-nav-profiles").performClick()
            rule.waitUntil(20_000) { rule.onAllNodesWithTag("profiles-new").fetchSemanticsNodes().isNotEmpty() }
            click("profiles-new"); text("profile-name", name)
            click("profile-counters"); managerClick("profile-counter-add")
            text("profile-counter-label", "Probe charge"); text("profile-counter-initial", "-5")
            text("profile-counter-minimum", "-10"); text("profile-counter-maximum", "10")
            click("profile-counter-loss-above"); text("profile-counter-threshold", "99")
            click("profile-counter-use-color"); text("profile-counter-color", "#8000FF00")
            click("profile-counter-reset-turn"); click("profile-counter-apply")
            assertTrue(runBlocking { ownedRows(repo, name) }.isEmpty())
            managerClick("profile-counters-back"); click("profile-save"); waitSaved()
            val created = runBlocking { ownedRows(repo, name) }.single()
            val counter = created.value.counterDefinitions.single { it.role == CounterRole.OTHER }
            assertEquals("Probe charge", counter.label); assertEquals(-5, counter.initial)
            assertEquals(-10, counter.minimum); assertEquals(10, counter.maximum)
            assertEquals(CounterLossPolicy.AtOrAbove(99), counter.lossPolicy)
            assertEquals(DisplayColor(0x8000ff00), counter.color)
            assertEquals(CounterResetPolicy.ON_TURN_START, counter.resetPolicy)
            assertTrue(counter.custom); assertNull(counter.preset)
            revealProfileAction("profile-edit-${created.id.value}")
            click("profile-edit-${created.id.value}"); click("profile-counters")
            managerClick("profile-counter-edit-${counter.id.value}"); text("profile-counter-initial", "7")
            click("profile-counter-apply"); managerClick("profile-counters-back"); click("profile-save"); waitSaved()
            val updated = runBlocking { ok(repo.read(created.id, token)) }
            assertEquals(created.revision.next(), updated.revision)
            assertEquals(created.value.copy(counterDefinitions = SnapshotList.copyOf(created.value.counterDefinitions.map {
                if (it.id == counter.id) it.copy(initial = 7) else it
            })), updated.value)
        } finally {
            // This UUID name belongs only to this probe, including any duplicate rows a bug creates.
            runBlocking {
                for (row in ownedRows(repo, name)) {
                    val latest = repo.read(row.id, token)
                    if (latest is Outcome.Success) ok(repo.delete(row.id, latest.value.revision, token))
                }
            }
        }
    }
}
