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
)
