package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlin.time.Duration.Companion.nanoseconds

internal class ProfileProblem(val status: Outcome<Nothing>) : RuntimeException()
internal fun profileReject(message: String, code: FailureCode = FailureCode.INVALID_INPUT): Nothing =
    throw ProfileProblem(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
internal fun profileRequire(valid: Boolean, message: String) { if (!valid) profileReject(message) }
internal fun <T> profileValue(outcome: Outcome<T>): T = when (outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw ProfileProblem(outcome)
    is Outcome.Unavailable -> throw ProfileProblem(outcome)
    is Outcome.Unknown -> throw ProfileProblem(outcome)
}
internal inline fun <T> profileOutcome(action: () -> T): Outcome<T> = try { Outcome.Success(action()) }
    catch (problem: ProfileProblem) { problem.status }

internal fun profileText(value: String, max: Int): ByteArray {
    profileRequire(value.isNotBlank() && value.length <= max && value.none { Character.isISOControl(it) }, "Invalid provider text")
    val bytes = try {
        val encoded = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
        ByteArray(encoded.remaining()).also { encoded.get(it) }
    } catch (_: CharacterCodingException) { profileReject("Invalid provider Unicode") }
    profileRequire(bytes.size <= max, "Provider text exceeds byte limit")
    return bytes
}
internal fun profileDecodeText(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (_: CharacterCodingException) { profileReject("Invalid provider Unicode") }

/** Private stable wire tags, independent of enum ordinal and Kotlin serialization. */
internal object ProviderProfileCodec {
    const val VERSION = 1
    const val MAX_BYTES = 64 * 1024
    fun hash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val emptyHash: String = hash(byteArrayOf())

    fun encode(value: StoredProviderProfile): Outcome<SnapshotBytes> = profileOutcome {
        val normalized = profileValue(ProviderProfilePolicy.normalize(value.profile))
        profileRequire(normalized == value.profile && value.revision.value > 0, "Noncanonical provider payload")
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x50504631); out.writeInt(VERSION)
            out.text(normalized.id.value, 256); out.writeLong(value.revision.value)
            out.time(value.createdAt); out.time(value.modifiedAt)
            out.text(normalized.name, 1024)
            out.writeByte(when (normalized.dialect) {
                ProviderDialect.OPENAI_CHAT -> 1; ProviderDialect.OPENAI_RESPONSES -> 2
                ProviderDialect.ANTHROPIC -> 3; ProviderDialect.GEMINI -> 4; ProviderDialect.LOCAL -> 5
            })
            out.text(normalized.baseUri!!.value, 4096); out.text(normalized.selectedModelId, 1024)
            val c = normalized.capabilities
            listOf(c.modelListing, c.streaming, c.tools, c.promptCaching, c.structuredOutput, c.vision).forEach { out.writeByte(if (it) 1 else 0) }
            when (val api = normalized.apiVersion) {
                is Knowledge.Known -> { out.writeByte(1); out.text(api.value, 1024) }
                is Knowledge.Absent -> { out.writeByte(2); out.text(api.reason, 1024) }
                is Knowledge.Unknown -> { out.writeByte(3); out.text(api.reason, 1024) }
            }
            val e = normalized.endpoint!!
            out.writeInt(e.requestsPerSecond); out.writeInt(e.retry.maxAttempts)
            out.writeLong(e.retry.initialDelay.inWholeNanoseconds); out.writeLong(e.retry.maximumDelay.inWholeNanoseconds)
            out.writeByte(if (e.retry.honorRetryAfter) 1 else 0)
            val ref = normalized.secretRef
            out.writeByte(if (ref == null) 0 else 1)
            // Scope, endpoint identity/origin, empty headers/redirects are enforced constants derived
            // from the encoded identity and base URI, not independently lossy DTO fields.
            if (ref != null) out.text(ref.id.value, 256)
        }
        profileRequire(bytes.size() in 1..MAX_BYTES, "Provider payload exceeds byte limit")
        SnapshotBytes(bytes.toByteArray())
    }

    fun decode(payload: SnapshotBytes): Outcome<StoredProviderProfile> {
        if (payload.size !in 1..MAX_BYTES) return corrupt()
        return try {
            val input = DataInputStream(ByteArrayInputStream(payload.copyBytes()))
            profileRequire(input.readInt() == 0x50504631, "Invalid provider payload frame")
            val version = input.readInt()
            if (version > VERSION) return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer provider payload version")
            profileRequire(version == VERSION, "Invalid provider payload version")
            val id = EntityId(input.text(256)); val revision = Revision(input.readLong())
            val created = input.time(); val modified = input.time(); val name = input.text(1024)
            val dialect = when (input.readUnsignedByte()) {
                1 -> ProviderDialect.OPENAI_CHAT; 2 -> ProviderDialect.OPENAI_RESPONSES; 3 -> ProviderDialect.ANTHROPIC
                4 -> ProviderDialect.GEMINI; 5 -> ProviderDialect.LOCAL; else -> profileReject("Invalid provider dialect")
            }
            val base = ResourceUri(input.text(4096)); val model = input.text(1024)
            val c = ProviderCapabilities(input.flag(), input.flag(), input.flag(), input.flag(), input.flag(), input.flag())
            val api: Knowledge<String> = when (input.readUnsignedByte()) {
                1 -> Knowledge.Known(input.text(1024)); 2 -> Knowledge.Absent(input.text(1024))
                3 -> Knowledge.Unknown(input.text(1024), SnapshotList.empty()); else -> profileReject("Invalid provider API tag")
            }
            val rate = input.readInt(); val attempts = input.readInt()
            val initial = input.readLong(); val maximum = input.readLong()
            profileRequire(initial >= 0 && maximum >= initial && maximum <= 86_400_000_000_000L, "Invalid provider duration")
            val retry = RetryPolicy(attempts, initial.nanoseconds, maximum.nanoseconds, input.flag())
            val origin = profileValue(ProviderProfilePolicy.canonicalOrigin(base))
            val ref = if (input.flag()) SecretRef(EntityId(input.text(256)), SecretScope(id, origin, "provider-auth-v1")) else null
            profileRequire(input.available() == 0, "Trailing provider payload bytes")
            val endpoint = EndpointProfile(id, origin, SnapshotList.empty(), ref, rate, retry)
            val profile = ProviderProfile(id, name, dialect, endpoint, model, c, ref, api, SnapshotList.empty(), base)
            val normalized = profileValue(ProviderProfilePolicy.normalize(profile))
            profileRequire(normalized == profile && revision.value > 0, "Noncanonical provider payload")
            Outcome.Success(StoredProviderProfile(profile, revision, created, modified))
        } catch (_: ProfileProblem) { corrupt() }
        catch (_: java.io.IOException) { corrupt() }
        catch (_: IllegalArgumentException) { corrupt() }
    }

    private fun corrupt(): Outcome<Nothing> = Outcome.Failure(DomainFailure(FailureCode.CORRUPT_DATA, "Invalid retained provider payload", SnapshotList.empty()))
    private fun DataOutputStream.text(value: String, max: Int) { val b = profileText(value, max); writeInt(b.size); write(b) }
    private fun DataInputStream.text(max: Int): String {
        val size = readInt(); profileRequire(size in 1..max && size <= available(), "Invalid provider text length")
        val bytes = ByteArray(size); readFully(bytes)
        return profileDecodeText(bytes).also { profileText(it, max) }
    }
    private fun DataInputStream.flag(): Boolean = when (readUnsignedByte()) { 0 -> false; 1 -> true; else -> profileReject("Invalid provider flag") }
    private fun DataOutputStream.time(t: UtcTimestamp) { writeLong(t.epochSeconds); writeInt(t.nanoAdjustment) }
    private fun DataInputStream.time(): UtcTimestamp = UtcTimestamp(readLong(), readInt())
}
