package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class CatalogQueryCursorTest {
    private fun cursor() = CatalogQueryCursor("scryfall", "génération", "en", 1, 1, FaceMatchPolicy.ANY_FACE,
        "a".repeat(64), "éclair // 雪", "scryfall", "oracle-1")
    private fun rejected(action: () -> Unit) {
        try { action(); fail("Expected invalid cursor") } catch (_: Exception) { }
    }

    @Test fun canonicalRoundTripPreservesEveryFieldAndPolicy() {
        for (policy in FaceMatchPolicy.entries) {
            val expected = cursor().copy(facePolicy = policy)
            assertEquals(expected, CatalogQueryCursor.decode(expected.encode()))
            assertEquals(expected.encode(), CatalogQueryCursor.decode(expected.encode()).encode())
        }
    }

    @Test fun emptyNormalizedNameIsAnExactSortKey() {
        val expected = cursor().copy(name = "")
        assertEquals(expected, CatalogQueryCursor.decode(expected.encode()))
    }

    @Test fun rejectsNonCanonicalEncodingPaddingTrailingBytesAndTruncation() {
        val encoded = cursor().encode()
        val bytes = Base64.getUrlDecoder().decode(encoded)
        for (invalid in listOf("", encoded + "=", " $encoded", "!", encoded.dropLast(3),
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes + byteArrayOf(0))))
            rejected { CatalogQueryCursor.decode(invalid) }
    }

    @Test fun rejectsUnknownWireVersionAndPrintingPolicy() {
        val bytes = Base64.getUrlDecoder().decode(cursor().encode())
        for (old in listOf("catalog-query-1", "single-printing-1")) {
            val original = old.toByteArray()
            val start = bytes.indices.first { index -> index + original.size <= bytes.size && bytes.copyOfRange(index, index + original.size).contentEquals(original) }
            val changed = bytes.copyOf(); changed[start + original.lastIndex] = '2'.code.toByte()
            rejected { CatalogQueryCursor.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(changed)) }
        }
    }

    @Test fun rejectsInvalidUtf8AndNonCanonicalLengthPrefixes() {
        val bytes = Base64.getUrlDecoder().decode(cursor().encode())
        val malformed = bytes.copyOf(); malformed[4] = 0xff.toByte()
        val negative = bytes.copyOf(); negative[0] = 0xff.toByte()
        for (value in listOf(malformed, negative)) rejected {
            CatalogQueryCursor.decode(Base64.getUrlEncoder().withoutPadding().encodeToString(value))
        }
    }

    @Test fun rejectsInvalidBindingsHashesAndIdentityBudgets() {
        for (invalid in listOf(cursor().copy(provider = "foreign"), cursor().copy(language = "fr"),
            cursor().copy(projectionVersion = 2), cursor().copy(compilerVersion = 2), cursor().copy(lastProvider = "foreign"),
            cursor().copy(fingerprint = "A".repeat(64)), cursor().copy(fingerprint = "a".repeat(63)),
            cursor().copy(generation = "g".repeat(129)), cursor().copy(generation = "é".repeat(65)), cursor().copy(oracle = "o".repeat(129)),
            cursor().copy(oracle = "bad\u0000id"), cursor().copy(name = "bad\u0000name")))
            rejected { invalid.encode() }
    }

    @Test fun overflowIsExplicitAndNeverTruncatesNames() {
        rejected { cursor().copy(name = "x".repeat(2049)).encode() }
        rejected { cursor().copy(name = "x".repeat(2000)).encode() }
        rejected { cursor().copy(name = "雪".repeat(800)).encode() }
    }

    @Test fun encodingRejectsUnpairedSurrogates() {
        try { cursor().copy(name = "\ud800").encode(); fail("Expected strict Unicode rejection") }
        catch (_: java.nio.charset.CharacterCodingException) { }
    }
}
