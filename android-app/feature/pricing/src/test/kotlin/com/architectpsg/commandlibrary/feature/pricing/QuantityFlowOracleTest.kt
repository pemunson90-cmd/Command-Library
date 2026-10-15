package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class QuantityFlowOracleTest {
    private val f = QuantityFixtures()

    /** Enumerates each physical unit's possible destination, only for tiny independent test inputs. */
    private fun exhaustive(demands: IntArray, supplies: IntArray, edges: Array<BooleanArray>): Int {
        val units = supplies.indices.flatMap { lot -> List(supplies[lot]) { lot } }
        val remaining = demands.copyOf()
        fun search(unit: Int): Int {
            if (unit == units.size) return 0
            var best = search(unit + 1)
            val lot = units[unit]
            for (row in remaining.indices) {
                if (remaining[row] > 0 && edges[row][lot]) {
                    remaining[row]--
                    best = maxOf(best, 1 + search(unit + 1))
                    remaining[row]++
                }
            }
            return best
        }
        return search(0)
    }

    private fun actual(demands: IntArray, supplies: IntArray, edges: Array<BooleanArray>): Long {
        val source = demands.size + supplies.size; val sink = source + 1
        val flow = QuantityFlow(sink + 1, QuantityWork(f.token))
        val tracked = ArrayList<Triple<Int, Int, QuantityFlow.Edge>>()
        for (row in demands.indices) flow.add(source, row, demands[row].toLong())
        for (lot in supplies.indices) flow.add(demands.size + lot, sink, supplies[lot].toLong())
        for (row in demands.indices) for (lot in supplies.indices) if (edges[row][lot]) {
            tracked.add(Triple(row, lot, flow.add(row, demands.size + lot, minOf(demands[row], supplies[lot]).toLong())))
        }
        val total = flow.maximize(source, sink)
        val usedRows = LongArray(demands.size); val usedLots = LongArray(supplies.size)
        for ((row, lot, edge) in tracked) {
            val used = edge.initial - edge.capacity
            assertTrue(used >= 0); usedRows[row] += used; usedLots[lot] += used
        }
        assertEquals(total, usedRows.sum()); assertEquals(total, usedLots.sum())
        for (row in demands.indices) assertTrue(usedRows[row] <= demands[row])
        for (lot in supplies.indices) assertTrue(usedLots[lot] <= supplies[lot])
        return total
    }

    @Test fun everyTwoByTwoGraphWithAllZeroToTwoCapacitiesMatchesIndependentExhaustiveOptimum() {
        for (mask in 0 until 16) for (capacities in 0 until 81) {
            var code = capacities
            val counts = IntArray(4) { val quantity = code % 3; code /= 3; quantity }
            val edges = Array(2) { row -> BooleanArray(2) { lot -> mask and (1 shl (row * 2 + lot)) != 0 } }
            val demand = counts.copyOfRange(0, 2); val supply = counts.copyOfRange(2, 4)
            assertEquals("mask=$mask capacities=$capacities", exhaustive(demand, supply, edges).toLong(), actual(demand, supply, edges))
        }
    }

    @Test fun everyThreeByThreeUnitGraphMatchesIndependentExhaustiveOptimumIncludingReassignment() {
        for (mask in 0 until 512) {
            val edges = Array(3) { row -> BooleanArray(3) { lot -> mask and (1 shl (row * 3 + lot)) != 0 } }
            assertEquals("mask=$mask", exhaustive(IntArray(3) { 1 }, IntArray(3) { 1 }, edges).toLong(), actual(IntArray(3) { 1 }, IntArray(3) { 1 }, edges))
        }
        // Both rows have the same rank and the first row steals the second's only lot.
        assertEquals(2L, actual(intArrayOf(1, 1), intArrayOf(1, 1), arrayOf(booleanArrayOf(true, true), booleanArrayOf(true, false))))
    }

    @Test fun generatedRealEngineCasesMatchIndependentOracleConserveAndIgnoreInputPermutations() {
        val random = Random(0x514e71)
        repeat(220) { case ->
            val rowCount = random.nextInt(1, 5); val lotCount = random.nextInt(1, 5)
            val demand = IntArray(rowCount) { random.nextInt(1, 4) }
            val supply = IntArray(lotCount) { random.nextInt(0, 3) }
            val rowIdentity = IntArray(rowCount) { random.nextInt(0, 3) }
            val lotIdentity = IntArray(lotCount) { random.nextInt(0, 3) }
            // -1 = unasserted, 0/1 = exact known finish, 2 = unknown.
            val rowFinish = IntArray(rowCount) { random.nextInt(-1, 3) }
            val lotFinish = IntArray(lotCount) { random.nextInt(-1, 3) }
            val rowLanguage = IntArray(rowCount) { random.nextInt(0, 2) }
            val lotLanguage = IntArray(lotCount) { random.nextInt(0, 2) }
            val rowPrinting = IntArray(rowCount) { random.nextInt(0, 2) }
            val lotPrinting = IntArray(lotCount) { random.nextInt(0, 2) }
            val pinned = BooleanArray(rowCount) { random.nextBoolean() && rowFinish[it] in 0..1 }
            val exact = random.nextBoolean()
            fun finish(value: Int): Knowledge<Finish> = when (value) { -1 -> Knowledge.Absent("any"); 2 -> f.unknown(); else -> Knowledge.Known(if (value == 0) Finish.NONFOIL else Finish.FOIL) }
            fun identity(id: Int, printing: Int, language: Int) = f.identity(f.oracle("o$id"), f.printing("o$id-p$printing")).copy(language = LanguageTag(if (language == 0) "en" else "fr"))
            val rows = (0 until rowCount).map { f.entry("e$it", demand[it], finish(rowFinish[it])).copy(identity = identity(rowIdentity[it], rowPrinting[it], rowLanguage[it])) }
            val lots = (0 until lotCount).map { f.lot("l$it", supply[it], finish(lotFinish[it])).copy(identity = identity(lotIdentity[it], lotPrinting[it], lotLanguage[it])) }
            val pins = rows.indices.filter { pinned[it] }.map { PrintingPin(rows[it].id, (rows[it].identity.printingId as Knowledge.Known).value, (rows[it].finish as Knowledge.Known).value) }
            val policy = f.policy(pins, if (exact) OwnershipPolicy.EXACT_PRINTING else OwnershipPolicy.SAME_ORACLE)
            // Independent integer fixture rules: these graphs contain no contradictory mappings.
            val edges = Array(rowCount) { row -> BooleanArray(lotCount) { lot ->
                rowIdentity[row] == lotIdentity[lot] && rowLanguage[row] == lotLanguage[lot] &&
                    (!(exact || pinned[row]) || rowPrinting[row] == lotPrinting[lot]) &&
                    (rowFinish[row] == -1 || (rowFinish[row] in 0..1 && rowFinish[row] == lotFinish[lot]))
            } }
            val completeInventory = random.nextBoolean()
            val result = f.run(rows, lots, policy, completeInventory)
            assertEquals("case=$case", exhaustive(demand, supply, edges).toLong(), result.allocations.sumOf { it.quantity.toLong() })
            assertEquals(result, f.run(rows.shuffled(random), lots.shuffled(random), policy.copy(pins = SnapshotList.copyOf(pins.shuffled(random))), completeInventory))
            for (allocation in result.allocations) {
                val row = rows.indexOfFirst { it.id == allocation.entryId }; val lot = lots.indexOfFirst { it.id == allocation.lotId }
                assertTrue(edges[row][lot]); assertTrue(allocation.quantity > 0)
            }
            for (row in rows.indices) {
                val used = result.allocations.filter { it.entryId == rows[row].id }.sumOf { it.quantity.toLong() }
                val counter = result.demands.single { it.entryId == rows[row].id }
                assertTrue(used <= demand[row]); assertEquals(used, counter.ownedQuantity.toLong())
                assertEquals(demand[row] - used, counter.remainingQuantity.toLong())
                val unknownCandidate = lots.indices.any { lot ->
                    supply[lot] > 0 && rowLanguage[row] == lotLanguage[lot] &&
                        (!(exact || pinned[row]) || (rowIdentity[row] == lotIdentity[lot] && rowPrinting[row] == lotPrinting[lot])) &&
                        !(rowFinish[row] in 0..1 && lotFinish[lot] in 0..1 && rowFinish[row] != lotFinish[lot]) &&
                        rowIdentity[row] == lotIdentity[lot] &&
                        (rowFinish[row] == 2 || (rowFinish[row] in 0..1 && lotFinish[lot] !in 0..1))
                }
                assertEquals(used == demand[row].toLong() || (completeInventory && !unknownCandidate), counter.complete)
            }
            for (lot in lots.indices) assertTrue(result.allocations.filter { it.lotId == lots[lot].id }.sumOf { it.quantity.toLong() } <= supply[lot])
        }
    }

    @Test(timeout = 5000) fun traversalDepthCanReachTwentyThousandWithoutRecursion() {
        val size = 20002
        val flow = QuantityFlow(size, QuantityWork(f.token))
        for (node in 0 until size - 1) flow.add(node, node + 1, Int.MAX_VALUE.toLong())
        assertEquals(Int.MAX_VALUE.toLong(), flow.maximize(0, size - 1))
    }

    @Test fun residualFenceIncludesUnsuccessfulScansAndCancellationChecksStayWithin1024Units() {
        var checks = 0
        val token = object : CancelToken { override val isCancelled = false; override fun throwIfCancelled() { checks++ } }
        val work = QuantityWork(token)
        work.phase()
        repeat(OwnedQuantityLimits.CANCEL_CHECK_INTERVAL - 1) { work.residual() }
        assertEquals(1, checks)
        work.residual(); assertEquals(2, checks)
        repeat((OwnedQuantityLimits.MAX_RESIDUAL_EDGE_VISITS - OwnedQuantityLimits.CANCEL_CHECK_INTERVAL).toInt()) { work.residual() }
        try { work.residual(); fail("Residual visit budget must fail") } catch (invalid: InvalidQuantity) {
            assertEquals("Owned quantity residual edge visit budget exceeded", invalid.message)
        }
        // A reachable dead end is scanned even though no augmentation succeeds.
        val second = QuantityWork(token)
        val graph = QuantityFlow(1100, second)
        for (node in 1..1098) graph.add(0, node, 1)
        val before = checks
        assertEquals(0L, graph.maximize(0, 1099)); assertTrue(checks - before >= 3)
    }
}
