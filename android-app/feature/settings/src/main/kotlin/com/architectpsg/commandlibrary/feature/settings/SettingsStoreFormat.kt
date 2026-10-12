package com.architectpsg.commandlibrary.feature.settings

import com.architectpsg.commandlibrary.core.model.CachePolicy
import com.architectpsg.commandlibrary.core.model.ConsentRecord
import com.architectpsg.commandlibrary.core.model.ContentHash
import com.architectpsg.commandlibrary.core.model.DecimalAmount
import com.architectpsg.commandlibrary.core.model.DecimalUnit
import com.architectpsg.commandlibrary.core.model.DownloadIntent
import com.architectpsg.commandlibrary.core.model.DownloadKind
import com.architectpsg.commandlibrary.core.model.EntityId
import com.architectpsg.commandlibrary.core.model.Evidence
import com.architectpsg.commandlibrary.core.model.Knowledge
import com.architectpsg.commandlibrary.core.model.LanguageTag
import com.architectpsg.commandlibrary.core.model.ProviderId
import com.architectpsg.commandlibrary.core.model.ResourceUri
import com.architectpsg.commandlibrary.core.model.Revision
import com.architectpsg.commandlibrary.core.model.SnapshotList
import com.architectpsg.commandlibrary.core.model.StorageMode
import com.architectpsg.commandlibrary.core.model.ThemePreference
import com.architectpsg.commandlibrary.core.model.UtcTimestamp
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** Stored bytes are not a valid settings store of any version this build knows how to read. */
internal class CorruptSettingsStoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Stored bytes are a well-formed header of a schema version newer than this build supports. */
internal class UnsupportedSettingsStoreVersionException(
    val foundVersion: Int,
    val supportedVersion: Int,
) : IOException("Settings store version $foundVersion is newer than supported version $supportedVersion")

/** One consent exactly as persisted: the record plus the installation that approved it. */
internal data class StoredConsent(val installationId: String, val record: ConsentRecord)

/** Everything the settings feature persists. One value, one file, one atomic write. */
internal data class StoredSettings(
    val revision: Revision,
    val updatedAt: UtcTimestamp,
    val dataLanguage: LanguageTag,
    val theme: ThemePreference,
    val storageMode: StorageMode,
    val cache: CachePolicy,
    val budget: Knowledge<DecimalAmount>,
    val providerProfileIds: List<EntityId>,
    val consents: List<StoredConsent>,
) {
    companion object {
        /**
         * State of a store that has never been written. Revision 0 with the epoch as updatedAt means
         * "never updated": no clock reading is fabricated for a write that did not happen.
         */
        val INITIAL: StoredSettings = StoredSettings(
            revision = Revision(0),
            updatedAt = UtcTimestamp(0),
            dataLanguage = LanguageTag.ENGLISH,
            theme = ThemePreference.SYSTEM,
            storageMode = StorageMode.FULL,
            cache = CachePolicy(imageBytes = 0, retainRulings = false, wifiOnlyDownloads = true),
            budget = Knowledge.Unknown("No budget has been set", SnapshotList.empty()),
            providerProfileIds = emptyList(),
            consents = emptyList(),
        )
    }
}

/**
 * Binary format, all integers big-endian:
 *
 *     "CLSS"            4 bytes magic
 *     schema version    int32, currently 1
 *     body              version specific
 *     CRC-32            int32 over every byte before it
 *
 * The header (magic and version) is checked before the checksum so that a future version, which may
 * change the body and trailer, is reported as unsupported and never as corruption or an empty store.
 */
internal object SettingsStoreCodec {
    const val SUPPORTED_VERSION: Int = 1
    private const val HEADER_BYTES = 8
    private const val TRAILER_BYTES = 4
    const val MAX_STORE_BYTES: Int = 16 * 1024 * 1024
    private val MAGIC = byteArrayOf('C'.code.toByte(), 'L'.code.toByte(), 'S'.code.toByte(), 'S'.code.toByte())

