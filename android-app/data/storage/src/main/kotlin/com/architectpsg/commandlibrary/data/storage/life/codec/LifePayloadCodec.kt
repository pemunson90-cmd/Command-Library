package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*
import java.io.IOException

internal const val SNAPSHOT_LIMIT = 4 * 1024 * 1024
private const val EVENT_LIMIT = 16 * 1024 * 1024
private const val MAGIC = 0x434c4946 // CLIF
private const val CODEC_VERSION = 2

internal fun <T> encodeLifeFrame(root: Int, limit: Int, codec: LifeWire<T>, value: T): ByteArray {
    val writer = LifeWriter(limit)
    writer.int(MAGIC); writer.int(CODEC_VERSION); writer.byte(root); writer.v(codec, value)
    return writer.finish()
}
internal fun <T> decodeLifeFrame(bytes: ByteArray, root: Int, limit: Int, codec: LifeWire<T>): T {
    require(bytes.size <= limit) { "Life payload exceeds byte budget" }
    val reader = LifeReader(bytes)
    require(reader.int() == MAGIC) { "Unsupported life payload magic" }
    val version = reader.int()
    require(version in 1..CODEC_VERSION && reader.byte() == root && (root != 6 || version >= 2)) { "Unsupported life payload frame" }
    reader.formatVersion = version
    val value = reader.v(codec)
    require(reader.remaining == 0) { "Trailing life payload bytes" }
    return value
}

/** Versioned bounded model serialization, independent of Room and transactional service graph validation. */
public class LifePayloadCodec {
    public fun encodeCreation(value: GameCreation): Outcome<SnapshotBytes> = encode(value, 6, SNAPSHOT_LIMIT, creationWire)
    public fun decodeCreation(bytes: SnapshotBytes): Outcome<GameCreation> = decode(bytes, 6, SNAPSHOT_LIMIT, creationWire)
    public fun encodeSnapshot(value: GameSnapshot): Outcome<SnapshotBytes> = encode(value, 1, SNAPSHOT_LIMIT, snapshotWire)
    public fun decodeSnapshot(bytes: SnapshotBytes): Outcome<GameSnapshot> = decode(bytes, 1, SNAPSHOT_LIMIT, snapshotWire)
    public fun encodeProfile(value: PlayerProfileDraft): Outcome<SnapshotBytes> = encode(value, 2, SNAPSHOT_LIMIT, profileWire)
    public fun decodeProfile(bytes: SnapshotBytes): Outcome<PlayerProfileDraft> = decode(bytes, 2, SNAPSHOT_LIMIT, profileWire)
    public fun encodeEvent(value: GameEvent): Outcome<SnapshotBytes> = encode(value, 3, EVENT_LIMIT, eventWire)
    public fun decodeEvent(bytes: SnapshotBytes): Outcome<GameEvent> = decode(bytes, 3, EVENT_LIMIT, eventWire)
    public fun encodeArchive(value: GameEventArchive): Outcome<SnapshotBytes> = encode(value, 4, EVENT_LIMIT, archiveWire)
    public fun decodeArchive(bytes: SnapshotBytes): Outcome<GameEventArchive> = decode(bytes, 4, EVENT_LIMIT, archiveWire)
    public fun encodeCheckpoint(value: GameCheckpoint): Outcome<SnapshotBytes> = encode(value, 5, SNAPSHOT_LIMIT + 65_536, checkpointWire)
    public fun decodeCheckpoint(bytes: SnapshotBytes): Outcome<GameCheckpoint> = decode(bytes, 5, SNAPSHOT_LIMIT + 65_536, checkpointWire)

    private fun <T> encode(value: T, root: Int, limit: Int, codec: LifeWire<T>): Outcome<SnapshotBytes> =
        guard(FailureCode.INVALID_INPUT) { SnapshotBytes(encodeLifeFrame(root, limit, codec, value)) }
    private fun <T> decode(bytes: SnapshotBytes, root: Int, limit: Int, codec: LifeWire<T>): Outcome<T> =
        guard(FailureCode.CORRUPT_DATA) {
            require(bytes.size <= limit) { "Life payload exceeds byte budget" }
            decodeLifeFrame(bytes.copyBytes(), root, limit, codec)
        }
    private inline fun <T> guard(code: FailureCode, operation: () -> T): Outcome<T> = try {
        Outcome.Success(operation())
    } catch (error: IllegalArgumentException) {
        failure(code, error)
    } catch (error: IOException) {
        failure(code, error)
    }
    private fun failure(code: FailureCode, error: Exception): Outcome.Failure = Outcome.Failure(
        DomainFailure(code, "Invalid life payload: ${error.message ?: error.javaClass.simpleName}", SnapshotList.empty()),
    )
}
