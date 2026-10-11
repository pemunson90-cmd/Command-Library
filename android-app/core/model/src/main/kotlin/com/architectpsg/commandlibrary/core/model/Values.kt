package com.architectpsg.commandlibrary.core.model

import java.math.BigDecimal
import java.util.Collections
import kotlin.time.Duration

/** Defensive, immutable snapshot: neither the input nor casts of the view can mutate it. */
public class SnapshotList<out T> private constructor(private val values: List<T>) : List<T> by values {
    public companion object {
        public fun <T> copyOf(values: Iterable<T>): SnapshotList<T> =
            SnapshotList(Collections.unmodifiableList(values.toList()))
        public fun <T> empty(): SnapshotList<T> = copyOf(emptyList())
        public fun <T> of(vararg values: T): SnapshotList<T> = copyOf(values.asList())
    }
    public override fun equals(other: Any?): Boolean = values == other
    public override fun hashCode(): Int = values.hashCode()
    public override fun toString(): String = values.toString()
}

public class SnapshotBytes(bytes: ByteArray) {
    private val bytes = bytes.copyOf()
    public val size: Int get() = bytes.size
    public fun copyBytes(): ByteArray = bytes.copyOf()
    public override fun equals(other: Any?): Boolean = other is SnapshotBytes && bytes.contentEquals(other.bytes)
    public override fun hashCode(): Int = bytes.contentHashCode()
}

@JvmInline public value class ProviderId(public val value: String) { init { require(value.isNotBlank()) } }
@JvmInline public value class EntityId(public val value: String) { init { require(value.isNotBlank()) } }
@JvmInline public value class GenerationId(public val value: String) { init { require(value.isNotBlank()) } }
@JvmInline public value class LanguageTag(public val value: String) {
    init { require(value.isNotBlank()) }
    public companion object { public val ENGLISH: LanguageTag = LanguageTag("en") }
}
public data class CardId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class OracleId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class RuleId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class DeckId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class GameId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class FormatId(public val source: ProviderId, public val raw: String) { init { require(raw.isNotBlank()) } }
public data class Revision(public val value: Long) {
    init { require(value >= 0) }
    public fun next(): Revision { check(value < Long.MAX_VALUE); return Revision(value + 1) }
}
public data class UtcTimestamp(public val epochSeconds: Long, public val nanoAdjustment: Int = 0) {
    init { require(nanoAdjustment in 0..999_999_999) }
}
public data class ResourceUri(public val value: String) { init { require(value.isNotBlank()) } }
public data class ContentHash(public val algorithm: String, public val hex: String) {
    init { require(algorithm.isNotBlank()); require(hex.isNotBlank() && hex.all { it in "0123456789abcdefABCDEF" }) }
}
public data class Evidence(public val description: String, public val source: ResourceUri?, public val recordedAt: UtcTimestamp?) {
    init { require(description.isNotBlank()) }
}
public sealed interface Knowledge<out T> {
    public data class Known<T>(public val value: T) : Knowledge<T>
    public data class Absent(public val reason: String) : Knowledge<Nothing> { init { require(reason.isNotBlank()) } }
    public data class Unknown(public val reason: String, public val evidence: SnapshotList<Evidence>) : Knowledge<Nothing> {
        init { require(reason.isNotBlank()) }
    }
}
public enum class UnavailableReason { OFFLINE, NOT_INSTALLED, NOT_FOUND, CONSENT_REQUIRED, REVOKED, UNSUPPORTED, SOURCE_UNAVAILABLE, KEY_MISSING }
public enum class FailureCode { INVALID_INPUT, STALE_REVISION, STALE_GENERATION, CORRUPT_DATA, TRANSPORT, PERSISTENCE, PERMISSION, SOURCE_MISMATCH, INTERNAL }
public data class DomainFailure(public val code: FailureCode, public val message: String, public val evidence: SnapshotList<Evidence>) {
    init { require(message.isNotBlank()) }
}
public sealed interface Outcome<out T> {
    public data class Success<T>(public val value: T) : Outcome<T>
    public data class Unavailable(public val reason: UnavailableReason, public val detail: String) : Outcome<Nothing>
    public data class Failure(public val error: DomainFailure) : Outcome<Nothing>
    public data class Unknown(public val reason: String, public val evidence: SnapshotList<Evidence>) : Outcome<Nothing> {
        init { require(reason.isNotBlank()) }
    }
}
public data class SourceNotice(
    public val provider: ProviderId,
    public val title: String,
    public val license: Knowledge<String>,
    public val attribution: String,
    public val uri: ResourceUri?,
)
public data class Provenance(
    public val provider: ProviderId,
    public val source: ResourceUri?,
    public val version: Knowledge<String>,
    public val sourceDate: Knowledge<UtcTimestamp>,
    public val fetchedAt: UtcTimestamp,
    public val effectiveAt: Knowledge<UtcTimestamp>,
    public val generation: GenerationId?,
    public val checksum: ContentHash?,
    public val notice: SourceNotice?,
)
public data class SnapshotReference(
    public val source: ProviderId,
    public val generation: GenerationId?,
    public val revision: Revision?,
    public val language: LanguageTag?,
)
public data class PageRequest(public val limit: Int, public val cursor: String? = null) {
    init {
        require(limit in 1..MAX_LIMIT)
        require(cursor == null || (cursor.isNotBlank() && cursor.toByteArray(Charsets.UTF_8).size <= MAX_CURSOR_BYTES))
    }
    public companion object {
        public const val MAX_LIMIT: Int = 100
        public const val MAX_CURSOR_BYTES: Int = 2048
    }
}
public data class Page<T>(public val items: SnapshotList<T>, public val nextCursor: String?, public val snapshot: SnapshotReference) {
    init {
        require(items.size <= PageRequest.MAX_LIMIT)
        require(nextCursor == null || (nextCursor.isNotBlank() && nextCursor.toByteArray(Charsets.UTF_8).size <= PageRequest.MAX_CURSOR_BYTES))
    }
    public val revision: Revision? get() = snapshot.revision
}
public sealed interface DecimalUnit {
    public data class Fiat(public val currencyCode: String) : DecimalUnit { init { require(currencyCode.matches(Regex("[A-Z]{3}"))) } }
    public data object Tix : DecimalUnit
    public data class Native(public val provider: ProviderId, public val symbol: String) : DecimalUnit { init { require(symbol.isNotBlank()) } }
}
/** BigDecimal is immutable; supplied scale and precision describe the exact represented value. */
public data class DecimalAmount(
    public val value: BigDecimal,
    public val unit: DecimalUnit,
    public val scale: Int = value.scale(),
    public val precision: Int = value.precision(),
) {
    init { require(scale >= 0 && scale == value.scale()); require(precision > 0 && precision == value.precision()) }
    public companion object { public fun exact(text: String, unit: DecimalUnit): DecimalAmount = DecimalAmount(BigDecimal(text), unit) }
}
public interface Clock {
    public fun utcNow(): UtcTimestamp
    public fun monotonicNow(): Duration
}
public interface CancelToken {
    public val isCancelled: Boolean
    public fun throwIfCancelled(): Unit
}
public interface IdSource {
    public fun nextId(): String
}
public interface RandomSource {
    public fun nextInt(upperBound: Int): Int
}
public interface DecimalPolicy {
    public fun multiply(amount: DecimalAmount, quantity: Int): DecimalAmount
}
