package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal

public enum class PriceMedium { PAPER, DIGITAL }
public enum class PriceStatistic { MARKET, LOW, MID, HIGH, TREND, BUYLIST, PROVIDER_DEFINED }
public data class QuoteKey(
    public val provider: ProviderId,
    public val printingId: CardId,
    public val finish: Finish,
    public val medium: PriceMedium,
    public val statistic: PriceStatistic,
    public val nativeUnit: DecimalUnit,
    public val asOf: UtcTimestamp,
    public val providerStatistic: String?,
)
public data class Quote(
    public val key: QuoteKey,
    public val amount: Knowledge<DecimalAmount>,
    public val fetchedAt: UtcTimestamp,
    public val provenance: Provenance,
    public val stale: Boolean,
    public val confidence: Knowledge<BigDecimal>,
    public val providerQuoteId: Knowledge<String>,
) {
    init { if (amount is Knowledge.Known) require(amount.value.unit == key.nativeUnit) }
}
public data class FxRate(
    public val base: DecimalUnit.Fiat,
    public val quote: DecimalUnit.Fiat,
    public val day: UtcTimestamp,
    public val provider: ProviderId,
    public val rate: BigDecimal,
    public val provenance: Provenance,
) { init { require(rate.signum() > 0) } }
public data class QuoteSnapshot(
    public val id: GenerationId,
    public val capturedAt: UtcTimestamp,
    public val quotes: SnapshotList<Quote>,
    public val fxRates: SnapshotList<FxRate>,
    public val complete: Boolean,
)
public data class QuoteRequest(
    public val printingIds: SnapshotList<CardId>,
    public val providers: SnapshotList<ProviderId>,
    public val finishes: SnapshotList<Finish>,
    public val medium: PriceMedium,
    public val statistics: SnapshotList<PriceStatistic>,
    public val page: PageRequest,
) { init { require(printingIds.size <= PageRequest.MAX_LIMIT) } }
public enum class OwnershipPolicy { IGNORE, EXACT_PRINTING, SAME_ORACLE }
public enum class StaleQuotePolicy { ALLOW_MARKED, EXCLUDE }
public data class PricePolicy(
    public val unit: DecimalUnit,
    public val medium: PriceMedium,
    public val statistic: PriceStatistic,
    public val ownership: OwnershipPolicy,
    public val staleQuotes: StaleQuotePolicy,
    public val allowFiatConversion: Boolean,
    public val budget: Knowledge<DecimalAmount>,
    public val includeBoards: SnapshotList<BoardRole>,
)
public data class OwnedAllocation(public val lotId: EntityId, public val entryId: EntityId, public val quantity: Int) { init { require(quantity > 0) } }
public data class AcquisitionDemand(
    public val entryId: EntityId,
    public val requestedQuantity: Int,
    public val ownedQuantity: Int,
    public val remainingQuantity: Int,
    public val quote: Knowledge<Quote>,
    public val remainingCost: Knowledge<DecimalAmount>,
) {
    init { require(requestedQuantity >= 0 && ownedQuantity in 0..requestedQuantity && remainingQuantity == requestedQuantity - ownedQuantity) }
}
public data class AcquisitionPlan(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val inventoryRevision: Revision,
    public val quoteGeneration: GenerationId,
    public val policy: PricePolicy,
    public val allocations: SnapshotList<OwnedAllocation>,
    public val demands: SnapshotList<AcquisitionDemand>,
    public val knownSubtotal: DecimalAmount,
    public val total: Knowledge<DecimalAmount>,
    public val fxRatesUsed: SnapshotList<FxRate>,
    public val unknownEntryIds: SnapshotList<EntityId>,
    public val complete: Boolean,
)
public data class PrintingPin(public val entryId: EntityId, public val printingId: CardId, public val finish: Finish)
public enum class OptimizationObjective { MINIMUM_ACQUISITION_COST, MINIMUM_TOTAL_VALUE }
public data class OptimizationPolicy(
    public val price: PricePolicy,
    public val objective: OptimizationObjective,
    public val pins: SnapshotList<PrintingPin>,
    public val permittedLanguages: SnapshotList<LanguageTag>,
    public val permittedFinishes: SnapshotList<Finish>,
    public val eligibleSetCodes: SnapshotList<String>,
)
public data class PrintingChoice(
    public val entryId: EntityId,
    public val current: Knowledge<CardId>,
    public val selected: CardId,
    public val finish: Finish,
    public val quote: Quote,
    public val quantity: Int,
    public val cost: DecimalAmount,
) { init { require(quantity > 0) } }
public data class OptimizationPlan(
    public val id: EntityId,
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val inventoryRevision: Revision,
    public val quoteGeneration: GenerationId,
    public val policy: OptimizationPolicy,
    public val choices: SnapshotList<PrintingChoice>,
    public val unknownEntryIds: SnapshotList<EntityId>,
    public val conflicts: SnapshotList<DomainIssue>,
    public val total: Knowledge<DecimalAmount>,
    public val complete: Boolean,
)
public interface PricingService {
    public fun calculate(deck: DeckSnapshot, inventory: InventorySnapshot, quotes: QuoteSnapshot, policy: PricePolicy): Outcome<AcquisitionPlan>
    public fun optimize(
        deck: DeckSnapshot,
        inventory: InventorySnapshot,
        quotes: QuoteSnapshot,
        policy: OptimizationPolicy,
    ): Outcome<OptimizationPlan>
    public suspend fun refresh(request: QuoteRequest, cancel: CancelToken): Outcome<QuoteSnapshot>
}

