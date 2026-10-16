package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.decks.PERSONAL_TAG_HEADER_BYTES
import com.architectpsg.commandlibrary.feature.decks.validatePersonalTags
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal class FuturePersonalTagFormat : IllegalArgumentException()

/** PTAG1: magic/version/revision/time/count, then ordered exact ID/name/retired records. */
internal object PersonalTagPoolWire {
    const val VERSION: Int = 1
    private const val MAGIC: Int = 0x50544147

    fun encode(pool: PersonalTagPool, cancel: CancelToken): ByteArray {
        require(pool.revision.value > 0)
        val modified = requireNotNull(pool.modifiedAt)
        validatePersonalTags(pool.tags, cancel)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(pool.revision.value)
            out.writeLong(modified.epochSeconds); out.writeInt(modified.nanoAdjustment)
            out.writeInt(pool.tags.size)
            for (tag in pool.tags) {
                cancel.throwIfCancelled()
                for (text in listOf(tag.id.value, tag.name)) {
                    val bytes = text.toByteArray(Charsets.UTF_8)
                    out.writeInt(bytes.size); out.write(bytes)
                }
                out.writeByte(if (tag.retired) 1 else 0)
            }
        }
        return buffer.toByteArray().also { require(it.size <= PersonalTagLimits.MAX_PAYLOAD_BYTES) }
    }

    fun decode(bytes: ByteArray, cancel: CancelToken): PersonalTagPool {
        require(bytes.size in PERSONAL_TAG_HEADER_BYTES..PersonalTagLimits.MAX_PAYLOAD_BYTES)
        val reader = ByteBuffer.wrap(bytes)
        require(reader.int == MAGIC)
        val version = reader.int
        if (version > VERSION) throw FuturePersonalTagFormat()
        require(version == VERSION)
        val revision = reader.long.also { require(it > 0) }
        val seconds = reader.long
        val nanos = reader.int.also { require(it in 0..999_999_999) }
        val count = reader.int
        require(count in 0..PersonalTagLimits.MAX_TAGS && count <= reader.remaining() / 11)
        val tags = ArrayList<PersonalTag>()
        repeat(count) {
            cancel.throwIfCancelled()
            val id = string(reader, DeckPayloadLimits.MAX_ID_BYTES)
            val name = string(reader, PersonalTagLimits.MAX_NAME_BYTES)
            require(reader.hasRemaining())
            val retired = when (reader.get().toInt()) { 0 -> false; 1 -> true; else -> throw IllegalArgumentException() }
            tags += PersonalTag(EntityId(id), name, retired)
        }
        require(!reader.hasRemaining())
        return PersonalTagPool(Revision(revision), SnapshotList.copyOf(tags), UtcTimestamp(seconds, nanos)).also {
            validatePersonalTags(it.tags, cancel)
        }
    }

    private fun string(reader: ByteBuffer, cap: Int): String {
        require(reader.remaining() >= 4)
        val length = reader.int
        require(length in 0..cap && length <= reader.remaining())
        val part = reader.slice()
        part.limit(length)
        val value = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(part).toString()
        reader.position(reader.position() + length)
        return value
    }
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
