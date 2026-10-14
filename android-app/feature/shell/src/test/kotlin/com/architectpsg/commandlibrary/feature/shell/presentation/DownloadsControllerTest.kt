@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class DownloadsControllerTest {
    private fun TestScope.controller(f: DownloadsFixture, refresh: () -> Unit = {}): DownloadsController =
        DownloadsController(ownerScope(), f.access, f.settings, f, StandardTestDispatcher(testScheduler), refresh)
    private fun TestScope.open(c: DownloadsController) { c.load(); advanceUntilIdle(); assertTrue(c.state.value.ready) }
    private fun TestScope.start(c: DownloadsController): EntityId {
        c.plan("Synthetic English"); advanceUntilIdle()
        val id = c.state.value.approval!!.plan.jobId
        c.confirmApproval(); advanceUntilIdle(); return id
    }

    @Test fun openingAndRealCursorPagingNeverApproveOrRunAndAccessCanRetry() = runTest {
        val f = DownloadsFixture(); repeat(23) { f.seed() }
        f.accessFailure = Outcome.Unavailable(UnavailableReason.SOURCE_UNAVAILABLE, "Synthetic")
        val c = controller(f); c.load(); advanceUntilIdle(); assertFalse(c.state.value.ready); assertNotNull(c.state.value.problem)
        f.accessFailure = null; open(c)
        assertEquals(20, c.state.value.items.size); assertNotNull(c.state.value.nextCursor)
        c.loadMore(); advanceUntilIdle(); assertEquals(23, c.state.value.items.size); assertNull(c.state.value.nextCursor)
        assertTrue(f.settingsBase.stored.consents.isEmpty()); assertTrue(c.state.value.running.isEmpty())
        assertTrue(f.rows.values.all { it.state == AcquisitionState.PLANNED })
    }

    @Test fun dismissingPlanDoesNothingAndChangedPersistedRevisionRejectsBeforeConsent() = runTest {
        val f = DownloadsFixture(); val c = controller(f); open(c)
        c.changeWifiOnly(false); c.plan("Synthetic English"); advanceUntilIdle()
        val shown = c.state.value.approval!!; assertFalse(shown.plan.request.wifiOnly)
        c.dismissApproval(); assertTrue(f.settingsBase.stored.consents.isEmpty()); assertEquals(AcquisitionState.PLANNED, f.rows.getValue(shown.plan.jobId).state)
        c.prepare(shown.plan.jobId); advanceUntilIdle()
        f.publish(shown.plan.jobId) { it.copy(plan = it.plan.copy(request = it.plan.request.copy(displayName = "Changed elsewhere"))) }
        c.confirmApproval(); advanceUntilIdle()
        assertEquals(Problem.STALE_REVISION, c.state.value.problem); assertEquals(shown, c.state.value.approval)
        assertTrue(f.settingsBase.stored.consents.isEmpty()); assertTrue(c.state.value.running.isEmpty())
        c.prepare(shown.plan.jobId); advanceUntilIdle(); val actual = c.state.value.approval!!
        c.confirmApproval(); c.confirmApproval(); advanceUntilIdle()
        assertEquals(actual.plan.request.intent, f.settingsBase.stored.consents.single().intent)
        assertEquals(AcquisitionState.RUNNING, f.rows.getValue(shown.plan.jobId).state)
        assertEquals(setOf(shown.plan.jobId), c.state.value.running)
    }

    @Test fun consentFailureUnknownAndUnavailableRetainPlanAndNeverQueueUntilExplicitRetry() = runTest {
        val f = DownloadsFixture(); val c = controller(f); open(c); c.plan("Synthetic"); advanceUntilIdle()
        val shown = c.state.value.approval!!
        val failures: List<Outcome<Nothing>> = listOf(failure(FailureCode.PERSISTENCE),
            Outcome.Unavailable(UnavailableReason.CONSENT_REQUIRED, "Synthetic"), Outcome.Unknown("Synthetic", SnapshotList.empty()))
        for (failure in failures) {
            f.approvalFailure = failure; c.confirmApproval(); advanceUntilIdle()
            assertEquals(shown, c.state.value.approval); assertNotNull(c.state.value.problem)
            assertEquals(AcquisitionState.PLANNED, f.rows.getValue(shown.plan.jobId).state); assertTrue(f.settingsBase.stored.consents.isEmpty())
        }
        f.approvalFailure = null; c.confirmApproval(); advanceUntilIdle()
        assertNull(c.state.value.approval); assertEquals(AcquisitionState.RUNNING, f.rows.getValue(shown.plan.jobId).state)
    }

    @Test fun pauseAndConfirmedCancelStayUsableDuringRunnerAndResumeIsExplicit() = runTest {
        val f = DownloadsFixture(); val c = controller(f); open(c); val id = start(c)
        assertEquals(AcquisitionState.RUNNING, c.state.value.selected!!.state)
        c.pause(id); advanceUntilIdle()
        assertEquals(AcquisitionState.PAUSED, c.state.value.selected!!.state); assertNull(c.state.value.problem); assertTrue(c.state.value.running.isEmpty())
        c.prepare(id); advanceUntilIdle(); assertEquals(AcquisitionState.PAUSED, f.rows.getValue(id).state)
        c.confirmApproval(); advanceUntilIdle(); assertEquals(AcquisitionState.RUNNING, f.rows.getValue(id).state)
        c.requestCancel(id); c.dismissCancel(); assertEquals(AcquisitionState.RUNNING, f.rows.getValue(id).state)
        c.requestCancel(id); c.confirmCancel(); advanceUntilIdle()
        assertEquals(AcquisitionState.CANCELLED, f.rows.getValue(id).state); assertNull(c.state.value.pendingCancel)
        assertNull(c.state.value.problem); assertTrue(c.state.value.running.isEmpty()); assertFalse(f.catalog.initialized)
    }

    @Test fun oneDetailObserverRejectsOlderRevisionAndBackReleasesObservation() = runTest {
        val f = DownloadsFixture(); val first = f.seed(); val second = f.seed(); val c = controller(f); open(c)
        c.select(first.plan.jobId); advanceUntilIdle(); assertEquals(1, f.observers.get())
        val fresh = f.publish(first.plan.jobId) { it.copy(transferredBytes = 100) }; advanceUntilIdle()
        f.emitObserved(first); advanceUntilIdle(); assertEquals(fresh, c.state.value.selected)
        c.select(second.plan.jobId); advanceUntilIdle(); assertEquals(1, f.observers.get()); assertEquals(second, c.state.value.selected)
        c.closeDetail(); advanceUntilIdle(); assertEquals(0, f.observers.get()); assertNull(c.state.value.selectedId)
    }

    @Test fun failedInstallKeepsOldCatalogAndCompletedArtifactThenExplicitRetryRefreshesActualGeneration() = runTest {
        val f = DownloadsFixture(); val old = f.catalog; var refreshes = 0
        f.installFailure = failure(FailureCode.PERSISTENCE)
        val c = controller(f) { refreshes++ }; open(c); val id = start(c)
        f.runGate.complete(Unit); advanceUntilIdle()
        assertEquals(AcquisitionState.COMPLETED, f.rows.getValue(id).state); assertTrue(f.rows.getValue(id).artifact is Knowledge.Known)
        assertEquals(old, f.catalog); assertEquals(old, c.state.value.catalog); assertEquals(0, refreshes)
        assertNull(c.state.value.installed); assertEquals(Problem.PERSISTENCE, c.state.value.problem)
        f.installFailure = null; c.install(id); advanceUntilIdle()
        assertTrue(f.catalog.initialized); assertEquals(f.catalog, c.state.value.catalog); assertEquals(1, refreshes)
        assertEquals(f.catalog.activeGenerations.single(), c.state.value.installed!!.installed.generation)
    }

    @Test fun completingTwoTransfersQueuesInstallAndQueuedOrRunningInstallCanBeStoppedWithoutSuccessClaim() = runTest {
        val f = DownloadsFixture(); f.installGate = CompletableDeferred(); var refreshes = 0
        val c = controller(f) { refreshes++ }; open(c); val one = start(c); val two = start(c)
        f.runGate.complete(Unit); advanceUntilIdle()
        assertEquals(1, c.state.value.installing.size); assertEquals(1, c.state.value.queuedInstalls.size)
        assertEquals(setOf(one, two), c.state.value.installing + c.state.value.queuedInstalls)
        val queued = c.state.value.queuedInstalls.single(); c.stopInstall(queued); advanceUntilIdle()
        assertTrue(c.state.value.queuedInstalls.isEmpty())
        val active = c.state.value.installing.single(); assertNotNull(c.state.value.installProgress[active])
        c.stopInstall(active); advanceUntilIdle()
        assertTrue(c.state.value.installing.isEmpty()); assertTrue(c.state.value.installProgress.isEmpty())
        assertNull(c.state.value.installed); assertFalse(f.catalog.initialized); assertEquals(f.catalog, c.state.value.catalog)
        assertEquals(1, refreshes) // Cancellation re-reads durable truth; it never asserts success.
        f.installGate = null; c.install(queued); advanceUntilIdle(); assertNotNull(c.state.value.installed)
    }

    @Test fun stoppingAfterDurableActivationReconcilesActualCatalogWithoutAcknowledgedSuccess() = runTest {
        val f = DownloadsFixture(); f.installAfterCommitGate = CompletableDeferred(); var refreshes = 0
        val c = controller(f) { refreshes++ }; open(c); val id = start(c)
        f.runGate.complete(Unit); advanceUntilIdle()
        assertTrue(f.catalog.initialized); assertTrue(id in c.state.value.installing)
        assertNull(c.state.value.installed); assertEquals(0, refreshes)
        c.stopInstall(id); advanceUntilIdle()
        assertEquals(f.catalog, c.state.value.catalog); assertTrue(c.state.value.catalog!!.initialized)
        assertNull(c.state.value.installed); assertEquals(1, refreshes); assertTrue(c.state.value.installing.isEmpty())
        assertNull(c.state.value.problem)
    }

    @Test fun completedDownloadsInstallSequentiallyAndOpeningAgainDoesNotAutoInstallOldJobs() = runTest {
        val f = DownloadsFixture(); f.installGate = CompletableDeferred(); var refreshes = 0
        val c = controller(f) { refreshes++ }; open(c); start(c); start(c)
        f.runGate.complete(Unit); advanceUntilIdle(); assertEquals(1, c.state.value.queuedInstalls.size)
        f.installGate!!.complete(Unit); advanceUntilIdle()
        assertEquals(2, refreshes); assertTrue(c.state.value.installing.isEmpty()); assertTrue(c.state.value.queuedInstalls.isEmpty())
        val reopened = controller(f) { refreshes++ }; open(reopened); assertEquals(2, refreshes); assertNull(reopened.state.value.installed)
        assertTrue(reopened.state.value.installing.isEmpty())
    }
}
