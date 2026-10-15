package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Outcome
import java.math.BigInteger
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class LocalDrawProbabilitiesTest {
    private val cancel = object : CancelToken {
        override val isCancelled = false
        override fun throwIfCancelled() = Unit
    }

    private fun distribution(n: Int, s: Int, d: Int): DrawProbabilityDistribution {
        val result = LocalDrawProbabilities.calculate(n, s, d, cancel)
        assertTrue(result.toString(), result is Outcome.Success)
        return (result as Outcome.Success).value
    }

    private fun assertDistribution(n: Int, s: Int, d: Int, total: String, vararg ways: String) {
        val actual = distribution(n, s, d)
        assertEquals(n, actual.population)
        assertEquals(s, actual.successes)
        assertEquals(d, actual.draws)
        assertEquals(BigInteger(total), actual.totalWays)
        assertEquals((0..d).toList(), actual.points.map { it.successesDrawn })
        assertEquals(ways.map { BigInteger(it) }, actual.points.map { it.ways })
        assertEquals(actual.totalWays, actual.points.fold(BigInteger.ZERO) { sum, point -> sum + point.ways })
    }

    @Test fun literalSmallDistributionAndDegenerateCases() {
        assertDistribution(4, 2, 2, "6", "1", "4", "1")
        assertDistribution(0, 0, 0, "1", "1")
        assertDistribution(8, 3, 0, "1", "1")
        assertDistribution(4, 0, 2, "6", "6", "0", "0")
        assertDistribution(4, 4, 2, "6", "0", "0", "6")
        assertDistribution(5, 3, 5, "1", "0", "0", "0", "1", "0", "0")
        // Forced minimum k=2; recurrence must never start from the zero at k=0.
        assertDistribution(5, 4, 3, "10", "0", "0", "6", "4")
    }

    @Test fun ordinaryDeckReferenceCounts() {
        // Literal integers derived independently with Python math.comb, without production recurrence.
        assertDistribution(60, 24, 7, "386206920",
            "8347680", "46747008", "104049792", "119223720", "75869640", "26777520", "4845456", "346104")
        assertDistribution(99, 35, 7, "14887031544",
            "621216192", "2624102880", "4536584640", "4158535920", "2181527040", "654458112", "103882240", "6724520")
    }

    @Test fun exhaustiveSmallSubsetsMatchEveryPointAndComplement() {
        // Each bit is one distinguishable population member, with the first s marked successful.
        // Enumerating subsets is independent of binomial coefficients and ratio recurrences.
        for (n in 0..7) for (s in 0..n) for (d in 0..n) {
            val expected = LongArray(d + 1)
            for (mask in 0 until (1 shl n)) {
                if (Integer.bitCount(mask) == d) {
                    expected[Integer.bitCount(mask and ((1 shl s) - 1))]++
                }
            }
            val actual = distribution(n, s, d)
            val complement = distribution(n, n - s, d)
            assertEquals("N=$n S=$s D=$d", expected.map { BigInteger.valueOf(it) }, actual.points.map { it.ways })
            assertEquals(BigInteger.valueOf(expected.sum()), actual.totalWays)
            assertEquals(actual.totalWays, actual.points.fold(BigInteger.ZERO) { sum, point -> sum + point.ways })
            assertEquals(actual.points.map { it.ways }, complement.points.reversed().map { it.ways })
        }
    }

    @Test fun invalidAndResourceBoundsAreTypedWithoutClamping() {
        val invalid = listOf(
            Triple(-1, 0, 0), Triple(1_000_001, 0, 0),
            Triple(4, -1, 2), Triple(4, 5, 2), Triple(4, 2, -1), Triple(4, 2, 5),
            Triple(1001, 500, 1001), Triple(0, 1, 0), Triple(0, 0, 1),
            Triple(Int.MIN_VALUE, 0, 0), Triple(Int.MAX_VALUE, 0, 0),
            Triple(4, Int.MIN_VALUE, 0), Triple(4, Int.MAX_VALUE, 0),
            Triple(4, 2, Int.MIN_VALUE), Triple(4, 2, Int.MAX_VALUE),
        )
        for ((n, s, d) in invalid) {
            val result = LocalDrawProbabilities.calculate(n, s, d, cancel)
            assertTrue(result.toString(), result is Outcome.Failure)
            assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)
        }
    }

    @Test fun upperResourceBoundaryRemainsExactAndBounded() {
        val actual = distribution(1_000_000, 500_000, 1000)
        assertEquals(1001, actual.points.size)
        assertEquals((0..1000).toList(), actual.points.map { it.successesDrawn })
        assertTrue(actual.totalWays > BigInteger.valueOf(Long.MAX_VALUE))
        assertTrue(actual.points.all { it.ways.signum() > 0 })
        assertEquals(actual.totalWays, actual.points.fold(BigInteger.ZERO) { sum, point -> sum + point.ways })
        assertEquals(actual.points.map { it.ways }, actual.points.reversed().map { it.ways })
    }

    @Test fun cancellationEscapesBeforeWorkWithinCombinationsAndWithinPointRecurrence() {
        // For 8/4/4: initial check + 4 total-binomial steps + final binomial check,
        // then the two zero-term seed checks, then point checks. Limit 10 reaches the point loop.
        for (limit in listOf(1, 3, 10)) {
            val exception = CancellationException("exact cancellation $limit")
            val token = object : CancelToken {
                var checks = 0
                override val isCancelled get() = checks >= limit
                override fun throwIfCancelled() { if (++checks >= limit) throw exception }
            }
            try {
                LocalDrawProbabilities.calculate(8, 4, 4, token)
                fail("Expected cancellation at check $limit")
            } catch (actual: CancellationException) {
                assertSame(exception, actual)
            }
        }
        val exception = CancellationException("cancel invalid input before validation")
        val token = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw exception
        }
        try {
            LocalDrawProbabilities.calculate(Int.MIN_VALUE, 0, 0, token)
            fail("Expected cancellation before invalid-input result")
        } catch (actual: CancellationException) {
            assertSame(exception, actual)
        }
    }
}
