package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.*
import org.junit.Test

/** Profile/history/exchange input invariants, not repository query or persisted-service evidence. */
class LifePersistenceContractsTest {
    private fun definition(id: String = "life", role: CounterRole = CounterRole.LIFE, initial: Int = 20) =
        CounterDefinition(EntityId(id), "Synthetic counter", null, null, initial, CounterLossPolicy.None, role == CounterRole.OTHER, role)
    private fun commander(id: String) = CommanderSlot(EntityId(id), CardIdentity(
        Knowledge.Unknown("No oracle", SnapshotList.empty()), Knowledge.Unknown("No printing", SnapshotList.empty()),
        ProviderIdentity(ProviderId("synthetic"), "card", "raw-$id"), Knowledge.Known("Synthetic commander"), LanguageTag.ENGLISH), "Synthetic")
    private fun profile() = PlayerProfileDraft("  Synthetic player  ", 20, SnapshotList.of(definition()),
        SeatAppearance(null, null), SnapshotList.empty())
    private val art = LocalArtReference(EntityId("synthetic-art"), "private/synthetic.png", ContentHash("SHA-256", "0123456789abcdef"))
    private val preferences = RestorableSettings(LanguageTag.ENGLISH, ThemePreference.DARK, StorageMode.LEAN,
        CachePolicy(1024, true, true), Knowledge.Absent("No budget"), SnapshotList.empty())
    private fun exchange() = ProfileExchange(InterchangeVersion(1, "synthetic"), EntityId("synthetic-profile"),
        profile().name, 20, profile().counterDefinitions, SnapshotList.of(art), preferences)
    private fun entry(id: String) = GameHistoryEntry(GameId(ProviderId("synthetic"), id), Revision(0), UtcTimestamp(1000),
        Knowledge.Absent("Not finished"), SnapshotList.empty(), Knowledge.Absent("No result"))
    private fun page(count: Int) = Page(SnapshotList.copyOf((0 until count).map { entry("game-$it") }), null,
        SnapshotReference(ProviderId("synthetic"), null, Revision(0), null))

