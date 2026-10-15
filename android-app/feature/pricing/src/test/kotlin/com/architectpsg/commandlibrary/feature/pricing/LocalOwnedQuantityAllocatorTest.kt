package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalOwnedQuantityAllocatorTest : OwnedQuantityContractCases() {
    override val allocator: OwnedQuantityAllocator = LocalOwnedQuantityAllocator()
    private val f = QuantityFixtures()

    @Test fun equalRankOverlappingIdentityProofsRequireResidualReassignment() {
        val flexible = f.entry("a-flexible")
        val fallback = f.entry("b-printing-fallback").copy(identity = f.identity(printing = f.printing("p2")).copy(oracleId = f.unknown()))
        val shared = f.lot("a-shared").copy(identity = f.identity(printing = f.printing("p2")))
        val alternative = f.lot("b-alternative")
        val result = f.run(listOf(flexible, fallback), listOf(shared, alternative))
        assertTrue(result.complete)
        assertEquals(setOf(OwnedAllocation(shared.id, fallback.id, 1), OwnedAllocation(alternative.id, flexible.id, 1)), result.allocations.toSet())
        assertEquals(result, f.run(listOf(fallback, flexible), listOf(alternative, shared)))
    }

    @Test fun originalInputsRemainUnchangedAndIndependentPlansReuseSupply() {
        val rows = listOf(f.entry("b", 2), f.entry("a", 1))
        val lots = listOf(f.lot("lot", 2))
        val deck = f.deck(rows); val inventory = f.inventory(lots)
        val policy = f.policy()
        val first = f.ok(allocator.allocate(deck, inventory, policy, f.token))
        assertEquals(first, f.ok(allocator.allocate(deck, inventory, policy, f.token)))
        assertEquals(rows, deck.entries); assertEquals(lots, inventory.lots)
        assertEquals(2L, first.allocations.sumOf { it.quantity.toLong() })
        assertEquals(listOf("a", "b"), first.demands.map { it.entryId.value })
    }

    @Test fun identityProofUsesSourceQualifiedIdsNeverRawNamesOrSelectedPrintingAsImplicitPin() {
        val row = f.entry("row")
        val alternative = f.lot("lot").copy(identity = f.identity(printing = f.printing("other")))
        assertEquals(1, f.run(listOf(row), listOf(alternative)).demands.single().ownedQuantity)
        val foreign = alternative.copy(identity = f.identity(oracle = OracleId(ProviderId("foreign"), "o"), printing = CardId(ProviderId("foreign"), "p")))
        assertTrue(f.run(listOf(row), listOf(foreign)).complete)
        assertEquals(0, f.run(listOf(row), listOf(foreign)).demands.single().ownedQuantity)
        val raw = ProviderIdentity(f.provider, "card", "raw")
        val unresolved = f.identity().copy(oracleId = f.unknown(), printingId = Knowledge.Absent("none"), raw = raw)
        val unknown = f.run(listOf(row.copy(identity = unresolved)), listOf(alternative.copy(identity = unresolved)))
        assertFalse(unknown.complete); assertTrue(unknown.allocations.isEmpty())
        val missingOracle = row.identity.copy(oracleId = Knowledge.Absent("none"))
        assertEquals(1, f.run(listOf(row.copy(identity = missingOracle)), listOf(f.lot("equal-printing"))).demands.single().ownedQuantity)
    }

    @Test fun hardExclusionsWinOverUnknownAndConflictingMappings() {
        val row = f.entry("row", finish = f.unknown())
        val otherOracle = f.lot("lot").copy(identity = f.identity(oracle = f.oracle("different"), printing = f.printing("different")))
        assertTrue(f.run(listOf(row), listOf(otherOracle)).complete)
        val foreignLanguage = f.lot("lot").copy(identity = f.identity().copy(language = LanguageTag("EN")))
        assertTrue(f.run(listOf(row), listOf(foreignLanguage)).complete)
        val strict = f.policy(ownership = OwnershipPolicy.EXACT_PRINTING)
        assertTrue(f.run(listOf(row), listOf(otherOracle), strict).complete)
        val ambiguous = f.lot("lot").copy(identity = f.identity(oracle = f.oracle("different")))
        assertFalse(f.run(listOf(row), listOf(ambiguous)).complete)
        assertTrue(f.run(listOf(row.copy(finish = Knowledge.Known(Finish.FOIL))), listOf(ambiguous)).complete)
    }

    @Test fun crossRowMappingAssertionsIncludeOnlyIncludedRowsAndPositiveLots() {
        val row = f.entry("row")
        val excluded = f.entry("excluded").copy(board = BoardRole.MAYBEBOARD, identity = f.identity(oracle = f.oracle("wrong")))
        val zero = f.lot("zero", 0).copy(identity = excluded.identity)
        assertTrue(f.run(listOf(row, excluded), listOf(f.lot("lot"), zero)).complete)
        val contradiction = excluded.copy(board = BoardRole.MAIN)
        val result = f.run(listOf(row, contradiction), listOf(f.lot("lot")))
        assertTrue(result.allocations.isEmpty()); assertFalse(result.complete)
    }

    @Test fun incompleteInventoryHasLowerBoundsExceptForFullyCoveredRowsAndIgnore() {
        val rows = listOf(f.entry("row", 2))
        assertFalse(f.run(rows, listOf(f.lot("lot", 1)), complete = false).complete)
        assertTrue(f.run(rows, listOf(f.lot("lot", 2)), complete = false).complete)
        assertTrue(f.run(rows, emptyList(), complete = true).complete)
        assertTrue(f.run(rows, emptyList(), f.policy(ownership = OwnershipPolicy.IGNORE), complete = false).complete)
    }

    @Test fun pinsMustReferenceIncludedKnownExactPrintingAndCompatibleKnownFinish() {
        val row = f.entry("row")
        val good = PrintingPin(row.id, f.printing(), Finish.NONFOIL)
        for (policy in listOf(
            f.policy(pins = listOf(good, good)),
            f.policy(pins = listOf(good.copy(entryId = EntityId("missing")))),
            f.policy(pins = listOf(good.copy(printingId = f.printing("other")))),
            f.policy(pins = listOf(good.copy(finish = Finish.FOIL))),
            f.policy(pins = listOf(good)).copy(includeBoards = SnapshotList.of(BoardRole.SIDEBOARD)),
        )) f.invalid(f.call(listOf(row), emptyList(), policy))
        f.invalid(f.call(listOf(row.copy(identity = row.identity.copy(printingId = f.unknown()))), emptyList(), f.policy(pins = listOf(good))))
        for (finish in listOf<Knowledge<Finish>>(Knowledge.Absent("any"), f.unknown())) {
            assertEquals(1, f.run(listOf(row.copy(finish = finish)), listOf(f.lot("lot")), f.policy(pins = listOf(good))).demands.single().ownedQuantity)
        }
    }

    @Test fun duplicateIdsSelectorsOversizesAndInvalidUnicodeFailWithoutPartialPlans() {
        val row = f.entry("row"); val lot = f.lot("lot")
        f.invalid(f.call(listOf(row, row.copy(board = BoardRole.SIDEBOARD)), emptyList()))
        f.invalid(f.call(listOf(row), listOf(lot, lot.copy(quantity = 0))))
        f.invalid(f.call(listOf(row), emptyList(), f.policy().copy(includeBoards = SnapshotList.of(BoardRole.MAIN, BoardRole.MAIN))))
        f.invalid(f.call(List(OwnedQuantityLimits.MAX_ENTRIES + 1) { f.entry("e$it") }, emptyList()))
        f.invalid(f.call(listOf(row), List(OwnedQuantityLimits.MAX_LOTS + 1) { f.lot("l$it", 0) }))
        for (bad in listOf("x".repeat(1025), "\uD800", "\uDC00", "é".repeat(513))) {
            f.invalid(f.call(listOf(row.copy(id = EntityId(bad))), emptyList()))
            f.invalid(f.call(listOf(row.copy(identity = row.identity.copy(language = LanguageTag(bad)))), emptyList()))
            f.invalid(f.call(listOf(row), listOf(lot.copy(identity = lot.identity.copy(raw = ProviderIdentity(f.provider, "kind", bad))))))
        }
        assertTrue(f.run(listOf(row.copy(id = EntityId("é".repeat(512)))), emptyList()).complete)
    }

    @Test fun ignoreBypassesEvenMalformedInventoryIdentitiesAndKeepsCanonicalPolicy() {
        val bad = f.lot("same").copy(identity = f.identity().copy(raw = ProviderIdentity(f.provider, "\uD800", "x")))
        val rows = listOf(f.entry("z"), f.entry("a"))
        val pins = rows.map { PrintingPin(it.id, f.printing(), Finish.NONFOIL) }
        val policy = f.policy(pins = pins, ownership = OwnershipPolicy.IGNORE).copy(includeBoards = SnapshotList.of(BoardRole.SIDEBOARD, BoardRole.MAIN))
        val result = f.run(rows, List(10001) { bad }, policy, false)
        assertTrue(result.complete); assertEquals(listOf("a", "z"), result.policy.pins.map { it.entryId.value })
        assertEquals(listOf(BoardRole.MAIN, BoardRole.SIDEBOARD), result.policy.includeBoards)
    }

    @Test fun entityOrderingUsesUnsignedUtf8AndAllocationTotalsUseLong() {
        val rows = listOf(f.entry("\uD800\uDC00", Int.MAX_VALUE), f.entry("\uE000", Int.MAX_VALUE))
        val result = f.run(rows, listOf(f.lot("b", Int.MAX_VALUE), f.lot("a", Int.MAX_VALUE)))
        assertEquals(listOf("\uE000", "\uD800\uDC00"), result.demands.map { it.entryId.value })
        assertEquals(2L * Int.MAX_VALUE, result.allocations.sumOf { it.quantity.toLong() })
        assertTrue(result.complete)
    }

    @Test fun candidateAndEdgeBudgetsRejectInsteadOfTruncating() {
        val rows = (0..1000).map { f.entry("e$it") }
        val foreign = (0..1000).map { f.lot("l$it").copy(identity = f.identity(oracle = f.oracle("other"), printing = f.printing("other"))) }
        val candidates = f.call(rows, foreign)
        f.invalid(candidates); assertTrue((candidates as Outcome.Failure).error.message.contains("candidate"))
        val edges = f.call((0..316).map { f.entry("e$it") }, (0..316).map { f.lot("l$it") })
        f.invalid(edges); assertTrue((edges as Outcome.Failure).error.message.contains("edge"))
    }

    @Test fun cancellationAtFinalPublicationAlsoPropagatesForIgnore() {
        val deck = f.deck(listOf(f.entry("row")))
        val inventory = f.inventory(emptyList())
        val policy = f.policy(ownership = OwnershipPolicy.IGNORE)
        var checks = 0
        val observing = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() { checks++ }
        }
        f.ok(allocator.allocate(deck, inventory, policy, observing))
        val expected = CancellationException("Cancel at publication")
        var seen = 0
        val cancelling = object : CancelToken {
            override val isCancelled get() = seen >= checks
            override fun throwIfCancelled() { if (++seen == checks) throw expected }
        }
        try {
            allocator.allocate(deck, inventory, policy, cancelling)
            fail("Final cancellation must not publish success")
        } catch (actual: CancellationException) { assertSame(expected, actual) }
    }

    @Test fun midWorkCancellationPropagatesTheSameExceptionAcrossValidationGraphFlowAndOutput() {
        val rows = (0..79).map { f.entry("e$it", 2) }
        val lots = (0..79).map { f.lot("l$it", 2) }
        var checks = 0
        val observing = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() { checks++ }
        }
        allocator.allocate(f.deck(rows), f.inventory(lots), f.policy(), observing)
        assertTrue(checks > 20)
        for (at in listOf(2, 5, checks / 2, checks - 1, checks)) {
            val expected = CancellationException("at $at")
            var seen = 0
            val cancelling = object : CancelToken {
                override val isCancelled get() = seen >= at
                override fun throwIfCancelled() { if (++seen == at) throw expected }
            }
            try {
                allocator.allocate(f.deck(rows), f.inventory(lots), f.policy(), cancelling)
                fail("Expected cancellation at $at of $checks")
            } catch (actual: CancellationException) { assertSame(expected, actual) }
        }
    }
}

