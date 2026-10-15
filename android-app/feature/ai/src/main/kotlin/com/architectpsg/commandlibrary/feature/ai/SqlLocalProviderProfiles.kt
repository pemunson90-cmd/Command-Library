package com.architectpsg.commandlibrary.feature.ai

import com.architectpsg.commandlibrary.core.model.*

/** Nonsecret provider CRUD only. reference() is pure; this repository never accesses key material. */
public class SqlLocalProviderProfiles(
    private val db: Database,
    private val clock: Clock,
    private val vault: SecretVault,
) : LocalProviderProfiles {
    public override suspend fun create(profile: ProviderProfile, cancel: CancelToken): Outcome<StoredProviderProfile> = operation(cancel) { tx ->
        val admitted = admit(profile, false)
        val existing = query(tx, "SELECT 1 AS present FROM provider_profile WHERE profile_id=:id", cancel, "id" to text(admitted.id.value))
        if (existing.isNotEmpty()) profileReject("Provider identity is permanently reserved", FailureCode.STALE_REVISION)
        val sequence = nextEpoch(tx, cancel)
        val now = clock.utcNow()
        val stored = StoredProviderProfile(admitted, Revision(1), now, now)
        val payload = profileValue(ProviderProfileCodec.encode(stored)).copyBytes()
        val params = values(stored, sequence, payload)
        exactlyOne(profileValue(tx.execute(request("""INSERT INTO provider_profile
            (profile_id,revision,change_sequence,deleted,created_seconds,created_nanos,modified_seconds,modified_nanos,payload_version,payload_length,payload_sha256,payload)
            VALUES (:id,:revision,:sequence,0,:created,:createdNano,:modified,:modifiedNano,1,:length,:hash,:payload)""", *params), cancel)))
        stored
    }

    public override suspend fun load(id: EntityId, cancel: CancelToken): Outcome<StoredProviderProfile> = operation(cancel) { tx ->
        checkedId(id.value, false)
        load(tx, metadata(tx, id.value, cancel), cancel)
    }

    public override suspend fun replace(profile: ProviderProfile, expected: Revision, cancel: CancelToken): Outcome<StoredProviderProfile> = operation(cancel) { tx ->
        val admitted = admit(profile, false)
        val old = load(tx, metadata(tx, admitted.id.value, cancel), cancel)
        expected(old, expected)
        profileRequire(old.profile.secretRef == null || old.profile.endpoint!!.origin == admitted.endpoint!!.origin,
            "Detach provider secret before changing origin")
        val revision = nextRevision(old.revision)
        val sequence = nextEpoch(tx, cancel)
        val stored = StoredProviderProfile(admitted, revision, old.createdAt, clock.utcNow())
        val payload = profileValue(ProviderProfileCodec.encode(stored)).copyBytes()
        exactlyOne(profileValue(tx.execute(request("""UPDATE provider_profile SET revision=:revision,change_sequence=:sequence,
            modified_seconds=:modified,modified_nanos=:modifiedNano,payload_version=1,payload_length=:length,payload_sha256=:hash,payload=:payload
            WHERE profile_id=:id AND deleted=0 AND revision=:expected""",
            *values(stored, sequence, payload, includeCreated = false), "expected" to number(expected.value)), cancel)))
        stored
    }

    public override suspend fun remove(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit> = operation(cancel) { tx ->
        checkedId(id.value, false)
        val old = load(tx, metadata(tx, id.value, cancel), cancel)
        expected(old, expected)
        profileRequire(old.profile.secretRef == null, "Detach provider secret before deletion")
        val revision = nextRevision(old.revision)
        val sequence = nextEpoch(tx, cancel)
        val now = clock.utcNow()
        exactlyOne(profileValue(tx.execute(request("""UPDATE provider_profile SET revision=:revision,change_sequence=:sequence,deleted=1,
            modified_seconds=:modified,modified_nanos=:modifiedNano,payload_version=1,payload_length=0,payload_sha256=:hash,payload=NULL
            WHERE profile_id=:id AND deleted=0 AND revision=:expected""",
            "revision" to number(revision.value), "sequence" to number(sequence), "modified" to number(now.epochSeconds),
            "modifiedNano" to number(now.nanoAdjustment.toLong()), "hash" to text(ProviderProfileCodec.emptyHash),
            "id" to text(id.value), "expected" to number(expected.value)), cancel)))
    }

    public override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<StoredProviderProfile>> = operation(cancel) { tx ->
        val cursor = page.cursor?.let(ProviderProfileCursor::decode)
        val epoch = epoch(tx, cancel)
        if (cursor != null && cursor.epoch != epoch) profileReject("Provider list changed; reload", FailureCode.STALE_REVISION)
        val params = mutableListOf("limit" to number(page.limit.toLong() + 1))
        val after = if (cursor == null) "" else "AND profile_id COLLATE BINARY > :after".also { params += "after" to text(cursor.lastId) }
        // Lookahead fetches only bounded keys. Metadata and BLOB reads are limited to admitted page rows.
        val keys = profileValue(tx.query(request("SELECT ${boundedText("profile_id", 256)} FROM provider_profile WHERE (typeof(deleted)!='integer' OR deleted<>1) $after ORDER BY profile_id COLLATE BINARY LIMIT :limit",
            *params.toTypedArray(), maxRows = page.limit + 1), cancel)).map { row -> row.utf8("profile_id").also { checkedId(it, true) } }
        val items = mutableListOf<StoredProviderProfile>()
        var bytes = 0L
        for (key in keys.take(page.limit)) {
            val meta = metadata(tx, key, cancel)
            if (bytes + meta.length > PAGE_BYTES) break
            items += load(tx, meta, cancel)
            bytes += meta.length
        }
        val next = if (items.size < keys.size) ProviderProfileCursor(epoch, items.last().profile.id.value).encode() else null
        Page(SnapshotList.copyOf(items), next, SnapshotReference(ProviderId("local-provider-profiles"), null, Revision(epoch), null))
    }

    private fun admit(profile: ProviderProfile, stored: Boolean): ProviderProfile {
        val result = ProviderProfilePolicy.normalize(profile)
        val normalized = if (stored && result !is Outcome.Success) corrupt("Invalid stored provider configuration") else profileValue(result)
        if (stored && normalized != profile) corrupt("Noncanonical stored provider configuration")
        normalized.secretRef?.let { ref ->
            val derived = vault.reference(ref.scope)
            if (derived !is Outcome.Success || derived.value != ref) {
                if (stored) corrupt("Stored provider reference does not match vault identity")
                else profileReject("Provider reference does not match vault identity")
            }
        }
        return normalized
    }

    private data class Metadata(val id: EntityId, val revision: Revision, val sequence: Long, val deleted: Boolean,
        val created: UtcTimestamp, val modified: UtcTimestamp, val length: Long, val hash: String)

    private suspend fun metadata(tx: DbTransaction, id: String, cancel: CancelToken): Metadata {
        val rows = query(tx, "SELECT $METADATA FROM provider_profile WHERE profile_id=:id", cancel, "id" to text(id))
        if (rows.isEmpty()) throw ProfileProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Provider profile not found"))
        if (rows.size != 1) corrupt("Duplicate provider identity")
        val row = rows.single()
        val raw = row.utf8("profile_id"); checkedId(raw, true)
        if (raw != id) corrupt("Provider identity projection mismatch")
        val revision = row.long("revision"); val sequence = row.long("change_sequence"); val deleted = row.long("deleted")
        if (revision <= 0 || sequence <= 0 || deleted !in 0..1) corrupt("Invalid provider revision metadata")
        val created = timestamp(row, "created"); val modified = timestamp(row, "modified")
        val version = row.long("payload_version")
        if (version > ProviderProfileCodec.VERSION) throw ProfileProblem(Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Newer provider payload version"))
        if (version != ProviderProfileCodec.VERSION.toLong()) corrupt("Invalid provider payload version")
        val length = row.long("payload_length"); val actualLength = row.long("actual_length")
        val hash = row.utf8("payload_sha256")
        if (hash.length != 64 || hash.any { it !in "0123456789abcdef" }) corrupt("Invalid provider payload checksum")
        val type = row.string("payload_type")
        if (deleted == 1L) {
            if (type != "null" || length != 0L || actualLength != 0L || hash != ProviderProfileCodec.emptyHash) corrupt("Invalid provider tombstone")
        } else if (type != "blob" || length !in 1..ProviderProfileCodec.MAX_BYTES.toLong() || actualLength != length) {
            corrupt("Invalid provider payload type or length")
        }
        return Metadata(EntityId(raw), Revision(revision), sequence, deleted == 1L, created, modified, length, hash)
    }

    private suspend fun load(tx: DbTransaction, meta: Metadata, cancel: CancelToken): StoredProviderProfile {
        if (meta.deleted) throw ProfileProblem(Outcome.Unavailable(UnavailableReason.NOT_FOUND, "Provider profile not found"))
        val rows = query(tx, "SELECT payload FROM provider_profile WHERE profile_id=:id AND deleted=0", cancel, "id" to text(meta.id.value))
        if (rows.size != 1) corrupt("Missing provider payload")
        val payload = (rows.single().column("payload") as? SqlValue.Blob)?.value ?: corrupt("Invalid provider payload")
        if (payload.size.toLong() != meta.length || ProviderProfileCodec.hash(payload.copyBytes()) != meta.hash) corrupt("Provider payload checksum mismatch")
        val decoded = profileValue(ProviderProfileCodec.decode(payload))
        if (decoded.profile.id != meta.id || decoded.revision != meta.revision || decoded.createdAt != meta.created || decoded.modifiedAt != meta.modified) {
            corrupt("Provider payload metadata mismatch")
        }
        admit(decoded.profile, true)
        return decoded
    }

    private suspend fun epoch(tx: DbTransaction, cancel: CancelToken): Long {
        val rows = query(tx, "SELECT CASE WHEN COUNT(*)=0 THEN 0 WHEN typeof(MAX(change_sequence))='integer' THEN MAX(change_sequence) ELSE NULL END AS epoch FROM provider_profile", cancel)
        if (rows.size != 1) corrupt("Invalid provider change epoch")
        return rows.single().long("epoch").also { if (it < 0) corrupt("Invalid provider change epoch") }
    }
    private suspend fun nextEpoch(tx: DbTransaction, cancel: CancelToken): Long {
        val epoch = epoch(tx, cancel)
        profileRequire(epoch < Long.MAX_VALUE, "Provider change sequence exhausted")
        return epoch + 1
    }
    private fun nextRevision(revision: Revision): Revision {
        profileRequire(revision.value < Long.MAX_VALUE, "Provider revision exhausted")
        return Revision(revision.value + 1)
    }
    private fun expected(old: StoredProviderProfile, expected: Revision) {
        if (old.revision != expected) profileReject("Provider profile changed; reload", FailureCode.STALE_REVISION)
    }
    private fun checkedId(id: String, stored: Boolean) {
        try { profileText(id, 256) } catch (problem: ProfileProblem) {
            if (stored) corrupt("Invalid stored provider identity") else throw problem
        }
    }
    private fun timestamp(row: RowData, prefix: String): UtcTimestamp {
        val seconds = row.long("${prefix}_seconds"); val nanos = row.long("${prefix}_nanos")
        if (nanos !in 0..999_999_999L) corrupt("Invalid provider timestamp")
        return UtcTimestamp(seconds, nanos.toInt())
    }
    private fun exactlyOne(result: DbWriteResult) {
        if (result.affectedRows != 1L) profileReject("Provider compare-and-swap did not affect one row", FailureCode.STALE_REVISION)
    }
    private fun values(stored: StoredProviderProfile, sequence: Long, payload: ByteArray, includeCreated: Boolean = true): Array<Pair<String, SqlValue>> {
        val params = mutableListOf("id" to text(stored.profile.id.value), "revision" to number(stored.revision.value),
            "sequence" to number(sequence), "modified" to number(stored.modifiedAt.epochSeconds), "modifiedNano" to number(stored.modifiedAt.nanoAdjustment.toLong()),
            "length" to number(payload.size.toLong()), "hash" to text(ProviderProfileCodec.hash(payload)), "payload" to SqlValue.Blob(SnapshotBytes(payload)))
        if (includeCreated) { params += "created" to number(stored.createdAt.epochSeconds); params += "createdNano" to number(stored.createdAt.nanoAdjustment.toLong()) }
        return params.toTypedArray()
    }
    private fun RowData.column(name: String): SqlValue = columns.singleOrNull { it.name == name }?.value ?: corrupt("Missing provider metadata column")
    private fun RowData.long(name: String): Long = (column(name) as? SqlValue.Integer)?.value ?: corrupt("Invalid provider integer metadata")
    private fun RowData.string(name: String): String = (column(name) as? SqlValue.Text)?.value ?: corrupt("Invalid provider text metadata")
    private fun RowData.utf8(name: String): String {
        val bytes = (column(name) as? SqlValue.Blob)?.value ?: corrupt("Invalid provider UTF8 metadata")
        return try { profileDecodeText(bytes.copyBytes()) } catch (_: ProfileProblem) { corrupt("Invalid provider UTF8 metadata") }
    }
    private fun corrupt(message: String): Nothing = profileReject(message, FailureCode.CORRUPT_DATA)
    private suspend fun query(tx: DbTransaction, sql: String, cancel: CancelToken, vararg params: Pair<String, SqlValue>): SnapshotList<RowData> =
        profileValue(tx.query(request(sql, *params), cancel))
    private fun request(sql: String, vararg params: Pair<String, SqlValue>, maxRows: Int = 1): SqlRequest =
        SqlRequest(sql, SnapshotList.copyOf(params.map { SqlParameter(it.first, it.second) }), maxRows)
    private fun text(value: String): SqlValue = SqlValue.Text(value)
    private fun number(value: Long): SqlValue = SqlValue.Integer(value)
    private suspend fun <T> operation(cancel: CancelToken, action: suspend (DbTransaction) -> T): Outcome<T> {
        cancel.throwIfCancelled()
        return when (val result = db.transaction({ tx -> profileOutcome { action(tx) } }, cancel)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> result
            is Outcome.Unavailable -> result
            is Outcome.Unknown -> result
        }
    }
    private companion object {
        const val PAGE_BYTES = 1024L * 1024
        fun boundedText(column: String, max: Int): String =
            "CASE WHEN typeof($column)='text' AND length(CAST($column AS BLOB))<=$max THEN CAST($column AS BLOB) ELSE NULL END AS $column"
        fun integer(column: String): String = "CASE WHEN typeof($column)='integer' THEN $column ELSE NULL END AS $column"
        val METADATA = (listOf(boundedText("profile_id", 256), boundedText("payload_sha256", 64)) +
            listOf("revision", "change_sequence", "deleted", "created_seconds", "created_nanos", "modified_seconds", "modified_nanos", "payload_version", "payload_length").map(::integer) +
            listOf("typeof(payload) AS payload_type", "CASE WHEN typeof(payload)='blob' THEN length(payload) WHEN payload IS NULL THEN 0 ELSE NULL END AS actual_length")).joinToString(",")
    }
}
