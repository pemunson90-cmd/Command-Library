package com.architectpsg.commandlibrary.core.model

/** A durable nonsecret configuration. A configured reference does not establish credential presence. */
public data class StoredProviderProfile(
    public val profile: ProviderProfile,
    public val revision: Revision,
    public val createdAt: UtcTimestamp,
    public val modifiedAt: UtcTimestamp,
)

/**
 * Complete local provider configuration, independent of generation/model discovery or key mutation.
 * IDs are caller-supplied and permanently reserved after creation, including deleted tombstones.
 * Create starts revision1; replace/delete compare expected revision and advance once atomically.
 * Profile secretRef is authoritative; endpoint.secret must equal it including null. References
 * must match exact normalized profile/origin/purpose provider-auth-v1. Attached refs prohibit
 * origin changes/deletion; explicit vault removal then CAS detach precedes either operation.
 * Only admitted HTTPS base paths/empty extraHeaders/empty redirect allowlists are stored initially;
 * this does not establish provider eligibility or silently defer other retained provider protocols.
 * List is stable ID BINARY ascending and excludes tombstones. Any mutation invalidates its cursor.
 * Missing load/delete is NOT_FOUND, stale expected/cursor STALE_REVISION, malformed INVALID_INPUT,
 * contradictory stored metadata/payload CORRUPT_DATA, newer codec UNSUPPORTED. No silent truncation.
 * Cancellation propagates and cannot prove rollback after possible commit. Explicit retry retains
 * the caller's ID then reloads; there is no historical exactly-once receipt or hidden key/network work.
 */
public interface LocalProviderProfiles {
    public suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<StoredProviderProfile>>
    public suspend fun load(id: EntityId, cancel: CancelToken): Outcome<StoredProviderProfile>
    public suspend fun create(profile: ProviderProfile, cancel: CancelToken): Outcome<StoredProviderProfile>
    public suspend fun replace(profile: ProviderProfile, expected: Revision, cancel: CancelToken): Outcome<StoredProviderProfile>
    public suspend fun remove(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit>
}
