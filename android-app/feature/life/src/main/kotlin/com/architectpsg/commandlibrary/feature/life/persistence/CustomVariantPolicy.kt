package com.architectpsg.commandlibrary.feature.life.persistence

import com.architectpsg.commandlibrary.core.model.*
import com.architectpsg.commandlibrary.feature.life.domain.*
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal const val CUSTOM_POLICY_KEY = "command-library.custom-policy-v1"
private const val VOCABULARY_LIMIT = 128
private const val CARD_LIMIT = 10_000
private const val POLICY_BYTES = 4 * 1024 * 1024

internal fun customVariantManager(manifests: SnapshotList<CustomVariantManifest>, process: EntityId, source: ProviderId): Outcome<LifeVariantManager> {
    if (manifests.any { it.definition.namespace != VariantNamespace.CUSTOM })
        return Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Official variant namespaces require their own installed source-backed manager")
    return try {
        gameIdentity(process.value, FailureCode.INVALID_INPUT)
        gameIdentity(source.value, FailureCode.INVALID_INPUT)
        cardinality(manifests.size, VOCABULARY_LIMIT)
        requirePolicy(manifests.map { it.definition.id to it.definition.version }.toSet().size == manifests.size, "Duplicate custom manifest version")
        manifests.forEach(::preflight)
        val plans = manifests.map { manifest ->
            val value = PolicyDigest().compute(manifest)
            val marked = manifest.initialState.copy(properties = SnapshotList.copyOf(manifest.initialState.properties + VariantProperty(CUSTOM_POLICY_KEY, VariantValue.Text(value))))
            VariantManagerPlan(manifest.definition, marked, CUSTOM_POLICY_KEY, value, SnapshotList.copyOf(manifest.transitions.map { transition ->
                VariantManagerTransition(transition.actionId, transition.phases, transition.fromZone, transition.toZone,
                    when (val selection = transition.selection) {
                        CustomVariantSelection.None -> VariantManagerSelection.None
                        CustomVariantSelection.Shuffle -> VariantManagerSelection.Shuffle
                        is CustomVariantSelection.Top -> VariantManagerSelection.Top(selection.count)
                        is CustomVariantSelection.Explicit -> VariantManagerSelection.Explicit(selection.minimum, selection.maximum)
                    }, when (transition.insertion) { CustomVariantInsertion.TOP -> VariantInsertion.TOP; CustomVariantInsertion.BOTTOM -> VariantInsertion.BOTTOM },
                    transition.appendDrawn, transition.appendDiscarded, transition.nextPhase, transition.properties, transition.reset)
            }))
        }
        val manager = LifeVariantManager(plans)
        gameValue(manager.validateConfiguration())
        Outcome.Success(manager)
    } catch (problem: GameStoreProblem) { problem.status }
}

private fun requirePolicy(valid: Boolean, message: String) = requireGame(valid, FailureCode.INVALID_INPUT, message)
private fun cardinality(size: Int, maximum: Int) = requirePolicy(size <= maximum, "Custom manifest collection exceeds budget")
private fun executionId(value: String) = gameIdentity(value, FailureCode.INVALID_INPUT)
private fun identity(id: ProviderIdentity) { executionId(id.provider.value); executionId(id.kind); executionId(id.rawId) }
private fun properties(values: SnapshotList<VariantProperty>) {
    cardinality(values.size, VOCABULARY_LIMIT)
    for (property in values) {
        executionId(property.key)
        requirePolicy(property.key != CUSTOM_POLICY_KEY, "Custom policy marker is reserved")
        (property.value as? VariantValue.Card)?.let { identity(it.value) }
    }
}
private fun preflight(manifest: CustomVariantManifest) {
    val d = manifest.definition; val s = manifest.initialState
    identity(d.id); executionId(d.version); identity(s.definitionId); executionId(s.version); executionId(s.phase)
    cardinality(d.allowedActions.size,VOCABULARY_LIMIT); cardinality(d.zones.size,VOCABULARY_LIMIT)
    cardinality(d.phases.size,VOCABULARY_LIMIT); cardinality(d.cards.size,CARD_LIMIT)
    cardinality(s.zones.size,VOCABULARY_LIMIT); cardinality(s.drawn.size,CARD_LIMIT); cardinality(s.discarded.size,CARD_LIMIT)
    cardinality(s.actionCounts.size,VOCABULARY_LIMIT); cardinality(manifest.transitions.size,VOCABULARY_LIMIT)
    for (a in d.allowedActions) { executionId(a.id); a.fromZone?.let(::executionId); a.toZone?.let(::executionId) }
    d.zones.forEach { executionId(it.id) }; d.phases.forEach(::executionId)
    for (card in d.cards) { identity(card.id); cardinality(card.actionIds.size,VOCABULARY_LIMIT); card.actionIds.forEach(::executionId) }
    for (zone in s.zones) { executionId(zone.zoneId); cardinality(zone.cardIds.size,CARD_LIMIT); zone.cardIds.forEach(::identity) }
    s.drawn.forEach(::identity); s.discarded.forEach(::identity); s.actionCounts.forEach { executionId(it.actionId) }
    properties(s.properties)
    for (t in manifest.transitions) {
        executionId(t.actionId); cardinality(t.phases.size,VOCABULARY_LIMIT); t.phases.forEach(::executionId)
        t.fromZone?.let(::executionId); t.toZone?.let(::executionId); t.nextPhase?.let(::executionId); properties(t.properties)
    }
}

