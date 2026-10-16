package com.architectpsg.commandlibrary.core.model

/** Personal vocabulary, separate from sourced facts, deck annotations and derived suggestions. */
public data class PersonalTag(public val id: EntityId, public val name: String, public val retired: Boolean)

/** Ordered vocabulary includes retired identities. Revision0/null time is the never-stored pool only. */
public data class PersonalTagPool(
    public val revision: Revision,
    public val tags: SnapshotList<PersonalTag>,
    public val modifiedAt: UtcTimestamp?,
)

public object PersonalTagLimits {
    public const val MAX_TAGS: Int = 4096
    public const val MAX_NAME_BYTES: Int = 256
    public const val MAX_PAYLOAD_BYTES: Int = 2 * 1024 * 1024
}

/**
 * Complete ordered personal vocabulary. Names are exact nonblank strict Unicode/UTF8 <=256bytes;
 * IDs are distinct, exact nonblank strict Unicode/UTF8 <=DeckPayloadLimits.MAX_ID_BYTES,
 * with no ID normalization/reassignment, and are permanently reserved. At most4096 identities,
 * including retired entries, are stored. Active names are unique by NFKC + trim + Locale.ROOT
 * lowercase; search uses the same key's substring relation (blank query lists active tags).
 * Names may contain ordinary internal spaces, never control characters; no silent truncation.
 *
 * load returns revision0/empty/null timestamp for a never-stored pool. replace requires exact current
 * revision including0, validates the complete ordered list, and cannot drop any reserved identity.
 * Retirement is explicit and reversible. CAS precedes no-op detection; identical list returns the
 * unchanged pool. First actual save is revision1; later changes advance once atomically with time.
 * Overflow fails before writing. No hidden seed/default tags, catalog writes or network effects.
 *
 * Pool rename/reorder/retirement NEVER changes any saved deck/version/undo/draft. A deck captures
 * its own EntryLabel name and stable identity. Importing a pool tag into a deck is a separate
 * explicit existing DeckPatch transaction; an existing deck-local label wins over a later pool
 * name. Retiring a tag only hides it from the default picker; it does not remove memberships.
 * This vocabulary does not reinterpret RoleOverride.role as an ID or implement inferred defaults.
 *
 * Malformed input INVALID_INPUT; stale expected STALE_REVISION; contradictory stored content
 * CORRUPT_DATA; newer payload UNSUPPORTED. Cancellation propagates; lost acknowledgement is
 * uncertain, not rollback. Caller preserves dirty input and explicitly reloads for reconciliation;
 * there is no historical receipt or automatic write replay. Static errors exclude personal names.
 */
public interface LocalPersonalTags {
    public suspend fun load(cancel: CancelToken): Outcome<PersonalTagPool>
    public suspend fun replace(expected: Revision, tags: SnapshotList<PersonalTag>, cancel: CancelToken): Outcome<PersonalTagPool>
}