    @Test fun validProfileRetainsNegativeCustomLifeCommanderSlotsAndAppearancePreferences() {
        val definitions = SnapshotList.of(definition(initial = -5), definition("damage", CounterRole.COMMANDER_DAMAGE, 0),
            definition("tax", CounterRole.COMMANDER_TAX, 0), definition("custom-a", CounterRole.OTHER, -2), definition("custom-b", CounterRole.OTHER, 3))
        val commanders = SnapshotList.of(commander("slot-a"), commander("slot-b"), commander("slot-c"))
        val appearance = SeatAppearance(DisplayColor(0xff123456), GameArtReference.CachedCard(commanders.first().identity, EntityId("unavailable-art")),
            ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.8, 0.9), SeatOrientation.INVERTED)
        val input = PlayerProfileDraft("  Synthetic player  ", -5, definitions, appearance, commanders, 5)
        assertEquals(-5, input.preferredLife); assertEquals(definitions, input.counterDefinitions)
        assertEquals(commanders, input.commanders); assertEquals(appearance, input.appearance)
        assertEquals("  Synthetic player  ", input.name); assertEquals(5, input.adjustmentStep)
    }
    @Test fun profilesRejectNonpositiveAdjustmentStepsAndDuplicateCommanderIdentity() {
        val profile = profile(); assertEquals(1, profile.adjustmentStep)
        for (step in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { profile.copy(adjustmentStep = step) }
        }
        val commander = commander("repeated")
        assertThrows(IllegalArgumentException::class.java) { profile.copy(commanders = SnapshotList.of(commander, commander.copy(label = "Different label"))) }
    }
    @Test fun profilesRequireUniqueCounterIdentityAndExplicitLifeMatchingPreferredLife() {
        val profile = profile(); val life = definition()
        assertThrows(IllegalArgumentException::class.java) { profile.copy(counterDefinitions = SnapshotList.empty()) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(counterDefinitions = SnapshotList.of(life.copy(role = CounterRole.OTHER, label = "Life"))) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(counterDefinitions = SnapshotList.of(life, life.copy(role = CounterRole.OTHER))) }
        assertThrows(IllegalArgumentException::class.java) { profile.copy(preferredLife = 21) }
        for (role in listOf(CounterRole.LIFE, CounterRole.COMMANDER_DAMAGE, CounterRole.COMMANDER_TAX)) {
            val repeated = listOf(definition("first", role), definition("second", role))
            val definitions = SnapshotList.copyOf(if (role == CounterRole.LIFE) repeated else listOf(life) + repeated)
            assertThrows(IllegalArgumentException::class.java) { profile.copy(counterDefinitions = definitions) }
        }
    }
    @Test fun historyFilterPreservesNamesAndAllowsOpenOrEqualBounds() {
        val defaults = GameHistoryFilter()
        assertNull(defaults.playerName); assertNull(defaults.deckId); assertNull(defaults.deckName)
        assertNull(defaults.fromInclusive); assertNull(defaults.untilExclusive)
        val bound = UtcTimestamp(1000, 123)
        val deck = DeckId(ProviderId("synthetic"), "deleted-deck")
        val filter = GameHistoryFilter("  Player  ", deck, "  Stored deck name  ", bound, bound)
        assertEquals("  Player  ", filter.playerName); assertEquals("  Stored deck name  ", filter.deckName)
        assertEquals(deck, filter.deckId); assertEquals(bound, filter.fromInclusive); assertEquals(bound, filter.untilExclusive)
        assertEquals(bound, GameHistoryFilter(fromInclusive = bound).fromInclusive)
        assertEquals(bound, GameHistoryFilter(untilExclusive = bound).untilExclusive)
    }
    @Test fun historyBoundsCompareNanosecondsWithinAndAcrossSeconds() {
        val from = UtcTimestamp(1000, 999_999_998); val until = UtcTimestamp(1000, 999_999_999)
        assertEquals(until, GameHistoryFilter(fromInclusive = from, untilExclusive = until).untilExclusive)
        assertThrows(IllegalArgumentException::class.java) { GameHistoryFilter(fromInclusive = until, untilExclusive = from) }
        val nextSecond = UtcTimestamp(1001, 0)
        assertEquals(nextSecond, GameHistoryFilter(fromInclusive = until, untilExclusive = nextSecond).untilExclusive)
        assertThrows(IllegalArgumentException::class.java) { GameHistoryFilter(fromInclusive = nextSecond, untilExclusive = until) }
    }
    @Test fun historyBoundsCompareExtremeSecondsWithoutOverflowSubtraction() {
        val earliest = UtcTimestamp(Long.MIN_VALUE, 999_999_999); val latest = UtcTimestamp(Long.MAX_VALUE, 0)
        assertEquals(earliest, GameHistoryFilter(fromInclusive = earliest, untilExclusive = latest).fromInclusive)
        assertThrows(IllegalArgumentException::class.java) { GameHistoryFilter(fromInclusive = latest, untilExclusive = earliest) }
        assertEquals(latest, GameHistoryFilter(fromInclusive = latest, untilExclusive = latest).untilExclusive)
    }
    @Test fun historyPageCountsAllowEmptyAndLaterPagesButRejectImpossibleTotals() {
        assertEquals(0L, GameHistoryPage(page(0), 0).totalMatches)
        val secondPage = page(2).copy(nextCursor = "synthetic-next")
        assertEquals(secondPage, GameHistoryPage(secondPage, 100).games)
        assertEquals(2L, GameHistoryPage(page(2), 2).totalMatches)
        assertThrows(IllegalArgumentException::class.java) { GameHistoryPage(page(0), -1) }
        assertThrows(IllegalArgumentException::class.java) { GameHistoryPage(page(2), 1) }
        assertEquals(Long.MAX_VALUE, GameHistoryPage(page(2), Long.MAX_VALUE).totalMatches)
    }
    @Test fun legacyExchangeHasNoLifeSetupAndPreservesArtAndAppPreferences() {
        val exchange = exchange()
        assertNull(exchange.lifeSetup); assertEquals(SnapshotList.of(art), exchange.art); assertEquals(preferences, exchange.preferences)
        // Old exchange without an explicit LIFE role remains a legacy DTO, not a certified profile.
        val legacy = exchange.copy(counterDefinitions = SnapshotList.of(definition(role = CounterRole.OTHER)), preferredLife = -5)
        assertNull(legacy.lifeSetup); assertEquals(-5, legacy.preferredLife)
    }
    @Test fun matchingLifeSetupUsesStructuralEqualityAndRetainsLegacyExchangeFields() {
        val setup = profile().copy(counterDefinitions = SnapshotList.copyOf(profile().counterDefinitions.map { it.copy() }))
        val exchange = exchange().copy(lifeSetup = setup)
        assertEquals(setup, exchange.lifeSetup); assertEquals(exchange.name, setup.name)
        assertEquals(exchange.counterDefinitions, setup.counterDefinitions)
        assertEquals(SnapshotList.of(art), exchange.art); assertEquals(preferences, exchange.preferences)
    }
    @Test fun exchangeRejectsEachInconsistentDuplicatedSetupField() {
        val base = exchange().copy(lifeSetup = profile())
        assertThrows(IllegalArgumentException::class.java) { base.copy(name = "Different player") }
        assertThrows(IllegalArgumentException::class.java) { base.copy(preferredLife = 21) }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(counterDefinitions = SnapshotList.of(base.counterDefinitions.single().copy(label = "Different label")))
        }
        assertThrows(IllegalArgumentException::class.java) { base.copy(counterDefinitions = SnapshotList.empty()) }
    }
}
