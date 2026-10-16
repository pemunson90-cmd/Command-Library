package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*

internal data class BasicEntryEvidence(val entry: DeckEntry, val facts: CatalogDeckFacts?, val legality: CatalogFormatLegality?)

/** Bounded construction observations. No rules-text whitelist, pairing solver or name-equivalence registry. */
internal object BasicCommanderChecks {
    private val countedBoards = setOf(BoardRole.MAIN, BoardRole.COMMANDER, BoardRole.PARTNER, BoardRole.BACKGROUND)
    private val commanderBoards = setOf(BoardRole.COMMANDER, BoardRole.PARTNER, BoardRole.BACKGROUND)
    private val singleLayouts = setOf("normal", "saga", "class", "leveler", "prototype", "mutate")
    private val landColors = mapOf("Plains" to Color.WHITE, "Island" to Color.BLUE, "Swamp" to Color.BLACK,
        "Mountain" to Color.RED, "Forest" to Color.GREEN)
    private data class IdentityContext(val status: LegalityStatus, val colors: Set<Color>)

    fun assess(request: BasicDeckValidationRequest, evidence: List<BasicEntryEvidence>, poll: () -> Unit): BasicDeckValidationResult {
        poll()
        validationRequire(evidence.size <= DeckStatisticsLimits.MAX_ENTRIES)
        val expected = request.deck.entries.filter { poll(); it.board != BoardRole.MAYBEBOARD }
        validationRequire(expected.size == evidence.size && expected.indices.all { poll(); expected[it] == evidence[it].entry },
            message = "Validation entry evidence attribution mismatch")
        val included = evidence.filter { poll(); it.entry.board in countedBoards }
        val commanders = included.filter { poll(); it.entry.board in commanderBoards }
        val checks = ArrayList<BasicDeckValidationCheck>()
        var copies = 0L
        for (entry in included) { poll(); copies = validationAdd(copies, entry.entry.quantity.toLong()) }
        checks.add(check("deck_count", if (copies == 100L) LegalityStatus.LEGAL else LegalityStatus.NOT_LEGAL,
            included.map { poll(); it.entry.id }, "Counted boards are MAIN, COMMANDER, PARTNER and BACKGROUND; their total must be exactly 100 copies.", "903.5a"))
        checks.add(check("commander_presence", if (commanders.isNotEmpty()) LegalityStatus.LEGAL else LegalityStatus.NOT_LEGAL,
            commanders.map { poll(); it.entry.id }, "At least one explicit commander slot is required; presence alone does not establish pairing or card eligibility.", "903.3"))
        val pairing = commanders.size > 1 || commanders.any { poll(); it.entry.board != BoardRole.COMMANDER }
        checks.add(check("commander_arrangement", if (pairing) LegalityStatus.REVIEW_REQUIRED else LegalityStatus.LEGAL,
            commanders.map { poll(); it.entry.id }, if (pairing) "Partner, Background or multiple commander arrangements require a separate pairing review; no pairing is approved here."
            else "No paired commander arrangement was designated in these boards.", "702.124", "903.3"))
        val sideboard = evidence.filter { poll(); it.entry.board == BoardRole.SIDEBOARD }
        checks.add(check("sideboard", if (sideboard.isEmpty()) LegalityStatus.LEGAL else LegalityStatus.NOT_LEGAL,
            sideboard.map { poll(); it.entry.id }, "Commander games do not use sideboards; SIDEBOARD copies are preserved and excluded from the 100-card total.", "903.5e"))
        val companion = evidence.filter { poll(); it.entry.board == BoardRole.COMPANION }
        checks.add(check("companion", if (companion.isEmpty()) LegalityStatus.LEGAL else LegalityStatus.REVIEW_REQUIRED,
            companion.map { poll(); it.entry.id }, "COMPANION is outside the counted deck; any designated companion requires a separate eligibility review.", "903.5"))
        val identity = identityContext(commanders, poll)
        checks.add(check("commander_identity_context", identity.status, commanders.map { poll(); it.entry.id },
            when (identity.status) {
                LegalityStatus.LEGAL -> "Source-reported commander identities have an unambiguous supported single-card empty-text context for color comparisons."
                LegalityStatus.UNKNOWN -> "Commander whole-card identity, layout or relevant card/face text is missing; a conclusive color comparison is unavailable."
                else -> "Choice-dependent, multiface or unsupported commander identity context is not assessed; this does not assert that every commander chooses colors."
            }, "903.4", "903.4b", "903.5c"))
        val groups = nameGroups(included, poll)
        for ((index, row) in evidence.withIndex()) {
            poll()
            val entry = row.entry
            if (entry.board in commanderBoards) {
                checks.add(check("commander_quantity_$index", if (entry.quantity == 1) LegalityStatus.LEGAL else LegalityStatus.NOT_LEGAL,
                    listOf(entry.id), "Each explicitly designated commander slot must contain exactly one copy.", "903.5a"))
                checks.add(check("commander_eligibility_$index", commanderEligibility(row.facts?.card), listOf(entry.id),
                    "Positive normal eligibility needs a known legendary creature, or a legendary Vehicle or Spacecraft with a known power/toughness box; missing or special eligibility remains unassessed.", "903.3", "903.3a"))
            }
            val reported = row.legality?.status?.validationKnown()
            checks.add(check("source_eligibility_$index", when (reported) {
                LegalityStatus.LEGAL -> LegalityStatus.LEGAL
                LegalityStatus.BANNED, LegalityStatus.NOT_LEGAL -> LegalityStatus.NOT_LEGAL
                LegalityStatus.RESTRICTED -> LegalityStatus.REVIEW_REQUIRED
                else -> LegalityStatus.UNKNOWN
            }, listOf(entry.id), "This is installed source-reported Commander card eligibility, separate from deck approval or a current live banned list; restricted or missing evidence needs review.", "903.5"))
            if (entry.board !in countedBoards) continue
            val card = row.facts?.card
            val colors = card?.colorIdentity?.validationKnown()
            val colorStatus = if (colors == null) LegalityStatus.UNKNOWN else if (identity.status != LegalityStatus.LEGAL) identity.status
                else if (colors.all { poll(); it in identity.colors }) LegalityStatus.LEGAL else LegalityStatus.NOT_LEGAL
            checks.add(check("color_identity_$index", colorStatus, listOf(entry.id),
                "Every source-reported whole-card color identity must fit the established commander identity union; colors, costs and reported mana production are not substitutes.", "903.4", "903.5c"))
            val types = card?.types?.validationKnown()
            val basicColors = types?.subtypes?.mapNotNull { poll(); landColors[it] }
            val landStatus = when {
                basicColors == null -> LegalityStatus.UNKNOWN
                basicColors.isEmpty() -> LegalityStatus.LEGAL
                identity.status != LegalityStatus.LEGAL -> identity.status
                basicColors.all { poll(); it in identity.colors } -> LegalityStatus.LEGAL
                else -> LegalityStatus.NOT_LEGAL
            }
            checks.add(check("basic_land_types_$index", landStatus, listOf(entry.id),
                "Known Plains, Island, Swamp, Mountain and Forest types are checked separately against commander colors; reported production is not unconditional supply.", "903.5d"))
            val basicLand = types != null && "Basic" in types.supertypes && "Land" in types.types
            val textKnown = card?.originalText is Knowledge.Known
            val repeated = (groups[entry.id] ?: 0L) > 1L
            val singletonStatus = when {
                basicLand -> LegalityStatus.LEGAL
                card == null || types == null || !textKnown -> LegalityStatus.UNKNOWN
                else -> LegalityStatus.REVIEW_REQUIRED
            }
            checks.add(check("singleton_$index", singletonStatus, listOf(entry.id), when {
                basicLand -> "Known Basic Land is exempt from the singleton name restriction."
                repeated -> "Repeated exact English-name or Oracle grouping is a duplicate candidate; copy allowances, face names and interchangeable-name exceptions are not established."
                else -> "No duplicate was established by this exact English-name and Oracle grouping; copy allowances and interchangeable-name coverage remain unassessed."
            }, "903.5b", "201.3"))
        }
        checks.add(check("singleton_exception_coverage", LegalityStatus.REVIEW_REQUIRED, included.map { poll(); it.entry.id },
            "This slice does not establish interchangeable names, any-number or limited-copy allowances, face-name equivalence, or all construction exceptions; different names alone do not prove compliance.", "903.5b", "201.3", "903.3a"))
        val aggregate = if (checks.any { poll(); it.status == LegalityStatus.NOT_LEGAL || it.status == LegalityStatus.BANNED })
            LegalityStatus.NOT_LEGAL else LegalityStatus.REVIEW_REQUIRED
        val source = linkedMapOf<OracleId, CatalogFormatLegality>()
        for (row in evidence) { poll(); row.legality?.let { source.putIfAbsent(it.oracleId, it) } }
        return BasicDeckValidationResult(request.deck.id, request.deck.revision, request.snapshot, request.format, aggregate,
            SnapshotList.copyOf(checks), SnapshotList.copyOf(source.values), "2026-09-25")
    }

