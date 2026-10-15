package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.DomainFailure
import com.architectpsg.commandlibrary.core.model.FailureCode
import com.architectpsg.commandlibrary.core.model.Outcome
import com.architectpsg.commandlibrary.core.model.SnapshotList
import java.math.BigInteger

/** Exact count of unordered draws containing [successesDrawn] successes. */
public data class DrawProbabilityPoint(public val successesDrawn: Int, public val ways: BigInteger)

/** Each point's probability is ways / [totalWays]; points include every k from zero to [draws]. */
public data class DrawProbabilityDistribution(
    public val population: Int,
    public val successes: Int,
    public val draws: Int,
    public val totalWays: BigInteger,
    public val points: SnapshotList<DrawProbabilityPoint>,
)

/** Offline sampling without replacement. Limits bound calculator work, rather than clamp inputs. */
public object LocalDrawProbabilities {
    public fun calculate(population: Int, successes: Int, draws: Int, cancel: CancelToken): Outcome<DrawProbabilityDistribution> {
        cancel.throwIfCancelled()
        if (population !in 0..1_000_000 || successes !in 0..population || draws !in 0..minOf(population, 1000)) {
            return Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,
                "Require population 0..1000000, successes 0..population, and draws 0..min(population,1000)", SnapshotList.empty()))
        }
        val failures = population - successes
        val first = maxOf(0, draws - failures)
        val last = minOf(draws, successes)
        val total = choose(population, draws, cancel)
        // Start inside feasible support: both the seed and recurrence denominators are nonzero.
        var ways = choose(successes, first, cancel).multiply(choose(failures, draws - first, cancel))
        val points = ArrayList<DrawProbabilityPoint>(draws + 1)
        for (k in 0..draws) {
            cancel.throwIfCancelled()
            points.add(DrawProbabilityPoint(k, if (k in first..last) ways else BigInteger.ZERO))
            if (k in first until last) {
                // Promote factors before multiplication; their product can exceed Int.MAX_VALUE.
                val numerator = BigInteger.valueOf((successes - k).toLong()).multiply(BigInteger.valueOf((draws - k).toLong()))
                val denominator = BigInteger.valueOf((k + 1).toLong()).multiply(BigInteger.valueOf((failures - draws + k + 1).toLong()))
                ways = ways.multiply(numerator).divide(denominator)
            }
        }
        cancel.throwIfCancelled()
        return Outcome.Success(DrawProbabilityDistribution(population, successes, draws, total, SnapshotList.copyOf(points)))
    }

    private fun choose(n: Int, k: Int, cancel: CancelToken): BigInteger {
        var result = BigInteger.ONE
        val terms = minOf(k, n - k)
        for (i in 1..terms) {
            cancel.throwIfCancelled()
            result = result.multiply(BigInteger.valueOf((n - terms + i).toLong())).divide(BigInteger.valueOf(i.toLong()))
        }
        cancel.throwIfCancelled()
        return result
    }
}
