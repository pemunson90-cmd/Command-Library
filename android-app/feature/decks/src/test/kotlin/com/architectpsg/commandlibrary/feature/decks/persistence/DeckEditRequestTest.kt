package com.architectpsg.commandlibrary.feature.decks.persistence

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test

class DeckEditRequestTest {
    private val id = DeckId(LocalDeckLibraryLimits.SOURCE, "golden-deck")
    private fun golden(name: String): ByteArray = requireNotNull(javaClass.getResourceAsStream("/$name.hex"))
        .bufferedReader().use { it.readText().trim() }.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    @Test fun applyAndUndoMatchIndependentRequestGoldensAndExactSha() {
        val apply = DeckLibraryRequests.apply(id, Revision(0), SnapshotBytes(golden("patch-rename-v1")))
        assertEquals("be04a1397477cd86e650456b59779ca03433213d912ed77c13bc45195f34deb6", apply)
        assertEquals(DeckLibraryRequests.sha256(golden("apply-request-v1")), apply)
        val undo = DeckLibraryRequests.undo(id, Revision(1))
        assertEquals("3bd8ad4995520ec512f0c628aebda433116d7e6a0bafb5382384c5a13ee49977", undo)
        assertEquals(DeckLibraryRequests.sha256(golden("undo-request-v1")), undo)
        assertNotEquals(undo, DeckLibraryRequests.undo(id, Revision(0)))
        assertNotEquals(apply, DeckLibraryRequests.apply(id, Revision(1), SnapshotBytes(golden("patch-rename-v1"))))
        assertNotEquals(apply, DeckLibraryRequests.apply(id.copy(raw="other"), Revision(0), SnapshotBytes(golden("patch-rename-v1"))))
    }
    @Test fun requestV1RejectsWrongPatchProtocolOrVersionRatherThanHashingFutureDefault() {
        for (wrong in listOf(byteArrayOf(), ByteArray(DeckPayloadLimits.MAX_ENCODED_BYTES + 1), golden("patch-rename-v1").also { it[3] = 75 }, golden("patch-rename-v1").also { it[5] = 2 })) {
            assertThrows(IllegalArgumentException::class.java) { DeckLibraryRequests.apply(id, Revision(0), SnapshotBytes(wrong)) }
        }
    }
}
