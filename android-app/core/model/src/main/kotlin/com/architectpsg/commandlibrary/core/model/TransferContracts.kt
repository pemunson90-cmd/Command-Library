package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

/** Byte ownership is external and streaming; no platform file handles cross this boundary. */
public data class TransferInput(
    public val id: EntityId,
    public val displayName: String,
    public val mediaType: Knowledge<String>,
    public val expectedBytes: Knowledge<Long>,
    public val chunks: Flow<ByteChunk>,
)
public enum class ImportTarget { DECK, COLLECTION, PACKAGE, PROFILE, GAME, SETTINGS, RULES, VARIANT, BACKUP }
public enum class ImportFormat { NATIVE, CSV, TEXT_LIST, QR }
public data class ImportProfile(
    public val format: ImportFormat,
    public val target: ImportTarget,
    public val language: LanguageTag,
    public val source: ProviderId?,
    public val maxBytes: Long,
    public val maxRows: Int,
    public val maxRowBytes: Int,
    public val delimiter: Char?,
    public val schemaVersion: Int?,
) { init { require(maxBytes > 0 && maxRows > 0 && maxRowBytes > 0) } }
public data class RowLocation(public val sourceName: String, public val rowNumber: Long, public val byteOffset: Long?, public val fieldName: String?) { init { require(rowNumber >= 0); require(byteOffset == null || byteOffset >= 0) } }
public data class ImportIssue(
    public val location: RowLocation?,
    public val code: String,
    public val severity: IssueSeverity,
    public val description: String,
    public val resolution: Knowledge<String>,
)
public sealed interface ImportPayload {
    public data class DeckCard(public val identity: CardIdentity, public val quantity: Int, public val board: BoardRole, public val notes: String) : ImportPayload { init { require(quantity > 0) } }
    public data class InventoryLot(public val lot: OwnedLot) : ImportPayload
    public data class Profile(public val profile: ProfileExchange) : ImportPayload
    public data class Game(public val game: GameExchange) : ImportPayload
    public data class Package(public val version: PackageVersion) : ImportPayload
    public data class Variant(public val definition: VariantDefinition) : ImportPayload
    public data class Rules(public val entry: RuleEntry) : ImportPayload
    public data class Preferences(public val settings: RestorableSettings) : ImportPayload
}
public data class ImportRow(
    public val location: RowLocation,
    public val rawText: String,
    public val payload: Knowledge<ImportPayload>,
    public val issues: SnapshotList<ImportIssue>,
)
public data class ImportPreview(
    public val id: EntityId,
    public val inputId: EntityId,
    public val profile: ImportProfile,
    public val rows: SnapshotList<ImportRow>,
    public val totalRows: Long,
    public val issues: SnapshotList<ImportIssue>,
    public val unresolvedIdentities: SnapshotList<CardIdentity>,
    public val losses: SnapshotList<String>,
    public val expectedRevision: Revision?,
    public val complete: Boolean,
)
public data class InterchangeVersion(public val schemaVersion: Int, public val appVersion: String) { init { require(schemaVersion > 0 && appVersion.isNotBlank()) } }
public data class ManifestDataset(
    public val kind: ImportTarget,
    public val count: Long,
    public val path: String,
    public val checksum: ContentHash,
    public val included: Boolean,
) { init { require(count >= 0) } }
public data class LicenseDecision(public val source: ProviderId, public val notice: SourceNotice, public val included: Boolean, public val reason: String)
public data class TransferManifest(
    public val version: InterchangeVersion,
    public val createdAt: UtcTimestamp,
    public val datasets: SnapshotList<ManifestDataset>,
    public val licenses: SnapshotList<LicenseDecision>,
    public val totalBytes: Long,
    public val containsSecrets: Boolean = false,
) { init { require(totalBytes >= 0); require(!containsSecrets) } }
public data class RestorableSettings(
    public val dataLanguage: LanguageTag,
    public val theme: ThemePreference,
    public val storageMode: StorageMode,
    public val cache: CachePolicy,
    public val budget: Knowledge<DecimalAmount>,
    public val promptOverrides: SnapshotList<PromptTemplate>,
)
public data class LocalArtReference(public val id: EntityId, public val relativePath: String, public val checksum: ContentHash)
public data class ProfileExchange(
    public val version: InterchangeVersion,
    public val id: EntityId,
    public val name: String,
    public val preferredLife: Int,
    public val counterDefinitions: SnapshotList<CounterDefinition>,
    public val art: SnapshotList<LocalArtReference>,
    public val preferences: RestorableSettings,
    public val lifeSetup: PlayerProfileDraft? = null,
) {
    init {
        lifeSetup?.let { setup ->
            require(name == setup.name && preferredLife == setup.preferredLife && counterDefinitions == setup.counterDefinitions) {
                "Profile exchange fields must agree with its explicit life setup"
            }
        }
    }
}
public data class GameExchange(
    public val version: InterchangeVersion,
    public val snapshot: GameSnapshot,
    public val events: SnapshotList<GameEvent>,
    public val result: Knowledge<GameResult>,
    public val variantDefinitions: SnapshotList<VariantDefinition>,
)
public data class SharePrivacy(
    public val includeNotes: Boolean,
    public val includePlayerNames: Boolean,
    public val includeHistory: Boolean,
    public val includePersonalArt: Boolean,
)
public data class ShareManifest(
    public val version: InterchangeVersion,
    public val manifest: TransferManifest,
    public val privacy: SharePrivacy,
    public val hash: ContentHash,
)
public data class QrFrame(
    public val shareId: EntityId,
    public val index: Int,
    public val count: Int,
    public val contentHash: ContentHash,
    public val payload: SnapshotBytes,
) { init { require(count in 1..1000 && index in 0 until count) } }
public sealed interface ExportSelection {
    public data class Deck(public val snapshot: DeckSnapshot) : ExportSelection
    public data class Collection(public val snapshot: InventorySnapshot) : ExportSelection
    public data class Profile(public val profile: ProfileExchange) : ExportSelection
    public data class Game(public val game: GameExchange) : ExportSelection
    public data class Backup(public val selectedDatasets: SnapshotList<ImportTarget>, public val includePayloads: Boolean) : ExportSelection
}
public data class ExportRequest(
    public val selection: ExportSelection,
    public val format: ImportFormat,
    public val version: InterchangeVersion,
    public val privacy: SharePrivacy,
    public val maxBytes: Long,
) { init { require(maxBytes > 0) } }
public data class ByteArtifact(
    public val fileName: String,
    public val mediaType: String,
    public val bytes: SnapshotBytes,
    public val checksum: ContentHash,
    public val manifest: TransferManifest?,
    public val losses: SnapshotList<String>,
)
public data class RestoreDataset(
    public val kind: ImportTarget,
    public val importedCount: Long,
    public val existingCount: Long,
    public val selected: Boolean,
    public val expectedRevision: Revision?,
) { init { require(importedCount >= 0 && existingCount >= 0) } }
public data class RestorePreview(
    public val id: EntityId,
    public val inputId: EntityId,
    public val manifest: TransferManifest,
    public val datasets: SnapshotList<RestoreDataset>,
    public val issues: SnapshotList<ImportIssue>,
    public val recoveryPointId: EntityId,
    public val absentSecretReferences: SnapshotList<EntityId>,
    public val optionalDownloads: SnapshotList<DownloadIntent>,
    public val complete: Boolean,
)
public interface TransferService {
    public fun parse(input: TransferInput, profile: ImportProfile, cancel: CancelToken): Flow<Outcome<ImportRow>>
    public suspend fun preview(input: TransferInput, profile: ImportProfile, cancel: CancelToken): Outcome<ImportPreview>
    public suspend fun export(request: ExportRequest, cancel: CancelToken): Outcome<ByteArtifact>
    public suspend fun inspectBackup(input: TransferInput, cancel: CancelToken): Outcome<RestorePreview>
    public suspend fun restore(preview: RestorePreview, cancel: CancelToken): Outcome<Unit>
}
