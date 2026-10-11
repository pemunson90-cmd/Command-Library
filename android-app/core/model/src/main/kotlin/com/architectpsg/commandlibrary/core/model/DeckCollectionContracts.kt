package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal

public enum class BoardRole { MAIN, SIDEBOARD, MAYBEBOARD, COMMANDER, PARTNER, BACKGROUND, COMPANION }
public data class EntryLabel(public val id: EntityId, public val name: String)
public data class DeckEntry(
    public val id: EntityId,
    public val identity: CardIdentity,
    public val quantity: Int,
    public val board: BoardRole,
    public val finish: Knowledge<Finish>,
    public val notes: String,
    public val labels: SnapshotList<EntryLabel>,
    public val roleOverrides: SnapshotList<RoleOverride>,
) { init { require(quantity > 0) } }
public data class DeckSnapshot(
    public val id: DeckId,
    public val name: String,
    public val format: Knowledge<FormatId>,
    public val description: String,
    public val folder: Knowledge<String>,
    public val tags: SnapshotList<String>,
    public val createdAt: UtcTimestamp,
    public val modifiedAt: UtcTimestamp,
    public val revision: Revision,
    public val entries: SnapshotList<DeckEntry>,
    public val budget: Knowledge<DecimalAmount>,
    public val profileId: EntityId?,
    public val catalog: SnapshotList<SnapshotReference>,
    public val packageInstances: SnapshotList<PackageInstance>,
)
public sealed interface DeckEdit {
    public data class Rename(public val name: String) : DeckEdit
    public data class Description(public val text: String) : DeckEdit
    public data class Format(public val format: Knowledge<FormatId>) : DeckEdit
    public data class Metadata(
        public val folder: Knowledge<String>,
        public val tags: SnapshotList<String>,
        public val budget: Knowledge<DecimalAmount>,
        public val profileId: EntityId?,
    ) : DeckEdit
    public data class Add(public val entry: DeckEntry) : DeckEdit
    public data class Replace(public val entry: DeckEntry) : DeckEdit
    public data class Remove(public val entryId: EntityId) : DeckEdit
    public data class RestoreVersion(public val versionId: EntityId) : DeckEdit
    public data class ApplyPackage(public val preview: PackagePreview) : DeckEdit
}
public data class DeckPatch(public val edits: SnapshotList<DeckEdit>, public val reason: String)
public data class DeckVersion(
    public val id: EntityId,
    public val deckId: DeckId,
    public val label: String,
    public val createdAt: UtcTimestamp,
    public val sourceRevision: Revision,
    public val manifest: DeckSnapshot,
    public val predecessorVersionId: EntityId?,
)
public enum class PackageLinkPolicy { LINKED, COPY }
public data class CardPackage(
    public val id: EntityId,
    public val name: String,
    public val description: String,
    public val currentVersionId: EntityId,
    public val tags: SnapshotList<String>,
)
public data class PackageVersion(
    public val id: EntityId,
    public val packageId: EntityId,
    public val number: Int,
    public val createdAt: UtcTimestamp,
    public val entries: SnapshotList<DeckEntry>,
    public val notes: String,
) { init { require(number > 0) } }
public data class PackageContribution(public val packageEntryId: EntityId, public val deckEntryId: EntityId, public val quantity: Int) { init { require(quantity > 0) } }
public data class PackageInstance(
    public val id: EntityId,
    public val packageId: EntityId,
    public val versionId: EntityId,
    public val policy: PackageLinkPolicy,
    public val contributions: SnapshotList<PackageContribution>,
)
public sealed interface PackageAction {
    public data class Add(public val version: PackageVersion, public val policy: PackageLinkPolicy) : PackageAction
    public data class Update(public val instanceId: EntityId, public val version: PackageVersion) : PackageAction
    public data class Remove(public val instanceId: EntityId) : PackageAction
}
public data class PackagePreview(
    public val id: EntityId,
    public val deckId: DeckId,
    public val sourceRevision: Revision,
    public val action: PackageAction,
    public val changes: SnapshotList<CardChange>,
    public val contributions: SnapshotList<PackageContribution>,
    public val issues: SnapshotList<DomainIssue>,
)
public data class AnalysisPolicy(
    public val tagVersion: String,
    public val includeBoards: SnapshotList<BoardRole>,
    public val includeUnresolved: Boolean,
    public val sampleSeed: Long?,
    public val sampleCount: Int,
) { init { require(sampleCount >= 0) } }
public data class ManaDistribution(public val manaValue: Knowledge<BigDecimal>, public val quantity: Int) { init { require(quantity >= 0) } }
public data class RoleTag(public val name: String, public val automaticVersion: String, public val evidence: SnapshotList<Evidence>)
public data class RoleOverride(
    public val entryId: EntityId?,
    public val oracleId: Knowledge<OracleId>,
    public val role: String,
    public val enabled: Boolean,
    public val modifiedAt: UtcTimestamp,
)
public data class EntryAnalysis(
    public val entryId: EntityId,
    public val roles: SnapshotList<RoleTag>,
    public val unresolved: Boolean,
    public val evidence: SnapshotList<Evidence>,
)
public data class AnalysisResult(
    public val deckId: DeckId,
    public val deckRevision: Revision,
    public val catalog: SnapshotList<SnapshotReference>,
    public val policy: AnalysisPolicy,
    public val manaCurve: SnapshotList<ManaDistribution>,
    public val entries: SnapshotList<EntryAnalysis>,
    public val issues: SnapshotList<DomainIssue>,
    public val complete: Boolean,
)
public interface DeckRepository {
    public suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot>
    public suspend fun apply(id: DeckId, patch: DeckPatch, expected: Revision, cancel: CancelToken): Outcome<DeckSnapshot>
    public suspend fun snapshot(id: DeckId, label: String, cancel: CancelToken): Outcome<DeckVersion>
    public suspend fun previewPackage(id: DeckId, action: PackageAction, cancel: CancelToken): Outcome<PackagePreview>
}
public interface DeckAnalysis {
    public fun analyze(deck: DeckSnapshot, policy: AnalysisPolicy): Outcome<AnalysisResult>
}

