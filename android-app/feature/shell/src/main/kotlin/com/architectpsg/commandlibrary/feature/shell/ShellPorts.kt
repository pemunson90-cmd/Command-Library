package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.CatalogImport
import com.architectpsg.commandlibrary.core.model.CatalogImportLimits
import com.architectpsg.commandlibrary.core.model.CatalogLookup
import com.architectpsg.commandlibrary.core.model.CatalogStatus
import com.architectpsg.commandlibrary.core.model.SettingsRepository

/**
 * The accepted core capabilities the shell presents. Root assembly supplies real adapters, created
 * once per process; the shell keeps the instances it first receives for the life of its ViewModel.
 *
 * [importLimits] must be the same limits the [catalogImport] adapter enforces. The shell discloses
 * them to the user, stops reading a selected file once [CatalogImportLimits.maxInputBytes] is
 * exceeded, and never relaxes them.
 */
public class ShellPorts(
    public val catalogLookup: CatalogLookup,
    public val catalogStatus: CatalogStatus,
    public val catalogImport: CatalogImport,
    public val settings: SettingsRepository,
    public val importLimits: CatalogImportLimits = CatalogImportLimits(),
)
