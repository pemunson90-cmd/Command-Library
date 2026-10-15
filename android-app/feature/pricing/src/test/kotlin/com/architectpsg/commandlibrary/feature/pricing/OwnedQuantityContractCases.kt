package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Root-owned executable examples; a concrete worker test must inject the real engine. */
abstract class OwnedQuantityContractCases {
    protected abstract val allocator: OwnedQuantityAllocator
    private val provider = ProviderId("synthetic-contract")
    private val oracle = OracleId(provider, "oracle")
    private val p1 = CardId(provider, "printing-one")
    private val p2 = CardId(provider, "printing-two")
    private val token = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }
    private fun identity(printing: CardId = p1, oracleId: OracleId = oracle, language: LanguageTag = LanguageTag.ENGLISH) =
        CardIdentity(Knowledge.Known(oracleId), Knowledge.Known(printing), null, Knowledge.Absent("synthetic"), language)
    private fun entry(id: String, quantity: Int = 1, finish: Knowledge<Finish> = Knowledge.Known(Finish.NONFOIL)) =
        DeckEntry(EntityId(id), identity(), quantity, BoardRole.MAIN, finish, "", SnapshotList.empty(), SnapshotList.empty())
    private fun lot(id: String, printing: CardId = p1, quantity: Int = 1, finish: Knowledge<Finish> = Knowledge.Known(Finish.NONFOIL), oracleId: OracleId = oracle) =
        OwnedLot(EntityId(id), identity(printing, oracleId), finish, CardCondition.UNKNOWN, "", quantity, "", Revision(0), Knowledge.Absent("synthetic"))
    private fun deck(vararg entries: DeckEntry) = DeckSnapshot(DeckId(ProviderId("local-decks"), "contract"), "Synthetic contract", Knowledge.Absent("none"), "",
        Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(1), Revision(7), SnapshotList.of(*entries), Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
    private fun inventory(lots: List<OwnedLot>, complete: Boolean = true) = InventorySnapshot(Revision(9), UtcTimestamp(1), SnapshotList.copyOf(lots), complete)
    private fun policy(vararg pins: PrintingPin, ownership: OwnershipPolicy = OwnershipPolicy.SAME_ORACLE) =
        OwnedQuantityPolicy(SnapshotList.of(BoardRole.SIDEBOARD, BoardRole.MAIN), SnapshotList.of(*pins), ownership)
    private fun ok(result: Outcome<OwnedQuantityPlan>): OwnedQuantityPlan {
        assertTrue("Expected success, got ${result.javaClass.simpleName}", result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    @Test fun strictAndFlexibleRowsUseAlternativePrintingWithoutDoubleConsumptionOrPermutationDrift() {
        val strict = entry("strict"); val flexible = entry("flexible")
        val pin = PrintingPin(strict.id, p1, Finish.NONFOIL)
        val owned = listOf(lot("only-strict"), lot("alternative", p2))
        val first = ok(allocator.allocate(deck(flexible, strict), inventory(owned), policy(pin), token))
        val reversed = ok(allocator.allocate(deck(strict, flexible), inventory(owned.reversed()),
            policy(pin).copy(includeBoards = SnapshotList.of(BoardRole.MAIN, BoardRole.SIDEBOARD)), token))
        assertEquals(first, reversed)
        assertEquals(2L, first.allocations.sumOf { it.quantity.toLong() })
        assertEquals(setOf(OwnedAllocation(EntityId("only-strict"), strict.id, 1), OwnedAllocation(EntityId("alternative"), flexible.id, 1)), first.allocations.toSet())
        assertTrue(first.complete); assertTrue(first.demands.all { it.remainingQuantity == 0 && it.complete })
        assertEquals(listOf(BoardRole.MAIN, BoardRole.SIDEBOARD), first.policy.includeBoards)
    }
    @Test fun absentFinishIsUnconstrainedUnknownFinishIsUnprovenAndPinCanRefineIt() {
        val owned = inventory(listOf(lot("lot", finish = Knowledge.Unknown("unknown", SnapshotList.empty()))))
        val known = entry("entry")
        val uncertain = ok(allocator.allocate(deck(known), owned, policy(), token))
        assertFalse(uncertain.complete); assertEquals(1, uncertain.demands.single().remainingQuantity)
        assertTrue(uncertain.allocations.isEmpty())
        val unconstrained = ok(allocator.allocate(deck(known.copy(finish = Knowledge.Absent("unconstrained"))), owned, policy(), token))
        assertTrue(unconstrained.complete); assertEquals(1, unconstrained.demands.single().ownedQuantity)
        val unresolved = known.copy(finish = Knowledge.Unknown("unknown", SnapshotList.empty()))
        val pinned = ok(allocator.allocate(deck(unresolved), inventory(listOf(lot("lot"))), policy(PrintingPin(unresolved.id, p1, Finish.NONFOIL)), token))
        assertEquals(1, pinned.demands.single().ownedQuantity); assertTrue(pinned.complete)
    }
    @Test fun originallyPositiveIndeterminateLotStillMattersWhenAllocatedToAnotherDemand() {
        val needsKnown = entry("known", 2)
        val unconstrained = entry("unconstrained", finish = Knowledge.Absent("any"))
        val owned = inventory(listOf(lot("unknown-finish", finish = Knowledge.Unknown("unknown", SnapshotList.empty()))))
        val result = ok(allocator.allocate(deck(needsKnown, unconstrained), owned, policy(), token))
        assertEquals(1, result.demands.single { it.entryId == unconstrained.id }.ownedQuantity)
        assertFalse(result.demands.single { it.entryId == needsKnown.id }.complete)
        assertEquals(listOf(needsKnown.id), result.incompleteEntryIds)
    }
    @Test fun conflictingPrintingMappingCannotBypassExactOrPinAndHardFinishMismatchStillExcludes() {
        val row = entry("entry")
        val contradictory = lot("lot", oracleId = OracleId(provider, "contradictory"))
        for (p in listOf(policy(ownership = OwnershipPolicy.EXACT_PRINTING), policy(PrintingPin(row.id, p1, Finish.NONFOIL)))) {
            val result = ok(allocator.allocate(deck(row), inventory(listOf(contradictory)), p, token))
            assertFalse(result.complete); assertTrue(result.allocations.isEmpty())
            val excluded = ok(allocator.allocate(deck(row), inventory(listOf(contradictory.copy(finish = Knowledge.Known(Finish.FOIL)))), p, token))
            assertTrue(excluded.complete); assertEquals(1, excluded.demands.single().remainingQuantity)
        }
    }
    @Test fun ignoreDoesNotTraverseOrValidateUnrelatedInventoryButStillValidatesPins() {
        val row = entry("entry", 4)
        val repeated = lot("duplicate")
        val oversized = inventory(List(OwnedQuantityLimits.MAX_LOTS + 1) { index ->
            if (index == 1) repeated.copy(identity = identity(oracleId = OracleId(provider, "contradictory"))) else repeated
        }, false)
        val result = ok(allocator.allocate(deck(row), oversized, policy(ownership = OwnershipPolicy.IGNORE), token))
        assertTrue(result.complete); assertTrue(result.allocations.isEmpty()); assertTrue(result.incompleteEntryIds.isEmpty())
        assertEquals(OwnedQuantityDemand(row.id, 4, 0, 4, true), result.demands.single())
        assertEquals(oversized.revision, result.inventoryRevision)
        val badPin = policy(PrintingPin(EntityId("absent"), p1, Finish.NONFOIL), ownership = OwnershipPolicy.IGNORE)
        val failure = allocator.allocate(deck(row), oversized, badPin, token)
        assertEquals(FailureCode.INVALID_INPUT, (failure as Outcome.Failure).error.code)
    }
    @Test(timeout = 5_000) fun capacitiesAreNotExpandedIntoIndividualPhysicalCopies() {
        val row = entry("entry", Int.MAX_VALUE)
        val result = ok(allocator.allocate(deck(row), inventory(listOf(lot("lot", quantity = Int.MAX_VALUE)), false), policy(), token))
        assertEquals(Int.MAX_VALUE, result.demands.single().ownedQuantity)
        assertEquals(Int.MAX_VALUE, result.allocations.single().quantity); assertTrue(result.complete)
    }
    @Test fun cancellationPropagatesBeforeQuantityWorkEvenWhenOwnershipIsIgnored() {
        val cancelled = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled() { throw CancellationException("Synthetic cancellation") }
        }
        try {
            allocator.allocate(deck(entry("entry")), inventory(emptyList()), policy(ownership = OwnershipPolicy.IGNORE), cancelled)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
}
