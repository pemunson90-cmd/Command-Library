package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*

/** Per-call accounting only; no shared mutable state or user-controlled failure messages. */
internal class InventoryProjectionWork(private val cancel: CancelToken) {
    internal class Rejected(val code: FailureCode, override val message: String) : RuntimeException(message)
    private var units = 0L
    private var untilCheck = 0
    private var textBytes = 0L
    private var elements = 0L
    private var candidates = 0L

    fun phase() { cancel.throwIfCancelled(); untilCheck = InventoryProjectionLimits.CANCEL_CHECK_INTERVAL }
    fun spend(amount: Int = 1) {
        var left = amount
        while (left > 0) {
            if (untilCheck == 0) phase()
            val step = minOf(left, untilCheck)
            if (units > InventoryProjectionLimits.MAX_WORK_UNITS - step) reject("Inventory work limit exceeded.")
            units += step; untilCheck -= step; left -= step
        }
    }
    fun reject(message: String): Nothing = throw Rejected(FailureCode.INVALID_INPUT, message)
    fun fail(code: FailureCode, message: String): Nothing = throw Rejected(code, message)
    fun items(count: Int) {
        if (count.toLong() > InventoryProjectionLimits.MAX_COLLECTION_ELEMENTS - elements) reject("Inventory collection limit exceeded.")
        elements += count
    }
    fun candidate() {
        spend()
        if (++candidates > InventoryProjectionLimits.MAX_CANDIDATE_COMPARISONS) reject("Inventory comparison limit exceeded.")
    }
    /** Validate strict UTF-8 and its size before any encoding or metadata index allocation. */
    fun text(value: String, identifier: Boolean = false) {
        val limit = if (identifier) InventoryProjectionLimits.MAX_ID_BYTES else InventoryProjectionLimits.MAX_TEXT_BYTES
        if (value.length > limit) reject("Inventory scalar text limit exceeded.")
        var bytes = 0L
        var i = 0
        while (i < value.length) {
            spend()
            val ch = value[i++].code
            val count = when {
                ch < 0x80 -> 1
                ch < 0x800 -> 2
                ch in 0xd800..0xdbff -> {
                    if (i == value.length || value[i].code !in 0xdc00..0xdfff) reject("Inventory text is not strict UTF-8.")
                    spend(); i++; 4
                }
                ch in 0xdc00..0xdfff -> reject("Inventory text is not strict UTF-8.")
                else -> 3
            }
            spend(count); bytes += count
            if (bytes > limit) reject("Inventory scalar text limit exceeded.")
        }
        if (bytes > InventoryProjectionLimits.MAX_TOTAL_TEXT_BYTES - textBytes) reject("Inventory total text limit exceeded.")
        textBytes += bytes
    }
    fun timestamp(value: UtcTimestamp) { spend(2); if (value.nanoAdjustment !in 0..999_999_999) reject("Invalid inventory timestamp.") }
    fun <T> knowledge(value: Knowledge<T>, known: (T) -> Unit) {
        spend()
        when (value) {
            is Knowledge.Known -> known(value.value)
            is Knowledge.Absent -> text(value.reason)
            is Knowledge.Unknown -> {
                text(value.reason); items(value.evidence.size)
                for (e in value.evidence) {
                    spend(); text(e.description)
                    e.source?.let { text(it.value) }
                    e.recordedAt?.let { timestamp(it) }
                }
            }
        }
    }
    fun lot(value: OwnedLot) {
        spend(5); text(value.id.value, true)
        val identity = value.identity
        knowledge(identity.oracleId) { text(it.source.value, true); text(it.raw, true) }
        knowledge(identity.printingId) { text(it.source.value, true); text(it.raw, true) }
        identity.raw?.let { spend(); text(it.provider.value, true); text(it.kind); text(it.rawId, true) }
        knowledge(identity.originalName) { text(it) }
        text(identity.language.value, true)
        knowledge(value.finish) { spend() }
        text(value.location); text(value.notes)
        knowledge(value.acquiredAt) { timestamp(it) }
        if (value.quantity < 0) reject("Invalid stored inventory quantity.")
    }
    /** Unicode scalar order equals unsigned strict UTF-8 order; inputs have already been validated. */
    fun compare(a: String, b: String): Int {
        var i = 0; var j = 0
        while (i < a.length && j < b.length) {
            spend(2)
            val ac = Character.codePointAt(a, i); val bc = Character.codePointAt(b, j)
            spend(utf8Width(ac) + utf8Width(bc))
            if (ac != bc) return ac.compareTo(bc)
            i += Character.charCount(ac); j += Character.charCount(bc)
        }
        spend(); return (a.length - i).compareTo(b.length - j)
    }
    private fun utf8Width(cp: Int): Int = when { cp < 0x80 -> 1; cp < 0x800 -> 2; cp < 0x10000 -> 3; else -> 4 }
    fun same(a: String, b: String): Boolean = compare(a, b) == 0
    /** Bounded, cancellable merge sort, including every reference copy. */
    fun <T> sorted(input: List<T>, comparator: Comparator<T>): List<T> {
        phase()
        var source = ArrayList<T>(input.size)
        for (value in input) { spend(); source.add(value) }
        var width = 1
        while (width < source.size) {
            val target = ArrayList<T>(source.size)
            var start = 0
            while (start < source.size) {
                val middle = minOf(start + width, source.size)
                val end = minOf(start + width * 2, source.size)
                var a = start; var b = middle
                while (a < middle || b < end) {
                    spend()
                    if (b == end || (a < middle && comparator.compare(source[a], source[b]) <= 0)) target.add(source[a++])
                    else target.add(source[b++])
                }
                start = end
            }
            source = target; width *= 2
        }
        return source
    }
    /** SnapshotList's defensive copy consumes this iterator, so its copies also remain cancellable. */
    fun <T> freeze(values: List<T>): SnapshotList<T> {
        phase()
        return SnapshotList.copyOf(Iterable {
            val iterator = values.iterator()
            object : Iterator<T> {
                override fun hasNext(): Boolean = iterator.hasNext()
                override fun next(): T { spend(); return iterator.next() }
            }
        })
    }
}
