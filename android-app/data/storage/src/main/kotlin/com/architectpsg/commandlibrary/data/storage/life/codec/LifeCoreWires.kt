package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*

internal val entityWire = wire<EntityId>({ string(it.value) }, { EntityId(string()) })
internal val providerWire = wire<ProviderId>({ string(it.value) }, { ProviderId(string()) })
internal val uriWire = wire<ResourceUri>({ string(it.value) }, { ResourceUri(string()) })
internal val timestampWire = wire<UtcTimestamp>({ long(it.epochSeconds); int(it.nanoAdjustment) }, { UtcTimestamp(long(), int()) })
internal val revisionWire = wire<Revision>({ long(it.value) }, { Revision(long()) })
internal val hashWire = wire<ContentHash>({ string(it.algorithm); string(it.hex) }, { ContentHash(string(), string()) })
internal val evidenceWire = wire<Evidence>({ string(it.description); v(nullable(uriWire), it.source); v(nullable(timestampWire), it.recordedAt) },
    { Evidence(string(), v(nullable(uriWire)), v(nullable(timestampWire))) })
internal fun <T> knowledge(codec: LifeWire<T>) = wire<Knowledge<T>>({ value ->
    when (value) {
        is Knowledge.Known -> { byte(1); v(codec, value.value) }
        is Knowledge.Absent -> { byte(2); string(value.reason) }
        is Knowledge.Unknown -> { byte(3); string(value.reason); v(list(evidenceWire), value.evidence) }
    }
}, {
    when (byte()) {
        1 -> Knowledge.Known(v(codec)); 2 -> Knowledge.Absent(string())
        3 -> Knowledge.Unknown(string(), v(list(evidenceWire))); else -> errorTag()
    }
})
internal val noticeWire = wire<SourceNotice>({ v(providerWire, it.provider); string(it.title); v(knowledge(stringWire), it.license);
    string(it.attribution); v(nullable(uriWire), it.uri) },
    { SourceNotice(v(providerWire), string(), v(knowledge(stringWire)), string(), v(nullable(uriWire))) })
private val generationWire = wire<GenerationId>({ string(it.value) }, { GenerationId(string()) })
internal val provenanceWire = wire<Provenance>({ v(providerWire, it.provider); v(nullable(uriWire), it.source); v(knowledge(stringWire), it.version);
    v(knowledge(timestampWire), it.sourceDate); v(timestampWire, it.fetchedAt); v(knowledge(timestampWire), it.effectiveAt);
    v(nullable(generationWire), it.generation); v(nullable(hashWire), it.checksum); v(nullable(noticeWire), it.notice) },
    { Provenance(v(providerWire), v(nullable(uriWire)), v(knowledge(stringWire)), v(knowledge(timestampWire)), v(timestampWire),
        v(knowledge(timestampWire)), v(nullable(generationWire)), v(nullable(hashWire)), v(nullable(noticeWire))) })
internal val providerIdentityWire = wire<ProviderIdentity>({ v(providerWire, it.provider); string(it.kind); string(it.rawId) },
    { ProviderIdentity(v(providerWire), string(), string()) })
private val oracleIdWire = wire<OracleId>({ v(providerWire, it.source); string(it.raw) }, { OracleId(v(providerWire), string()) })
private val cardIdWire = wire<CardId>({ v(providerWire, it.source); string(it.raw) }, { CardId(v(providerWire), string()) })
internal val deckIdWire = wire<DeckId>({ v(providerWire, it.source); string(it.raw) }, { DeckId(v(providerWire), string()) })
internal val gameIdWire = wire<GameId>({ v(providerWire, it.source); string(it.raw) }, { GameId(v(providerWire), string()) })
internal val formatIdWire = wire<FormatId>({ v(providerWire, it.source); string(it.raw) }, { FormatId(v(providerWire), string()) })
internal val cardWire = wire<CardIdentity>({ v(knowledge(oracleIdWire), it.oracleId); v(knowledge(cardIdWire), it.printingId);
    v(nullable(providerIdentityWire), it.raw); v(knowledge(stringWire), it.originalName); string(it.language.value) },
    { CardIdentity(v(knowledge(oracleIdWire)), v(knowledge(cardIdWire)), v(nullable(providerIdentityWire)), v(knowledge(stringWire)), LanguageTag(string())) })
