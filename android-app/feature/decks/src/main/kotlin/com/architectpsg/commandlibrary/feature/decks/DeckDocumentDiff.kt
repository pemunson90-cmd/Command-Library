package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*

/** Complete immutable comparison report; it does not authorize restore or package mutation. */
public object DeckDocumentDiff {
    public fun compare(before: DeckSnapshot, after: DeckSnapshot): Outcome<DeckDifference> {
        val entryChanges = when (val result = DeckEntryDiff.compare(before, after)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> return result
            is Outcome.Unknown -> return result
            is Outcome.Unavailable -> return result
        }
        val fields = DeckChangedField.entries.filter { field ->
            when (field) {
                DeckChangedField.NAME -> before.name != after.name
                DeckChangedField.FORMAT -> before.format != after.format
                DeckChangedField.DESCRIPTION -> before.description != after.description
                DeckChangedField.PRIMER -> before.primer != after.primer
                DeckChangedField.FOLDER -> before.folder != after.folder
                DeckChangedField.TAGS -> before.tags != after.tags
                DeckChangedField.CREATED_AT -> before.createdAt != after.createdAt
                DeckChangedField.MODIFIED_AT -> before.modifiedAt != after.modifiedAt
                DeckChangedField.REVISION -> before.revision != after.revision
                DeckChangedField.BUDGET -> before.budget != after.budget
                DeckChangedField.PROFILE -> before.profileId != after.profileId
                DeckChangedField.CATALOG_REFERENCES -> before.catalog != after.catalog
                DeckChangedField.CATEGORIES -> before.categoryOrder != after.categoryOrder
                DeckChangedField.PACKAGES -> before.packageInstances != after.packageInstances
                DeckChangedField.ENTRY_ORDER -> before.entries.map { it.id } != after.entries.map { it.id }
            }
        }
        return Outcome.Success(DeckDifference(before, after, SnapshotList.copyOf(fields), entryChanges))
    }
}