internal class QuantityFixtures {
    val provider = ProviderId("test")
    val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() = Unit }
    fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("unknown", SnapshotList.empty())
    fun oracle(raw: String = "o") = OracleId(provider, raw)
    fun printing(raw: String = "p") = CardId(provider, raw)
    fun identity(oracle: OracleId = oracle(), printing: CardId = printing()) = CardIdentity(Knowledge.Known(oracle), Knowledge.Known(printing), null, Knowledge.Absent("none"), LanguageTag.ENGLISH)
    fun entry(id: String, quantity: Int = 1, finish: Knowledge<Finish> = Knowledge.Known(Finish.NONFOIL)) = DeckEntry(EntityId(id), identity(), quantity, BoardRole.MAIN, finish, "", SnapshotList.empty(), SnapshotList.empty())
    fun lot(id: String, quantity: Int = 1, finish: Knowledge<Finish> = Knowledge.Known(Finish.NONFOIL)) = OwnedLot(EntityId(id), identity(), finish, CardCondition.UNKNOWN, "", quantity, "", Revision(0), Knowledge.Absent("none"))
    fun deck(rows: List<DeckEntry>) = DeckSnapshot(DeckId(provider, "deck"), "Test", Knowledge.Absent("none"), "", Knowledge.Absent("none"), SnapshotList.empty(), UtcTimestamp(0), UtcTimestamp(0), Revision(1), SnapshotList.copyOf(rows), Knowledge.Absent("none"), null, SnapshotList.empty(), SnapshotList.empty())
    fun inventory(lots: List<OwnedLot>, complete: Boolean = true) = InventorySnapshot(Revision(2), UtcTimestamp(0), SnapshotList.copyOf(lots), complete)
    fun policy(pins: List<PrintingPin> = emptyList(), ownership: OwnershipPolicy = OwnershipPolicy.SAME_ORACLE) = OwnedQuantityPolicy(SnapshotList.of(BoardRole.MAIN), SnapshotList.copyOf(pins), ownership)
    fun call(rows: List<DeckEntry>, lots: List<OwnedLot>, policy: OwnedQuantityPolicy = policy(), complete: Boolean = true) = LocalOwnedQuantityAllocator().allocate(deck(rows), inventory(lots, complete), policy, token)
    fun run(rows: List<DeckEntry>, lots: List<OwnedLot>, policy: OwnedQuantityPolicy = policy(), complete: Boolean = true) = ok(call(rows, lots, policy, complete))
    fun ok(outcome: Outcome<OwnedQuantityPlan>): OwnedQuantityPlan { assertTrue("Expected success: $outcome", outcome is Outcome.Success); return (outcome as Outcome.Success).value }
    fun invalid(outcome: Outcome<OwnedQuantityPlan>) { assertTrue("Expected failure: $outcome", outcome is Outcome.Failure); assertEquals(FailureCode.INVALID_INPUT, (outcome as Outcome.Failure).error.code) }
}