    fun encode(state: StoredSettings): ByteArray {
        val bytes = ByteArrayOutputStream()
        val out = DataOutputStream(bytes)
        out.write(MAGIC)
        out.writeInt(SUPPORTED_VERSION)
        writeBody(out, state)
        out.flush()
        val checksum = CRC32().also { it.update(bytes.toByteArray()) }.value.toInt()
        out.writeInt(checksum)
        out.flush()
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StoredSettings {
        if (bytes.size < HEADER_BYTES) throw CorruptSettingsStoreException("Settings store is truncated")
        if (!bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw CorruptSettingsStoreException("Settings store header is not recognized")
        }
        val version = ByteBuffer.wrap(bytes, MAGIC.size, 4).int
        if (version > SUPPORTED_VERSION) throw UnsupportedSettingsStoreVersionException(version, SUPPORTED_VERSION)
        if (version < 1) throw CorruptSettingsStoreException("Settings store has an invalid version")
        if (bytes.size < HEADER_BYTES + TRAILER_BYTES) throw CorruptSettingsStoreException("Settings store is truncated")
        val expected = ByteBuffer.wrap(bytes, bytes.size - TRAILER_BYTES, TRAILER_BYTES).int
        val actual = CRC32().also { it.update(bytes, 0, bytes.size - TRAILER_BYTES) }.value.toInt()
        if (expected != actual) throw CorruptSettingsStoreException("Settings store checksum does not match")
        val reader = Reader(ByteBuffer.wrap(bytes, HEADER_BYTES, bytes.size - HEADER_BYTES - TRAILER_BYTES))
        try {
            val state = readBody(reader)
            if (reader.remaining != 0) throw CorruptSettingsStoreException("Settings store has trailing bytes")
            return state
        } catch (failure: CorruptSettingsStoreException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            // Domain constructors reject invalid values with require(); a stored value they reject is damage.
            throw CorruptSettingsStoreException("Settings store holds a value that is not valid", failure)
        }
    }

    private fun writeBody(out: DataOutputStream, state: StoredSettings) {
        out.writeLong(state.revision.value)
        writeTimestamp(out, state.updatedAt)
        writeString(out, state.dataLanguage.value)
        writeString(out, state.theme.name)
        writeString(out, state.storageMode.name)
        out.writeLong(state.cache.imageBytes)
        out.writeBoolean(state.cache.retainRulings)
        out.writeBoolean(state.cache.wifiOnlyDownloads)
        writeKnowledge(out, state.budget, ::writeDecimalAmount)
        out.writeInt(state.providerProfileIds.size)
        state.providerProfileIds.forEach { writeString(out, it.value) }
        out.writeInt(state.consents.size)
        state.consents.forEach { stored ->
            writeString(out, stored.installationId)
            writeConsent(out, stored.record)
        }
    }

    private fun readBody(input: Reader): StoredSettings {
        val revision = Revision(input.long())
        val updatedAt = readTimestamp(input)
        val dataLanguage = LanguageTag(input.string())
        val theme = input.enum<ThemePreference>()
        val storageMode = input.enum<StorageMode>()
        val cache = CachePolicy(input.long(), input.boolean(), input.boolean())
        val budget = readKnowledge(input, ::readDecimalAmount)
        val profiles = List(input.count()) { EntityId(input.string()) }
        val consents = List(input.count()) { StoredConsent(input.string(), readConsent(input)) }
        return StoredSettings(revision, updatedAt, dataLanguage, theme, storageMode, cache, budget, profiles, consents)
    }

    private fun writeConsent(out: DataOutputStream, record: ConsentRecord) {
        writeString(out, record.id.value)
        writeIntent(out, record.intent)
        writeTimestamp(out, record.approvedAt)
        writeOptional(out, record.revokedAt) { writeTimestamp(out, it) }
        out.writeBoolean(record.explicitUserApproval)
    }

    private fun readConsent(input: Reader): ConsentRecord = ConsentRecord(
        id = EntityId(input.string()),
        intent = readIntent(input),
        approvedAt = readTimestamp(input),
        revokedAt = input.optional { readTimestamp(input) },
        explicitUserApproval = input.boolean(),
    )

    private fun writeIntent(out: DataOutputStream, intent: DownloadIntent) {
        writeString(out, intent.id.value)
        writeString(out, intent.kind.name)
        writeString(out, intent.source.value)
        writeString(out, intent.resource.value)
        writeString(out, intent.version)
        writeOptional(out, intent.language) { writeString(out, it.value) }
        writeOptional(out, intent.packId) { writeString(out, it.value) }
        writeOptional(out, intent.checksum) {
            writeString(out, it.algorithm)
            writeString(out, it.hex)
        }
        writeKnowledge(out, intent.expectedBytes) { stream, value -> stream.writeLong(value) }
    }

    private fun readIntent(input: Reader): DownloadIntent = DownloadIntent(
        id = EntityId(input.string()),
        kind = input.enum<DownloadKind>(),
        source = ProviderId(input.string()),
        resource = ResourceUri(input.string()),
        version = input.string(),
        language = input.optional { LanguageTag(input.string()) },
        packId = input.optional { EntityId(input.string()) },
        checksum = input.optional { ContentHash(input.string(), input.string()) },
        expectedBytes = readKnowledge(input) { it.long() },
    )

    private fun writeDecimalAmount(out: DataOutputStream, amount: DecimalAmount) {
        writeString(out, amount.value.unscaledValue().toString())
        out.writeInt(amount.value.scale())
        when (val unit = amount.unit) {
            is DecimalUnit.Fiat -> {
                out.writeByte(UNIT_FIAT)
                writeString(out, unit.currencyCode)
            }
            DecimalUnit.Tix -> out.writeByte(UNIT_TIX)
            is DecimalUnit.Native -> {
                out.writeByte(UNIT_NATIVE)
                writeString(out, unit.provider.value)
                writeString(out, unit.symbol)
            }
        }
    }

    private fun readDecimalAmount(input: Reader): DecimalAmount {
        val unscaled = BigInteger(input.string())
        val value = BigDecimal(unscaled, input.int())
        val unit = when (val tag = input.byte().toInt()) {
            UNIT_FIAT -> DecimalUnit.Fiat(input.string())
            UNIT_TIX -> DecimalUnit.Tix
            UNIT_NATIVE -> DecimalUnit.Native(ProviderId(input.string()), input.string())
            else -> throw CorruptSettingsStoreException("Unknown decimal unit tag $tag")
        }
        return DecimalAmount(value, unit)
    }

    private fun <T> writeKnowledge(out: DataOutputStream, knowledge: Knowledge<T>, writeValue: (DataOutputStream, T) -> Unit) {
        when (knowledge) {
            is Knowledge.Known -> {
                out.writeByte(KNOWN)
                writeValue(out, knowledge.value)
            }
            is Knowledge.Absent -> {
                out.writeByte(ABSENT)
                writeString(out, knowledge.reason)
            }
            is Knowledge.Unknown -> {
                out.writeByte(UNKNOWN)
                writeString(out, knowledge.reason)
                out.writeInt(knowledge.evidence.size)
                knowledge.evidence.forEach { evidence ->
                    writeString(out, evidence.description)
                    writeOptional(out, evidence.source) { writeString(out, it.value) }
                    writeOptional(out, evidence.recordedAt) { writeTimestamp(out, it) }
                }
            }
        }
    }

    private fun <T> readKnowledge(input: Reader, readValue: (Reader) -> T): Knowledge<T> =
        when (val tag = input.byte().toInt()) {
            KNOWN -> Knowledge.Known(readValue(input))
            ABSENT -> Knowledge.Absent(input.string())
            UNKNOWN -> Knowledge.Unknown(
                input.string(),
                SnapshotList.copyOf(
                    List(input.count()) {
                        Evidence(
                            description = input.string(),
                            source = input.optional { ResourceUri(input.string()) },
                            recordedAt = input.optional { readTimestamp(input) },
                        )
                    },
                ),
            )
            else -> throw CorruptSettingsStoreException("Unknown knowledge tag $tag")
        }

    private fun writeTimestamp(out: DataOutputStream, timestamp: UtcTimestamp) {
        out.writeLong(timestamp.epochSeconds)
        out.writeInt(timestamp.nanoAdjustment)
    }

    private fun readTimestamp(input: Reader): UtcTimestamp = UtcTimestamp(input.long(), input.int())

    private fun writeString(out: DataOutputStream, value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        out.writeInt(encoded.size)
        out.write(encoded)
    }

    private inline fun <T : Any> writeOptional(out: DataOutputStream, value: T?, write: (T) -> Unit) {
        if (value == null) {
            out.writeBoolean(false)
        } else {
            out.writeBoolean(true)
            write(value)
        }
    }

    private const val KNOWN = 0
    private const val ABSENT = 1
    private const val UNKNOWN = 2
    private const val UNIT_FIAT = 0
    private const val UNIT_TIX = 1
    private const val UNIT_NATIVE = 2

    /** Bounds-checked cursor: a damaged length can never allocate or read past the real payload. */
    private class Reader(private val buffer: ByteBuffer) {
        val remaining: Int get() = buffer.remaining()

        fun byte(): Byte = need(1).let { buffer.get() }
        fun int(): Int = need(4).let { buffer.int }
        fun long(): Long = need(8).let { buffer.long }

        fun boolean(): Boolean = when (val raw = byte().toInt()) {
            0 -> false
            1 -> true
            else -> throw CorruptSettingsStoreException("Invalid boolean byte $raw")
        }

        /** Element count; every element needs at least one byte, so a count above the remaining bytes is damage. */
        fun count(): Int {
            val value = int()
            if (value < 0 || value > buffer.remaining()) throw CorruptSettingsStoreException("Invalid element count")
            return value
        }

        fun string(): String {
            val length = int()
            if (length < 0 || length > buffer.remaining()) throw CorruptSettingsStoreException("Invalid string length")
            val raw = ByteArray(length)
            buffer.get(raw)
            return String(raw, Charsets.UTF_8)
        }

        inline fun <reified E : Enum<E>> enum(): E {
            val name = string()
            return enumValues<E>().firstOrNull { it.name == name }
                ?: throw CorruptSettingsStoreException("Unknown ${E::class.java.simpleName} value")
        }

        inline fun <T : Any> optional(read: () -> T): T? = if (boolean()) read() else null

        private fun need(bytes: Int) {
            if (buffer.remaining() < bytes) throw CorruptSettingsStoreException("Settings store is truncated")
        }
    }
}
