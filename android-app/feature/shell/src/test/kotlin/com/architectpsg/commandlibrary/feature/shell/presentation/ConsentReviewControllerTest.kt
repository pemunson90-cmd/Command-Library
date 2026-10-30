@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.SyntheticSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class ConsentReviewControllerTest {
    private val intent = DownloadIntent(EntityId("synthetic-image"), DownloadKind.IMAGES, ProviderId("scryfall"),
        ResourceUri("https://cards.scryfall.io/normal/synthetic.jpg"), "v1", LanguageTag.ENGLISH, null, null,
        Knowledge.Unknown("Synthetic unknown size", SnapshotList.empty()))
    private val record = ConsentRecord(EntityId("consent-a"), intent, UtcTimestamp(1), null, true)
    private inner class Repository : SettingsRepository {
        var stored = SyntheticSettings().stored.copy(consents = SnapshotList.of(record))
        var writes = 0; var fail = false; var gate: CompletableDeferred<Unit>? = null
        override suspend fun read(cancel: CancelToken) = Outcome.Success(stored)
        override suspend fun approve(intent: DownloadIntent, cancel: CancelToken): Outcome<ConsentRecord> = error("review never approves")
        override suspend fun update(patch: SettingsPatch, expected: Revision, cancel: CancelToken): Outcome<SettingsSnapshot> {
            writes++; gate?.await()
            if (fail) return Outcome.Unknown("Synthetic uncertain persistence", SnapshotList.empty())
            if (expected != stored.revision) return Outcome.Failure(DomainFailure(FailureCode.STALE_REVISION, "Synthetic CAS", SnapshotList.empty()))
            val change = patch.changes.single() as SettingChange.RevokeConsent
            assertFalse("This control never claims downloaded-data deletion", change.removeDownloadedData)
            stored = stored.copy(revision = stored.revision.next(), consents = SnapshotList.copyOf(stored.consents.map {
                if (it.id == change.id) it.copy(revokedAt = UtcTimestamp(2)) else it
            }))
            return Outcome.Success(stored)
        }
    }
    private fun TestScope.controller(r: Repository) = ConsentReviewController(backgroundScope, r, StandardTestDispatcher(testScheduler))
    private fun ConsentReviewController.confirm() { val s = state.value; s.review?.let { revoke(s.reviewTicket, it) } }

    @Test fun loadAndReviewNeverWriteOrApprove() = runTest {
        val r = Repository(); val c = controller(r); c.load(); runCurrent(); c.review(record.id)
        assertEquals(record, c.state.value.review); assertEquals(0, r.writes)
        c.dismiss(c.state.value.reviewTicket); assertNull(c.state.value.review)
    }
    @Test fun explicitRevokeUsesExpectedRevisionAndPreservesOtherSettingsAndConsents() = runTest {
        val r = Repository(); val other = record.copy(id = EntityId("other")); r.stored = r.stored.copy(consents = SnapshotList.of(record, other))
        val before = r.stored; val c = controller(r); c.load(); runCurrent(); c.review(record.id); c.confirm(); runCurrent()
        assertEquals(1, r.writes); assertNotNull(c.state.value.snapshot!!.consents.first().revokedAt)
        assertEquals(other, r.stored.consents.last()); assertEquals(before.cache, r.stored.cache); assertEquals(before.theme, r.stored.theme)
        c.review(record.id); c.confirm(); runCurrent(); assertEquals(1, r.writes)
    }
    @Test fun staleDialogCannotRevokeAnotherRecordOrReopenedReview() = runTest {
        val r = Repository(); val other = record.copy(id = EntityId("other")); r.stored = r.stored.copy(consents = SnapshotList.of(record, other))
        val c = controller(r); c.load(); runCurrent(); c.review(record.id); val ticket = c.state.value.reviewTicket
        c.review(other.id); c.revoke(ticket, record); runCurrent(); assertEquals(0, r.writes)
        c.review(record.id); c.revoke(ticket, record); runCurrent(); assertEquals(0, r.writes)
        c.dismiss(ticket); assertEquals(record, c.state.value.review)
    }
    @Test fun staleRevisionReloadsWithoutReplayingRevocation() = runTest {
        val r = Repository(); val c = controller(r); c.load(); runCurrent(); c.review(record.id)
        r.stored = r.stored.copy(revision = Revision(1), theme = ThemePreference.DARK)
        c.confirm(); runCurrent(); assertEquals(1, r.writes)
        assertEquals(ThemePreference.DARK, c.state.value.snapshot!!.theme)
        assertTrue(c.state.value.changedElsewhere); assertNull(r.stored.consents.single().revokedAt)
    }
    @Test fun unknownWriteDoesNotPretendRevocationSucceeded() = runTest {
        val r = Repository().apply { fail = true }; val c = controller(r); c.load(); runCurrent(); c.review(record.id); c.confirm(); runCurrent()
        assertEquals(Problem.UNKNOWN_RESULT, c.state.value.problem); assertNull(c.state.value.snapshot!!.consents.single().revokedAt)
    }
    @Test fun closedViewRejectsAnOldConfirmationBeforeAnyCollectorRuns() = runTest {
        val r = Repository(); val c = controller(r); c.load(); runCurrent(); c.review(record.id)
        val ticket = c.state.value.reviewTicket
        c.close(); c.revoke(ticket, record); runCurrent()
        assertEquals(0, r.writes)
    }
    @Test fun closeBeforeQueuedConfirmationNeverWrites() = runTest {
        val r = Repository(); val c = controller(r); c.load(); runCurrent(); c.review(record.id); c.confirm(); c.close(); runCurrent()
        assertEquals(0, r.writes); assertNull(c.state.value.snapshot)
    }
}
