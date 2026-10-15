package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import kotlin.time.Duration

public enum class ProviderDialect { OPENAI_CHAT, OPENAI_RESPONSES, ANTHROPIC, GEMINI, LOCAL }
public data class ProviderCapabilities(
    public val modelListing: Boolean,
    public val streaming: Boolean,
    public val tools: Boolean,
    public val promptCaching: Boolean,
    public val structuredOutput: Boolean,
    public val vision: Boolean,
)
public data class ProviderProfile(
    public val id: EntityId,
    public val name: String,
    public val dialect: ProviderDialect,
    public val endpoint: EndpointProfile?,
    public val selectedModelId: String,
    public val capabilities: ProviderCapabilities,
    public val secretRef: SecretRef?,
    public val apiVersion: Knowledge<String>,
    public val extraHeaders: SnapshotList<HttpHeader>,
    /** Explicit protocol prefix, never inferred from a display name or origin-only endpoint. */
    public val baseUri: ResourceUri? = null,
)
public data class ModelDescriptor(
    public val id: String,
    public val name: Knowledge<String>,
    public val contextTokens: Knowledge<Int>,
    public val capabilities: ProviderCapabilities,
    public val provenance: Provenance?,
)
public data class ModelPage(
    public val models: Page<ModelDescriptor>,
    public val providerId: EntityId,
    public val fetchedAt: UtcTimestamp,
    public val manualModelAllowed: Boolean,
)
public data class ModelManifest(
    public val id: ProviderIdentity,
    public val version: String,
    public val fileName: String,
    public val uri: ResourceUri,
    public val sizeBytes: Long,
    public val hash: ContentHash,
    public val quantization: String,
    public val architecture: String,
    public val contextTokens: Int,
    public val provenance: Provenance,
    public val notice: SourceNotice,
) { init { require(sizeBytes > 0 && contextTokens > 0) } }
public data class ModelSelection(public val manifest: ModelManifest, public val localArtifactId: EntityId, public val consent: ConsentRecord?)
public enum class InferenceBackend { CPU, GPU, AUTOMATIC }
public data class RuntimeProfile(
    public val threads: Int,
    public val contextTokens: Int,
    public val batchTokens: Int,
    public val memoryBudgetBytes: Long,
    public val backend: InferenceBackend,
) { init { require(threads > 0 && contextTokens > 0 && batchTokens > 0 && memoryBudgetBytes > 0) } }
public data class ModelSession(
    public val id: EntityId,
    public val model: ProviderIdentity,
    public val modelVersion: String,
    public val runtime: RuntimeProfile,
    public val loadedAt: UtcTimestamp,
)
public enum class MessageRole { SYSTEM, USER, ASSISTANT, TOOL }
public data class GenerationMessage(public val role: MessageRole, public val text: String, public val toolCallId: String?)
public data class GenerationRequest(
    public val id: EntityId,
    public val messages: SnapshotList<GenerationMessage>,
    public val maxTokens: Int,
    public val temperature: BigDecimal,
    public val topP: BigDecimal,
    public val seed: Long?,
    public val stopSequences: SnapshotList<String>,
) { init { require(maxTokens > 0); require(temperature >= BigDecimal.ZERO); require(topP > BigDecimal.ZERO && topP <= BigDecimal.ONE) } }
public enum class GenerationFinishReason { STOP, TOKEN_LIMIT, TOOL_CALL, INTERRUPTED, FAILED }
public data class TokenChunk(
    public val requestId: EntityId,
    public val sequence: Long,
    public val text: String,
    public val tokenIds: SnapshotList<Int>,
    public val finishReason: GenerationFinishReason?,
    public val usage: Knowledge<Usage>,
) { init { require(sequence >= 0) } }
public interface LocalInference {
    public suspend fun load(model: ModelSelection, profile: RuntimeProfile, cancel: CancelToken): Outcome<ModelSession>
    public fun generate(session: ModelSession, request: GenerationRequest, cancel: CancelToken): Flow<Outcome<TokenChunk>>
    public suspend fun unload(session: ModelSession): Unit
}