public enum class IssueSeverity { INFORMATION, WARNING, ERROR, REVIEW_REQUIRED }
public data class DomainIssue(
    public val code: String,
    public val severity: IssueSeverity,
    public val message: String,
    public val entryId: EntityId?,
    public val evidence: SnapshotList<Evidence>,
) { init { require(code.isNotBlank() && message.isNotBlank()) } }
public enum class LegalityStatus { LEGAL, BANNED, RESTRICTED, NOT_LEGAL, UNKNOWN, REVIEW_REQUIRED }
public data class Legality(
    public val oracleId: OracleId,
    public val formatId: FormatId,
    public val generation: GenerationId,
    public val status: LegalityStatus,
    public val provenance: Provenance,
)
public data class BoardConstraint(public val board: BoardRole, public val minimum: Int, public val maximum: Int?, public val countsTowardDeckSize: Boolean) { init { require(minimum >= 0); require(maximum == null || maximum >= minimum) } }
public sealed interface FormatPredicate {
    public data class Singleton(public val maximumCopies: Int, public val exceptions: SnapshotList<OracleId>) : FormatPredicate { init { require(maximumCopies > 0) } }
    public data class ColorIdentity(public val commanderBoards: SnapshotList<BoardRole>) : FormatPredicate
    public data class Membership(public val permittedSets: SnapshotList<String>, public val permittedCards: SnapshotList<OracleId>) : FormatPredicate
    public data class Companion(
        public val oracleId: OracleId,
        public val description: String,
        public val automaticallyCheckable: Boolean,
        public val evidence: SnapshotList<Evidence>,
    ) : FormatPredicate
    public data class Manual(public val id: String, public val description: String, public val evidence: SnapshotList<Evidence>) : FormatPredicate
}
public data class FormatDefinition(
    public val id: FormatId,
    public val name: String,
    public val version: String,
    public val boards: SnapshotList<BoardConstraint>,
    public val predicates: SnapshotList<FormatPredicate>,
    public val banned: SnapshotList<OracleId>,
    public val restricted: SnapshotList<OracleId>,
    public val provenance: Provenance,
    public val complete: Boolean,
)
public data class ValidationEvidence(
    public val entryId: EntityId?,
    public val predicate: String,
    public val status: LegalityStatus,
    public val evidence: SnapshotList<Evidence>,
)
public data class ValidationReport(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val catalog: SnapshotList<SnapshotReference>,
    public val formatId: FormatId,
    public val formatVersion: String,
    public val status: LegalityStatus,
    public val entries: SnapshotList<ValidationEvidence>,
    public val issues: SnapshotList<DomainIssue>,
    public val assessedAt: UtcTimestamp,
)
public interface DeckValidator {
    public fun validate(deck: DeckSnapshot, catalog: CatalogSnapshot, format: FormatDefinition): Outcome<ValidationReport>
    public suspend fun format(id: FormatId, cancel: CancelToken): Outcome<FormatDefinition>
}

