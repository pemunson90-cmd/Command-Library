package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SourceMetadataCodecTest {
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
}
