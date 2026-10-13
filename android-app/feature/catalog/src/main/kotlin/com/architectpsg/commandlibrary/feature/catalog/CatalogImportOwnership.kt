package com.architectpsg.commandlibrary.feature.catalog

import kotlinx.coroutines.sync.Mutex

/** Small and full importers share one process-wide staging/recovery owner. */
internal object CatalogImportOwnership { val mutex: Mutex = Mutex() }