public data class IntentQuestion(public val id: String, public val prompt: String, public val relevantCriteria: SnapshotList<String>)
public data class IntentAnswer(public val questionId: String, public val answer: Knowledge<String>, public val evidence: SnapshotList<Evidence>)
public data class IntentAnswers(public val answers: SnapshotList<IntentAnswer>)
public data class BracketCriterion(
    public val id: String,
    public val description: String,
    public val maximumCount: Knowledge<Int>,
    public val requiresIntent: Boolean,
    public val evidence: SnapshotList<Evidence>,
)
public data class BracketRuleSet(
    public val id: ProviderIdentity,
    public val version: String,
    public val criteria: SnapshotList<BracketCriterion>,
    public val intentQuestions: SnapshotList<IntentQuestion>,
    public val provenance: Provenance,
    public val accepted: Boolean,
)
public data class GameChangerMember(public val oracleId: Knowledge<OracleId>, public val rawIdentity: ProviderIdentity, public val evidence: SnapshotList<Evidence>)
public data class ComboCard(public val identity: CardIdentity, public val role: String, public val quantity: Int) { init { require(quantity > 0) } }
public data class Combo(
    public val id: ProviderIdentity,
    public val cards: SnapshotList<ComboCard>,
    public val prerequisites: SnapshotList<String>,
    public val requirements: SnapshotList<String>,
    public val results: SnapshotList<String>,
    public val evidence: SnapshotList<Evidence>,
    public val provenance: Provenance,
)
public data class BracketData(
    public val ruleSet: BracketRuleSet,
    public val gameChangers: SnapshotList<GameChangerMember>,
    public val combos: SnapshotList<Combo>,
    public val discrepancies: SnapshotList<DomainIssue>,
    public val complete: Boolean,
)
public data class CriterionAssessment(
    public val criterionId: String,
    public val count: Knowledge<Int>,
    public val satisfied: Knowledge<Boolean>,
    public val entryIds: SnapshotList<EntityId>,
    public val evidence: SnapshotList<Evidence>,
)
public data class BracketAssessment(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val ruleSetId: ProviderIdentity,
    public val ruleSetVersion: String,
    public val bracket: Knowledge<Int>,
    public val criteria: SnapshotList<CriterionAssessment>,
    public val intent: IntentAnswers,
    public val issues: SnapshotList<DomainIssue>,
    public val complete: Boolean,
)
public data class BracketTarget(public val bracket: Int, public val rulesetVersion: String) { init { require(bracket > 0) } }
public data class ChangeConstraints(
    public val target: BracketTarget?,
    public val mustRemove: SnapshotList<OracleId>,
    public val preserveEntryIds: SnapshotList<EntityId>,
    public val maximumCounts: SnapshotList<CriterionAssessment>,
    public val budget: Knowledge<DecimalAmount>,
    public val issues: SnapshotList<DomainIssue>,
)
public interface BracketService {
    public fun assess(deck: DeckSnapshot, data: BracketData, intent: IntentAnswers): Outcome<BracketAssessment>
    public suspend fun refresh(cancel: CancelToken): Outcome<BracketData>
    public fun target(assessment: BracketAssessment, target: BracketTarget): Outcome<ChangeConstraints>
}

public data class CardChange(
    public val entryId: EntityId?,
    public val before: DeckEntry?,
    public val after: DeckEntry?,
    public val reason: String,
    public val evidence: SnapshotList<Evidence>,
) { init { require(before != null || after != null) } }
public data class RecommendationContext(
    public val catalog: CatalogSnapshot,
    public val format: FormatDefinition,
    public val quotes: QuoteSnapshot?,
    public val inventory: InventorySnapshot?,
    public val bracket: BracketData?,
    public val constraints: ChangeConstraints,
    public val analysis: AnalysisResult?,
)
public data class RecommendationRequest(
    public val deck: DeckSnapshot,
    public val context: RecommendationContext,
    public val requestedRoles: SnapshotList<String>,
    public val excluded: SnapshotList<OracleId>,
    public val page: PageRequest,
)
public data class Recommendation(
    public val id: EntityId,
    public val identity: CardIdentity,
    public val score: BigDecimal,
    public val roles: SnapshotList<RoleTag>,
    public val proposedChanges: SnapshotList<CardChange>,
    public val priceDelta: Knowledge<DecimalAmount>,
    public val legality: LegalityStatus,
    public val evidence: SnapshotList<Evidence>,
)
public data class RecommendationPage(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val catalog: SnapshotList<SnapshotReference>,
    public val quoteGeneration: GenerationId?,
    public val recommendations: Page<Recommendation>,
    public val complete: Boolean,
    public val issues: SnapshotList<DomainIssue>,
)
public data class UpgradePlan(
    public val id: EntityId,
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val changes: SnapshotList<CardChange>,
    public val resultingValidation: ValidationReport,
    public val costDelta: Knowledge<DecimalAmount>,
    public val bracket: BracketAssessment?,
    public val issues: SnapshotList<DomainIssue>,
)
public interface RecommendationEngine {
    public suspend fun recommend(request: RecommendationRequest, cancel: CancelToken): Outcome<RecommendationPage>
    public fun preview(deck: DeckSnapshot, changes: SnapshotList<CardChange>, context: RecommendationContext): Outcome<UpgradePlan>
}
