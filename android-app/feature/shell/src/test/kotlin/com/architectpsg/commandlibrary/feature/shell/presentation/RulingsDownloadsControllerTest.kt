@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

class RulingsDownloadsControllerTest {
    private fun TestScope.controller(f: RulingsFixture) = DownloadsController(ownerScope(), f.downloads.access, f.downloads.settings, f.downloads,
        StandardTestDispatcher(testScheduler), {}, f.access, f)
    private fun TestScope.open(c: DownloadsController) { c.load(); advanceUntilIdle(); assertTrue(c.state.value.ready) }
    private fun TestScope.metadata(c: DownloadsController, f: RulingsFixture): EntityId {
        c.planRulings("Synthetic discovery"); advanceUntilIdle(); val id = c.state.value.approval!!.plan.jobId
        c.confirmApproval(); advanceUntilIdle(); f.downloads.runGate.complete(Unit); advanceUntilIdle(); return id
    }
    @Test fun metadataNeverInstallsAndFileNeedsItsOwnVisibleApproval() = runTest {
        val f = RulingsFixture(); val c = controller(f); open(c)
        assertEquals(0, f.metadataAdmissions); assertTrue(f.downloads.settingsBase.stored.consents.isEmpty())
        val id = metadata(c, f)
        assertEquals(0, f.installs); assertFalse(f.downloads.catalog.initialized); assertEquals(1, f.downloads.rows.size)
        c.planRulingsFile(id, "Synthetic ruling file"); advanceUntilIdle()
        val file = c.state.value.approval!!; assertNull(file.plan.request.intent.language)
        assertEquals(1, f.metadataAdmissions); assertEquals(1, f.downloads.settingsBase.stored.consents.size)
        c.dismissApproval(); advanceUntilIdle(); assertEquals(AcquisitionState.PLANNED, f.downloads.rows.getValue(file.plan.jobId).state)
        c.prepare(file.plan.jobId); advanceUntilIdle(); c.confirmApproval(); advanceUntilIdle()
        assertEquals(2, f.downloads.settingsBase.stored.consents.size); assertEquals(1, f.installs)
        assertNotNull(c.state.value.rulingsInstalled); assertEquals(f.active, c.state.value.rulings?.active)
        assertFalse(f.downloads.catalog.initialized)
    }
    @Test fun admissionFailureAndCancelledInstallationNeverReplaceOldRulings() = runTest {
        val f = RulingsFixture().also { it.installed() }; val old = f.active; val c = controller(f); open(c); val id = metadata(c, f)
        f.admissionFailure = Outcome.Unknown("Synthetic", SnapshotList.empty()); c.planRulingsFile(id, "File"); advanceUntilIdle()
        assertEquals(Problem.UNKNOWN_RESULT, c.state.value.problem); assertNull(c.state.value.approval); assertEquals(1, f.downloads.rows.size)
        f.admissionFailure = null; c.planRulingsFile(id, "File"); advanceUntilIdle()
        val file = c.state.value.approval!!.plan.jobId; f.downloads.installGate = CompletableDeferred()
        c.confirmApproval(); advanceUntilIdle(); assertTrue(file in c.state.value.installing)
        c.stopInstall(file); advanceUntilIdle(); assertEquals(old, f.active); assertEquals(old, c.state.value.rulings?.active)
        assertNull(c.state.value.rulingsInstalled)
        f.downloads.installGate = null; f.installFailure = failure(FailureCode.PERSISTENCE)
        c.install(file); advanceUntilIdle(); assertEquals(old, f.active); assertEquals(Problem.PERSISTENCE, c.state.value.problem)
        f.installFailure = null; c.install(file); advanceUntilIdle(); assertNotEquals(old, f.active)
        assertNotNull(c.state.value.rulingsInstalled)
    }
    @Test fun directMetadataInstallIsRejectedAndMissingCapabilityCannotPlanRulings() = runTest {
        val f = RulingsFixture(); val c = controller(f); open(c); val id = metadata(c, f)
        c.install(id); advanceUntilIdle(); assertEquals(0, f.installs); assertEquals(Problem.INVALID_INPUT, c.state.value.problem)
        val english = DownloadsController(ownerScope(), f.downloads.access, f.downloads.settings, f.downloads, StandardTestDispatcher(testScheduler), {})
        open(english); english.planRulings("Unsupported"); advanceUntilIdle(); assertEquals(1, f.downloads.rows.size)
    }
    @Test fun differingDownloadOwnersRejectRulingPlanningBeforeConsentOrJobWrites() = runTest {
        val english = DownloadsFixture(); val foreign = RulingsFixture()
        val c = DownloadsController(ownerScope(), english.access, english.settings, english, StandardTestDispatcher(testScheduler), {}, foreign.access, foreign)
        open(c); c.planRulings("Wrong owner"); advanceUntilIdle()
        assertEquals(Problem.SOURCE_MISMATCH, c.state.value.problem)
        assertTrue(english.rows.isEmpty()); assertTrue(foreign.downloads.rows.isEmpty())
        assertTrue(english.settingsBase.stored.consents.isEmpty())
    }
}
