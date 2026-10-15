package com.architectpsg.commandlibrary.feature.links

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LinkTemplateRendererTest {
    private val links = LocalCardLinks()
    private val values = LinkTokenValues(
        "Éowyn, \"Toolbox\" // Ice & 100%?", Knowledge.Known(OracleId(ProviderId("source"), "oracle/%2F")),
        Knowledge.Known(CardId(ProviderId("source"), "print/?&")), Knowledge.Known("s/ét"),
        Knowledge.Known("001a★/ %"), LanguageTag("fr"), SnapshotList.empty(),
    )

    @Test fun encodesUnicodeAndReservedComponentsExactlyOnce() {
        val result = success(links.template("https://example.test/{name}?set={set}&no={collector_number}&lang={lang}", values))
        assertEquals("https://example.test/%C3%89owyn%2C%20%22Toolbox%22%20%2F%2F%20Ice%20%26%20100%25%3F?set=s%2F%C3%A9t&no=001a%E2%98%85%2F%20%25&lang=fr", result.value)
        assertFalse(result.value.contains('+'))
    }

    @Test fun idsAreRawProducerComponentsAndStaticPercentEscapesStayUntouched() {
        assertEquals("https://example.test/%2f/%7Bliteral%7D?oracle=oracle%2F%252F&printing=print%2F%3F%26",
            success(links.template("https://example.test/%2f/%7Bliteral%7D?oracle={oracle_id}&printing={printing_id}", values)).value)
    }

    @Test fun splitDfcPunctuationAndEmojiNamesKeepAllCharacters() {
        val fixtures = mapOf(
            "Fire // Ice" to "Fire%20%2F%2F%20Ice",
            "Bala Ged Recovery // Bala Ged Sanctuary" to "Bala%20Ged%20Recovery%20%2F%2F%20Bala%20Ged%20Sanctuary",
            "Chandra's Spitfire" to "Chandra%27s%20Spitfire",
            "Thalia, Guardian of Thraben" to "Thalia%2C%20Guardian%20of%20Thraben",
            "雪😀" to "%E9%9B%AA%F0%9F%98%80",
            "a+b=\"x\"%2F#?&\\/" to "a%2Bb%3D%22x%22%252F%23%3F%26%5C%2F",
        )
        for ((raw, encoded) in fixtures) assertEquals(raw, "https://example.test/$encoded",
            success(links.template("https://example.test/{name}", values.copy(name = raw))).value)
    }

    @Test fun repeatedTokensAndUnreservedBytesAreStable() {
        assertEquals("https://example.test/Az09-._~/Az09-._~", success(links.template("https://example.test/{name}/{name}", values.copy(name = "Az09-._~"))).value)
    }

    @Test fun missingKnownEmptyUnknownAndAbsentRequiredValuesAreUnavailable() {
        val unknown = Knowledge.Unknown("not supplied", SnapshotList.empty())
        for (missing in listOf(Knowledge.Absent("not supplied"), unknown)) {
            assertTrue(links.template("https://example.test/{oracle_id}", values.copy(oracleId = missing)) is Outcome.Unavailable)
            assertTrue(links.template("https://example.test/{printing_id}", values.copy(printingId = missing)) is Outcome.Unavailable)
            assertTrue(links.template("https://example.test/{set}", values.copy(setCode = missing)) is Outcome.Unavailable)
            assertTrue(links.template("https://example.test/{collector_number}", values.copy(collectorNumber = missing)) is Outcome.Unavailable)
        }
        assertTrue(links.template("https://example.test/{name}", values.copy(name = "")) is Outcome.Unavailable)
        assertTrue(links.template("https://example.test/static", values.copy(oracleId = unknown)) is Outcome.Success)
    }

    @Test fun malformedAndUnsupportedTokensFailBeforeMissingValues() {
        for (pattern in listOf("{name", "name}", "{{name}}", "{}", "{NAME}", "{vendor_id}", "{scryfall_uri}", "{front_face_name}", "{name_slug}", "{ name }", "{name}{bad}")) {
            invalid(links.template("https://example.test/$pattern", values.copy(name = "")))
        }
    }

    @Test fun tokensCannotChangeSchemeOrAuthorityEvenWithSafeValues() {
        for (pattern in listOf("{name}://example.test/x", "https://{name}/x", "https://example.{name}/x", "https://example.test:{name}/x", "https://{name}@example.test/x")) invalid(links.template(pattern, values))
    }

    @Test fun unsafeAbsoluteUrlsAreRejected() {
        for (url in listOf(
            "javascript:alert(1)", "file:///tmp/card", "data:text/plain,a", "ftp://example.test/a", "//example.test/a", "/card/a",
            "https:///a", "https://", "https://user@example.test/a", "https://user:pass@example.test/a", "https://@example.test/a",
            "https://example.test\\@evil.test/a", "https:\\example.test/a", "https://example.test/a\\b",
            "https://example.test/a\nb", "https://example.test/a\rb", "https://example.test/a\u0000b", "https://example.test/a\u0085b",
            " https://example.test/a", "https://example.test/a b", "https://éxample.test/a", "https://*.example.test/a",
            "https://example_test/a", "https://example.test:/a", "https://example.test:0/a", "https://example.test:65536/a",
            "https://example.test:-1/a", "https://example.test:abc/a", "https://example.test:443:80/a",
            "https://example.test/%", "https://example.test/%0", "https://example.test/%GG", "https://example.test/%u0041",
        )) invalid(links.template(url, values))
    }

    @Test fun validAbsoluteUrlsKeepOriginalSpellingAndQuery() {
        for (url in listOf("HTTP://EXAMPLE.test:80/a?x=%2f+y#z", "https://example.test:443/a", "https://example.test:8443/a", "http://127.0.0.1:8080/a", "https://[::1]:444/a", "https://example.test/雪")) {
            assertEquals(url, url, success(links.template(url, values)).value)
        }
    }

    @Test fun invalidUtf16IsNotSilentlyReplaced() {
        for (raw in listOf("\uD800", "\uDC00", "a\uD800b")) {
            invalid(links.template("https://example.test/{name}", values.copy(name = raw)))
            invalid(links.template("https://example.test/$raw", values))
        }
    }

    @Test fun boundsUseUtf8BytesAndExpandedUrlSize() {
        val prefix = "https://example.test/"
        val exact = prefix + "a".repeat(16 * 1024 - prefix.length)
        assertEquals(exact, success(links.template(exact, values)).value)
        invalid(links.template(exact + "a", values))
        invalid(links.template(prefix + "é".repeat(8192), values))
        invalid(links.template(prefix + "{name}", values.copy(name = "a".repeat(16385))))
        invalid(links.template(prefix + "{name}", values.copy(name = "é".repeat(8193))))
        invalid(links.template(prefix + "{name}", values.copy(name = "é".repeat(3000))))
        invalid(links.template(prefix + "{name}", values.copy(collectorNumber = Knowledge.Known("x".repeat(16385)))))
    }

    @Test fun noVendorGrammarOrWholeLinkSubstitutionIsInvented() {
        assertEquals("https://example.test/?q=card%3D%22a%22b%22", success(links.template("https://example.test/?q={name}", values.copy(name = "card=\"a\"b\""))).value)
        for (token in listOf("scryfall_id", "multiverse_id", "tcgplayer_id", "cardmarket_uri", "printed_name")) invalid(links.template("https://example.test/{$token}", values))
    }

    private fun success(outcome: Outcome<ResourceUri>): ResourceUri {
        assertTrue(outcome.toString(), outcome is Outcome.Success)
        return (outcome as Outcome.Success).value
    }
    private fun invalid(outcome: Outcome<*>) {
        assertTrue(outcome.toString(), outcome is Outcome.Failure)
        assertEquals(FailureCode.INVALID_INPUT, (outcome as Outcome.Failure).error.code)
    }
}
