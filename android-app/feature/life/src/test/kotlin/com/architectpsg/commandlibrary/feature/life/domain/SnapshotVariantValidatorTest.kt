package com.architectpsg.commandlibrary.feature.life.domain

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Test

class SnapshotVariantValidatorTest {
    private val provider = LifeFixtures.provider
    private val missing = ProviderIdentity(provider, "card", "missing")
    private val unknown = Knowledge.Absent("Synthetic source absence")
    private val provenance = Provenance(provider, null, unknown, unknown, UtcTimestamp(0), unknown, null, null, null)
    private val card = VariantCard(ProviderIdentity(provider, "card", "one"), "Synthetic", "Synthetic", unknown, SnapshotList.of("draw"), provenance)
    private val definition = VariantDefinition(ProviderIdentity(provider, "variant", "one"), VariantNamespace.CUSTOM, "1", "Synthetic",
        SnapshotList.of(VariantActionDefinition("draw", "Draw", "Synthetic", "deck", "deck", 2)),
        SnapshotList.of(VariantZoneDefinition("deck", "Deck", 1, true)), SnapshotList.of("main"), SnapshotList.of(card), provenance,
        SourceNotice(provider, "Synthetic", unknown, "Test", null))
    private val state = VariantState(definition.id, "1", SnapshotList.of(VariantZone("deck", SnapshotList.of(card.id))), SnapshotList.empty(), SnapshotList.empty(), "main", SnapshotList.empty(), 0, SnapshotList.of(VariantActionCount("draw", 2)))
    private fun check(d: VariantDefinition = definition, states: SnapshotList<VariantState> = SnapshotList.of(state)): Outcome<Unit> {
        val s = LifeFixtures.snapshot()
        return validateSnapshotVariants(s.copy(settings = s.settings.copy(variantDefinitions = SnapshotList.of(d)), variants = states))
    }
    @Test fun declaredStateAndLegacyFreeformPhaseAreAccepted() {
        check().success()
        check(definition.copy(phases = SnapshotList.empty()), SnapshotList.of(state.copy(phase = "retained-legacy"))).success()
        check(states = SnapshotList.of(state.copy(drawn = SnapshotList.of(card.id), discarded = SnapshotList.of(card.id)))).success()
    }
    @Test fun coverageVersionZoneAndCapacityCannotContradictDefinition() {
        for (states in listOf(SnapshotList.empty(), SnapshotList.of(state,state), SnapshotList.of(state.copy(definitionId = missing)), SnapshotList.of(state.copy(version = "2")), SnapshotList.of(state.copy(zones = SnapshotList.empty())), SnapshotList.of(state.copy(zones = SnapshotList.of(state.zones[0],state.zones[0]))), SnapshotList.of(state.copy(zones = SnapshotList.of(VariantZone("missing", SnapshotList.empty())))), SnapshotList.of(state.copy(zones = SnapshotList.of(VariantZone("deck", SnapshotList.of(card.id,card.id))))))) check(states = states).failure(FailureCode.CORRUPT_DATA)
    }
    @Test fun everyStoredCardReferenceAndMapKeyIsChecked() {
        for (invalid in listOf(state.copy(drawn = SnapshotList.of(missing)), state.copy(discarded = SnapshotList.of(missing)), state.copy(zones = SnapshotList.of(VariantZone("deck", SnapshotList.of(missing)))), state.copy(properties = SnapshotList.of(VariantProperty("card", VariantValue.Card(missing)))), state.copy(properties = SnapshotList.of(VariantProperty("x", VariantValue.Flag(true)), VariantProperty("x", VariantValue.Flag(false)))), state.copy(actionCounts = SnapshotList.of(VariantActionCount("missing",1))), state.copy(actionCounts = SnapshotList.of(VariantActionCount("draw",1), VariantActionCount("draw",1))), state.copy(actionCounts = SnapshotList.of(VariantActionCount("draw",3))), state.copy(phase = "missing"))) check(states = SnapshotList.of(invalid)).failure(FailureCode.CORRUPT_DATA)
    }
    @Test fun definitionsCannotContainDuplicateOrDanglingVocabulary() {
        for (invalid in listOf(definition.copy(zones = SnapshotList.of(definition.zones[0],definition.zones[0])), definition.copy(cards = SnapshotList.of(card,card)), definition.copy(phases = SnapshotList.of("main","main")), definition.copy(allowedActions = SnapshotList.of(definition.allowedActions[0],definition.allowedActions[0])), definition.copy(allowedActions = SnapshotList.of(definition.allowedActions[0].copy(fromZone = "missing"))), definition.copy(allowedActions = SnapshotList.of(definition.allowedActions[0].copy(toZone = "missing"))), definition.copy(cards = SnapshotList.of(card.copy(actionIds = SnapshotList.of("missing")))))) check(invalid).failure(FailureCode.CORRUPT_DATA)
        val s = LifeFixtures.snapshot()
        validateSnapshotVariants(s.copy(settings = s.settings.copy(variantDefinitions = SnapshotList.of(definition,definition)), variants = SnapshotList.of(state))).failure(FailureCode.CORRUPT_DATA)
    }
}
