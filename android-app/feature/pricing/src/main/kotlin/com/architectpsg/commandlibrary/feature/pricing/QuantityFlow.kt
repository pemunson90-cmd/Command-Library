package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*

internal class InvalidQuantity(message: String) : RuntimeException(message)

/** Shared work counter covers validation, sorting, graph construction, solving and output. */
internal class QuantityWork(private val cancel: CancelToken) {
    private var untilCheck = OwnedQuantityLimits.CANCEL_CHECK_INTERVAL
    private var residualVisits = 0L
    fun phase() { cancel.throwIfCancelled(); untilCheck = OwnedQuantityLimits.CANCEL_CHECK_INTERVAL }
    fun tick() { if (--untilCheck == 0) phase() }
    fun residual() {
        tick()
        if (++residualVisits > OwnedQuantityLimits.MAX_RESIDUAL_EDGE_VISITS) throw InvalidQuantity("Owned quantity residual edge visit budget exceeded")
    }
}

/** Capacity Dinic with explicit path arrays: no recursion and no per-copy expansion. */
internal class QuantityFlow(private val nodeCount: Int, private val work: QuantityWork) {
    internal class Edge(val to: Int, val reverse: Int, var capacity: Long, val initial: Long)
    private val graph = Array(nodeCount) { work.tick(); ArrayList<Edge>() }
    fun add(from: Int, to: Int, capacity: Long): Edge {
        work.tick()
        val forward = Edge(to, graph[to].size, capacity, capacity)
        val reverse = Edge(from, graph[from].size, 0, 0)
        graph[from].add(forward); graph[to].add(reverse)
        return forward
    }
    fun maximize(source: Int, sink: Int): Long {
        work.phase()
        val levels = IntArray(nodeCount)
        val queue = IntArray(nodeCount)
        val next = IntArray(nodeCount)
        val pathNodes = IntArray(nodeCount)
        val pathEdges = IntArray(nodeCount)
        val pathCapacity = LongArray(nodeCount)
        var total = 0L
        while (true) {
            work.phase()
            for (node in 0 until nodeCount) { work.tick(); levels[node] = -1; next[node] = 0 }
            var head = 0; var tail = 1
            queue[0] = source; levels[source] = 0
            while (head < tail) {
                work.tick()
                val node = queue[head++]
                for (edge in graph[node]) {
                    work.residual()
                    if (edge.capacity > 0 && levels[edge.to] < 0) {
                        levels[edge.to] = levels[node] + 1; queue[tail++] = edge.to
                    }
                }
            }
            if (levels[sink] < 0) return total
            while (true) {
                var depth = 0
                pathNodes[0] = source; pathCapacity[0] = Long.MAX_VALUE
                var pushed = 0L
                while (depth >= 0) {
                    work.tick()
                    val node = pathNodes[depth]
                    if (node == sink) {
                        pushed = pathCapacity[depth]
                        for (step in 0 until depth) {
                            work.residual()
                            val edge = graph[pathNodes[step]][pathEdges[step]]
                            edge.capacity -= pushed; graph[edge.to][edge.reverse].capacity += pushed
                        }
                        break
                    }
                    var advanced = false
                    while (next[node] < graph[node].size) {
                        work.residual()
                        val index = next[node]
                        val edge = graph[node][index]
                        if (edge.capacity > 0 && levels[edge.to] == levels[node] + 1) {
                            pathEdges[depth] = index; pathNodes[depth + 1] = edge.to
                            pathCapacity[depth + 1] = minOf(pathCapacity[depth], edge.capacity)
                            depth++; advanced = true; break
                        }
                        next[node]++
                    }
                    if (!advanced) { depth--; if (depth >= 0) next[pathNodes[depth]]++ }
                }
                if (pushed == 0L) break
                total += pushed
            }
        }
    }
}
