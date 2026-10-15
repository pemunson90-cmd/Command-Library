package com.architectpsg.commandlibrary.feature.transfer

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayOutputStream
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

/** Application-owned quantity/name/board projection; does not resolve or import identities. */
public class PlainTextDeckListCodec : PlainDeckListCodec {
    public override suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList> {
        val work = Work(cancel, currentCoroutineContext())
        return try {
            work.check()
            textBytes(input.displayName, PlainDeckListLimits.MAX_DISPLAY_NAME_BYTES, work)
            textBytes(input.id.value, PlainDeckListLimits.MAX_IDENTIFIER_BYTES, work)
            val expected = (input.expectedBytes as? Knowledge.Known)?.value
            requireInput(expected == null || expected in 0L..PlainDeckListLimits.MAX_BYTES.toLong())
            val parser = Parser(input.displayName, work)
            var count = 0L
            var sequence = 0L
            var finalSeen = false
            input.chunks.catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception) throw ReadFailure()
                throw error
            }.collect { chunk ->
                work.check()
                requireInput(!finalSeen && chunk.sequence == sequence && chunk.offset == count)
                val size = chunk.bytes.size
                requireInput(chunk.final || size > 0)
                requireInput(size.toLong() <= PlainDeckListLimits.MAX_BYTES - count)
                val bytes = chunk.bytes.copyBytes() // bounded before the defensive native copy
                work.check()
                for (byte in bytes) {
                    work.visit()
                    parser.byte(byte.toInt() and 255, count)
                    count++
                }
                finalSeen = chunk.final
                sequence++ // at most MAX_BYTES nonempty frames plus one empty final
            }
            work.check()
            requireInput(finalSeen && (expected == null || expected == count))
            parser.finish()
            if (parser.futureVersion) {
                Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported application deck list version")
            } else {
                val result = PlainDeckList(input.id, snapshot(parser.rows, work), parser.complete)
                work.check()
                Outcome.Success(result)
            }
        } catch (error: InvalidInput) {
            invalid()
        } catch (error: ReadFailure) {
            Outcome.Failure(DomainFailure(FailureCode.TRANSPORT, "Unable to read deck list", SnapshotList.empty()))
        }
    }

    public override fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport> {
        val work = Work(cancel)
        return try {
            work.check()
            requireInput(deck.entries.size <= PlainDeckListLimits.MAX_ENTRIES)
            val groups = List(7) { ArrayList<DeckEntry>() }
            val ids = HashMap<Long, MutableList<String>>()
            var outputSize = PlainDeckListLimits.MARKER.length + 1L
            var highest = -1
            var reordered = false
            for (entry in deck.entries) {
                work.visit()
                val id = entry.id.value
                textBytes(id, PlainDeckListLimits.MAX_IDENTIFIER_BYTES, work)
                var hash = -3750763034362895579L
                for (char in id) { work.visit(); hash = (hash xor char.code.toLong()) * 1099511628211L }
                val candidates = ids.getOrPut(hash) { ArrayList() }
                for (candidate in candidates) { work.visit(); requireInput(!same(candidate, id, work)) }
                candidates.add(id)
                val name = (entry.identity.originalName as? Knowledge.Known)?.value ?: throw InvalidInput()
                requireInput(name.isNotEmpty() && name[0] != ' ' && name[0] != '\t')
                val nameBytes = textBytes(name, PlainDeckListLimits.MAX_NAME_BYTES, work, singleLine = true)
                requireInput(!blank(name, work))
                val rank = rank(entry.board)
                if (rank < highest) reordered = true
                highest = maxOf(highest, rank)
                if (groups[rank].isEmpty()) outputSize += headings[rank].length + 1L
                outputSize += entry.quantity.toString().length + 2L + nameBytes
                requireInput(outputSize <= PlainDeckListLimits.MAX_BYTES)
                groups[rank].add(entry)
            }
            val losses = ArrayList<PlainDeckListLoss>()
            fun loss(field: PlainDeckListLossField, id: EntityId? = null) {
                work.visit()
                requireInput(losses.size < PlainDeckListLimits.MAX_LOSSES)
                losses.add(PlainDeckListLoss(id, field))
            }
            loss(PlainDeckListLossField.DECK_ID)
            loss(PlainDeckListLossField.DECK_NAME)
            loss(PlainDeckListLossField.FORMAT)
            if (deck.description.isNotEmpty()) loss(PlainDeckListLossField.DESCRIPTION)
            loss(PlainDeckListLossField.FOLDER)
            if (deck.tags.isNotEmpty()) loss(PlainDeckListLossField.TAGS)
            loss(PlainDeckListLossField.CREATED_AT)
            loss(PlainDeckListLossField.MODIFIED_AT)
            loss(PlainDeckListLossField.REVISION)
            loss(PlainDeckListLossField.BUDGET)
            if (deck.profileId != null) loss(PlainDeckListLossField.PROFILE_ID)
            if (deck.catalog.isNotEmpty()) loss(PlainDeckListLossField.CATALOG_REFERENCES)
            if (deck.packageInstances.isNotEmpty()) loss(PlainDeckListLossField.PACKAGE_INSTANCES)
            if (deck.primer.isNotEmpty()) loss(PlainDeckListLossField.PRIMER)
            if (deck.categoryOrder != null) loss(PlainDeckListLossField.CATEGORY_ORDER)
            if (reordered) loss(PlainDeckListLossField.ENTRY_ORDER)
            for (entry in deck.entries) {
                work.visit()
                loss(PlainDeckListLossField.ENTRY_ID, entry.id)
                loss(PlainDeckListLossField.ORACLE_ID, entry.id)
                loss(PlainDeckListLossField.PRINTING_ID, entry.id)
                if (entry.identity.raw != null) loss(PlainDeckListLossField.RAW_IDENTITY, entry.id)
                loss(PlainDeckListLossField.LANGUAGE, entry.id)
                loss(PlainDeckListLossField.FINISH, entry.id)
                if (entry.notes.isNotEmpty()) loss(PlainDeckListLossField.NOTES, entry.id)
                if (entry.labels.isNotEmpty()) loss(PlainDeckListLossField.LABELS, entry.id)
                if (entry.roleOverrides.isNotEmpty()) loss(PlainDeckListLossField.ROLE_OVERRIDES, entry.id)
            }
            val output = ByteArrayOutputStream(outputSize.toInt())
            fun line(text: String) { encode(text, output, work); output.write(10); work.visit() }
            line(PlainDeckListLimits.MARKER)
            for (index in groups.indices) {
                work.visit()
                if (groups[index].isNotEmpty()) {
                    line(headings[index])
                    for (entry in groups[index]) {
                        work.visit()
                        encode(entry.quantity.toString(), output, work)
                        output.write(32)
                        line((entry.identity.originalName as Knowledge.Known).value)
                    }
                }
            }
            work.check()
            val bytes = SnapshotBytes(output.toByteArray())
            val result = PlainDeckListExport(bytes, snapshot(losses, work))
            work.check()
            Outcome.Success(result)
        } catch (error: InvalidInput) { invalid() }
    }
}

