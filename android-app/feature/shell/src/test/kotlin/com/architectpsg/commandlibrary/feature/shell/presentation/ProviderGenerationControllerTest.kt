@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic UI consent/lifecycle oracles. Real history and transport are tested separately. */
class ProviderGenerationControllerTest {
    private val prompt = HistoryPromptCapture(OfflineLabMode.CHAT,
        PromptArtifact(OfflineLabMode.CHAT.templateId, 1, DeckId(ProviderId("local"), "deck"), Revision(7),
            "Exact private prompt Ω", SnapshotList.empty(), ContentHash("SHA-256", "a".repeat(64)), UtcTimestamp(1)),
        HistoryPromptProvenance(ContentHash("SHA-256", "b".repeat(64)), false, null))
    private class Fixture {
        val profiles = ProfileFixture()
        val row = StoredProviderProfile(profiles.profile(), Revision(2), UtcTimestamp(1), UtcTimestamp(2))
        var sends = 0
        val inputs = mutableListOf<NewHistoryTurn>()
        var turn: Turn? = null
        var wait: CompletableDeferred<Unit>? = null
        var fail = false
        init { profiles.rows[row.profile.id.value] = row }
        val history = Proxy.newProxyInstance(LocalAiHistory::class.java.classLoader,
            arrayOf(LocalAiHistory::class.java)) { _, method, args ->
            check(method.name.substringBefore('-') == "getTurn") { "Unexpected history call: ${method.name}" }
            check(args?.first() == inputs.last().id.value) { "Reload must use original attempt ID" }
            turn?.let { Outcome.Success(it) } ?: Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Synthetic missing")
        } as LocalAiHistory
        val access = AiHistoryAccess { Outcome.Success(history) }
        val generation = ProviderGenerationAccess { _, header, input, _, progress ->
            sends++; inputs += input
            val value = Turn(input.id, header.id, Revision(2), 1, HistoryTurnSource.PROVIDER_GENERATION,
                input.prompt, input.provenance, header.mode, input.providerProfileId, input.modelId, input.retryOf,
                EntityId("epoch"), TurnStatus.COMPLETE, "Received Ω", Knowledge.Unknown("Unknown", SnapshotList.empty()),
                UtcTimestamp(1), Knowledge.Known(UtcTimestamp(2)))
            turn = value
            progress(value)
            wait?.await()
            if (fail) Outcome.Unknown("Lost acknowledgement", SnapshotList.empty()) else Outcome.Success(value)
        }
        fun TestScope.controller(): ProviderGenerationController {
            var ids = 0
            return ProviderGenerationController(backgroundScope, profiles, generation, access,
                StandardTestDispatcher(testScheduler)) { EntityId("attempt-${++ids}") }
        }
    }
    @Test fun noSendOnOpenSelectionOrMissingConsentAndExactCapturedInput() = runTest {
        val f = Fixture(); val c = with(f) { controller() }
        c.open(prompt); runCurrent(); c.select(f.row)
        c.send(c.state.value.frame); runCurrent(); assertEquals(0, f.sends)
        c.consent(true, c.state.value.frame); c.send(c.state.value.frame); runCurrent()
        assertEquals(1, f.sends); assertEquals(prompt.artifact, f.inputs.single().prompt)
        assertEquals(prompt.provenance, f.inputs.single().provenance)
        assertEquals("", f.inputs.single().rawContent); assertFalse(c.state.value.consent)
        assertEquals(TurnStatus.COMPLETE, c.state.value.turn!!.status)
    }
    @Test fun oldRenderedConsentFrameCannotApproveDifferentSelection() = runTest {
        val f = Fixture(); val c = with(f) { controller() }
        c.open(prompt); runCurrent(); c.select(f.row); val old = c.state.value.frame
        c.select(f.row.copy(revision = Revision(3))) // Not a loaded row; no mutation.
        assertEquals(old, c.state.value.frame)
        c.loadProfiles(); runCurrent(); c.select(f.row)
        c.consent(true, old); c.send(c.state.value.frame); runCurrent()
        assertFalse(c.state.value.consent); assertEquals(0, f.sends)
    }
    @Test fun explicitRetryNeedsFreshConsentAndNewIdWithRetryLineage() = runTest {
        val f = Fixture(); val c = with(f) { controller() }
        c.open(prompt); runCurrent(); c.select(f.row)
        c.consent(true, c.state.value.frame); c.send(c.state.value.frame); runCurrent()
        val first = f.inputs.single().id
        c.send(c.state.value.frame); runCurrent(); assertEquals(1, f.sends)
        c.consent(true, c.state.value.frame); c.send(c.state.value.frame); runCurrent()
        assertEquals(2, f.sends); assertNotEquals(first, f.inputs.last().id)
        assertEquals(first, f.inputs.last().retryOf)
    }
    @Test fun uncertainAcknowledgementReloadsOriginalWithoutResendAndRecreationNeverSends() = runTest {
        val f = Fixture().also { it.fail = true }; val c = with(f) { controller() }
        c.open(prompt); runCurrent(); c.select(f.row)
        c.consent(true, c.state.value.frame); c.send(c.state.value.frame); runCurrent()
        assertTrue(c.state.value.uncertain)
        c.send(c.state.value.frame); runCurrent(); assertEquals(1, f.sends)
        c.reload(); runCurrent(); assertFalse(c.state.value.uncertain)
        assertEquals(f.inputs.single().id, c.state.value.turn!!.id)
        val recreated = with(f) { controller() }; recreated.open(prompt); runCurrent()
        assertEquals(1, f.sends); assertNull(recreated.state.value.turn)
    }
    @Test fun navigationCancellationDoesNotReopenDialogOrResend() = runTest {
        val f = Fixture().also { it.wait = CompletableDeferred() }; val c = with(f) { controller() }
        c.open(prompt); runCurrent(); c.select(f.row)
        c.consent(true, c.state.value.frame); c.send(c.state.value.frame); runCurrent()
        assertTrue(c.state.value.sending)
        c.hide(); runCurrent()
        assertFalse(c.state.value.visible); assertFalse(c.state.value.sending)
        assertTrue(c.state.value.uncertain); assertEquals(1, f.sends)
        c.open(prompt); runCurrent(); assertTrue(c.state.value.visible)
        assertEquals(1, f.sends)
    }
}