internal val commanderWire = wire<CommanderSlot>({ v(entityWire, it.id); v(cardWire, it.identity); string(it.label) },
    { CommanderSlot(v(entityWire), v(cardWire), string()) })
private val localArtWire = wire<LocalArtReference>({ v(entityWire, it.id); string(it.relativePath); v(hashWire, it.checksum) },
    { LocalArtReference(v(entityWire), string(), v(hashWire)) })
private val artWire = wire<GameArtReference>({ value -> when (value) {
    is GameArtReference.Local -> { byte(1); v(localArtWire, value.art) }
    is GameArtReference.CachedCard -> { byte(2); v(cardWire, value.identity); v(entityWire, value.assetId) }
} }, { when (byte()) {
    1 -> GameArtReference.Local(v(localArtWire)); 2 -> GameArtReference.CachedCard(v(cardWire), v(entityWire)); else -> errorTag()
} })
private val colorWire = wire<DisplayColor>({ long(it.argb) }, { DisplayColor(long()) })
private val cropWire = wire<ArtCrop>({ double(it.left); double(it.top); double(it.right); double(it.bottom) },
    { ArtCrop(double(), double(), double(), double()) })
private val fitWire = enumeration(ArtFit.CONTAIN, ArtFit.COVER)
private val orientationWire = enumeration(SeatOrientation.UPRIGHT, SeatOrientation.CLOCKWISE, SeatOrientation.INVERTED, SeatOrientation.COUNTERCLOCKWISE)
internal val appearanceWire = wire<SeatAppearance>({ v(nullable(colorWire), it.color); v(nullable(artWire), it.art); v(fitWire, it.fit);
    v(nullable(cropWire), it.crop); v(orientationWire, it.orientation) },
    { SeatAppearance(v(nullable(colorWire)), v(nullable(artWire)), v(fitWire), v(nullable(cropWire)), v(orientationWire)) })
internal val lossPolicyWire = wire<CounterLossPolicy>({ value -> when (value) {
    CounterLossPolicy.None -> byte(1); is CounterLossPolicy.AtOrAbove -> { byte(2); int(value.threshold) }
    is CounterLossPolicy.AtOrBelow -> { byte(3); int(value.threshold) }
} }, { when (byte()) {
    1 -> CounterLossPolicy.None; 2 -> CounterLossPolicy.AtOrAbove(int()); 3 -> CounterLossPolicy.AtOrBelow(int()); else -> errorTag()
} })
private val roleWire = enumeration(CounterRole.LIFE, CounterRole.COMMANDER_DAMAGE, CounterRole.COMMANDER_TAX, CounterRole.OTHER)
private val presetWire = enumeration(CounterPreset.POISON, CounterPreset.ENERGY, CounterPreset.EXPERIENCE, CounterPreset.RAD, CounterPreset.STORM,
    CounterPreset.MANA, CounterPreset.TREASURE, CounterPreset.CHARGE, CounterPreset.BLOOD, CounterPreset.FOOD, CounterPreset.CLUE,
    CounterPreset.RING, CounterPreset.PLANESWALKER, CounterPreset.SAGA, CounterPreset.SIEGE)
private val resetWire = enumeration(CounterResetPolicy.MANUAL, CounterResetPolicy.ON_TURN_START)
internal val definitionWire = wire<CounterDefinition>({ v(entityWire, it.id); string(it.label); v(nullable(intWire), it.minimum);
    v(nullable(intWire), it.maximum); int(it.initial); v(lossPolicyWire, it.lossPolicy); bool(it.custom); v(roleWire, it.role);
    v(nullable(colorWire), it.color); v(nullable(presetWire), it.preset); v(resetWire, it.resetPolicy) },
    { CounterDefinition(v(entityWire), string(), v(nullable(intWire)), v(nullable(intWire)), int(), v(lossPolicyWire), bool(),
        v(roleWire), v(nullable(colorWire)), v(nullable(presetWire)), v(resetWire)) })
internal val profileWire = wire<PlayerProfileDraft>({ string(it.name); int(it.preferredLife); v(list(definitionWire, 128), it.counterDefinitions);
    v(appearanceWire, it.appearance); v(list(commanderWire), it.commanders); int(it.adjustmentStep) },
    { PlayerProfileDraft(string(), int(), v(list(definitionWire, 128)), v(appearanceWire), v(list(commanderWire)), int()) })