public enum class CardCondition { MINT, NEAR_MINT, LIGHT_PLAYED, MODERATE_PLAYED, HEAVY_PLAYED, DAMAGED, UNKNOWN }
public data class OwnedLot(
    public val id: EntityId,
    public val identity: CardIdentity,
    public val finish: Knowledge<Finish>,
    public val condition: CardCondition,
    public val location: String,
    public val quantity: Int,
    public val notes: String,
    public val revision: Revision,
    public val acquiredAt: Knowledge<UtcTimestamp>,
) { init { require(quantity >= 0) } }
public data class InventorySnapshot(
    public val revision: Revision,
    public val capturedAt: UtcTimestamp,
    public val lots: SnapshotList<OwnedLot>,
    public val complete: Boolean,
)
public data class CollectionQuery(
    public val text: String?,
    public val printingId: CardId?,
    public val oracleId: OracleId?,
    public val languages: SnapshotList<LanguageTag>,
    public val finishes: SnapshotList<Finish>,
    public val location: String?,
    public val includeZeroQuantity: Boolean,
)
public sealed interface CollectionEdit {
    public data class Add(public val lot: OwnedLot) : CollectionEdit
    public data class Replace(public val lot: OwnedLot) : CollectionEdit
    public data class Adjust(public val lotId: EntityId, public val delta: Int, public val reason: String) : CollectionEdit
    public data class Remove(public val lotId: EntityId, public val reason: String) : CollectionEdit
}
public data class CollectionPatch(public val edits: SnapshotList<CollectionEdit>)
public data class InventoryAdjustment(
    public val id: EntityId,
    public val lotId: EntityId,
    public val beforeQuantity: Int,
    public val afterQuantity: Int,
    public val reason: String,
    public val at: UtcTimestamp,
    public val reversalOf: EntityId?,
) { init { require(beforeQuantity >= 0 && afterQuantity >= 0) } }
public data class CollectionPreview(
    public val id: EntityId,
    public val sourceRevision: Revision,
    public val patch: CollectionPatch,
    public val resultingLots: SnapshotList<OwnedLot>,
    public val adjustments: SnapshotList<InventoryAdjustment>,
    public val issues: SnapshotList<DomainIssue>,
)
public interface CollectionRepository {
    public suspend fun list(query: CollectionQuery, page: PageRequest, cancel: CancelToken): Outcome<Page<OwnedLot>>
    public suspend fun preview(patch: CollectionPatch, cancel: CancelToken): Outcome<CollectionPreview>
    public suspend fun apply(preview: CollectionPreview, expected: Revision, cancel: CancelToken): Outcome<Unit>
}

public enum class PlaytestZone { LIBRARY, HAND, BATTLEFIELD, GRAVEYARD, EXILE, COMMAND, SIDEBOARD }
public data class PlaytestCard(public val instanceId: EntityId, public val entryId: EntityId, public val identity: CardIdentity)
public data class PlaytestZoneState(public val zone: PlaytestZone, public val cards: SnapshotList<PlaytestCard>)
public data class PlaytestScenario(
    public val id: EntityId,
    public val version: Int,
    public val seed: Long,
    public val deck: DeckSnapshot,
    public val disruption: Knowledge<String>,
    public val zones: SnapshotList<PlaytestZoneState>,
)
public sealed interface PlaytestAction {
    public data class Move(public val instanceId: EntityId, public val from: PlaytestZone, public val to: PlaytestZone, public val position: Int) : PlaytestAction
    public data class Shuffle(public val zone: PlaytestZone, public val seed: Long) : PlaytestAction
    public data class Note(public val text: String) : PlaytestAction
}
public data class PlaytestEvent(
    public val id: Long,
    public val groupId: EntityId,
    public val scenarioId: EntityId,
    public val action: PlaytestAction,
    public val at: UtcTimestamp,
    public val undoOf: Knowledge<Long>,
) { init { require(id >= 0) } }
