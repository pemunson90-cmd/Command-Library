package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*

private val actionDefinitionWire = wire<VariantActionDefinition>({ string(it.id); string(it.label); string(it.instructions);
    v(nullable(stringWire), it.fromZone); v(nullable(stringWire), it.toZone); v(nullable(intWire), it.maximumPerPhase) },
    { VariantActionDefinition(string(), string(), string(), v(nullable(stringWire)), v(nullable(stringWire)), v(nullable(intWire))) })
private val zoneDefinitionWire = wire<VariantZoneDefinition>({ string(it.id); string(it.label); v(nullable(intWire), it.maximumCards); bool(it.ordered) },
    { VariantZoneDefinition(string(), string(), v(nullable(intWire)), bool()) })
private val variantCardWire = wire<VariantCard>({ v(providerIdentityWire, it.id); string(it.name); string(it.text); v(knowledge(uriWire), it.image);
    v(list(stringWire), it.actionIds); v(provenanceWire, it.provenance) },
    { VariantCard(v(providerIdentityWire), string(), string(), v(knowledge(uriWire)), v(list(stringWire)), v(provenanceWire)) })
private val namespaceWire = enumeration(VariantNamespace.PLANECHASE, VariantNamespace.ARCHENEMY, VariantNamespace.BOUNTY, VariantNamespace.CUSTOM)
internal val variantDefinitionWire = wire<VariantDefinition>({ v(providerIdentityWire, it.id); v(namespaceWire, it.namespace); string(it.version);
    string(it.instructions); v(list(actionDefinitionWire, 128), it.allowedActions); v(list(zoneDefinitionWire, 128), it.zones);
    v(list(stringWire), it.phases); v(list(variantCardWire), it.cards); v(provenanceWire, it.provenance); v(noticeWire, it.notice) },
    { VariantDefinition(v(providerIdentityWire), v(namespaceWire), string(), string(), v(list(actionDefinitionWire, 128)),
        v(list(zoneDefinitionWire, 128)), v(list(stringWire)), v(list(variantCardWire)), v(provenanceWire), v(noticeWire)) })
private val zoneWire = wire<VariantZone>({ string(it.zoneId); v(list(providerIdentityWire), it.cardIds) },
    { VariantZone(string(), v(list(providerIdentityWire))) })
private val valueWire = wire<VariantValue>({ value -> when (value) {
    is VariantValue.Text -> { byte(1); string(value.value) }; is VariantValue.Integer -> { byte(2); long(value.value) }
    is VariantValue.Flag -> { byte(3); bool(value.value) }; is VariantValue.Card -> { byte(4); v(providerIdentityWire, value.value) }
} }, { when (byte()) {
    1 -> VariantValue.Text(string()); 2 -> VariantValue.Integer(long()); 3 -> VariantValue.Flag(bool())
    4 -> VariantValue.Card(v(providerIdentityWire)); else -> errorTag()
} })
private val propertyWire = wire<VariantProperty>({ string(it.key); v(valueWire, it.value) }, { VariantProperty(string(), v(valueWire)) })
private val actionCountWire = wire<VariantActionCount>({ string(it.actionId); int(it.count) }, { VariantActionCount(string(), int()) })
internal val variantStateWire = wire<VariantState>({ v(providerIdentityWire, it.definitionId); string(it.version); v(list(zoneWire), it.zones);
    v(list(providerIdentityWire), it.drawn); v(list(providerIdentityWire), it.discarded); string(it.phase); v(list(propertyWire), it.properties);
    long(it.seed); v(list(actionCountWire), it.actionCounts) },
    { VariantState(v(providerIdentityWire), string(), v(list(zoneWire)), v(list(providerIdentityWire)), v(list(providerIdentityWire)),
        string(), v(list(propertyWire)), long(), v(list(actionCountWire))) })
