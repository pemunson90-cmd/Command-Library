package com.architectpsg.commandlibrary.data.storage.decks.codec

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal
import java.math.BigInteger

private val providerWire = wire<ProviderId>({ v(idTextWire, it.value) }, { ProviderId(v(idTextWire)) })
internal val entityWire = wire<EntityId>({ v(idTextWire, it.value) }, { EntityId(v(idTextWire)) })
private val generationWire = wire<GenerationId>({ v(idTextWire, it.value) }, { GenerationId(v(idTextWire)) })
private val languageWire = wire<LanguageTag>({ v(idTextWire, it.value) }, { LanguageTag(v(idTextWire)) })
private val deckIdWire = wire<DeckId>({ v(providerWire, it.source); v(idTextWire, it.raw) }, { DeckId(v(providerWire), v(idTextWire)) })
private val oracleWire = wire<OracleId>({ v(providerWire, it.source); v(idTextWire, it.raw) }, { OracleId(v(providerWire), v(idTextWire)) })
private val cardWire = wire<CardId>({ v(providerWire, it.source); v(idTextWire, it.raw) }, { CardId(v(providerWire), v(idTextWire)) })
internal val formatWire = wire<FormatId>({ v(providerWire, it.source); v(idTextWire, it.raw) }, { FormatId(v(providerWire), v(idTextWire)) })
private val revisionWire = wire<Revision>({ long(it.value) }, { Revision(long()) })
private val timestampWire = wire<UtcTimestamp>({ long(it.epochSeconds); int(it.nanoAdjustment) }, { UtcTimestamp(long(), int()) })
private val uriWire = wire<ResourceUri>({ v(textWire, it.value) }, { ResourceUri(v(textWire)) })
private val evidenceWire = wire<Evidence>({
    v(textWire, it.description); v(nullable(uriWire), it.source); v(nullable(timestampWire), it.recordedAt)
}, { Evidence(v(textWire), v(nullable(uriWire)), v(nullable(timestampWire))) })

internal fun <T> knowledge(codec: DeckWire<T>): DeckWire<Knowledge<T>> = wire({ value ->
    when (value) {
        is Knowledge.Known -> { byte(0); v(codec, value.value) }
        is Knowledge.Absent -> { byte(1); v(textWire, value.reason) }
        is Knowledge.Unknown -> { byte(2); v(textWire, value.reason); v(list(evidenceWire), value.evidence) }
    }
}, {
    when (byte()) {
        0 -> Knowledge.Known(v(codec))
        1 -> Knowledge.Absent(v(textWire))
        2 -> Knowledge.Unknown(v(textWire), v(list(evidenceWire)))
        else -> badTag()
    }
})

private val providerIdentityWire = wire<ProviderIdentity>({
    v(providerWire, it.provider); v(textWire, it.kind); v(idTextWire, it.rawId)
}, { ProviderIdentity(v(providerWire), v(textWire), v(idTextWire)) })
private val identityWire = wire<CardIdentity>({
    v(knowledge(oracleWire), it.oracleId); v(knowledge(cardWire), it.printingId)
    v(nullable(providerIdentityWire), it.raw); v(knowledge(textWire), it.originalName); v(languageWire, it.language)
}, { CardIdentity(v(knowledge(oracleWire)), v(knowledge(cardWire)), v(nullable(providerIdentityWire)), v(knowledge(textWire)), v(languageWire)) })
internal val labelWire = wire<EntryLabel>({ v(entityWire, it.id); v(textWire, it.name) }, { EntryLabel(v(entityWire), v(textWire)) })
private val overrideWire = wire<RoleOverride>({
    v(nullable(entityWire), it.entryId); v(knowledge(oracleWire), it.oracleId); v(textWire, it.role); bool(it.enabled); v(timestampWire, it.modifiedAt)
}, { RoleOverride(v(nullable(entityWire)), v(knowledge(oracleWire)), v(textWire), bool(), v(timestampWire)) })
internal val entryWire = wire<DeckEntry>({
    v(entityWire, it.id); v(identityWire, it.identity); int(it.quantity); v(enumeration<BoardRole>(), it.board)
    v(knowledge(enumeration<Finish>()), it.finish); v(textWire, it.notes); v(list(labelWire), it.labels); v(list(overrideWire), it.roleOverrides)
}, { DeckEntry(v(entityWire), v(identityWire), int(), v(enumeration<BoardRole>()), v(knowledge(enumeration<Finish>())), v(textWire), v(list(labelWire)), v(list(overrideWire))) })

