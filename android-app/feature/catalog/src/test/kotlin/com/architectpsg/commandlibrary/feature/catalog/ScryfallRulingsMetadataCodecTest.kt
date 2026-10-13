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
}
