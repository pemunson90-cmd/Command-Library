package com.architectpsg.commandlibrary.core.model

/** Metadata-only projection; it does not certify unread payload byte integrity. */
public data class DeckSummary(
    public val id: DeckId,
    public val name: String,
    public val revision: Revision,
    public val createdAt: UtcTimestamp,
    public val modifiedAt: UtcTimestamp,
    public val entryCount: Int,
    public val totalQuantity: Long,
)

public object LocalDeckLibraryLimits {
    public val SOURCE: ProviderId = ProviderId("local-decks")
    public const val MAX_NAME_BYTES: Int = 1_024
    public const val MAX_ID_BYTES: Int = 1_024
}

/**
 * Complete local library capability. Editing, versions and package mutation have separate owners.
 * Create preserves caller content/identity/timestamps, requiring local source and revision zero.
 * Mutations reconcile the same operation ID and exact input before checking current source state.
 * A replay returns its historical committed result. Conflicting operation reuse is INVALID_INPUT.
 * Cancellation propagates; an interrupted acknowledgement does not prove durable rollback.
 */
public interface LocalDeckLibrary {
    public suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot>
    public suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot>
    /** BINARY identity order, metadata only; new successful operations invalidate old cursors. */
    public suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>>
    /** Fresh local deck/entry/package-instance IDs, remapped overrides and contribution targets,
     * injected current time and revision zero; package/version identities remain unchanged. */
    public suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot>
}
