package com.architectpsg.commandlibrary.core.common

import com.architectpsg.commandlibrary.core.model.CardFace
import com.architectpsg.commandlibrary.core.model.CardRecord
import com.architectpsg.commandlibrary.core.model.CanonicalFaceRelationship
import com.architectpsg.commandlibrary.core.model.FaceIdentity
import com.architectpsg.commandlibrary.core.model.Knowledge

/**
 * Compares canonical game data for duplicate-oracle validation. Acquisition provenance, revision,
 * producer mappings, derived search text, relationship evidence, and printing assets are excluded.
 * Unknown remains distinct from absent and known; its diagnostic reason/evidence is metadata.
 * Canonical face identity uses source-qualified oracle plus face index, never a guessed producer ID.
 * Keyword/color collection order is incidental; original source order remains stored. Duplicate
 * multiplicity remains comparable, and ordered card faces are never reordered.
 */
public fun sameCanonicalGameData(first: CardRecord, second: CardRecord): Boolean =
    first.oracleId == second.oracleId &&
        first.language == second.language &&
        first.name == second.name &&
        sameKnowledge(first.originalText, second.originalText) &&
        sameKnowledge(first.manaCost, second.manaCost) &&
        sameKnowledge(first.manaValue, second.manaValue) { left, right -> left.compareTo(right) == 0 } &&
        sameKnowledge(first.colors, second.colors) { left, right -> left.sortedBy { it.name } == right.sortedBy { it.name } } &&
        sameKnowledge(first.colorIdentity, second.colorIdentity) { left, right -> left.sortedBy { it.name } == right.sortedBy { it.name } } &&
        sameKnowledge(first.types, second.types) &&
        sameKnowledge(first.stats.power, second.stats.power) &&
        sameKnowledge(first.stats.toughness, second.stats.toughness) &&
        sameKnowledge(first.stats.loyalty, second.stats.loyalty) &&
        sameKnowledge(first.stats.defense, second.stats.defense) &&
        sameKnowledge(first.keywords, second.keywords) { left, right -> left.sorted() == right.sorted() } &&
        sameKnowledge(first.layout, second.layout) &&
        sameKnowledge(first.reserved, second.reserved) &&
        first.faces.size == second.faces.size &&
        first.faces.zip(second.faces).all { (left, right) -> sameFace(left, right) }

private fun sameFace(first: CardFace, second: CardFace): Boolean =
    sameFaceIdentity(first.identity, second.identity) &&
        first.name == second.name &&
        sameKnowledge(first.text, second.text) &&
        sameKnowledge(first.manaCost, second.manaCost) &&
        sameKnowledge(first.types, second.types) &&
        sameKnowledge(first.stats.power, second.stats.power) &&
        sameKnowledge(first.stats.toughness, second.stats.toughness) &&
        sameKnowledge(first.stats.loyalty, second.stats.loyalty) &&
        sameKnowledge(first.stats.defense, second.stats.defense) &&
        first.relationships.size == second.relationships.size &&
        first.relationships.zip(second.relationships).all { (left, right) -> sameRelationship(left, right) }

private fun sameFaceIdentity(first: FaceIdentity, second: FaceIdentity): Boolean =
    first.oracleId == second.oracleId && first.index == second.index

private fun sameRelationship(first: CanonicalFaceRelationship, second: CanonicalFaceRelationship): Boolean =
    first.kind == second.kind &&
        sameKnowledge(first.relatedOracle, second.relatedOracle) &&
        sameKnowledge(first.relatedFace, second.relatedFace, ::sameFaceIdentity)

private fun <T> sameKnowledge(
    first: Knowledge<T>,
    second: Knowledge<T>,
    compare: (T, T) -> Boolean = { left, right -> left == right },
): Boolean = when {
    first is Knowledge.Known && second is Knowledge.Known -> compare(first.value, second.value)
    first is Knowledge.Absent && second is Knowledge.Absent -> true
    first is Knowledge.Unknown && second is Knowledge.Unknown -> true
    else -> false
}
