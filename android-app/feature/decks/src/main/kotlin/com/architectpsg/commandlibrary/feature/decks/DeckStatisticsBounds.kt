package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.math.BigDecimal

internal class StatisticsRejection(val outcome: Outcome<Nothing>) : RuntimeException()
internal fun statisticsReject(code: FailureCode, message: String): Nothing =
    throw StatisticsRejection(Outcome.Failure(DomainFailure(code, message, SnapshotList.empty())))
internal fun statisticsRequire(condition: Boolean, code: FailureCode = FailureCode.CORRUPT_DATA, message: String = "Invalid retained statistics facts") {
    if (!condition) statisticsReject(code, message)
}
internal fun statisticsUnsupported(): Nothing = throw StatisticsRejection(
    Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Unsupported source statistics fact"))
internal fun <T> statisticsValue(outcome: Outcome<T>): T = when (outcome) {
    is Outcome.Success -> outcome.value
    is Outcome.Failure -> throw StatisticsRejection(outcome)
    is Outcome.Unavailable -> throw StatisticsRejection(outcome)
    is Outcome.Unknown -> throw StatisticsRejection(outcome)
}
internal fun <T> Knowledge<T>.statisticsKnown(): T? = (this as? Knowledge.Known)?.value
internal fun statisticsAdd(a: Long, b: Long): Long {
    statisticsRequire(a >= 0 && b >= 0 && b <= Long.MAX_VALUE - a, message = "Statistics quantity overflow")
    return a + b
}

/** Visits all retained fields, including unknown reasons/evidence and unused face metadata.
 * Charges UTF-8 text, list slots and scalar/object overhead before retaining facts or reducing them.
 * No decimal stringification and no encoded copies are needed for this cumulative admission. */
internal class DeckStatisticsBounds(private val poll: () -> Unit, private val failureCode: FailureCode = FailureCode.CORRUPT_DATA) {
    private fun requireFact(condition: Boolean, message: String) = statisticsRequire(condition, failureCode, message)
    private var bytes = 0L
    fun text(value: String, maximum: Int = DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES) {
        poll()
        requireFact(value.length <= maximum && value.length.toLong() + 16 <= DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES - bytes,
            message = "Statistics text budget exceeded")
        var index = 0
        var size = 0
        var nextCheck = 0
        while (index < value.length) {
            // A surrogate pair advances by two UTF-16 positions and may skip an exact modulus.
            if (index >= nextCheck) {
                poll()
                nextCheck = index + 1_024
            }
            val char = value[index++]
            size += when {
                char.isHighSurrogate() -> {
                    requireFact(index < value.length && value[index].isLowSurrogate(), message = "Invalid statistics Unicode")
                    index++; 4
                }
                char.isLowSurrogate() -> statisticsReject(failureCode, "Invalid statistics Unicode")
                char.code < 128 -> 1
                char.code < 2048 -> 2
                else -> 3
            }
            requireFact(size <= maximum, message = "Statistics text budget exceeded")
        }
        charge(size.toLong() + 16)
    }
    private fun charge(size: Long = 32) {
        poll()
        requireFact(size >= 0 && size <= DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES - bytes,
            message = "Statistics aggregate facts budget exceeded")
        bytes += size
    }
    private fun <T> list(values: List<T>, maximum: Int = DeckStatisticsLimits.MAX_AGGREGATE_FACT_BYTES / 8, visit: (T) -> Unit) {
        requireFact(values.size <= maximum, message = "Statistics collection budget exceeded")
        charge(16L + values.size.toLong() * 8)
        for (value in values) { poll(); visit(value) }
    }
    private fun <T> knowledge(value: Knowledge<T>, visit: (T) -> Unit) {
        charge()
        when (value) {
            is Knowledge.Known -> visit(value.value)
            is Knowledge.Absent -> text(value.reason)
            is Knowledge.Unknown -> { text(value.reason); list(value.evidence, visit = ::evidence) }
        }
    }
    private fun evidence(value: Evidence) {
        charge(); text(value.description); value.source?.let { text(it.value) }; charge()
    }
    private fun oracle(value: OracleId) { charge(); text(value.source.value); text(value.raw) }
    private fun printing(value: CardId) { charge(); text(value.source.value); text(value.raw) }
    private fun faceIdentity(value: FaceIdentity) {
        charge(); oracle(value.oracleId); knowledge(value.providerFaceId) { text(it) }
    }
    private fun provenance(value: Provenance) {
        charge(); text(value.provider.value); value.source?.let { text(it.value) }
        knowledge(value.version) { text(it) }; knowledge(value.sourceDate) { charge() }
        knowledge(value.effectiveAt) { charge() }; value.generation?.let { text(it.value) }
        value.checksum?.let { charge(); text(it.algorithm); text(it.hex) }
        value.notice?.let { notice ->
            charge(); text(notice.provider.value); text(notice.title); knowledge(notice.license) { text(it) }
            text(notice.attribution); notice.uri?.let { text(it.value) }
        }
    }
    private fun types(value: TypeLine) {
        charge(); text(value.original)
        list(value.supertypes, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        list(value.types, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        list(value.subtypes, DeckStatisticsLimits.MAX_TYPE_TOKENS) { text(it, DeckStatisticsLimits.MAX_TYPE_BYTES) }
        requireFact(value.supertypes.size + value.types.size + value.subtypes.size <= DeckStatisticsLimits.MAX_TYPE_TOKENS,
            message = "Statistics type token budget exceeded")
        requireFact(value.types.size == value.types.toSet().size, message = "Duplicate source card type")
    }
    private fun stats(value: CardStats) {
        charge(); knowledge(value.power) { text(it) }; knowledge(value.toughness) { text(it) }
        knowledge(value.loyalty) { text(it) }; knowledge(value.defense) { text(it) }
    }
    fun mana(value: BigDecimal) {
        poll()
        requireFact(value.signum() >= 0 && value.precision() <= DeckStatisticsLimits.MAX_MANA_PRECISION &&
            value.scale() in 0..DeckStatisticsLimits.MAX_MANA_SCALE, message = "Invalid source mana value")
        charge(64)
    }
    fun facts(value: CatalogDeckFacts) {
        charge()
        val card = value.card
        charge(); oracle(card.oracleId); text(card.name); knowledge(card.originalText) { text(it) }
        knowledge(card.manaCost) { text(it, DeckStatisticsLimits.MAX_COST_BYTES) }; knowledge(card.manaValue, ::mana)
        knowledge(card.colors) { list(it) { charge(4) } }; knowledge(card.colorIdentity) { list(it) { charge(4) } }
        knowledge(card.types, ::types); stats(card.stats); knowledge(card.keywords) { list(it) { text(it) } }
        knowledge(card.layout) { text(it) }; knowledge(card.reserved) { charge(4) }
        text(card.normalizedSearchText); text(card.language.value)
        list(card.faces, DeckStatisticsLimits.MAX_FACES) { face ->
            charge(); faceIdentity(face.identity); text(face.name); knowledge(face.text) { text(it) }
            knowledge(face.manaCost) { text(it, DeckStatisticsLimits.MAX_COST_BYTES) }
            knowledge(face.types, ::types); stats(face.stats)
            list(face.relationships) { relationship ->
                charge(); knowledge(relationship.relatedOracle, ::oracle)
                knowledge(relationship.relatedFace, ::faceIdentity); list(relationship.evidence, visit = ::evidence)
            }
        }
        list(card.providerIdentities) { identity -> charge(); text(identity.provider.value); text(identity.kind); text(identity.rawId) }
        provenance(card.provenance); charge()
        text(value.snapshot.source.value); value.snapshot.generation?.let { text(it.value) }
        value.snapshot.language?.let { text(it.value) }; value.printingId?.let(::printing)
        knowledge(value.reportedProduction) { list(it) { text(it) } }
        knowledge(value.reportedParts) { parts -> list(parts, DeckStatisticsLimits.MAX_RELATED_PARTS) { part ->
            charge(); printing(part.printingId); text(part.component)
            knowledge(part.name) { text(it) }; knowledge(part.typeLine) { text(it) }
        } }
    }
}