private class InvalidInput : RuntimeException()
private class ReadFailure : RuntimeException()
private fun requireInput(value: Boolean) { if (!value) throw InvalidInput() }
private fun invalid(): Outcome.Failure = Outcome.Failure(
    DomainFailure(FailureCode.INVALID_INPUT, "Invalid application deck list", SnapshotList.empty()),
)

/** Observable Kotlin visits are polled at most 1024 apart. Native bounded copies are not interruptible. */
private class Work(private val cancel: CancelToken, private val context: CoroutineContext? = null) {
    private var remaining = 1024
    fun visit() { if (--remaining == 0) { check(); remaining = 1024 } }
    fun check() { cancel.throwIfCancelled(); context?.ensureActive() }
}
private fun <T> snapshot(values: List<T>, work: Work): SnapshotList<T> = SnapshotList.copyOf(
    Iterable {
        val iterator = values.iterator()
        object : Iterator<T> {
            override fun hasNext(): Boolean = iterator.hasNext()
            override fun next(): T { work.visit(); return iterator.next() }
        }
    },
)
private fun same(a: String, b: String, work: Work): Boolean {
    if (a.length != b.length) return false
    for (index in a.indices) { work.visit(); if (a[index] != b[index]) return false }
    return true
}
private fun blank(value: String, work: Work): Boolean {
    for (char in value) { work.visit(); if (!char.isWhitespace()) return false }
    return true
}
private fun textBytes(value: String, limit: Int, work: Work, singleLine: Boolean = false): Int {
    var size = 0
    var index = 0
    while (index < value.length) {
        work.visit()
        val char = value[index++]
        if (singleLine) requireInput(char != '\r' && char != '\n')
        size += when {
            char.code < 128 -> 1
            char.code < 2048 -> 2
            char.isHighSurrogate() -> {
                requireInput(index < value.length && value[index].isLowSurrogate())
                work.visit(); index++; 4
            }
            char.isLowSurrogate() -> throw InvalidInput()
            else -> 3
        }
        requireInput(size <= limit)
    }
    return size
}
private fun encode(value: String, output: ByteArrayOutputStream, work: Work) {
    var index = 0
    while (index < value.length) {
        work.visit()
        val char = value[index++]
        val cp = if (char.isHighSurrogate()) {
            work.visit(); Character.toCodePoint(char, value[index++])
        } else char.code
        when {
            cp < 128 -> output.write(cp)
            cp < 2048 -> { output.write(192 or (cp shr 6)); output.write(128 or (cp and 63)) }
            cp < 65536 -> {
                output.write(224 or (cp shr 12)); output.write(128 or ((cp shr 6) and 63)); output.write(128 or (cp and 63))
            }
            else -> {
                output.write(240 or (cp shr 18)); output.write(128 or ((cp shr 12) and 63))
                output.write(128 or ((cp shr 6) and 63)); output.write(128 or (cp and 63))
            }
        }
    }
}
private val headings = arrayOf("Commander", "Partner", "Background", "Companion", "Deck", "Sideboard", "Maybeboard")
private val boards = arrayOf(BoardRole.COMMANDER, BoardRole.PARTNER, BoardRole.BACKGROUND, BoardRole.COMPANION, BoardRole.MAIN, BoardRole.SIDEBOARD, BoardRole.MAYBEBOARD)
private fun rank(board: BoardRole): Int = when (board) {
    BoardRole.COMMANDER -> 0; BoardRole.PARTNER -> 1; BoardRole.BACKGROUND -> 2
    BoardRole.COMPANION -> 3; BoardRole.MAIN -> 4; BoardRole.SIDEBOARD -> 5; BoardRole.MAYBEBOARD -> 6
}

