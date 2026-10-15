package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import java.util.TreeMap
import java.util.LinkedList

/** The digest only selects candidates. Every match is proved by exact, budgeted field comparison. */
internal class InventoryProjectionIndex(private val work: InventoryProjectionWork) {
    class Group(val representative: OwnedLot, var original: OwnedLot?) {
        val incoming = LinkedList<OwnedLot>()
        var quantity = original?.quantity?.toLong() ?: 0L
    }
    private val buckets = TreeMap<String, LinkedList<Group>>(Comparator(work::compare))
    private val digest = MessageDigest.getInstance("SHA-256")
    private val buffer = ByteArray(InventoryProjectionLimits.CANCEL_CHECK_INTERVAL)
    private var used = 0
    val touched = ArrayList<Group>(InventoryProjectionLimits.MAX_EDITS)

    fun original(lot: OwnedLot) {
        work.spend()
        if (!proven(lot)) return
        val group = group(lot)
        val old = group.original
        if (old == null || work.compare(lot.id.value, old.id.value) < 0) group.original = lot
        // Existing duplicate rows are candidates only; their quantities are never collapsed.
        group.quantity = group.original!!.quantity.toLong()
    }
    fun add(lot: OwnedLot) {
        work.spend()
        val group = if (proven(lot)) group(lot) else Group(lot, null)
        if (group.incoming.isEmpty()) { work.spend(); touched.add(group) }
        work.spend(); group.incoming.add(lot); group.quantity += lot.quantity.toLong()
    }
    private fun proven(lot: OwnedLot): Boolean {
        work.spend(2)
        return lot.identity.printingId is Knowledge.Known && lot.finish is Knowledge.Known
    }
    private fun group(lot: OwnedLot): Group {
        val fingerprint = fingerprint(lot)
        val bucket = buckets[fingerprint]
        if (bucket != null) for (candidate in bucket) {
            work.spend(); work.candidate()
            if (equivalent(candidate.representative, lot)) return candidate
        }
        val result = Group(lot, null)
        work.spend()
        if (bucket == null) buckets[fingerprint] = LinkedList<Group>().also { it.add(result) } else bucket.add(result)
        return result
    }
    private fun scalar(equal: Boolean): Boolean { work.spend(); return equal }
    private fun time(a: UtcTimestamp, b: UtcTimestamp): Boolean =
        scalar(a.epochSeconds == b.epochSeconds) && scalar(a.nanoAdjustment == b.nanoAdjustment)
    private fun evidence(a: Evidence, b: Evidence): Boolean {
        work.spend()
        if (!work.same(a.description, b.description)) return false
        val x = a.source; val y = b.source
        if (x == null || y == null) { if (!scalar(x == null && y == null)) return false }
        else if (!work.same(x.value, y.value)) return false
        val at = a.recordedAt; val bt = b.recordedAt
        return if (at == null || bt == null) scalar(at == null && bt == null) else time(at, bt)
    }
    private fun <T> knowledge(a: Knowledge<T>, b: Knowledge<T>, known: (T, T) -> Boolean): Boolean {
        work.spend()
        return when (a) {
            is Knowledge.Known -> b is Knowledge.Known && known(a.value, b.value)
            is Knowledge.Absent -> b is Knowledge.Absent && work.same(a.reason, b.reason)
            is Knowledge.Unknown -> {
                if (b !is Knowledge.Unknown || !work.same(a.reason, b.reason) || !scalar(a.evidence.size == b.evidence.size)) false
                else {
                    var same = true
                    for (i in a.evidence.indices) {
                        work.spend()
                        if (!evidence(a.evidence[i], b.evidence[i])) { same = false; break }
                    }
                    same
                }
            }
        }
    }
    private fun equivalent(a: OwnedLot, b: OwnedLot): Boolean {
        work.spend()
        val x = a.identity; val y = b.identity
        if (!knowledge(x.oracleId, y.oracleId) { p, q -> work.same(p.source.value, q.source.value) && work.same(p.raw, q.raw) }) return false
        if (!knowledge(x.printingId, y.printingId) { p, q -> work.same(p.source.value, q.source.value) && work.same(p.raw, q.raw) }) return false
        val rawA = x.raw; val rawB = y.raw
        if (rawA == null || rawB == null) { if (!scalar(rawA == null && rawB == null)) return false }
        else if (!work.same(rawA.provider.value, rawB.provider.value) || !work.same(rawA.kind, rawB.kind) || !work.same(rawA.rawId, rawB.rawId)) return false
        return knowledge(x.originalName, y.originalName, work::same) && work.same(x.language.value, y.language.value) &&
            knowledge(a.finish, b.finish) { p, q -> scalar(p == q) } && scalar(a.condition == b.condition) &&
            work.same(a.location, b.location) && work.same(a.notes, b.notes) && knowledge(a.acquiredAt, b.acquiredAt, ::time)
    }
    private fun byte(value: Int) {
        work.spend(); buffer[used++] = value.toByte()
        if (used == buffer.size) flush()
    }
    private fun flush() {
        if (used > 0) { work.phase(); work.spend(used); digest.update(buffer, 0, used); used = 0 }
    }
    private fun number(value: Long) { for (shift in 56 downTo 0 step 8) byte((value ushr shift).toInt()) }
    private fun text(value: String) {
        number(value.length.toLong())
        var i = 0
        while (i < value.length) {
            val cp = Character.codePointAt(value, i)
            work.spend(Character.charCount(cp)); i += Character.charCount(cp)
            when {
                cp < 0x80 -> byte(cp)
                cp < 0x800 -> { byte(0xc0 or (cp ushr 6)); byte(0x80 or (cp and 63)) }
                cp < 0x10000 -> { byte(0xe0 or (cp ushr 12)); byte(0x80 or ((cp ushr 6) and 63)); byte(0x80 or (cp and 63)) }
                else -> { byte(0xf0 or (cp ushr 18)); byte(0x80 or ((cp ushr 12) and 63)); byte(0x80 or ((cp ushr 6) and 63)); byte(0x80 or (cp and 63)) }
            }
        }
    }
    private fun time(value: UtcTimestamp) { number(value.epochSeconds); number(value.nanoAdjustment.toLong()) }
    private fun <T> knowledge(value: Knowledge<T>, known: (T) -> Unit) {
        work.spend()
        when (value) {
            is Knowledge.Known -> { byte(1); known(value.value) }
            is Knowledge.Absent -> { byte(2); text(value.reason) }
            is Knowledge.Unknown -> {
                byte(3); text(value.reason); number(value.evidence.size.toLong())
                for (e in value.evidence) {
                    work.spend(); text(e.description)
                    val uri = e.source
                    if (uri == null) byte(0) else { byte(1); text(uri.value) }
                    val at = e.recordedAt
                    if (at == null) byte(0) else { byte(1); time(at) }
                }
            }
        }
    }
    private fun fingerprint(lot: OwnedLot): String {
        work.phase(); digest.reset(); used = 0
        val identity = lot.identity
        knowledge(identity.oracleId) { text(it.source.value); text(it.raw) }
        knowledge(identity.printingId) { text(it.source.value); text(it.raw) }
        val raw = identity.raw
        if (raw == null) byte(0) else { byte(1); text(raw.provider.value); text(raw.kind); text(raw.rawId) }
        knowledge(identity.originalName, ::text); text(identity.language.value)
        knowledge(lot.finish) { number(it.ordinal.toLong()) }; number(lot.condition.ordinal.toLong())
        text(lot.location); text(lot.notes); knowledge(lot.acquiredAt, ::time)
        flush(); work.phase(); val bytes = digest.digest()
        val chars = CharArray(64)
        val alphabet = "0123456789abcdef"
        for (i in bytes.indices) { work.spend(3); chars[i * 2] = alphabet[(bytes[i].toInt() ushr 4) and 15]; chars[i * 2 + 1] = alphabet[bytes[i].toInt() and 15] }
        work.spend(64); return chars.concatToString()
    }
}
