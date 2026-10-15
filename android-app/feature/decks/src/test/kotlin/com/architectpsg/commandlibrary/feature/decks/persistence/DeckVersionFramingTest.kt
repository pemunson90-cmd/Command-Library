package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import org.junit.Assert.*
import org.junit.Test

class DeckVersionFramingTest {
    private val id = DeckId(ProviderId("local-decks"), "golden-deck")
    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    private fun user() = DeckVersionSummary(EntityId("user-1"), id, "Checkpoint Ω", UtcTimestamp(1_700_000_000, 123),
        Revision(7), null, DeckVersionKind.USER, null)
    private fun recovery() = DeckVersionSummary(EntityId("recovery-1"), id, "Before restore Ω", UtcTimestamp(1_700_000_001, 456),
        Revision(7), EntityId("user-1"), DeckVersionKind.RECOVERY, EntityId("target-1"))
    private fun invalid(block: () -> Any?) {
        try { block(); fail("Expected invalid framing input") }
        catch (_: IllegalArgumentException) { }
        catch (_: CharacterCodingException) { }
    }
    private fun bytes(hex: String): ByteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sha(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    // Independent fixed Python oracles retained in named-version-proposed-v1-oracles.json.
    // None of these expected bytes/hashes was generated from the production helper.
    private val captureHex = "434c44520001040000000b6c6f63616c2d6465636b730000000b676f6c64656e2d6465636b00000000000000070000000d436865636b706f696e7420cea9"
    private val restoreHex = "434c44520001050000000b6c6f63616c2d6465636b730000000b676f6c64656e2d6465636b0000000000000007000000087461726765742d31000000114265666f726520726573746f726520cea9"
    private val scopeHex = "434c564400010000000b6c6f63616c2d6465636b730000000b676f6c64656e2d6465636b"
    private val userHex = "434c4e56000100000006757365722d310000000b6c6f63616c2d6465636b730000000b676f6c64656e2d6465636b0000000d436865636b706f696e7420cea9000000006553f1000000007b00000000000000070000000000000000000029"
    private val recoveryHex = "434c4e5600010000000a7265636f766572792d310000000b6c6f63616c2d6465636b730000000b676f6c64656e2d6465636b000000114265666f726520726573746f726520cea9000000006553f101000001c800000000000000070100000006757365722d310101000000087461726765742d31000000000000002a"
    private val cursorHex = "434c56430001000000000000002a00000000000000292d7e618fdfcf3679d26c4f29359278588a22ba5dacd219a0fac25e5c0197049b"
    private val captureSha = "d7b72fe3171ad465a6a133f77315603798d0951d5e794ef0e5b1a00ecd22680a"
    private val restoreSha = "395ece893325b8e57f50b96520f52596db6b1376c9f3cce5645383dbe2f03c9f"
    private val scopeSha = "2d7e618fdfcf3679d26c4f29359278588a22ba5dacd219a0fac25e5c0197049b"
    private val userSha = "9b6f476b37a7d50a0561c337f1806d57c7260cba3d4e9ad780f0151ad4f84215"
    private val recoverySha = "4aa179b5a68fe7a06748e7984011f28981882df2c0bf74b4c5cf47137df42bbc"
    private val cursorSha = "82415ecfe28ec8a65ab8f108fdb92f27bd5d5885e971be9a2c652c61c3e3f368"
    private val cursorText = "Q0xWQwABAAAAAAAAACoAAAAAAAAAKS1-YY_fzzZ50mxPKTWSeFiKIrpdrNIZoPrCXlwBlwSb"

    @Test fun allSixIndependentOraclePreimagesHaveFrozenLengthsAndHashes() {
        val fixtures = listOf(Triple(captureHex, 62, captureSha), Triple(restoreHex, 78, restoreSha),
            Triple(scopeHex, 36, scopeSha), Triple(userHex, 94, userSha), Triple(recoveryHex, 124, recoverySha), Triple(cursorHex, 54, cursorSha))
        for ((preimage, size, digest) in fixtures) { assertEquals(size, bytes(preimage).size); assertEquals(digest, sha(bytes(preimage))) }
        assertEquals(cursorText, base64.encode(bytes(cursorHex)))
        assertEquals(0, bytes(userHex)[84].toInt()); assertEquals(1, bytes(recoveryHex)[102].toInt())
    }

    @Test fun scopeMatchesExactRaw32ByteOracleAndDefensiveByteWrapper() {
        val scope = DeckVersionFraming.scope(id)
        assertEquals(32, scope.size); assertEquals(scopeSha, hex(scope.copyBytes()))
        val external = scope.copyBytes(); external.fill(0)
        assertEquals(scopeSha, hex(scope.copyBytes())); assertEquals(scope, DeckVersionFraming.scope(id))
    }

    @Test fun bothMetadataKindsMatchFixedIndependentOracleDigests() {
        assertEquals(userSha, DeckVersionFraming.metadata(user(), 41))
        assertEquals(recoverySha, DeckVersionFraming.metadata(recovery(), 42))
    }

    @Test fun creatingRequestFingerprintsMatchFrozenCaptureRestoreAndSharedMethodsExactly() {
        assertEquals(captureSha, DeckVersionFraming.requestFingerprint(user()))
        assertEquals(restoreSha, DeckVersionFraming.requestFingerprint(recovery()))
        assertEquals(DeckLibraryRequests.capture(id, Revision(7), "Checkpoint Ω"), DeckVersionFraming.requestFingerprint(user()))
        assertEquals(DeckLibraryRequests.restore(id, Revision(7), EntityId("target-1"), "Before restore Ω"), DeckVersionFraming.requestFingerprint(recovery()))
    }

    @Test fun canonicalCursorMatchesEveryOracleByteAndReencodesExactly() {
        assertEquals(cursorText, DeckVersionFraming.cursor(42, 41, id))
        assertEquals(cursorHex, hex(base64.decode(DeckVersionFraming.cursor(42, 41, id))))
        val decoded = DeckVersionFraming.cursor(cursorText, id)
        assertEquals(DeckVersionFraming.Cursor(42, 41), decoded)
        assertEquals(cursorText, DeckVersionFraming.cursor(decoded.epoch, decoded.afterSequence, id))
    }

    @Test fun deckScopesAreSourceQualifiedAndLengthFramedWithoutNormalization() {
        val scopes = listOf(id, id.copy(source = ProviderId("other")), id.copy(raw = "other"), id.copy(raw = " golden-deck"),
            DeckId(ProviderId("a"), "bc"), DeckId(ProviderId("ab"), "c")).map { hex(DeckVersionFraming.scope(it).copyBytes()) }
        assertEquals(scopes.size, scopes.toSet().size)
        invalid { DeckVersionFraming.cursor(cursorText, id.copy(source = ProviderId("other"))) }
        invalid { DeckVersionFraming.cursor(cursorText, id.copy(raw = "other")) }
    }

    @Test fun everyMetadataFieldAndCreatingSequenceInfluencesDigest() {
        val base = user()
        val changes = listOf(base.copy(id = EntityId("user-2")), base.copy(deckId = id.copy(source = ProviderId("other"))),
            base.copy(deckId = id.copy(raw = "other")), base.copy(label = "Checkpoint Ω "),
            base.copy(createdAt = UtcTimestamp(1_700_000_001, 123)), base.copy(createdAt = UtcTimestamp(1_700_000_000, 124)),
            base.copy(sourceRevision = Revision(8)), base.copy(predecessorVersionId = EntityId("prior")),
            base.copy(kind = DeckVersionKind.RECOVERY, restoredTargetVersionId = EntityId("target")))
        for (changed in changes) assertNotEquals(userSha, DeckVersionFraming.metadata(changed, 41))
        assertNotEquals(userSha, DeckVersionFraming.metadata(base, 42))
        assertNotEquals(recoverySha, DeckVersionFraming.metadata(recovery().copy(restoredTargetVersionId = EntityId("different")), 42))
    }

    @Test fun requestFingerprintsExcludeGeneratedIdsTimesAndPredecessors() {
        assertEquals(captureSha, DeckVersionFraming.requestFingerprint(user().copy(id = EntityId("generated other"),
            createdAt = UtcTimestamp(Long.MIN_VALUE, 999_999_999), predecessorVersionId = EntityId("other predecessor"))))
        assertEquals(restoreSha, DeckVersionFraming.requestFingerprint(recovery().copy(id = EntityId("generated other"),
            createdAt = UtcTimestamp(Long.MAX_VALUE), predecessorVersionId = null)))
        assertNotEquals(captureSha, DeckVersionFraming.requestFingerprint(user().copy(label = "Checkpoint Ω ")))
        assertNotEquals(captureSha, DeckVersionFraming.requestFingerprint(user().copy(sourceRevision = Revision(8))))
        assertNotEquals(restoreSha, DeckVersionFraming.requestFingerprint(recovery().copy(restoredTargetVersionId = EntityId("other target"))))
    }

    @Test fun nullShapesAndSelfReferencesRejectBeforeProducingAnyMetadataOrRequest() {
        val invalidSummaries = listOf(user().copy(restoredTargetVersionId = EntityId("target")), recovery().copy(restoredTargetVersionId = null),
            user().copy(predecessorVersionId = user().id), recovery().copy(restoredTargetVersionId = recovery().id),
            recovery().copy(predecessorVersionId = recovery().id), recovery().copy(sourceRevision = Revision(Long.MAX_VALUE)))
        for (summary in invalidSummaries) { invalid { DeckVersionFraming.metadata(summary, 1) }; invalid { DeckVersionFraming.requestFingerprint(summary) } }
        // Same earlier predecessor and target is valid; existence/order belongs to the SQL caller.
        val sameEarlier = recovery().copy(restoredTargetVersionId = EntityId("user-1"))
        assertEquals(64, DeckVersionFraming.metadata(sameEarlier, 1).length)
        assertEquals(64, DeckVersionFraming.requestFingerprint(sameEarlier).length)
    }

    @Test fun sequencesAndMaximumRevisionsAreCheckedWithoutOverflowOrInventedOrdering() {
        invalid { DeckVersionFraming.metadata(user(), 0) }; invalid { DeckVersionFraming.metadata(user(), -1) }
        assertEquals(64, DeckVersionFraming.metadata(user().copy(sourceRevision = Revision(Long.MAX_VALUE)), Long.MAX_VALUE).length)
        assertEquals(64, DeckVersionFraming.requestFingerprint(user().copy(sourceRevision = Revision(Long.MAX_VALUE))).length)
        assertEquals(64, DeckVersionFraming.metadata(recovery().copy(sourceRevision = Revision(Long.MAX_VALUE - 1)), Long.MAX_VALUE).length)
        // No semantic sequence-vs-source ordering or timestamp epoch restriction is invented here.
        assertEquals(64, DeckVersionFraming.metadata(user().copy(createdAt = UtcTimestamp(Long.MIN_VALUE), sourceRevision = Revision(99)), 1).length)
    }

    @Test fun exactUnicodeAndByteBoundariesAreAcceptedAcrossAllFramedFields() {
        val exact = "😀".repeat(256)
        val deck = DeckId(ProviderId(exact), "é".repeat(512))
        val summary = user().copy(id = EntityId(exact), deckId = deck, label = "x".repeat(1_024), predecessorVersionId = EntityId("é".repeat(512)))
        assertEquals(64, DeckVersionFraming.metadata(summary, 1).length)
        assertEquals(32, DeckVersionFraming.scope(deck).size)
        assertEquals(64, DeckVersionFraming.requestFingerprint(summary).length)
        val token = DeckVersionFraming.cursor(1, 1, deck); assertEquals(DeckVersionFraming.Cursor(1, 1), DeckVersionFraming.cursor(token, deck))
    }

    @Test fun malformedOversizedAndBlankStringsFailWithoutSilentlyTrimming() {
        for (bad in listOf("x".repeat(1_025), "é".repeat(513), "\uD800")) {
            val candidates = listOf(user().copy(id = EntityId(bad)), user().copy(deckId = id.copy(source = ProviderId(bad))),
                user().copy(deckId = id.copy(raw = bad)), user().copy(label = bad), user().copy(predecessorVersionId = EntityId(bad)),
                recovery().copy(restoredTargetVersionId = EntityId(bad)))
            for (summary in candidates) { invalid { DeckVersionFraming.metadata(summary, 1) }; invalid { DeckVersionFraming.requestFingerprint(summary) } }
            invalid { DeckVersionFraming.scope(id.copy(raw = bad)) }; invalid { DeckVersionFraming.scope(id.copy(source = ProviderId(bad))) }
        }
        invalid { DeckVersionFraming.metadata(user().copy(label = ""), 1) }
        invalid { DeckVersionFraming.requestFingerprint(user().copy(label = " \t\n")) }
        val huge = id.copy(raw = " ".repeat(999_999) + "x")
        invalid { DeckVersionFraming.scope(huge) }
        val exactSpaces = user().copy(label = "  Checkpoint Ω  ")
        assertNotEquals(userSha, DeckVersionFraming.metadata(exactSpaces, 41))
        assertNotEquals(captureSha, DeckVersionFraming.requestFingerprint(exactSpaces))
    }

    @Test fun cursorInputLengthAlphabetPaddingAndTrailingBytesAreRejected() {
        for (raw in listOf("", "A".repeat(1_000_000), cursorText.dropLast(1), cursorText + "A", cursorText + "=",
            cursorText.dropLast(1) + "=", cursorText.dropLast(1) + "+", cursorText.dropLast(1) + "/", cursorText.dropLast(1) + " ",
            cursorText.dropLast(1) + "\n", base64.encode(bytes(cursorHex) + 0.toByte()), base64.encode(bytes(cursorHex).dropLast(1).toByteArray()))) {
            invalid { DeckVersionFraming.cursor(raw, id) }
        }
    }

    @Test fun cursorMagicVersionScopeAndEveryScalarRangeAreValidated() {
        fun changed(action: (ByteBuffer) -> Unit): String {
            val bytes = bytes(cursorHex); action(ByteBuffer.wrap(bytes)); return base64.encode(bytes)
        }
        val invalidTokens = listOf(changed { it.putInt(0, 0x434c5644) }, changed { it.putShort(4, 0) }, changed { it.putShort(4, 2) },
            changed { it.putLong(6, -1) }, changed { it.putLong(6, Long.MIN_VALUE) }, changed { it.putLong(6, 0) },
            changed { it.putLong(14, 0) }, changed { it.putLong(14, -1) }, changed { it.putLong(14, 43) }, changed { it.put(22, 0) })
        for (raw in invalidTokens) invalid { DeckVersionFraming.cursor(raw, id) }
        for ((epoch, after) in listOf(-1L to 1L, 0L to 1L, 1L to 0L, 1L to -1L, 1L to 2L, Long.MIN_VALUE to Long.MAX_VALUE)) {
            invalid { DeckVersionFraming.cursor(epoch, after, id) }
        }
    }

    @Test fun cursorMaximumValuesRoundTripCanonicallyAndDoNotCheckCurrentDatabaseEpoch() {
        for ((epoch, after) in listOf(1L to 1L, Long.MAX_VALUE to 1L, Long.MAX_VALUE to Long.MAX_VALUE)) {
            val raw = DeckVersionFraming.cursor(epoch, after, id)
            assertEquals(72, raw.length); assertEquals(54, base64.decode(raw).size)
            assertEquals(DeckVersionFraming.Cursor(epoch, after), DeckVersionFraming.cursor(raw, id))
            assertEquals(raw, base64.encode(base64.decode(raw)))
        }
    }
}
