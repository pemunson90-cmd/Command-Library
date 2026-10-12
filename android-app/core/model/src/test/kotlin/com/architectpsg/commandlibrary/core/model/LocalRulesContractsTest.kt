package com.architectpsg.commandlibrary.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic contract values only; no official rules source or corpus is asserted. */
class LocalRulesContractsTest {
    private val provider = ProviderId("synthetic-provider")
    private val unknownVersion = Knowledge.Unknown("No document version supplied", SnapshotList.empty())
    private val unknownDate = Knowledge.Unknown("No source date supplied", SnapshotList.empty())

    private fun source() = LocalRulesImportSource(
        contentUri = ResourceUri("content://synthetic/rules"),
        displayName = "Synthetic rules.txt",
        provider = provider,
        version = unknownVersion,
        effectiveAt = unknownDate,
        sourceDate = unknownDate,
        sourceUri = Knowledge.Absent("Local file only"),
        notice = Knowledge.Unknown("No notice supplied", SnapshotList.empty()),
        expectedChecksum = Knowledge.Unknown("No expected checksum", SnapshotList.empty()),
        expectedBytes = Knowledge.Unknown("No size supplied", SnapshotList.empty()),
    )

    @Test fun unversionedLocalSelectionStaysUnknownAndDoesNotGrantDownloadConsent() {
        val selected = source()
        assertTrue(selected.version is Knowledge.Unknown)
        assertTrue(selected.effectiveAt is Knowledge.Unknown)
        assertEquals(LanguageTag.ENGLISH, selected.language)
        val settings = SettingsSnapshot(
            Revision(0), cache = CachePolicy(0, true, false), budget = Knowledge.Absent("Unset"),
            providerProfileIds = SnapshotList.empty(), sourceNotices = SnapshotList.empty(),
            consents = SnapshotList.empty(), updatedAt = UtcTimestamp(1),
        )
        assertTrue(settings.consents.isEmpty())
    }

    @Test fun localScopeAndExpectedMetadataMustBeTruthful() {
        val valid = source()
        assertThrows(IllegalArgumentException::class.java) { valid.copy(contentUri = ResourceUri("https://example.invalid/rules")) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(language = LanguageTag("fr")) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(version = Knowledge.Known("")) }
        assertThrows(IllegalArgumentException::class.java) { valid.copy(expectedBytes = Knowledge.Known(-1)) }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(expectedChecksum = Knowledge.Known(ContentHash("SHA-256", "abcd")))
        }
    }

    @Test fun resultAndPageKeepGenerationSeparateFromUnknownSourceVersion() {
        val source = source()
        val generation = GenerationId("synthetic-generation")
        val hash = ContentHash("SHA-256", "a".repeat(64))
        val provenance = Provenance(provider, null, source.version, source.sourceDate, UtcTimestamp(2),
            source.effectiveAt, generation, hash, null)
        val corpus = LocalRulesCorpus(generation, source, provenance, GenerationStatus.ACTIVE, 3, 2)
        val result = LocalRulesImportResult(corpus, 123, hash, ChecksumVerification.COMPUTED_WITHOUT_EXPECTATION, UtcTimestamp(3))
        assertEquals(generation, result.corpus.id)
        assertTrue(result.corpus.provenance.version is Knowledge.Unknown)
        val snapshot = SnapshotReference(provider, generation, null, LanguageTag.ENGLISH)
        val page = Page<RuleEntry>(SnapshotList.empty(), null, snapshot)
        assertEquals(generation, page.snapshot.generation)
        assertThrows(IllegalArgumentException::class.java) {
            result.copy(corpus = corpus.copy(status = GenerationStatus.STAGING))
        }
    }
}
