package com.architectpsg.commandlibrary.feature.validation

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalBoardConstraintChecksTest {
    // Minimal synthetic snapshots, following DeckSnapshotValidatorTest's unresolved-card fixture.
    // These are supplied counts, not invented official format rules or source-registry fixtures.
    private fun entry(id: String, quantity: Int, board: BoardRole = BoardRole.MAIN) = DeckEntry(
        EntityId(id), CardIdentity(Knowledge.Unknown("synthetic unresolved oracle", SnapshotList.empty()),
            Knowledge.Absent("synthetic absent printing"), null,
            Knowledge.Unknown("synthetic unresolved name", SnapshotList.empty()), LanguageTag("ja")),
        quantity, board, Knowledge.Unknown("synthetic finish", SnapshotList.empty()), "",
        SnapshotList.empty(), SnapshotList.empty())

    private fun deck(vararg entries: DeckEntry) = DeckSnapshot(
        DeckId(ProviderId("foreign/source"), " untouched/deck/界 "), "Synthetic deck",
        Knowledge.Absent("no format supplied"), "", Knowledge.Absent("no folder"), SnapshotList.empty(),
        UtcTimestamp(1), UtcTimestamp(2), Revision(7), SnapshotList.of(*entries),
        Knowledge.Absent("no budget"), null, SnapshotList.empty(), SnapshotList.empty())

    private fun constraint(board: BoardRole = BoardRole.MAIN, minimum: Int = 0, maximum: Int? = null,
        countsTowardDeckSize: Boolean = false) = BoardConstraint(board, minimum, maximum, countsTowardDeckSize)

    private class Token(private val stop: Int = Int.MAX_VALUE) : CancelToken {
        var polls = 0
        val exception = CancellationException("synthetic cancellation")
        override val isCancelled: Boolean get() = polls >= stop
        override fun throwIfCancelled() { if (++polls >= stop) throw exception }
    }

    private fun assessed(value: DeckSnapshot, vararg constraints: BoardConstraint): BoardConstraintAssessment {
        val result = LocalBoardConstraintChecks.assess(value, SnapshotList.of(*constraints), Token())
        assertTrue(result.toString(), result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private fun invalid(value: DeckSnapshot, vararg constraints: BoardConstraint) {
        val result = LocalBoardConstraintChecks.assess(value, SnapshotList.of(*constraints), Token())
        assertTrue(result.toString(), result is Outcome.Failure)
        assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
        assertTrue(result.error.evidence.isEmpty())
    }

    @Test fun allSevenRolesHaveIndependentLiteralCountsAndEnumOrder() {
        val original = deck(entry("main-a", 2), entry("main-b", 3),
            entry("side", 7, BoardRole.SIDEBOARD), entry("maybe", 11, BoardRole.MAYBEBOARD),
            entry("commander", 13, BoardRole.COMMANDER), entry("partner", 17, BoardRole.PARTNER),
            entry("background", 19, BoardRole.BACKGROUND), entry("companion", 23, BoardRole.COMPANION))
        val supplied = arrayOf(constraint(BoardRole.COMPANION, 23, 23), constraint(BoardRole.BACKGROUND, 19, 19),
            constraint(BoardRole.PARTNER, 17, 17), constraint(BoardRole.COMMANDER, 13, 13),
            constraint(BoardRole.MAYBEBOARD, 11, 11), constraint(BoardRole.SIDEBOARD, 7, 7), constraint(BoardRole.MAIN, 5, 5))
        val result = assessed(original, *supplied)
        assertSame(original.id, result.deckId)
        assertSame(original.revision, result.deckRevision)
        assertEquals(listOf(BoardRole.MAIN, BoardRole.SIDEBOARD, BoardRole.MAYBEBOARD, BoardRole.COMMANDER,
            BoardRole.PARTNER, BoardRole.BACKGROUND, BoardRole.COMPANION), result.findings.map { it.constraint.board })
        assertEquals(listOf(5L, 7L, 11L, 13L, 17L, 19L, 23L), result.findings.map { it.quantity })
        assertTrue(result.findings.all { it.satisfied })
        assertEquals(result, assessed(original, *supplied.reversedArray()))
        for (finding in result.findings) assertSame(supplied.single { it.board == finding.constraint.board }, finding.constraint)
    }

    @Test fun inclusiveSuppliedBoundsAndEmptyBoardsHaveNoHiddenDefaults() {
        val original = deck(entry("main", 5), entry("ignored", 100, BoardRole.COMMANDER))
        for (bound in listOf(constraint(minimum = 5), constraint(maximum = 5), constraint(minimum = 5, maximum = 5))) {
            assertEquals(BoardConstraintFinding(bound, 5L, true), assessed(original, bound).findings.single())
        }
        assertFalse(assessed(original, constraint(minimum = 6)).findings.single().satisfied)
        assertFalse(assessed(original, constraint(maximum = 4)).findings.single().satisfied)
        assertEquals(listOf(0L), assessed(original, constraint(BoardRole.SIDEBOARD, 0, 0)).findings.map { it.quantity })
        assertTrue(assessed(deck(), constraint(maximum = 0)).findings.single().satisfied)
        assertFalse(assessed(deck(), constraint(minimum = 1)).findings.single().satisfied)
    }

    @Test fun labelsPrintingVariantsOverridesAndPackageContributionsDoNotDuplicateCopies() {
        val labels = SnapshotList.of(EntryLabel(EntityId("one"), "First"), EntryLabel(EntityId("two"), "Second"))
        val first = entry("a", 2).copy(labels = labels, roleOverrides = SnapshotList.of(
            RoleOverride(null, Knowledge.Absent("no oracle"), "commander", true, UtcTimestamp(1))))
        val second = entry("b", 3).copy(identity = first.identity.copy(
            printingId = Knowledge.Known(CardId(ProviderId("foreign"), "different printing"))), labels = labels)
        val packages = SnapshotList.of(
            PackageInstance(EntityId("linked"), EntityId("p"), EntityId("v"), PackageLinkPolicy.LINKED,
                SnapshotList.of(PackageContribution(EntityId("pa"), first.id, 2))),
            PackageInstance(EntityId("copy"), EntityId("p"), EntityId("v"), PackageLinkPolicy.COPY,
                SnapshotList.of(PackageContribution(EntityId("pb"), second.id, 3))))
        val original = deck(first, second, entry("side", 50, BoardRole.SIDEBOARD)).copy(packageInstances = packages)
        val before = original.copy()
        for (aggregateFlag in listOf(false, true)) {
            val finding = assessed(original, constraint(minimum = 5, maximum = 5,
                countsTowardDeckSize = aggregateFlag)).findings.single()
            assertEquals(5L, finding.quantity)
            assertTrue(finding.satisfied)
        }
        assertEquals(before, original)
        assertSame(first, original.entries.first())
        assertSame(packages, original.packageInstances)
        assertSame(labels, original.entries.first().labels)
    }

    @Test fun maximumEntryCountAndLargeQuantitiesRemainLong() {
        val entries = List(DeckPayloadLimits.MAX_ENTRIES) { entry("e$it", Int.MAX_VALUE) }
        val original = deck().copy(entries = SnapshotList.copyOf(entries), revision = Revision(Long.MAX_VALUE))
        val result = assessed(original, constraint())
        assertEquals(21_474_836_470_000L, result.findings.single().quantity)
        assertTrue(result.findings.single().satisfied)
        assertEquals(Revision(Long.MAX_VALUE), result.deckRevision)
        assertFalse(assessed(original, constraint(maximum = Int.MAX_VALUE)).findings.single().satisfied)
        invalid(original.copy(entries = SnapshotList.copyOf(entries + entry("extra", 1))), constraint())
    }

    @Test fun emptyDuplicateAndOversizedConstraintListsAreInvalid() {
        invalid(deck())
        invalid(deck(), constraint(), constraint(minimum = 1))
        invalid(deck(), *Array(8) { constraint() })
    }

    @Test fun duplicateIdsAreRejectedEvenAcrossUnconstrainedRoles() {
        invalid(deck(entry("same", 1), entry("same", 2, BoardRole.COMPANION)), constraint())
        invalid(deck(entry("same", 1, BoardRole.SIDEBOARD), entry("same", 2, BoardRole.COMPANION)), constraint())
    }

    @Test fun everyRelevantIdRejectsMalformedUnicodeAndByteOrUtf16Overflow() {
        for (id in listOf("\uD800", "\uDC00", "x\uD800y", "x".repeat(1_025), "é".repeat(513), "😀".repeat(257))) {
            val original = deck(entry("valid", 1))
            invalid(original.copy(id = DeckId(ProviderId(id), "deck")), constraint())
            invalid(original.copy(id = DeckId(ProviderId("source"), id)), constraint())
            invalid(deck(entry(id, 1, BoardRole.COMPANION)), constraint())
        }
    }

    @Test fun exactUtf8BoundariesAndUnnormalizedForeignIdsArePreserved() {
        for (id in listOf("x".repeat(1_024), "é".repeat(512), "😀".repeat(256))) {
            val original = deck(entry(id, 1)).copy(id = DeckId(ProviderId(id), id))
            assertSame(original.id, assessed(original, constraint()).deckId)
        }
        // Canonically equivalent strings are still distinct entry IDs; this helper never normalizes.
        assertEquals(3L, assessed(deck(entry("é", 1), entry("e\u0301", 2)), constraint()).findings.single().quantity)
    }

    @Test fun constructorBypassedNonpositiveQuantitiesAndBlankIdsStillRejectDirectCalls() {
        // Reflection only constructs corrupt JVM inputs that ordinary Kotlin constructors prohibit.
        for (quantity in listOf(0, -1)) {
            val broken = entry("broken", 1, BoardRole.COMPANION)
            DeckEntry::class.java.getDeclaredField("quantity").apply { isAccessible = true }.setInt(broken, quantity)
            invalid(deck(broken), constraint())
        }
        val brokenId = entry("broken", 1, BoardRole.COMPANION)
        DeckEntry::class.java.getDeclaredField("id").apply { isAccessible = true }.set(brokenId, " \t")
        invalid(deck(brokenId), constraint())
        val brokenDeck = deck()
        DeckId::class.java.getDeclaredField("raw").apply { isAccessible = true }.set(brokenDeck.id, " \n")
        invalid(brokenDeck, constraint())
    }

    @Test fun cancellationBeforeDuringAndImmediatelyBeforeResultPropagatesOriginalException() {
        val original = deck().copy(entries = SnapshotList.copyOf(List(100) { entry("e$it", 1) }))
        val constraints = SnapshotList.of(constraint())
        val complete = Token()
        assertTrue(LocalBoardConstraintChecks.assess(original, constraints, complete) is Outcome.Success)
        for (stop in listOf(1, complete.polls / 2, complete.polls)) {
            val token = Token(stop)
            try {
                LocalBoardConstraintChecks.assess(original, constraints, token)
                fail("Expected cancellation at poll $stop")
            } catch (actual: CancellationException) {
                assertSame(token.exception, actual)
                assertEquals(stop, token.polls)
            }
        }
        assertEquals(100, original.entries.size)
    }
}
