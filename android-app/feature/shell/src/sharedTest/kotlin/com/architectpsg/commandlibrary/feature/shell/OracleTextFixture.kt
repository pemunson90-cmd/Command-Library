package com.architectpsg.commandlibrary.feature.shell

import com.architectpsg.commandlibrary.core.model.*

/** Explicit synthetic port for UI/controller routing; actual FTS semantics use SQLite tests. */
internal class OracleTextFixture(private val catalog: SyntheticCatalog) : CatalogTextLookup {
    val requests = mutableListOf<Pair<CatalogTextField, String>>()
    override suspend fun lookupText(field: CatalogTextField, input: String, page: PageRequest, cancel: CancelToken): Outcome<Page<CardRecord>> {
        cancel.throwIfCancelled(); requests += field to input
        if (field == CatalogTextField.NAME) return catalog.lookupName(input, page, cancel)
        val generation = catalog.active?.id
        return Outcome.Success(Page(SnapshotList.of(syntheticCard("Synthetic text-only match", generation, "Draw a synthetic card.")), null,
            SnapshotReference(SYNTHETIC_PROVIDER, generation, null, LanguageTag.ENGLISH)))
    }
}
