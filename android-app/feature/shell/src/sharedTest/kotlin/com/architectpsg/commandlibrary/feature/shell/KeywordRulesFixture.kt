package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.flow.Flow

/** Synthetic local lookup only; this does not establish a keyword's canonical rule identity. */
internal class KeywordRulesFixture : LocalRulesLookup, LocalRulesImport {
    var installed = true
    var imports = 0
    val queries = mutableListOf<RulesQuery>()
    private val provider = ProviderId("local-rules")
    private val generation = GenerationId("synthetic-keyword-corpus")
    private val unknown = Knowledge.Unknown("Synthetic source", SnapshotList.empty())
    private val source = LocalRulesImportSource(ResourceUri("content://synthetic.rules/selected.txt"), "Synthetic rules", provider, LanguageTag.ENGLISH,
        unknown, unknown, unknown, unknown, unknown, Knowledge.Absent("No expected hash"), Knowledge.Absent("No expected bytes"))
    private val provenance = Provenance(provider, null, unknown, unknown, SYNTHETIC_TIME, unknown, generation, ContentHash("SHA-256", "a".repeat(64)), null)
    private val corpus = LocalRulesCorpus(generation, source, provenance, GenerationStatus.ACTIVE, 1, 0)
    override suspend fun status(cancel: CancelToken): Outcome<LocalRulesStatus> = Outcome.Success(LocalRulesStatus(
        if(installed) Knowledge.Known(corpus) else Knowledge.Absent("Synthetic absent corpus"), Knowledge.Absent("No attempt")))
    override suspend fun search(query: RulesQuery, cancel: CancelToken): Outcome<Page<RuleEntry>> {
        queries += query
        if(!installed) return Outcome.Unavailable(UnavailableReason.NOT_INSTALLED, "Synthetic rules unavailable")
        val entry = RuleEntry(RuleId(provider, "synthetic-keyword-result"), generation, RuleEntryKind.GLOSSARY, Knowledge.Absent("No number"),
            Knowledge.Known(query.text), "Synthetic local keyword search result", 1, SnapshotList.empty(), provenance)
        return Outcome.Success(Page(SnapshotList.of(entry), null, SnapshotReference(provider, generation, null, LanguageTag.ENGLISH)))
    }
    override suspend fun entry(id: RuleId, corpusId: GenerationId, cancel: CancelToken): Outcome<RuleEntry> = Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Synthetic lookup not requested")
    override suspend fun `import`(source: LocalRulesImportSource, reopen: () -> Flow<ByteChunk>, onProgress: (JobProgress) -> Unit, cancel: CancelToken): Outcome<LocalRulesImportResult> {
        imports++; return Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Synthetic fixture cannot import")
    }
}
