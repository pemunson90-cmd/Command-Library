package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.shell.CardArtworkAccess
import com.architectpsg.commandlibrary.feature.shell.CardArtworkImage
import com.architectpsg.commandlibrary.feature.shell.CardArtworkOffer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class CardArtworkState(
    val printing: CardId? = null,
    val generation: GenerationId? = null,
    val offers: List<CardArtworkOffer> = emptyList(),
    val approval: CardArtworkOffer? = null,
    val approvalTicket: Long = 0,
    val image: CardArtworkImage? = null,
    val busy: Boolean = false,
    val problem: Problem? = null,
)

/** Local preparation never downloads. Only an explicit confirmation may approve and load.
 * Selection tickets fence noncooperative returns; visible pixels are checked locally until closed.
 * Transient images/approvals are deliberately absent from saved state and never replayed.
 */
internal class CardArtworkController(
    private val scope: CoroutineScope,
    private val access: CardArtworkAccess,
    private val settings: SettingsRepository,
    private val dispatcher: CoroutineDispatcher,
    private val isCurrent: (CardId, GenerationId) -> Boolean = { _, _ -> true },
) {
    private val mutable = MutableStateFlow(CardArtworkState())
    val state: StateFlow<CardArtworkState> = mutable.asStateFlow()
    private var job: Job? = null
    private var ticket = 0L
    private var reviewSequence = 0L

    fun select(printing: CardId?, generation: GenerationId?) {
        if (state.value.printing == printing && state.value.generation == generation) return
        close()
        if (printing == null || generation == null || !isCurrent(printing, generation)) return
        val current = ticket
        mutable.value = CardArtworkState(printing, generation, busy = true)
        job = scope.launch {
            when (val result = callPort(dispatcher) { access.prepare(printing, generation, it) }) {
                is PortResult.Ok -> if (current == ticket) {
                    val offers = result.value
                    val valid = offers.size <= 17 && offers.distinct().size == offers.size && offers.all {
                        it.printingId == printing && it.generation == generation
                    }
                    mutable.update { it.copy(offers = if (valid) offers.toList() else emptyList(), busy = false,
                        problem = if (valid) null else Problem.SOURCE_MISMATCH) }
                }
                is PortResult.Failed -> if (current == ticket) mutable.update { it.copy(busy = false, problem = result.problem) }
            }
        }
    }

    fun review(offer: CardArtworkOffer) {
        val s = state.value
        if (!s.busy && offer in s.offers && offer.printingId == s.printing && offer.generation == s.generation && isCurrent(offer.printingId, offer.generation))
            mutable.update { it.copy(approval = offer, approvalTicket = ++reviewSequence, problem = null) }
    }

    fun dismissApproval(reviewTicket: Long) {
        if (state.value.approvalTicket == reviewTicket) mutable.update { it.copy(approval = null) }
    }

    /** Called only by the review dialog's positive action, never composition, restoration or polling. */
    fun approveAndLoad(reviewTicket: Long, offer: CardArtworkOffer) {
        val s = state.value
        if (s.approval != offer || s.approvalTicket != reviewTicket || s.busy || offer !in s.offers ||
            !isCurrent(offer.printingId, offer.generation)) return
        job?.cancel()
        val current = ++ticket
        mutable.update { it.copy(approval = null, image = null, busy = true, problem = null) }
        job = scope.launch {
            if (current != ticket || !isCurrent(offer.printingId, offer.generation)) return@launch
            val consent = when (val result = callPort(dispatcher) { settings.approve(offer.intent, it) }) {
                is PortResult.Ok -> result.value
                is PortResult.Failed -> { fail(current, result.problem); return@launch }
            }
            if (current != ticket || !isCurrent(offer.printingId, offer.generation)) return@launch
            if (!consent.permits(offer.intent)) { fail(current, Problem.CONSENT_REQUIRED); return@launch }
            val image = when (val result = callPort(dispatcher) { access.load(offer, consent.id, it) }) {
                is PortResult.Ok -> result.value
                is PortResult.Failed -> { fail(current, result.problem); return@launch }
            }
            if (current != ticket || !isCurrent(offer.printingId, offer.generation)) return@launch
            if (image.offer != offer) { fail(current, Problem.SOURCE_MISMATCH); return@launch }
            // Recheck after dispatcher return, before exposing pixels. Connectivity is not required
            // by permitted(): an already loaded image remains available offline until invalidated.
            while (current == ticket && isCurrent(offer.printingId, offer.generation)) {
                when (val result = callPort(dispatcher) { access.permitted(offer, consent.id, it) }) {
                    is PortResult.Ok -> if (!result.value) { fail(current, Problem.REVOKED); return@launch }
                    is PortResult.Failed -> { fail(current, result.problem); return@launch }
                }
                if (current != ticket || !isCurrent(offer.printingId, offer.generation)) return@launch
                mutable.update { it.copy(image = image, busy = false, problem = null) }
                delay(250)
            }
            if (current == ticket) close()
        }
    }

    fun close() {
        ++ticket
        job?.cancel(); job = null
        mutable.value = CardArtworkState()
    }

    private fun fail(current: Long, problem: Problem) {
        if (current == ticket) mutable.update { it.copy(image = null, busy = false, problem = problem) }
    }
}
