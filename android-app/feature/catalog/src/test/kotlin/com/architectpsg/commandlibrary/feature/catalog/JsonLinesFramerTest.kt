package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.ByteChunk
import com.architectpsg.commandlibrary.core.model.CancelToken
import com.architectpsg.commandlibrary.core.model.SnapshotBytes
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class JsonLinesFramerTest {
    private class Token : CancelToken {
        var cancelled = false
        override val isCancelled: Boolean get() = cancelled
        override fun throwIfCancelled() { if (cancelled) throw kotlinx.coroutines.CancellationException() }
    }
    private val defaults = JsonLinesLimits(1_000_000, 200_000, 10_000, 32)
    private fun chunk(bytes: ByteArray, sequence: Long = 0, offset: Long = 0, final: Boolean = true) =
        ByteChunk(sequence, offset, SnapshotBytes(bytes), null, final)

    private suspend fun frame(input: ByteArray, limits: JsonLinesLimits = defaults, width: Int = 7): List<String> {
        val framer = JsonLinesFramer(limits, Token())
        val rows = mutableListOf<String>()
        input.asList().chunked(width).forEachIndexed { index, part ->
            framer.accept(chunk(part.toByteArray(), index.toLong(), (index * width).toLong(),
                index == (input.size - 1) / width)) { rows.add(it) }
        }
        framer.finish()
        assertEquals(input.size.toLong(), framer.observedBytes)
        assertEquals(rows.size.toLong(), framer.recordCount)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(input).joinToString("") { "%02x".format(it) }, framer.sha256)
        return rows
    }

    @Test fun genuineThreeObjectsDerivedAsJsonlAcrossBytes(): Unit = runBlocking {
        // The checked-in JSON array remains the genuine fixture; only its framing is transformed.
        val source = javaClass.getResourceAsStream("/english-three-printings.json")!!.readBytes().toString(Charsets.UTF_8)
        val elements = com.google.gson.JsonParser.parseString(source).asJsonArray.map { it.toString() }
        val input = elements.joinToString("\r\n", postfix = "\n").toByteArray(Charsets.UTF_8)
        assertEquals(3, frame(input, width = 1).size)
    }

    @Test fun acceptsLfCrLfAndUnterminatedLastLineWithSplitUtf8(): Unit = runBlocking {
        val input = "{\"name\":\"É\"}\r\n{\"n\":1}\n{\"n\":2}".toByteArray(Charsets.UTF_8)
        assertEquals(3, frame(input, width = 1).size)
        assertEquals(1, frame("{}".toByteArray(), width = 1).size)
        assertEquals(1, frame("{}\r\n".toByteArray(), width = 1).size)
    }

    @Test fun rejectsMalformedLines(): Unit = runBlocking {
        val texts = listOf("", "\n", "{}\n\n", "{}\r", "{}\rX", "\uFEFF{}", "[]", "42", "{} trailing", "{\"a\":1,\"a\":2}", "{\"a\":[[0]]}")
        for (value in texts) {
            val limit = if (value.contains("[[")) defaults.copy(maxNesting = 2) else defaults
            assertThrows("Input: $value", Exception::class.java) { runBlocking { frame(value.toByteArray(), limit, width = 1) } }
        }
        for (bad in listOf(byteArrayOf(123, 34, 120, 34, 58, 34, -1, 34, 125),
            byteArrayOf(123, 34, 120, 34, 58, 34, -61, 34, 125))) {
            assertThrows(Exception::class.java) { runBlocking { frame(bad, width = 1) } }
        }
    }

    @Test fun inclusiveBoundsAndOneOver(): Unit = runBlocking {
        val input = "{}\n{}".toByteArray()
        assertEquals(2, frame(input, JsonLinesLimits(5, 2, 2, 1)).size)
        for (limits in listOf(JsonLinesLimits(4, 2, 2, 1), JsonLinesLimits(5, 1, 2, 1),
            JsonLinesLimits(5, 2, 1, 1))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { frame(input, limits) } }
        }
        assertEquals(1, frame("{\"x\":{\"y\":0}}".toByteArray(), defaults.copy(maxNesting = 2)).size)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { frame("{\"x\":{\"y\":0}}".toByteArray(), defaults.copy(maxNesting = 1)) }
        }
    }

    @Test fun enforcesChunkContinuityAndFinal(): Unit = runBlocking {
        val f = JsonLinesFramer(defaults, Token())
        f.accept(chunk("{}".toByteArray(), final = false)) {}
        assertThrows(IllegalArgumentException::class.java) { f.finish() }
        for (bad in listOf(chunk(byteArrayOf(), 0, 2), chunk(byteArrayOf(), 1, 1))) {
            assertThrows(IllegalArgumentException::class.java) { runBlocking { f.accept(bad) {} } }
        }
        f.accept(chunk(byteArrayOf(), 1, 2)) {}
        f.finish()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { f.accept(chunk(byteArrayOf(), 2, 2)) {} } }
    }

    @Test fun callbackBackpressureAndCancellation(): Unit = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val token = Token()
        val f = JsonLinesFramer(defaults, token)
        val seen = mutableListOf<String>()
        val job = async {
            f.accept(chunk("{}\n{}".toByteArray())) { row ->
                seen.add(row)
                if (seen.size == 1) { entered.complete(Unit); release.await() }
            }
        }
        entered.await()
        assertEquals(1, seen.size)
        assertEquals(3, f.observedBytes)
        release.complete(Unit)
        job.await()
        assertEquals(2, seen.size)
        f.finish()
        val cancelled = Token().also { it.cancelled = true }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            runBlocking { JsonLinesFramer(defaults, cancelled).accept(chunk("{}".toByteArray())) {} }
        }
        yield()
    }

    @Test fun syntheticManyRecordsUseOneCallbackAtATime(): Unit = runBlocking {
        val f = JsonLinesFramer(JsonLinesLimits(30_000, 2, 10_000, 1), Token())
        var count = 0
        val data = "{}\n".repeat(9_000).toByteArray()
        data.asList().chunked(91).forEachIndexed { index, part ->
            f.accept(chunk(part.toByteArray(), index.toLong(), (index * 91).toLong(),
                index == (data.size - 1) / 91)) { count++ }
        }
        f.finish()
        assertEquals(9_000, count)
    }
}