private class Parser(private val source: String, private val work: Work) {
    val rows = ArrayList<PlainDeckListRow>()
    var complete = true
        private set
    var futureVersion = false
        private set
    private var board = BoardRole.MAIN
    private var entries = 0
    private var physicalLines = 0
    private var line = StringBuilder()
    private var lineBytes = 0
    private var lineOffset = 0L
    private var pendingCr = false
    private var remaining = 0
    private var scalar = 0
    private var minimum = 0
    private var width = 0
    private var scalarOffset = 0L

    fun byte(byte: Int, offset: Long) {
        if (remaining == 0) {
            scalarOffset = offset
            when (byte) {
                in 0..127 -> { scalar = byte; width = 1 }
                in 194..223 -> { scalar = byte and 31; remaining = 1; width = 2; minimum = 128; return }
                in 224..239 -> { scalar = byte and 15; remaining = 2; width = 3; minimum = 2048; return }
                in 240..244 -> { scalar = byte and 7; remaining = 3; width = 4; minimum = 65536; return }
                else -> throw InvalidInput()
            }
        } else {
            requireInput(byte in 128..191)
            scalar = (scalar shl 6) or (byte and 63)
            remaining--
            if (remaining > 0) return
            requireInput(scalar >= minimum && scalar <= 0x10ffff && scalar !in 0xd800..0xdfff)
        }
        if (scalar == 0xfeff && scalarOffset == 0L) return
        if (pendingCr) requireInput(scalar == 10)
        when (scalar) {
            13 -> pendingCr = true
            10 -> { emit(); pendingCr = false; lineOffset = offset + 1L }
            else -> {
                lineBytes += width
                requireInput(lineBytes <= PlainDeckListLimits.MAX_LINE_BYTES)
                line.appendCodePoint(scalar)
            }
        }
    }
    fun finish() {
        requireInput(remaining == 0 && !pendingCr)
        if (line.isNotEmpty()) emit()
    }
    private fun emit() {
        requireInput(physicalLines < PlainDeckListLimits.MAX_LINES)
        physicalLines++
        work.check()
        // A future grammar is opaque; only physical encoding/framing/admission still applies.
        if (futureVersion) {
            line = StringBuilder()
            lineBytes = 0
            return
        }
        val raw = line.toString()
        line = StringBuilder()
        lineBytes = 0
        val location = RowLocation(source, physicalLines.toLong(), lineOffset, null)
        var entry: PlainDeckListEntry? = null
        var code: String? = null
        when {
            raw.startsWith("Command Library Deck List") -> {
                val prefix = "Command Library Deck List "
                var canonical = raw.startsWith(prefix) && raw.length > prefix.length
                if (canonical) {
                    for (index in prefix.length until raw.length) {
                        work.visit()
                        if (raw[index] !in '0'..'9' || (index == prefix.length && raw[index] == '0')) canonical = false
                    }
                }
                if (rows.isEmpty() && canonical) {
                    if (raw != PlainDeckListLimits.MARKER) futureVersion = true
                } else code = PlainDeckListIssueCodes.INVALID_MARKER
            }
            blank(raw, work) -> Unit
            else -> {
                var heading = -1
                for (index in headings.indices) { work.visit(); if (raw == headings[index]) heading = index }
                if (heading >= 0) board = boards[heading]
                else {
                    var end = 0
                    while (end < raw.length && raw[end] != ' ' && raw[end] != '\t') { work.visit(); end++ }
                    var nameStart = end
                    while (nameStart < raw.length && (raw[nameStart] == ' ' || raw[nameStart] == '\t')) { work.visit(); nameStart++ }
                    var quantity = 0
                    var valid = end > 0 && raw[0] in '1'..'9'
                    for (index in 0 until end) {
                        work.visit()
                        val digit = raw[index].code - 48
                        if (digit !in 0..9 || quantity > (Int.MAX_VALUE - digit) / 10) valid = false
                        if (valid) quantity = quantity * 10 + digit
                    }
                    if (valid && nameStart < raw.length) {
                        val name = raw.substring(nameStart)
                        textBytes(name, PlainDeckListLimits.MAX_NAME_BYTES, work)
                        if (!blank(name, work)) {
                            requireInput(entries < PlainDeckListLimits.MAX_ENTRIES)
                            entries++
                            entry = PlainDeckListEntry(quantity, name, board)
                        }
                    }
                    if (entry == null) code = if (raw[0].isDigit() || raw[0] == '+' || raw[0] == '-') {
                        PlainDeckListIssueCodes.INVALID_QUANTITY
                    } else PlainDeckListIssueCodes.INVALID_ROW
                }
            }
        }
        val issues = if (code == null) SnapshotList.empty() else {
            complete = false
            SnapshotList.of(ImportIssue(location, code, IssueSeverity.ERROR, when (code) {
                PlainDeckListIssueCodes.INVALID_MARKER -> "Invalid application deck list marker"
                PlainDeckListIssueCodes.INVALID_QUANTITY -> "Expected a canonical positive quantity and name"
                else -> "Unrecognized deck list row"
            }, Knowledge.Absent("No automatic resolution")))
        }
        rows.add(PlainDeckListRow(location, raw, entry, issues))
    }
}
