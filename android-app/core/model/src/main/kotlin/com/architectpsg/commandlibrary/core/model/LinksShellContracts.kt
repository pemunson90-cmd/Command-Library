package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

public enum class LinkResolutionKind { SUPPLIED, VERIFIED_VENDOR, TEMPLATE, SEARCH_FALLBACK }
public data class LinkDestination(public val id: EntityId, public val name: String, public val allowedOrigins: SnapshotList<Origin>, public val enabled: Boolean)
public data class LinkTemplate(public val destinationId: EntityId, public val pattern: String, public val allowedTokens: SnapshotList<String>)
public data class ResolvedLink(
    public val destination: LinkDestination,
    public val uri: ResourceUri,
    public val kind: LinkResolutionKind,
    public val selectedPrinting: CardId?,
    public val provenance: Provenance?,
    public val warning: Knowledge<String>,
)
public data class LinkPreferences(
    public val destinations: SnapshotList<LinkDestination>,
    public val templates: SnapshotList<LinkTemplate>,
    public val preferSelectedPrinting: Boolean,
    public val allowSearchFallback: Boolean,
)
public data class LinkTokenValues(
    public val name: String,
    public val oracleId: Knowledge<OracleId>,
    public val printingId: Knowledge<CardId>,
    public val setCode: Knowledge<String>,
    public val collectorNumber: Knowledge<String>,
    public val language: LanguageTag,
    public val vendorIds: SnapshotList<ProviderIdentity>,
)
public interface CardLinks {
    public fun resolve(card: CardRecord, printing: PrintingRecord?, preferences: LinkPreferences): Outcome<SnapshotList<ResolvedLink>>
    public fun template(pattern: String, values: LinkTokenValues): Outcome<ResourceUri>
}

public enum class ScreenDestination { CATALOG, CARD, RULES, RULE_ENTRY, DECKS, DECK, COLLECTION, PRICING, VALIDATION, BRACKETS, RECOMMENDATIONS, TRANSFER, GAME, DECK_LAB, MODELS, SETTINGS, SOURCE_STATUS }
public sealed interface ScreenReference {
    public data class Card(public val id: CardId) : ScreenReference
    public data class Oracle(public val id: OracleId) : ScreenReference
    public data class Rule(public val id: RuleId) : ScreenReference
    public data class Deck(public val id: DeckId) : ScreenReference
    public data class Game(public val id: GameId) : ScreenReference
    public data class Entity(public val id: EntityId) : ScreenReference
}
public data class ScreenIntent(
    public val destination: ScreenDestination,
    public val reference: ScreenReference?,
    public val query: String?,
    public val expectedRevision: Revision?,
)
public data class LogicalRect(public val left: Int, public val top: Int, public val width: Int, public val height: Int) { init { require(width >= 0 && height >= 0) } }
public enum class WindowPosture { FLAT, BOOK, TABLETOP }
public data class WindowGeometry(
    public val width: Int,
    public val height: Int,
    public val densityMilli: Int,
    public val posture: WindowPosture,
    public val safeRegions: SnapshotList<LogicalRect>,
    public val occludedRegions: SnapshotList<LogicalRect>,
    public val textScaleMilli: Int,
) { init { require(width > 0 && height > 0 && densityMilli > 0 && textScaleMilli > 0) } }
public sealed interface ViewState<out T> {
    public data class Loading<T>(public val previous: T?, public val progress: JobProgress?) : ViewState<T>
    public data class Ready<T>(public val snapshot: T, public val revision: Revision?, public val issues: SnapshotList<DomainIssue>) : ViewState<T>
    public data class Unavailable<T>(public val reason: UnavailableReason, public val detail: String, public val previous: T?) : ViewState<T>
    public data class Failed<T>(public val error: DomainFailure, public val previous: T?) : ViewState<T>
    public data class Unknown<T>(public val reason: String, public val evidence: SnapshotList<Evidence>, public val previous: T?) : ViewState<T>
}
public sealed interface UiIntent {
    public data class Navigate(public val screen: ScreenIntent) : UiIntent
    public data class Search(public val text: String, public val page: PageRequest) : UiIntent
    public data class EditDeck(public val id: DeckId, public val patch: DeckPatch, public val expected: Revision) : UiIntent
    public data class EditSettings(public val patch: SettingsPatch, public val expected: Revision) : UiIntent
    public data class EditCollection(public val patch: CollectionPatch, public val expected: Revision) : UiIntent
    public data class Game(public val id: GameId, public val action: GameAction, public val expected: Revision) : UiIntent
    public data class ApproveDownload(public val intent: DownloadIntent) : UiIntent
    public data class CancelJob(public val jobId: EntityId) : UiIntent
    public data class Retry(public val operationId: EntityId) : UiIntent
}
public interface NavigationHost {
    public suspend fun open(intent: ScreenIntent): Unit
    public fun applyWindow(geometry: WindowGeometry): Unit
}
public interface FeaturePresenter<T> {
    public fun watch(): Flow<ViewState<T>>
    public suspend fun dispatch(action: UiIntent, cancel: CancelToken): Unit
}
