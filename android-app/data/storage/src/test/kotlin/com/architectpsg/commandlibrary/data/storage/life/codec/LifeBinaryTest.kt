package com.architectpsg.commandlibrary.data.storage.life.codec

import org.junit.Assert.*
import org.junit.Test

class LifeBinaryTest {
    @Test fun invalidBooleanEnumAndListTagsFailBeforeValuesAreRead() {
        assertThrows(IllegalArgumentException::class.java) { LifeReader(byteArrayOf(2)).bool() }
        assertThrows(IllegalArgumentException::class.java) { LifeReader(byteArrayOf(0)).v(enumeration("one", "two")) }
        assertThrows(IllegalArgumentException::class.java) { LifeReader(byteArrayOf(3)).v(enumeration("one", "two")) }
        val writer = LifeWriter(10); writer.int(Int.MAX_VALUE)
        assertThrows(IllegalArgumentException::class.java) { LifeReader(writer.finish()).v(list(intWire)) }
        val negative = LifeWriter(10); negative.int(-1)
        assertThrows(IllegalArgumentException::class.java) { LifeReader(negative.finish()).v(list(intWire)) }
    }
    @Test fun noncanonicalOrOverflowingDurationsCannotRoundSilently() {
        for ((seconds, nanos) in listOf(1L to -1, -1L to 1, 0L to 1_000_000_000,
            Long.MAX_VALUE to 0, 4_611_686_018L to 427_000_001)) {
            val writer = LifeWriter(12); writer.long(seconds); writer.int(nanos)
            assertThrows(IllegalArgumentException::class.java) { LifeReader(writer.finish()).v(durationWire) }
        }
    }
    @Test fun utf8BudgetCountsBytesAndRejectsUnpairedSurrogates() {
        assertThrows(IllegalArgumentException::class.java) { LifeWriter(200_000).string("é".repeat(32_769)) }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) { LifeWriter(100).string("\uD800") }
    }
}
