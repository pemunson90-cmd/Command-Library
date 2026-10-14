package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import org.junit.Assert.*
import org.junit.Test

/** Synthetic metadata; no live provider response or official checksum is asserted. */
class ScryfallRulingsMetadataCodecTest {
    private val id = "11111111-2222-3333-4444-555555555555"
    private fun metadata(extra: String = "", uri: String = "https://data.scryfall.io/rulings/synthetic-20261013.jsonl.gz") = """{
        "object":"bulk_data","type":"rulings","id":"$id",
        "uri":"https://api.scryfall.com/bulk-data/$id",
        "updated_at":"2026-10-13T12:34:56.123456789+00:00",
        "jsonl_download_uri":"$uri","compressed_size":2147483648,
        "content_type":"application/json","content_encoding":"gzip"$extra
    }"""
    private fun rejected(raw: String) { assertThrows(IllegalArgumentException::class.java) { ScryfallRulingsMetadataCodec.decode(raw) } }

    @Test fun singleObjectRetainsExactSourceFactsWithoutUsingSizeOrChecksum() {
        val candidate = ScryfallRulingsMetadataCodec.decode(metadata(",\"size\":9,\"sha256\":\"unverified\""))
        assertEquals(id, candidate.bulkId.value)
        assertEquals("https://api.scryfall.com/bulk-data/$id", candidate.metadataUri.value)
        assertEquals("https://data.scryfall.io/rulings/synthetic-20261013.jsonl.gz", candidate.downloadUri.value)
        assertEquals(2147483648L, candidate.compressedBytes)
        assertEquals(UtcTimestamp(1791894896, 123456789), candidate.updatedAt)
        assertEquals("gzip", candidate.contentEncoding)
    }
    @Test fun listSelectionUsesExactTypeAndRequiresExactlyOneCandidate() {
        val other = metadata().replace("\"rulings\"", "\"oracle_cards\"")
        assertEquals(id, ScryfallRulingsMetadataCodec.decode("""{"object":"list","data":[$other,${metadata()}]}""").bulkId.value)
        rejected("""{"object":"list","data":[$other]}""")
        rejected("""{"object":"list","data":[${metadata()},${metadata()}]}""")
        rejected(metadata().replace("\"rulings\"", "\"Rulings\""))
        rejected(metadata().replace("\"bulk_data\"", "\"card\""))
    }
    @Test fun downloadUrisRejectUntrustedOriginAndAmbiguousRepresentations() {
        for (uri in listOf("http://data.scryfall.io/rulings/a.jsonl.gz", "https://data.scryfall.io.evil/rulings/a.jsonl.gz",
            "https://evil.scryfall.io/rulings/a.jsonl.gz", "https://u:p@data.scryfall.io/rulings/a.jsonl.gz",
            "https://data.scryfall.io:444/rulings/a.jsonl.gz", "https://data.scryfall.io/rulings/a.jsonl.gz#x",
            "https://data.scryfall.io/rulings/a.jsonl.gz?x=1", "https://data.scryfall.io/rulings/%61.jsonl.gz",
            "https://data.scryfall.io/rulings/../a.jsonl.gz", "https://data.scryfall.io/cards/a.jsonl.gz",
            "https://data.scryfall.io/rulings/a.json")) rejected(metadata(uri = uri))
        assertTrue(ScryfallRulingsMetadataCodec.decode(metadata(uri = "https://data.scryfall.io:443/rulings/a.jsonl.gz")).downloadUri.value.contains(":443/"))
    }
    @Test fun exactCompressedSizeIsMandatoryPositiveIntegralAndBounded() {
        for (size in listOf("0", "-1", "1.5", "2147483649", "9223372036854775808", "\"123\"", "null")) {
            rejected(metadata().replace("2147483648", size))
        }
        rejected(metadata().replace("\"compressed_size\":2147483648,", ""))
        rejected(metadata().replace("jsonl_download_uri", "download_uri"))
        assertEquals(1L, ScryfallRulingsMetadataCodec.decode(metadata().replace("2147483648", "1")).compressedBytes)
    }
    @Test fun timestampsAreStrictAndOffsetConversionRetainsNanoseconds() {
        val raw = metadata().replace("2026-10-13T12:34:56.123456789+00:00", "2026-10-13T14:34:56.123456789+02:00")
        assertEquals(ScryfallRulingsMetadataCodec.decode(metadata()).updatedAt, ScryfallRulingsMetadataCodec.decode(raw).updatedAt)
        for (date in listOf("2026-02-30T12:00:00Z", "2026-10-13T24:00:00Z", "2026-10-13T12:00:60Z",
            "2026-10-13", "2026-10-13T12:00:00.1234567890Z", "2026-10-13T12:00:00-00:00")) {
            rejected(metadata().replace("2026-10-13T12:34:56.123456789+00:00", date))
        }
    }
    @Test fun parserRejectsDuplicateFieldsMalformedPayloadAndOversizedUnknownStrings() {
        rejected(metadata(",\"type\":\"rulings\""))
        rejected(metadata() + "{}")
        rejected("{bad}")
        rejected(metadata(",\"ignored\":\"${"x".repeat(4097)}\""))
        rejected(" ".repeat(1048577))
        rejected(metadata().replace(id, "not-a-uuid"))
        rejected(metadata().replace("https://api.scryfall.com/bulk-data/$id", "https://evil.test/bulk-data/$id"))
    }
    @Test fun optionalTransportClaimsMayBeAbsentButCannotContradictGzipJsonlPolicy() {
        val absent = metadata().replace(",\n        \"content_type\":\"application/json\",\"content_encoding\":\"gzip\"", "")
        assertNull(ScryfallRulingsMetadataCodec.decode(absent).contentEncoding)
        rejected(metadata().replace("\"gzip\"", "\"br\""))
        rejected(metadata().replace("application/json", "text/html"))
    }

