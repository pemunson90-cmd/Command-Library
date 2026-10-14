package com.architectpsg.commandlibrary.core.common.compatibility

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.util.CSVFieldNumDifferentException
import com.github.doyaaaaaken.kotlincsv.util.MalformedCSVException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

/** Published-library probes, not TransferService or production import validation. */
class CsvCompatibilityProbeTest {
    @Test fun quotedCommaNewlineAndEscapedQuoteRemainOneField() {
        assertEquals(listOf(listOf("name", "note"), listOf("é,猫", "line1\nline2 \"yes\"")),
            csvReader().readAll("name,note\n\"é,猫\",\"line1\nline2 \"\"yes\"\"\""))
    }

    @Test fun utf8BomIsRemovedByStreamApi() {
        val bytes = "\uFEFFname,count\r\n猫,2".toByteArray(Charsets.UTF_8)
        bytes.inputStream().use { input ->
            assertEquals(listOf(listOf("name", "count"), listOf("猫", "2")), csvReader().readAll(input))
        }
    }

    @Test fun truncatedQuoteIsAnErrorRatherThanRowLoss() {
        assertThrows(MalformedCSVException::class.java) { csvReader().readAll("a,b\n1,\"unfinished") }
    }

    @Test fun mismatchedRowsAreErrorsRatherThanIgnoredOrTrimmed() {
        for (input in listOf("a,b\n1\n2,3", "a,b\n1,2,3\n4,5")) {
            assertThrows(CSVFieldNumDifferentException::class.java) { csvReader().readAll(input) }
        }
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2"), listOf("3", "4")),
            csvReader().readAll("a,b\n1,2\n3,4"))
    }

    @Test fun earlyStopDoesNotParseMalformedLaterRowAndClosesStream() {
        val input = TrackedInput("a,b\nc,\"truncated")
        val first = csvReader().open(input) { readAllAsSequence().take(1).toList() }
        assertEquals(listOf(listOf("a", "b")), first)
        assertTrue(input.closed)
        assertThrows(MalformedCSVException::class.java) { csvReader().readAll("a,b\nc,\"truncated") }
    }

    @Test fun cancellationAtCallerBoundaryStopsBeforeNextRowAndClosesStream() {
        val job = Job()
        val input = TrackedInput("a,b\nc,d\ne,f")
        var delivered = 0
        assertThrows(CancellationException::class.java) {
            csvReader().open(input) {
                val rows = readAllAsSequence().iterator()
                job.ensureActive()
                assertEquals(listOf("a", "b"), rows.next())
                delivered++
                job.cancel()
                job.ensureActive() // Explicit app boundary; library is a synchronous reader.
                rows.next()
                delivered++
            }
        }
        assertEquals(1, delivered)
        assertTrue(input.closed)
    }

    private class TrackedInput(text: String) : java.io.ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun libraryHasNoFieldBoundButTestOnlyBoundaryRejectsOversize() {
        assertEquals(listOf(listOf("12345", "x")), csvReader().readAll("12345,x"))
        assertThrows(IllegalArgumentException::class.java) { boundedRows("12345,x", maxField = 4) }
        assertEquals(listOf(listOf("1234", "x")), boundedRows("1234,x", maxField = 4))
    }

    @Test fun rowAndInputBoundsRejectInsteadOfSilentlyTruncating() {
        assertEquals(listOf(listOf("a"), listOf("b")), boundedRows("a\nb", maxRows = 2))
        assertThrows(IllegalArgumentException::class.java) { boundedRows("a\nb\nc", maxRows = 2) }
        assertEquals(listOf(listOf("1234")), boundedRows("1234", maxChars = 4))
        assertThrows(IllegalArgumentException::class.java) { boundedRows("12345", maxChars = 4) }
    }

    // Finite test-only envelope: raw input capped before parser allocation; fields/rows
    // checked after parsing. Not a streaming byte limit or a production importer.
    private fun boundedRows(input: String, maxChars: Int = 64, maxField: Int = 16, maxRows: Int = 4): List<List<String>> {
        require(input.length <= maxChars) { "input character bound" }
        val accepted = mutableListOf<List<String>>()
        csvReader().open(input.byteInputStream()) {
          for (row in readAllAsSequence()) {
            require(accepted.size < maxRows) { "row bound" }
            require(row.all { it.length <= maxField }) { "field character bound" }
            accepted += row
          }
        }
        return accepted
    }
}