    private fun commanderEligibility(card: CardRecord?): LegalityStatus {
        if (card == null) return LegalityStatus.UNKNOWN
        val layout = card.layout.validationKnown() ?: return LegalityStatus.UNKNOWN
        if (layout !in singleLayouts || card.faces.size > 1) return LegalityStatus.REVIEW_REQUIRED
        val face = card.faces.singleOrNull()
        val types = card.types.validationKnown() ?: face?.types?.validationKnown() ?: return LegalityStatus.UNKNOWN
        val legendary = "Legendary" in types.supertypes
        if (legendary && "Creature" in types.types) return LegalityStatus.LEGAL
        val power = card.stats.power.validationKnown() ?: face?.stats?.power?.validationKnown()
        val toughness = card.stats.toughness.validationKnown() ?: face?.stats?.toughness?.validationKnown()
        if (legendary && ("Vehicle" in types.subtypes || "Spacecraft" in types.subtypes))
            return if (!power.isNullOrBlank() && !toughness.isNullOrBlank()) LegalityStatus.LEGAL else LegalityStatus.UNKNOWN
        return if (card.originalText is Knowledge.Known) LegalityStatus.REVIEW_REQUIRED else LegalityStatus.UNKNOWN
    }
    private fun identityContext(commanders: List<BasicEntryEvidence>, poll: () -> Unit): IdentityContext {
        if (commanders.isEmpty()) return IdentityContext(LegalityStatus.UNKNOWN, emptySet())
        val colors = HashSet<Color>()
        var unknown = false
        var choiceUnassessed = false
        for (row in commanders) {
            poll()
            val card = row.facts?.card
            val identity = card?.colorIdentity?.validationKnown()
            val text = card?.originalText?.validationKnown()
            if (identity == null || text == null) unknown = true else {
                colors.addAll(identity)
                if (text.isNotEmpty()) choiceUnassessed = true
            }
            val layout = card?.layout?.validationKnown()
            if (layout == null) unknown = true
            else if (layout !in singleLayouts || (card?.faces?.size ?: 0) > 1) choiceUnassessed = true
            for (face in card?.faces ?: emptyList()) {
                poll()
                val faceText = face.text.validationKnown()
                if (faceText == null) unknown = true else if (faceText.isNotEmpty()) choiceUnassessed = true
            }
        }
        return IdentityContext(if (unknown) LegalityStatus.UNKNOWN else if (choiceUnassessed) LegalityStatus.REVIEW_REQUIRED else LegalityStatus.LEGAL, colors)
    }
    private fun nameGroups(entries: List<BasicEntryEvidence>, poll: () -> Unit): Map<EntityId, Long> {
        val parents = IntArray(entries.size) { it }
        fun root(index: Int): Int {
            var at = index
            while (parents[at] != at) { poll(); parents[at] = parents[parents[at]]; at = parents[at] }
            return at
        }
        val byName = HashMap<String, Int>()
        val byOracle = HashMap<OracleId, Int>()
        for ((index, row) in entries.withIndex()) {
            poll()
            val card = row.facts?.card ?: continue
            byName.putIfAbsent(card.name, index)?.let { parents[root(index)] = root(it) }
            byOracle.putIfAbsent(card.oracleId, index)?.let { parents[root(index)] = root(it) }
        }
        val totals = HashMap<Int, Long>()
        for ((index, row) in entries.withIndex()) { poll(); val at = root(index); totals[at] = validationAdd(totals[at] ?: 0, row.entry.quantity.toLong()) }
        return entries.indices.associate { poll(); entries[it].entry.id to totals.getValue(root(it)) }
    }
    private fun check(id: String, status: LegalityStatus, entries: List<EntityId>, message: String, vararg references: String) =
        BasicDeckValidationCheck(id, status, SnapshotList.copyOf(entries), message, SnapshotList.of(*references))
}
