package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.OracleId
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

/** Entirely synthetic prose/UUIDs; no publisher corpus is embedded. */
class ScryfallRulingRecordCodecTest {
    private val id = "11111111-2222-3333-4444-555555555555"
    private fun record(source: String = "wotc", published: String = "2024-02-29", comment: String = "Synthetic ruling"): JsonObject = JsonObject().apply {
        addProperty("object", "ruling"); addProperty("oracle_id", id); addProperty("source", source)
        addProperty("published_at", published); addProperty("comment", comment)
    }
    private fun rejected(raw: String) { assertThrows(IllegalArgumentException::class.java) { ScryfallRulingRecordCodec.decode(raw) } }

    @Test fun publisherCalendarDateAndUntrimmedUnicodeRemainLiteralAndOracleIsSourceQualified() {
        val text = "  Synthetic café: \uD83E\uDDE9\nSecond line — example.\u0000  "
        for (source in listOf("wotc", "scryfall")) {
            val result = ScryfallRulingRecordCodec.decode(record(source, "2024-02-29", text).toString())
            assertEquals(OracleId(ProviderId("scryfall"), id), result.oracleId)
            assertEquals(source, result.publisher.upstreamValue); assertEquals("2024-02-29", result.publishedAt.value); assertEquals(text, result.comment)
        }
    }

    @Test fun requiredFieldsCannotBeMissingNullOrCoercedFromOtherJsonTypes() {
        for (field in listOf("object", "oracle_id", "source", "published_at", "comment")) {
            rejected(record().apply { remove(field) }.toString())
            for (value in listOf("null", "false", "123", "[]", "{}")) rejected(record().apply { add(field, JsonParser.parseString(value)) }.toString())
        }
        for (text in listOf("", " \t\n")) rejected(record(comment = text).toString())
    }

    @Test fun exactObjectSourceAndCanonicalUuidAreMandatory() {
        for (source in listOf("WotC", "WOTC", "official", "", " wotc", "scryfall ")) rejected(record(source = source).toString())
        for (value in listOf("rulings", "card", "Ruling")) rejected(record().apply { addProperty("object", value) }.toString())
        for (value in listOf("1-2-3-4-5", "11111111222233334444555555555555", "11111111-2222-3333-4444-55555555555Z",
            "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE", "$id ", "not-an-oracle")) rejected(record().apply { addProperty("oracle_id", value) }.toString())
    }

    @Test fun datesAreRealProlepticGregorianDaysInExplicitFourDigitYearRange() {
        for (value in listOf("0001-01-01", "1500-03-01", "1600-02-29", "1900-02-28", "2000-02-29", "9999-12-31"))
            assertEquals(value, ScryfallRulingRecordCodec.decode(record(published = value).toString()).publishedAt.value)
        for (value in listOf("0000-01-01", "1500-02-29", "1900-02-29", "2023-02-29", "2024-04-31", "2024-00-01", "2024-13-01",
            "2024-01-00", "2024-01-32", "2024-2-29", "2024-02-29T00:00:00Z", "10000-01-01", " 2024-02-29")) rejected(record(published = value).toString())
    }

    @Test fun duplicateKeysTrailingDocumentsMalformedAndInvalidUnicodeFailClosed() {
        val raw = record().toString()
        rejected(raw.dropLast(1) + ",\"source\":\"scryfall\"}")
        for (value in listOf("$raw{}", "{bad}", "[]", raw.dropLast(1))) rejected(value)
        // Literal malformed UTF-16 is rejected before UTF-8 accounting; escaped forms by strict JSON parsing.
        rejected(record(comment = "x").toString().replace("\"x\"", "\"\uD800\""))
        for (escape in listOf("\\uD800", "\\uDC00")) {
            rejected(raw.dropLast(1) + ",\"unknown\":\"$escape\"}")
            rejected(raw.dropLast(1) + ",\"$escape\":null}")
        }
        rejected(raw.dropLast(1) + ",\"unknown\":{\"same\":1,\"same\":2}}")
    }

    @Test fun retainedAndUnknownValuesAndKeysUseUtf8ByteBounds() {
        val limit = ScryfallRulingRecordCodec.MAX_STRING_BYTES
        val exact = "é".repeat(limit / 2)
        assertEquals(exact, ScryfallRulingRecordCodec.decode(record(comment = exact).toString()).comment)
        rejected(record(comment = exact + "é").toString())
        ScryfallRulingRecordCodec.decode(record().apply { addProperty("unknown", exact) }.toString())
        rejected(record().apply { addProperty("unknown", exact + "é") }.toString())
        val key = "é".repeat(ScryfallRulingRecordCodec.MAX_KEY_BYTES / 2)
        ScryfallRulingRecordCodec.decode(record().apply { addProperty(key, "bounded") }.toString())
        rejected(record().apply { addProperty(key + "é", "bounded") }.toString())
    }

    @Test fun totalRecordLimitIncludesWhitespaceAndMultibyteUnknownFieldsBeforeParsing() {
        val raw = record().toString(); val limit = ScryfallRulingRecordCodec.MAX_RECORD_BYTES
        val exact = raw + " ".repeat(limit - raw.toByteArray(Charsets.UTF_8).size)
        assertEquals(id, ScryfallRulingRecordCodec.decode(exact).oracleId.raw); rejected(exact + " ")
        val multibyte = record().apply { addProperty("future_one", "é".repeat(32768)); addProperty("future_two", "é".repeat(32768)) }.toString()
        assertTrue(multibyte.length < limit); assertTrue(multibyte.toByteArray(Charsets.UTF_8).size > limit); rejected(multibyte)
    }

    @Test fun boundedFutureFieldsDoNotInventIdentityLanguageOrTrustAndCannotBypassDepthLimit() {
        val value = record().apply { addProperty("id", "not-a-supplied-ruling-id"); addProperty("uri", "https://untrusted.invalid/"); addProperty("lang", "xx") }
        assertEquals(ScryfallRulingRecordCodec.decode(record().toString()), ScryfallRulingRecordCodec.decode(value.toString()))
        val raw = record().toString().dropLast(1)
        val within = "[".repeat(15) + "null" + "]".repeat(15)
        ScryfallRulingRecordCodec.decode("$raw,\"future\":$within}")
        val excessive = "[".repeat(16) + "null" + "]".repeat(16)
        rejected("$raw,\"future\":$excessive}")
    }
}
