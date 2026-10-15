package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.CatalogImport
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.CatalogLookup
import com.architectpsg.commandlibrary.core.model.CatalogStatus
import com.architectpsg.commandlibrary.core.model.SettingsRepository
import com.architectpsg.commandlibrary.core.model.LocalRulesImport
import com.architectpsg.commandlibrary.core.model.LocalRulesLookup
import com.architectpsg.commandlibrary.core.model.PlayerProfileRepository
import com.architectpsg.commandlibrary.core.model.CatalogRulings
import com.architectpsg.commandlibrary.core.model.CatalogTextLookup
import com.architectpsg.commandlibrary.core.model.KeywordRulesLookup
import com.architectpsg.commandlibrary.core.model.CatalogPrintingLookup
import com.architectpsg.commandlibrary.core.model.CardLinks

/**
 * The accepted core capabilities the shell presents. Root assembly supplies real adapters, created
 * once per process; the shell keeps the instances it first receives for the life of its ViewModel.
 *
 * [importLimits] must be the same limits the [catalogImport] adapter enforces. The shell discloses
 * them to the user, stops reading a selected file once [CatalogImportLimits.maxInputBytes] is
 * exceeded, and never relaxes them.
 *
 * Rules are optional: both [rulesImport] and [rulesLookup] must be supplied before that destination
 * appears. The local rules adapter must enforce the shell's disclosed 8 MiB document bound.
 */
public class ShellPorts(
    public val catalogLookup: CatalogLookup,
    public val catalogStatus: CatalogStatus,
    public val catalogImport: CatalogImport,
    public val settings: SettingsRepository,
    public val importLimits: CatalogImportLimits = CatalogImportLimits(),
    public val rulesImport: LocalRulesImport? = null,
    public val rulesLookup: LocalRulesLookup? = null,
    /** Complete reusable-profile capability; absence hides the destination. No game service implied. */
    public val playerProfiles: PlayerProfileRepository? = null,
    /** Complete lazy English download/install access; no wire activity during shell startup. */
    public val englishCatalog: EnglishCatalogAccess? = null,
    /** Independent offline ruling snapshots; reading never initializes acquisition. */
    public val rulings: CatalogRulings? = null,
    /** Complete ruling acquisition/install access sharing the root's English process owner. */
    public val rulingsDownloads: RulingsCatalogAccess? = null,
    /** Complete independent indexed name/Oracle text reader, with no acquisition initialization. */
    public val catalogTextLookup: CatalogTextLookup? = null,
    /** Exact installed glossary links, independent of acquisition and literal search fallback. */
    public val keywordRulesLookup: KeywordRulesLookup? = null,
    /** Independent local printing reader; no acquisition initialization. */
    public val catalogPrintings: CatalogPrintingLookup? = null,
    /** Complete supplied/template resolver; rendering never performs network IO. */
    public val cardLinks: CardLinks? = null,
    /** Complete local library; absent until a real reviewed adapter is bound. */
    public val localDecks: com.architectpsg.commandlibrary.core.model.LocalDeckLibrary? = null,
    /** Real platform clock used for explicitly confirmed local deck creation. */
    public val utcClock: com.architectpsg.commandlibrary.core.model.Clock? = null,
    /** Complete persistent primitive edits and grouped undo; bound only with the real adapter. */
    public val localDeckEdits: com.architectpsg.commandlibrary.core.model.LocalDeckEdits? = null,
    /** Complete named checkpoint/preview/restore capability; absent until the real adapter is bound. */
    public val localDeckVersions: com.architectpsg.commandlibrary.core.model.LocalDeckVersions? = null,
    /** Complete durable inventory, bound only with the reviewed real adapter. */
    public val localInventory: com.architectpsg.commandlibrary.core.model.LocalInventory? = null,
    /** Pure quantity allocation, without quotes or global reservations. */
    public val ownedQuantity: com.architectpsg.commandlibrary.core.model.OwnedQuantityAllocator? = null,
    /** The accepted pure projection capability; a proposal never grants write authority. */
    public val inventoryProjection: ((com.architectpsg.commandlibrary.core.model.InventorySnapshot,
        com.architectpsg.commandlibrary.core.model.CollectionPatch, com.architectpsg.commandlibrary.core.model.Revision,
        com.architectpsg.commandlibrary.core.model.CancelToken) -> com.architectpsg.commandlibrary.core.model.Outcome<com.architectpsg.commandlibrary.core.model.InventoryProjection>)? = null,
    /** Complete raw recovery/preparation/commit capability; bind only after real adapter acceptance. */
    public val localDeckDrafts: com.architectpsg.commandlibrary.core.model.LocalDeckDrafts? = null,
    /** Shared canonical projection; enabled together with the complete durable draft capability. */
    public val deckDraftPlanner: com.architectpsg.commandlibrary.core.model.DeckDraftPlanner? = null,
)
