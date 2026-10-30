package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class ConsentReviewState(
    val snapshot: SettingsSnapshot? = null,
    val loading: Boolean = false,
    val pending: EntityId? = null,
    val review: ConsentRecord? = null,
    val reviewTicket: Long = 0,
    val problem: Problem? = null,
    val changedElsewhere: Boolean = false,
)

/** Existing repository owns revocation/consent serialization. This UI never approves downloads or
 * acknowledges deletion. Retaining downloaded data is explicit; removal needs the separate drainer.
 */
internal class ConsentReviewController(
    private val scope: CoroutineScope,
    private val repository: SettingsRepository,
    private val dispatcher: CoroutineDispatcher,
) {
    private val mutable = MutableStateFlow(ConsentReviewState())
    val state: StateFlow<ConsentReviewState> = mutable.asStateFlow()
    private var job: Job? = null
    private var epoch = 0L
    private var reviewSequence = 0L

    fun load() {
        if (job?.isActive == true) return
        val current = ++epoch
        mutable.update { it.copy(loading = true, review = null, problem = null, changedElsewhere = false) }
        job = scope.launch {
            when (val result = callPort(dispatcher) { repository.read(it) }) {
                is PortResult.Ok -> if (current == epoch) mutable.value = ConsentReviewState(snapshot = result.value)
                is PortResult.Failed -> if (current == epoch) mutable.update { it.copy(loading = false, problem = result.problem) }
            }
        }
    }

    fun review(id: EntityId) {
        val s = state.value
        if (s.loading || s.pending != null) return
        val record = s.snapshot?.consents?.singleOrNull { it.id == id && it.revokedAt == null } ?: return
        mutable.update { it.copy(review = record, reviewTicket = ++reviewSequence, problem = null, changedElsewhere = false) }
    }

    fun dismiss(ticket: Long) {
        if (state.value.reviewTicket == ticket) mutable.update { it.copy(review = null) }
    }

    fun revoke(ticket: Long, record: ConsentRecord) {
        val s = state.value
        val snapshot = s.snapshot ?: return
        if (s.loading || s.pending != null || s.reviewTicket != ticket || s.review != record ||
            record.revokedAt != null || record !in snapshot.consents) return
        val current = ++epoch
        mutable.update { it.copy(review = null, pending = record.id, problem = null) }
        job = scope.launch {
            if (current != epoch) return@launch
            val patch = SettingsPatch(SnapshotList.of(SettingChange.RevokeConsent(record.id, removeDownloadedData = false)))
            when (val result = callPort(dispatcher) { repository.update(patch, snapshot.revision, it) }) {
                is PortResult.Ok -> if (current == epoch) mutable.value = ConsentReviewState(snapshot = result.value)
                is PortResult.Failed -> if (current == epoch) {
                    if (result.problem == Problem.STALE_REVISION) {
                        when (val latest = callPort(dispatcher) { repository.read(it) }) {
                            is PortResult.Ok -> if (current == epoch) mutable.value = ConsentReviewState(snapshot = latest.value, changedElsewhere = true)
                            is PortResult.Failed -> if (current == epoch) mutable.update { it.copy(pending = null, problem = latest.problem) }
                        }
                    } else mutable.update { it.copy(pending = null, problem = result.problem) }
                }
            }
        }
    }

    fun close() {
        ++epoch; job?.cancel(); job = null
        mutable.value = ConsentReviewState()
    }
}
