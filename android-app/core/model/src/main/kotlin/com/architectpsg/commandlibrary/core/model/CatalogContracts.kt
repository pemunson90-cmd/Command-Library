package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

public enum class Color { WHITE, BLUE, BLACK, RED, GREEN }
public enum class Finish { NONFOIL, FOIL, ETCHED }
public enum class GenerationStatus { STAGING, VALIDATED, ACTIVE, RETIRED, FAILED }
public enum class StorageMode { FULL, LEAN }
public data class TypeLine(
    public val original: String,
    public val supertypes: SnapshotList<String>,
    public val types: SnapshotList<String>,
    public val subtypes: SnapshotList<String>,
)
public data class CardStats(
    public val power: Knowledge<String>,
    public val toughness: Knowledge<String>,
    public val loyalty: Knowledge<String>,
    public val defense: Knowledge<String>,
)
public data class FaceIdentity(public val oracleId: OracleId, public val index: Int, public val providerFaceId: Knowledge<String>) { init { require(index >= 0) } }
public enum class FaceRelationshipKind { SPLIT, ADVENTURE, TRANSFORM, MODAL_DOUBLE_FACE, MELD_PART, MELD_RESULT, OTHER }
public data class FaceRelationship(
    public val kind: FaceRelationshipKind,
    public val relatedOracle: Knowledge<OracleId>,
    public val relatedFace: Knowledge<FaceIdentity>,
    public val evidence: SnapshotList<Evidence>,
)
public data class CardFace(
    public val identity: FaceIdentity,
    public val name: String,
    public val text: Knowledge<String>,
    public val manaCost: Knowledge<String>,
    public val types: Knowledge<TypeLine>,
    public val stats: CardStats,
    public val image: Knowledge<ResourceUri>,
    public val relationships: SnapshotList<FaceRelationship>,
)
public data class ProviderIdentity(public val provider: ProviderId, public val kind: String, public val rawId: String) { init { require(rawId.isNotBlank()) } }
public data class CardRecord(
    public val oracleId: OracleId,
    public val name: String,
    public val originalText: Knowledge<String>,
    public val manaCost: Knowledge<String>,
    public val manaValue: Knowledge<BigDecimal>,
    public val colors: Knowledge<SnapshotList<Color>>,
    public val colorIdentity: Knowledge<SnapshotList<Color>>,
    public val types: Knowledge<TypeLine>,
    public val stats: CardStats,
    public val keywords: Knowledge<SnapshotList<String>>,
    public val layout: Knowledge<String>,
    public val reserved: Knowledge<Boolean>,
    public val normalizedSearchText: String,
    public val language: LanguageTag,
    public val faces: SnapshotList<CardFace>,
    public val providerIdentities: SnapshotList<ProviderIdentity>,
    public val provenance: Provenance,
    public val revision: Revision?,
)
public data class SuppliedLink(public val destination: String, public val uri: ResourceUri, public val provenance: Provenance)
public data class PrintingRecord(
    public val id: CardId,
    public val oracleId: Knowledge<OracleId>,
    public val setCode: String,
    public val setName: Knowledge<String>,
    public val collectorNumber: String,
    public val language: LanguageTag,
    public val rarity: Knowledge<String>,
    public val finishes: Knowledge<SnapshotList<Finish>>,
    public val imageUri: Knowledge<ResourceUri>,
    public val suppliedLinks: SnapshotList<SuppliedLink>,
    public val vendorIds: SnapshotList<ProviderIdentity>,
    public val provenance: Provenance,
)
/** Raw foreign identity survives missing language packs and unresolved oracle mapping. */
public data class CardIdentity(
    public val oracleId: Knowledge<OracleId>,
    public val printingId: Knowledge<CardId>,
    public val raw: ProviderIdentity?,
    public val originalName: Knowledge<String>,
    public val language: LanguageTag,
)
public data class CatalogCounts(public val oracleCards: Long, public val printings: Long, public val faces: Long, public val rulings: Long) {
    init { require(listOf(oracleCards, printings, faces, rulings).all { it >= 0 }) }
}
public data class CatalogGeneration(
    public val id: GenerationId,
    public val provenance: Provenance,
    public val languages: SnapshotList<LanguageTag>,
    public val format: String,
    public val counts: CatalogCounts,
    public val status: GenerationStatus,
)
public data class CatalogSelection(
    public val provider: ProviderId,
    public val dataset: String,
    public val languages: SnapshotList<LanguageTag>,
    public val mode: StorageMode,
    public val version: Knowledge<String>,
)
public data class CatalogSnapshot(
    public val generations: SnapshotList<CatalogGeneration>,
    public val cards: SnapshotList<CardRecord>,
    public val printings: SnapshotList<PrintingRecord>,
    public val legalities: SnapshotList<Legality>,
    public val complete: Boolean,
    public val capturedAt: UtcTimestamp,
)
public enum class JobPhase { QUEUED, DOWNLOADING, STAGING, VALIDATING, ACTIVATING, COMPLETED, FAILED, CANCELLED }
public data class JobProgress(
    public val jobId: EntityId,
    public val phase: JobPhase,
    public val completedUnits: Long,
    public val totalUnits: Knowledge<Long>,
    public val message: String,
    public val failure: DomainFailure?,
) { init { require(completedUnits >= 0) } }
public data class DownloadJob(
    public val id: EntityId,
    public val intent: DownloadIntent,
    public val phase: JobPhase,
    public val etag: Knowledge<String>,
    public val hash: Knowledge<ContentHash>,
    public val offset: Long,
    public val expectedBytes: Knowledge<Long>,
    public val consentId: EntityId?,
    public val wifiOnly: Boolean,
) { init { require(offset >= 0) } }
public data class PackMembership(
    public val packId: EntityId,
    public val version: String,
    public val printingIds: SnapshotList<CardId>,
    public val language: LanguageTag,
    public val pinned: Boolean,
    public val provenance: Provenance,
)
public data class Ruling(
    public val id: ProviderIdentity,
    public val oracleId: OracleId,
    public val rulingDate: UtcTimestamp,
    public val text: String,
    public val provenance: Provenance,
)
public interface CatalogRepository {
    public suspend fun lookupName(input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>>
    public suspend fun remoteSearch(
        input: String,
        page: PageRequest,
        selection: CatalogSelection,
        consent: ConsentRecord,
        cancel: CancelToken,
    ): Outcome<Page<CardRecord>>
    public suspend fun card(id: CardId, cancel: CancelToken): Outcome<CardRecord>
    public suspend fun printings(id: OracleId, page: PageRequest, cancel: CancelToken): Outcome<Page<PrintingRecord>>
    public fun refresh(selection: CatalogSelection, consent: ConsentRecord, cancel: CancelToken): Flow<JobProgress>
    public suspend fun switchMode(mode: StorageMode, cancel: CancelToken): Outcome<Unit>
}

public data class RulesSource(
    public val provider: ProviderId,
    public val uri: ResourceUri,
    public val version: String,
    public val effectiveAt: Knowledge<UtcTimestamp>,
    public val checksum: ContentHash?,
    public val notice: SourceNotice,
)
public data class RulesCorpus(
    public val id: GenerationId,
    public val source: RulesSource,
    public val provenance: Provenance,
    public val status: GenerationStatus,
    public val entryCount: Long,
) { init { require(entryCount >= 0) } }
public enum class RuleEntryKind { NUMBERED, GLOSSARY, EXAMPLE, NOTICE }
public data class RuleCrossReference(public val rawTarget: String, public val resolved: Knowledge<RuleId>, public val evidence: SnapshotList<Evidence>)
public data class RuleEntry(
    public val id: RuleId,
    public val corpusId: GenerationId,
    public val kind: RuleEntryKind,
    public val number: Knowledge<String>,
    public val title: Knowledge<String>,
    public val text: String,
    public val ordinal: Long,
    public val references: SnapshotList<RuleCrossReference>,
    public val provenance: Provenance,
)
public data class KeywordRuleLink(
    public val keyword: String,
    public val corpusId: GenerationId,
    public val target: Knowledge<RuleId>,
    public val evidence: SnapshotList<Evidence>,
)
public data class RulesQuery(
    public val text: String,
    public val corpusId: GenerationId?,
    public val kinds: SnapshotList<RuleEntryKind>,
    public val page: PageRequest,
)
public interface RulesRepository {
    public fun importCorpus(source: RulesSource, cancel: CancelToken): Flow<JobProgress>
    public suspend fun entry(id: RuleId, cancel: CancelToken): Outcome<RuleEntry>
    public suspend fun search(query: RulesQuery, cancel: CancelToken): Outcome<Page<RuleEntry>>
}

public enum class Comparison { EQ, NE, LT, LE, GT, GE }
public enum class SearchTextField { NAME, ORACLE_TEXT, TYPE, KEYWORD }
public enum class SearchNumericField { MANA_VALUE, POWER, TOUGHNESS, LOYALTY, DEFENSE }
public enum class FaceMatchPolicy { ANY_FACE, SAME_FACE, CANONICAL }
public sealed interface QueryAst {
    public data class Text(public val field: SearchTextField, public val value: String, public val exact: Boolean) : QueryAst
    public data class Numeric(public val field: SearchNumericField, public val comparison: Comparison, public val value: BigDecimal) : QueryAst
    public data class Colors(public val values: SnapshotList<Color>, public val identity: Boolean, public val comparison: Comparison) : QueryAst
    public data class LegalIn(public val format: FormatId, public val status: LegalityStatus) : QueryAst
    public data class And(public val terms: SnapshotList<QueryAst>) : QueryAst
    public data class Or(public val terms: SnapshotList<QueryAst>) : QueryAst
    public data class Not(public val term: QueryAst) : QueryAst
}
public data class QueryDiagnostic(public val start: Int, public val end: Int, public val message: String) { init { require(start >= 0 && end >= start) } }
public data class SearchContext(
    public val snapshots: SnapshotList<SnapshotReference>,
    public val languages: SnapshotList<LanguageTag>,
    public val facePolicy: FaceMatchPolicy,
    public val suggestionLimit: Int = 20,
) { init { require(suggestionLimit in 1..PageRequest.MAX_LIMIT) } }
public data class Suggestion(
    public val text: String,
    public val oracleId: Knowledge<OracleId>,
    public val field: SearchTextField,
    public val provenance: Provenance?,
)
public interface CardSearch {
    public fun parse(input: String): Outcome<QueryAst>
    public suspend fun local(query: QueryAst, context: SearchContext, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>>
    public suspend fun suggest(input: String, context: SearchContext, cancel: CancelToken): Outcome<SnapshotList<Suggestion>>
    public suspend fun online(input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>>
}
