package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SourceMetadataCodecTest {
    @Test fun sourceTimestampNeverTruncatesFractionalOrOverflowedValues() {
        for (date in listOf("{\"seconds\":1,\"nanos\":4294967296}", "{\"seconds\":1.5,\"nanos\":0}",
            "{\"seconds\":1,\"nanos\":0.5}", "{\"seconds\":\"1\",\"nanos\":0}", "{\"seconds\":1,\"nanos\":1000000000}")) {
            val payload = "{\"version\":1,\"sourceDate\":{\"state\":\"known\",\"value\":" + date + "},\"notice\":{\"state\":\"absent\",\"reason\":\"Not supplied\"}}"
            assertThrows(Exception::class.java) { SourceMetadataCodec.sourceDate(payload) }
        }
    }
    @Test fun retainsSourceKnowledgeAndNoticeAcrossPayload() {
        val evidence = Evidence("Upstream date is undocumented", ResourceUri("https://example.org/source"), UtcTimestamp(42, 7))
        val provider = ProviderId("scryfall")
        val notice = SourceNotice(provider, "Test source", Knowledge.Unknown("License uncertain", SnapshotList.of(evidence)), "Test attribution", ResourceUri("https://example.org/notice"))
        val source = CatalogImportSource(ResourceUri("content://test"), "test.json", provider, LanguageTag.ENGLISH,
            Knowledge.Unknown("Dataset date not supplied", SnapshotList.of(evidence)), Knowledge.Absent("No source URI"),
            Knowledge.Absent("No checksum"), Knowledge.Absent("No length"), Knowledge.Known(notice))
        val payload = SourceMetadataCodec.encode(source)
        assertEquals(source.sourceDate, SourceMetadataCodec.sourceDate(payload))
        assertEquals(notice, SourceMetadataCodec.notice(payload))
        assertTrue(SourceMetadataCodec.sourceDate(null) is Knowledge.Unknown)
    }
    @Test fun storedMetadataRejectsUnsupportedUntypedAndUnboundedVersions() {
        val payload = """{"version":1,"sourceDate":{"state":"absent","reason":"No date"},"notice":{"state":"absent","reason":"No notice"}}"""
        assertTrue(SourceMetadataCodec.sourceDate(payload) is Knowledge.Absent)
        assertNull(SourceMetadataCodec.notice(payload))
        for (bad in listOf(
            payload.replace("\"version\":1,", ""),
            payload.replace("\"version\":1", "\"version\":2"),
            payload.replace("\"version\":1", "\"version\":\"1\""),
            payload.replace("\"version\":1", "\"version\":1.5"),
            payload.replace("\"version\":1", "\"version\":999999999999999999999"),
            payload.replace("No date", "x".repeat(128 * 1024)),
        )) {
            assertThrows(Exception::class.java) { SourceMetadataCodec.sourceDate(bad) }
            assertThrows(Exception::class.java) { SourceMetadataCodec.notice(bad) }
        }
    }

}
