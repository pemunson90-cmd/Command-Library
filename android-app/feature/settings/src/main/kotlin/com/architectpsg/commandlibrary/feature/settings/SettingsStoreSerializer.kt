package com.architectpsg.commandlibrary.feature.settings

import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream

/**
 * DataStore serializer for the single settings file. A store that is corrupt or written by a newer
 * schema makes readFrom throw; no corruption handler is installed, so DataStore never replaces such
 * a file with defaults. Every operation then reports a typed failure and the bytes stay untouched.
 */
internal object SettingsStoreSerializer : Serializer<StoredSettings> {
    override val defaultValue: StoredSettings = StoredSettings.INITIAL

    override suspend fun readFrom(input: InputStream): StoredSettings {
        val limit = SettingsStoreCodec.MAX_STORE_BYTES
        val bytes = input.readNBytesCompat(limit + 1)
        if (bytes.size > limit) throw CorruptSettingsStoreException("Settings store exceeds the size limit")
        return SettingsStoreCodec.decode(bytes)
    }

    override suspend fun writeTo(t: StoredSettings, output: OutputStream) {
        output.write(SettingsStoreCodec.encode(t))
    }

    /** InputStream.readNBytes is API 33; this loop reads at most max bytes on every supported API level. */
    private fun InputStream.readNBytesCompat(max: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (buffer.size() < max) {
            val read = read(chunk, 0, minOf(chunk.size, max - buffer.size()))
            if (read < 0) break
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
