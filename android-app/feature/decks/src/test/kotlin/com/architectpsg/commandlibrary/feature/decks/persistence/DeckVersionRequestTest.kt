package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeckVersionRequestTest {
    private val id = DeckId(LocalDeckLibraryLimits.SOURCE, "golden-deck")

    @Test fun captureAndRestoreMatchIndependentlyFramedFixedOracles() {
        // Independent Python preimages retained in named-version-proposed-v1-oracles.json;
        // these constants are not generated from the production framing implementation.
        assertEquals("d7b72fe3171ad465a6a133f77315603798d0951d5e794ef0e5b1a00ecd22680a",
            DeckLibraryRequests.capture(id, Revision(7), "Checkpoint Ω"))
        assertEquals("395ece893325b8e57f50b96520f52596db6b1376c9f3cce5645383dbe2f03c9f",
            DeckLibraryRequests.restore(id, Revision(7), EntityId("target-1"), "Before restore Ω"))
    }

    @Test fun exactIdentityRevisionTargetAndLabelAreAllBoundWithoutNormalization() {
        val captured = DeckLibraryRequests.capture(id, Revision(7), "Checkpoint Ω")
        for (changed in listOf(
            DeckLibraryRequests.capture(id.copy(raw = "other"), Revision(7), "Checkpoint Ω"),
            DeckLibraryRequests.capture(id.copy(source = ProviderId("other")), Revision(7), "Checkpoint Ω"),
            DeckLibraryRequests.capture(id, Revision(8), "Checkpoint Ω"),
            DeckLibraryRequests.capture(id, Revision(7), " Checkpoint Ω"),
        )) assertNotEquals(captured, changed)
        val restored = DeckLibraryRequests.restore(id, Revision(7), EntityId("target-1"), "Before restore Ω")
        for (changed in listOf(
            DeckLibraryRequests.restore(id.copy(raw = "other"), Revision(7), EntityId("target-1"), "Before restore Ω"),
            DeckLibraryRequests.restore(id, Revision(8), EntityId("target-1"), "Before restore Ω"),
            DeckLibraryRequests.restore(id, Revision(7), EntityId("target-2"), "Before restore Ω"),
            DeckLibraryRequests.restore(id, Revision(7), EntityId("target-1"), "Before restore Ω "),
        )) assertNotEquals(restored, changed)
        assertNotEquals(captured, restored)
    }

    @Test fun labelByteBoundsAndStrictUnicodeRemainExact() {
        DeckLibraryRequests.capture(id, Revision(0), "é".repeat(512))
        DeckLibraryRequests.restore(id, Revision(0), EntityId("v"), "x".repeat(1024))
        for (invalid in listOf("", " ", "é".repeat(513), "x".repeat(1025), "bad\uD800")) {
            try { DeckLibraryRequests.capture(id, Revision(0), invalid); fail("Invalid capture label accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: java.nio.charset.CharacterCodingException) { }
            try { DeckLibraryRequests.restore(id, Revision(0), EntityId("v"), invalid); fail("Invalid recovery label accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: java.nio.charset.CharacterCodingException) { }
        }
    }
}
