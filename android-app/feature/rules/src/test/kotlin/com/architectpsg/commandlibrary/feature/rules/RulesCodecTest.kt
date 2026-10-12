package com.architectpsg.commandlibrary.feature.rules

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class RulesCodecTest {
    private val evidence = SnapshotList.of(Evidence("Caller supplied evidence", ResourceUri("https://example.invalid/source"), UtcTimestamp(10, 123)))
    private val source = LocalRulesImportSource(ResourceUri("content://synthetic/rules"), "synthetic", ProviderId("synthetic"), version = Knowledge.Unknown("Unversioned", evidence), effectiveAt = Knowledge.Absent("No date"), sourceDate = Knowledge.Known(UtcTimestamp(-10, 12)), sourceUri = Knowledge.Unknown("No upstream", evidence), notice = Knowledge.Known(SourceNotice(ProviderId("synthetic"), "Synthetic", Knowledge.Unknown("Unknown rights", evidence), "Synthetic attribution", null)), expectedChecksum = Knowledge.Absent("No expected digest"), expectedBytes = Knowledge.Unknown("Unknown size", evidence))
    @Test fun allSourceKnowledgeAndEvidenceStatesRoundTrip() { assertEquals(source, RulesCodec.source(RulesCodec.decode(RulesCodec.encode(RulesCodec.source(source))))) }
    @Test fun entryReferencesProvenanceAndUnicodeRoundTrip() {
        val generation = GenerationId("g")
        val provenance = Provenance(source.provider, null, source.version, source.sourceDate, UtcTimestamp(20), source.effectiveAt, generation, ContentHash("SHA-256", "f".repeat(64)), source.notice.known())
        val entry = RuleEntry(RuleId(source.provider, "100.1"), generation, RuleEntryKind.NUMBERED, Knowledge.Known("100.1"), Knowledge.Absent("Untitled"), "café Ω rules", 9,
            SnapshotList.of(RuleCrossReference("100.2", Knowledge.Unknown("Missing", evidence), evidence), RuleCrossReference("100.3", Knowledge.Known(RuleId(source.provider, "100.3")), SnapshotList.empty())), provenance)
        assertEquals(entry, RulesCodec.entry(RulesCodec.decode(RulesCodec.encode(RulesCodec.entry(entry)))))
    }
    @Test fun unfinishedAttemptAndFailureEvidenceRoundTrip() {
        val attempt = LocalRulesImportAttempt(EntityId("job"), source, JobPhase.FAILED, UtcTimestamp(3), Knowledge.Unknown("Interrupted", evidence), DomainFailure(FailureCode.PERSISTENCE, "Interrupted", evidence))
        assertEquals(attempt, RulesCodec.attempt(RulesCodec.decode(RulesCodec.encode(RulesCodec.attempt(attempt)))))
    }
    @Test fun unsupportedPayloadVersionRejected() { assertThrows(IllegalArgumentException::class.java) { RulesCodec.decode("{\"schema\":2}") } }
}
