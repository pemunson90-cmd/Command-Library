package com.architectpsg.commandlibrary.feature.catalog

import org.junit.Assert.*
import org.junit.Test

class SearchVerificationPositionTest {
    @Test fun exactCountsAndSignedWatermarksRoundTripIncludingBoundaryValues() {
        for (rowid in listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE)) {
            val counts = SearchVerificationCounts(Long.MAX_VALUE, 2, 3, Long.MAX_VALUE, 5)
            assertEquals(SearchVerificationPosition(counts, rowid), SearchVerificationPosition.decode(counts.encode(), rowid.toString()))
        }
        assertEquals(SearchVerificationPosition(), SearchVerificationPosition.decode("0,0,0,0,0", null))
    }

    @Test fun firstPageHasNoInventedZeroWatermarkOrCompletedCounts() {
        assertNull(SearchVerificationPosition().watermark)
        assertThrows(IllegalArgumentException::class.java) { SearchVerificationPosition.decode("1,0,0,0,0", null) }
        assertEquals(0L, SearchVerificationPosition.decode("0,0,0,0,0", "0").watermark)
    }

    @Test fun noncanonicalOverflowAndUnboundedCountsAreRejected() {
        for (input in listOf("", "0,0,0,0", "0,0,0,0,0,0", "01,0,0,0,0", "+1,0,0,0,0", "-1,0,0,0,0", "１,0,0,0,0", "1 ,0,0,0,0", "9223372036854775808,0,0,0,0", "1".repeat(10000)))
            assertThrows(input.take(80), IllegalArgumentException::class.java) { SearchVerificationPosition.decode(input, "1") }
        for (input in listOf("", "+1", "01", "-0", "-01", "1.0", "1e2", "１", " 1", "9223372036854775808", "-9223372036854775809", "1".repeat(10000)))
            assertThrows(input.take(80), IllegalArgumentException::class.java) { SearchVerificationPosition.decode("0,0,0,0,0", input) }
    }

    @Test fun countAdditionCannotWrapProgress() {
        assertEquals(SearchVerificationCounts(1, 2, 3, 4, 5), SearchVerificationCounts(1, 0, 3).add(SearchVerificationCounts(0, 2, 0, 4, 5)))
        assertThrows(ArithmeticException::class.java) { SearchVerificationCounts(texts = Long.MAX_VALUE).add(SearchVerificationCounts(texts = 1)) }
        assertThrows(IllegalArgumentException::class.java) { SearchVerificationCounts(faces = -1) }
    }
}