private val unitWire = wire<DecimalUnit>({ value ->
    when (value) {
        is DecimalUnit.Fiat -> { byte(0); v(idTextWire, value.currencyCode) }
        DecimalUnit.Tix -> byte(1)
        is DecimalUnit.Native -> { byte(2); v(providerWire, value.provider); v(textWire, value.symbol) }
    }
}, {
    when (byte()) {
        0 -> DecimalUnit.Fiat(v(idTextWire))
        1 -> DecimalUnit.Tix
        2 -> DecimalUnit.Native(v(providerWire), v(textWire))
        else -> badTag()
    }
})
internal val amountWire = wire<DecimalAmount>({ value ->
    // Inspect stored precision first: never stringify an arbitrarily large unscaled integer.
    require(value.precision <= DeckPayloadLimits.MAX_DECIMAL_PRECISION) { "Deck decimal precision exceeded" }
    string(value.value.unscaledValue().toString(), DeckPayloadLimits.MAX_DECIMAL_PRECISION + 1)
    int(value.scale)
    v(unitWire, value.unit)
}, {
    val integer = string(DeckPayloadLimits.MAX_DECIMAL_PRECISION + 1)
    val digits = integer.length - if (integer.startsWith('-')) 1 else 0
    require(digits in 1..DeckPayloadLimits.MAX_DECIMAL_PRECISION &&
        (integer == "0" || integer.matches(Regex("-?[1-9][0-9]*")))) { "Invalid deck decimal integer" }
    val scale = int()
    require(scale >= 0) { "Invalid deck decimal scale" }
    val value = BigDecimal(BigInteger(integer), scale)
    DecimalAmount(value, v(unitWire))
})
private val referenceWire = wire<SnapshotReference>({
    v(providerWire, it.source); v(nullable(generationWire), it.generation); v(nullable(revisionWire), it.revision); v(nullable(languageWire), it.language)
}, { SnapshotReference(v(providerWire), v(nullable(generationWire)), v(nullable(revisionWire)), v(nullable(languageWire))) })
private val contributionWire = wire<PackageContribution>({
    v(entityWire, it.packageEntryId); v(entityWire, it.deckEntryId); int(it.quantity)
}, { PackageContribution(v(entityWire), v(entityWire), int()) })
private val packageWire = wire<PackageInstance>({
    v(entityWire, it.id); v(entityWire, it.packageId); v(entityWire, it.versionId); v(enumeration<PackageLinkPolicy>(), it.policy); v(list(contributionWire), it.contributions)
}, { PackageInstance(v(entityWire), v(entityWire), v(entityWire), v(enumeration<PackageLinkPolicy>()), v(list(contributionWire))) })

/** Frozen CLDK1 field order follows DeckSnapshot and every nested DTO declaration. */
internal val snapshotWire: DeckWire<DeckSnapshot> = wire({
    v(deckIdWire, it.id); v(textWire, it.name); v(knowledge(formatWire), it.format); v(textWire, it.description)
    v(knowledge(textWire), it.folder); v(list(textWire), it.tags); v(timestampWire, it.createdAt); v(timestampWire, it.modifiedAt)
    v(revisionWire, it.revision); v(list(entryWire, DeckPayloadLimits.MAX_ENTRIES), it.entries); v(knowledge(amountWire), it.budget)
    v(nullable(entityWire), it.profileId); v(list(referenceWire), it.catalog); v(list(packageWire), it.packageInstances)
    v(textWire, it.primer); v(nullable(list(labelWire, DeckPayloadLimits.MAX_CATEGORIES)), it.categoryOrder)
}, {
    DeckSnapshot(v(deckIdWire), v(textWire), v(knowledge(formatWire)), v(textWire), v(knowledge(textWire)), v(list(textWire)),
        v(timestampWire), v(timestampWire), v(revisionWire), v(list(entryWire, DeckPayloadLimits.MAX_ENTRIES)), v(knowledge(amountWire)),
        v(nullable(entityWire)), v(list(referenceWire)), v(list(packageWire)), v(textWire), v(nullable(list(labelWire, DeckPayloadLimits.MAX_CATEGORIES))))
})
