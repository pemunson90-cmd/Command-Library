package com.architectpsg.commandlibrary.feature.catalog

import com.architectpsg.commandlibrary.core.model.*
import com.google.gson.JsonObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Source literals and v1 projection agreement, separate from any deck-construction judgment. */
internal object CatalogLegalityEvidence {
    data class Selected(val legalitiesKnown: Boolean, val projectedStatus: String?, val status: LegalityStatus?)

    suspend fun check(cancel: CancelToken) { currentCoroutineContext().ensureActive(); cancel.throwIfCancelled() }

    suspend fun parse(raw: String, cancel: CancelToken): JsonObject {
        check(cancel)
        CatalogDeckFactsFields.textBytes(raw, DeckStatisticsLimits.MAX_RAW_RECORD_BYTES, cancel)
        val value = ScryfallCodec.parse(raw, 32)
        check(cancel)
        val faces = value.get("card_faces")
        if (faces != null && !faces.isJsonNull)
            require(faces.isJsonArray && faces.asJsonArray.size() <= DeckStatisticsLimits.MAX_FACES)
        return value
    }

    suspend fun selected(value: JsonObject, format: String, cancel: CancelToken): Selected {
        check(cancel)
        val legalities = value.get("legalities")
        if (legalities == null || legalities.isJsonNull) return Selected(false, null, null)
        require(legalities.isJsonObject)
        val field = legalities.asJsonObject.get(format) ?: return Selected(true, null, null)
        if (field.isJsonNull) return Selected(true, "UNKNOWN", null)
        require(field.isJsonPrimitive && field.asJsonPrimitive.isString)
        val status = when (field.asString) {
            "legal" -> LegalityStatus.LEGAL
            "banned" -> LegalityStatus.BANNED
            "restricted" -> LegalityStatus.RESTRICTED
            "not_legal" -> LegalityStatus.NOT_LEGAL
            else -> null
        }
        check(cancel)
        return Selected(true, status?.name ?: "UNKNOWN", status)
    }

    fun identity(value: String, cancel: CancelToken) {
        CatalogDeckFactsFields.textBytes(value, 128, cancel)
        require(value.isNotBlank() && '\u0000' !in value)
    }

    fun unknown(): Knowledge<LegalityStatus> = Knowledge.Unknown(
        "Source printing eligibility is missing, unsupported or inconsistent", SnapshotList.empty())
}
