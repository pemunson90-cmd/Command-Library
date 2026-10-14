package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*

internal const val EVENT_ARCHIVE_BYTES = 16 * 1024 * 1024
internal const val EVENT_PAGE_BYTES = 32L * 1024 * 1024
internal data class LifeEventKey(val revision: Long, val eventId: Long)
internal data class LifeEventMetadata(val key: LifeEventKey, val groupId: String, val at: UtcTimestamp, val undo: Long?, val length: Int) {
    companion object {
        val columns: String = (listOf("event_id", "revision", "at_seconds", "at_nano", "undo_of_event_id").map(::typedGameInteger) +
            boundedGameText("group_id") + "typeof(archive_payload) AS archive_type" + "length(archive_payload) AS archive_length").joinToString(",")
        fun read(row: RowData, parentRevision: Long): LifeEventMetadata {
            val id = row.gameRequiredLong("event_id")
            val revision = row.gameRequiredLong("revision")
            val undo = row.gameOptionalLong("undo_of_event_id")
            corruptGame(id >= 0 && revision in 1..parentRevision, "Invalid event identity/revision")
            corruptGame(undo == null || (undo >= 0 && undo != id), "Invalid undo target identity")
            val group = row.gameRequiredText("group_id")
            gameIdentity(group, FailureCode.CORRUPT_DATA)
            val at = gameTimestamp(row.gameRequiredLong("at_seconds"), row.gameRequiredLong("at_nano"))
            val length = row.gameRequiredLong("archive_length")
            corruptGame((row.gameColumn("archive_type") as? SqlValue.Text)?.value == "blob" && length in 1..EVENT_ARCHIVE_BYTES.toLong(), "Invalid event archive type/size")
            return LifeEventMetadata(LifeEventKey(revision, id), group, at, undo, length.toInt())
        }
    }
}