    @Test fun unknownKeysAndStringsAreBoundedByUtf8Bytes() {
        assertEquals(id, ScryfallRulingsMetadataCodec.decode(metadata(",\"ignored\":\"${"é".repeat(2048)}\"")).bulkId.value)
        rejected(metadata(",\"ignored\":\"${"é".repeat(2049)}\""))
        assertEquals(id, ScryfallRulingsMetadataCodec.decode(metadata(",\"${"é".repeat(128)}\":null")).bulkId.value)
        rejected(metadata(",\"${"é".repeat(129)}\":null"))
    }

    @Test fun totalMetadataLimitCountsUtf8BytesAcrossIndividuallyLegalStrings() {
        val item = "\"${"é".repeat(2048)}\""
        val within = metadata(",\"ignored\":[${List(255) { item }.joinToString(",")}]")
        assertTrue(within.toByteArray(Charsets.UTF_8).size < 1048576)
        assertEquals(id, ScryfallRulingsMetadataCodec.decode(within).bulkId.value)
        val oversized = metadata(",\"ignored\":[${List(256) { item }.joinToString(",")}]")
        assertTrue(oversized.length < 1048576)
        assertTrue(oversized.toByteArray(Charsets.UTF_8).size > 1048576)
        rejected(oversized)
    }

    @Test fun unknownNestedValuesCannotBypassDepthOrUnicodeValidation() {
        val within = "[".repeat(15) + "null" + "]".repeat(15)
        assertEquals(id, ScryfallRulingsMetadataCodec.decode(metadata(",\"ignored\":$within")).bulkId.value)
        val excessive = "[".repeat(16) + "null" + "]".repeat(16)
        rejected(metadata(",\"ignored\":$excessive"))
        rejected(metadata(",\"ignored\":\"\\uD800\""))
        rejected(metadata(",\"ignored\":\"\\uDC00\""))
    }

    @Test fun configurableCompressedCeilingPreservesExactLongCounts() {
        val maximum = metadata().replace("2147483648", Long.MAX_VALUE.toString())
        assertEquals(Long.MAX_VALUE, ScryfallRulingsMetadataCodec.decode(maximum, Long.MAX_VALUE).compressedBytes)
        assertThrows(IllegalArgumentException::class.java) { ScryfallRulingsMetadataCodec.decode(maximum, Long.MAX_VALUE - 1) }
        val ten = metadata().replace("2147483648", "10")
        assertEquals(10L, ScryfallRulingsMetadataCodec.decode(ten, 10).compressedBytes)
        for (ceiling in listOf(9L, 0L, -1L)) {
            assertThrows(IllegalArgumentException::class.java) { ScryfallRulingsMetadataCodec.decode(ten, ceiling) }
        }
    }

    @Test fun timestampsUseProlepticGregorianDatesAndNormalizeOffsetBoundaries() {
        fun at(date: String) = metadata().replace("2026-10-13T12:34:56.123456789+00:00", date)
        val expected = mapOf(
            "1500-03-01T00:00:00Z" to UtcTimestamp(-14826672000L, 0),
            "1969-12-31T23:59:59.5Z" to UtcTimestamp(-1L, 500000000),
            "2026-10-13T12:34:56+18:00" to UtcTimestamp(1791830096L, 0),
            "2026-10-13T12:34:56-18:00" to UtcTimestamp(1791959696L, 0),
        )
        for ((date, timestamp) in expected) assertEquals(timestamp, ScryfallRulingsMetadataCodec.decode(at(date)).updatedAt)
        for (date in listOf("1500-02-29T00:00:00Z", "0000-01-01T00:00:00Z",
            "2026-10-13T12:34:56+18:01", "2026-10-13T12:34:56-19:00", "2026-10-13T12:34:56+00:60")) rejected(at(date))
    }

    @Test fun malformedListEntriesAndRequiredFieldShapesFailClosed() {
        for (data in listOf("null", "{}", "[null]", "[1]", "[{}]")) {
            rejected("""{"object":"list","data":$data}""")
        }
        for (value in listOf("null", "false", "123", "[]", "{}")) {
            rejected(metadata().replace("\"type\":\"rulings\"", "\"type\":$value"))
            rejected(metadata().replace("\"updated_at\":\"2026-10-13T12:34:56.123456789+00:00\"", "\"updated_at\":$value"))
        }
        for (value in listOf("false", "123", "[]", "{}"))
            rejected(metadata().replace("\"content_encoding\":\"gzip\"", "\"content_encoding\":$value"))
        assertNull(ScryfallRulingsMetadataCodec.decode(metadata().replace("\"content_encoding\":\"gzip\"", "\"content_encoding\":null")).contentEncoding)
        rejected(metadata().replace("https://api.scryfall.com/bulk-data/$id", "https://api.scryfall.com/bulk-data/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
    }
}