/** Pinned custom-policy-v1 wire identity. Keep this exact framing available for saved v1 policies. */
private class PolicyDigest {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var bytes = 0
    private fun put(value: ByteArray) {
        requirePolicy(value.size <= POLICY_BYTES - bytes, "Custom execution policy exceeds byte budget")
        bytes += value.size; digest.update(value)
    }
    private fun tag(value: Int) = put(byteArrayOf(value.toByte()))
    private fun bool(value: Boolean) = tag(if(value) 1 else 0)
    private fun int(value: Int) = put(byteArrayOf((value ushr 24).toByte(),(value ushr 16).toByte(),(value ushr 8).toByte(),value.toByte()))
    private fun long(value: Long) = put(ByteArray(8) { index -> (value ushr (56 - index * 8)).toByte() })
    private fun string(value: String) {
        requirePolicy(value.length <= GAME_STRING_BYTES, "Custom policy text exceeds byte budget")
        val encoded = try { Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value)) }
        catch (error: CharacterCodingException) { rejectGame(FailureCode.INVALID_INPUT,"Invalid custom policy UTF8") }
        requirePolicy(encoded.remaining() <= GAME_STRING_BYTES,"Custom policy text exceeds byte budget")
        int(encoded.remaining()); put(ByteArray(encoded.remaining()).also { encoded.get(it) })
    }
    private fun nullable(value: String?) { tag(if(value==null)0 else 1); if(value!=null) string(value) }
    private fun <T> list(values: List<T>, write: (T)->Unit) { int(values.size); values.forEach(write) }
    private fun id(value: ProviderIdentity) { string(value.provider.value); string(value.kind); string(value.rawId) }
    private fun property(value: VariantProperty) {
        string(value.key)
        when(val v=value.value) {
            is VariantValue.Text -> { tag(0); string(v.value) }
            is VariantValue.Integer -> { tag(1); long(v.value) }
            is VariantValue.Flag -> { tag(2); bool(v.value) }
            is VariantValue.Card -> { tag(3); id(v.value) }
        }
    }
    fun compute(manifest: CustomVariantManifest): String {
        string("command-library/custom-policy/1"); id(manifest.definition.id); string(manifest.definition.version)
        val state=manifest.initialState
        id(state.definitionId); string(state.version)
        list(state.zones) { zone -> string(zone.zoneId); list(zone.cardIds,::id) }
        list(state.drawn,::id); list(state.discarded,::id); string(state.phase); list(state.properties,::property)
        long(state.seed); list(state.actionCounts) { count -> string(count.actionId); int(count.count) }
        list(manifest.transitions) { transition ->
            string(transition.actionId); list(transition.phases,::string); nullable(transition.fromZone); nullable(transition.toZone)
            when(val selection=transition.selection) {
                CustomVariantSelection.None -> tag(0)
                is CustomVariantSelection.Top -> { tag(1); int(selection.count) }
                is CustomVariantSelection.Explicit -> { tag(2); int(selection.minimum); int(selection.maximum) }
                CustomVariantSelection.Shuffle -> tag(3)
            }
            tag(when(transition.insertion) { CustomVariantInsertion.TOP -> 0; CustomVariantInsertion.BOTTOM -> 1 })
            bool(transition.appendDrawn); bool(transition.appendDiscarded); nullable(transition.nextPhase)
            list(transition.properties,::property); bool(transition.reset)
        }
        val alphabet="0123456789abcdef"
        return "sha256:" + buildString(64) { for(byte in digest.digest()) { val n=byte.toInt() and 255; append(alphabet[n ushr 4]); append(alphabet[n and 15]) } }
    }
}
