package com.architectpsg.commandlibrary.core.model

/** Missing row differs from a retained reset row (body=null); versions are never silently adopted. */
public data class StoredPromptOverride(
    public val mode: OfflineLabMode,
    public val shippedVersion: Int,
    public val revision: Revision,
    public val body: String?,
    public val modifiedAt: UtcTimestamp,
)

/**
 * Complete local per-mode advanced template persistence; no deck/catalog/credential/network work.
 * Save validates exact current shipped placeholder grammar before I/O, preserving strict UTF8 body.
 * Null expected admits only a never-stored row; every existing reset/live row requires its revision.
 * CAS precedes no-op detection; identical current-version save/already-reset may return unchanged.
 * Missing reset with null expected returns null without a clock read/write. New save is revision1;
 * changes advance once, resets retain identity/revision and clear body. Overflow rejects before write.
 * Load preserves a different shipped-version body for review, never applies current grammar to it.
 * Current-version malformed body is CORRUPT_DATA; newer storage format is UNSUPPORTED; stale expected
 * is STALE_REVISION. Invalid input is INVALID_INPUT. Errors are static and exclude body contents.
 * Cancellation propagates and cannot establish rollback after a possible commit. Explicit reload
 * after uncertainty preserves the caller's dirty draft; no automatic write replay or fake receipts.
 */
public interface LocalPromptOverrides {
    public suspend fun load(mode: OfflineLabMode, cancel: CancelToken): Outcome<StoredPromptOverride?>
    public suspend fun save(mode: OfflineLabMode, expected: Revision?, body: String, cancel: CancelToken): Outcome<StoredPromptOverride>
    public suspend fun reset(mode: OfflineLabMode, expected: Revision?, cancel: CancelToken): Outcome<StoredPromptOverride?>
}