public enum class LabMode { ANALYZE, UPGRADE, BREW, EXPLAIN, CUSTOM }
public data class PromptVariable(public val name: String, public val required: Boolean, public val description: String)
public data class PromptTemplate(
    public val id: EntityId,
    public val shippedVersion: Int,
    public val title: String,
    public val body: String,
    public val overrideBody: Knowledge<String>,
    public val variables: SnapshotList<PromptVariable>,
) { init { require(shippedVersion > 0) } }
public data class PromptBinding(public val name: String, public val value: String)
public data class PromptArtifact(
    public val templateId: EntityId,
    public val templateVersion: Int,
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val rendered: String,
    public val bindings: SnapshotList<PromptBinding>,
    public val contextHash: ContentHash,
    public val createdAt: UtcTimestamp,
)
public data class DeckContext(
    public val deck: DeckSnapshot,
    public val catalog: CatalogSnapshot,
    public val format: Knowledge<FormatDefinition>,
    public val analysis: Knowledge<AnalysisResult>,
    public val inventory: Knowledge<InventorySnapshot>,
    public val quotes: Knowledge<QuoteSnapshot>,
    public val bracket: Knowledge<BracketAssessment>,
    public val constraints: ChangeConstraints,
    public val includePrivateNotes: Boolean,
)
public data class ToolPolicy(public val maximumCalls: Int = 8, public val maximumRows: Int = 50, public val maximumDuration: Duration) { init { require(maximumCalls in 0..8 && maximumRows in 1..50); require(maximumDuration > Duration.ZERO && maximumDuration <= Duration.parse("5s")) } }
public data class LabRequest(
    public val id: EntityId,
    public val conversationId: EntityId,
    public val context: DeckContext,
    public val mode: LabMode,
    public val prompt: PromptArtifact,
    public val provider: ProviderProfile?,
    public val localSession: ModelSession?,
    public val generation: GenerationRequest,
    public val toolPolicy: ToolPolicy,
) { init { require((provider == null) != (localSession == null)) } }
public data class Usage(
    public val inputTokens: Knowledge<Long>,
    public val outputTokens: Knowledge<Long>,
    public val cachedInputTokens: Knowledge<Long>,
    public val billedAmount: Knowledge<DecimalAmount>,
    public val providerRequestId: Knowledge<String>,
)
public sealed interface LabEvent {
    public data class TextDelta(public val text: String, public val sequence: Long) : LabEvent
    public data class ToolStarted(public val callId: String, public val name: String) : LabEvent
    public data class ToolFinished(public val callId: String, public val rows: Int, public val evidence: SnapshotList<Evidence>) : LabEvent { init { require(rows in 0..50) } }
    public data class Proposal(public val reply: ValidatedReply) : LabEvent
    public data class Finished(public val reason: GenerationFinishReason, public val usage: Usage) : LabEvent
}
public data class ParsedCard(
    public val rawName: String,
    public val identity: Knowledge<CardIdentity>,
    public val quantity: Int?,
    public val evidence: SnapshotList<Evidence>,
) { init { require(quantity == null || quantity > 0) } }
public data class ValidatedReply(
    public val rawText: String,
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val cards: SnapshotList<ParsedCard>,
    public val proposedChanges: SnapshotList<CardChange>,
    public val issues: SnapshotList<DomainIssue>,
    public val legality: LegalityStatus,
    public val costDelta: Knowledge<DecimalAmount>,
    public val complete: Boolean,
)
public enum class TurnStatus { PENDING, STREAMING, COMPLETE, INTERRUPTED, FAILED }
public data class Turn(
    public val id: EntityId,
    public val conversationId: EntityId,
    public val prompt: PromptArtifact,
    public val mode: LabMode,
    public val deckRevision: Revision,
    public val providerProfileId: EntityId?,
    public val modelId: String,
    public val status: TurnStatus,
    public val rawContent: String,
    public val validated: Knowledge<ValidatedReply>,
    public val usage: Knowledge<Usage>,
    public val startedAt: UtcTimestamp,
    public val finishedAt: Knowledge<UtcTimestamp>,
)
public data class Conversation(
    public val id: EntityId,
    public val deckId: DeckId,
    public val mode: LabMode,
    public val title: String,
    public val createdAt: UtcTimestamp,
    public val modifiedAt: UtcTimestamp,
    public val turns: SnapshotList<Turn>,
)
public interface DeckLab {
    public fun prompt(context: DeckContext, mode: LabMode, template: PromptTemplate): Outcome<PromptArtifact>
    public fun run(request: LabRequest, cancel: CancelToken): Flow<Outcome<LabEvent>>
    public suspend fun models(provider: ProviderProfile, cancel: CancelToken): Outcome<ModelPage>
    public fun validate(reply: String, context: DeckContext): Outcome<ValidatedReply>
}
