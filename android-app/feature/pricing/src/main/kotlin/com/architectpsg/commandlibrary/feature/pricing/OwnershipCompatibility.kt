package com.architectpsg.commandlibrary.feature.pricing

import com.architectpsg.commandlibrary.core.model.*

internal enum class OwnershipCompatibility { ELIGIBLE, INELIGIBLE, INDETERMINATE }

/** Hard exclusions precede inconsistent mappings, identity proof, then finish uncertainty. */
internal fun ownershipCompatibility(entry: DeckEntry, lot: OwnedLot, pin: PrintingPin?, ownership: OwnershipPolicy, conflictingPrintings: Set<CardId>): OwnershipCompatibility {
    val requested = entry.identity
    val supplied = lot.identity
    val finish = pin?.let { Knowledge.Known(it.finish) } ?: entry.finish
    val requestedPrinting = (requested.printingId as? Knowledge.Known)?.value
    val suppliedPrinting = (supplied.printingId as? Knowledge.Known)?.value
    val lotFinish = lot.finish
    val exact = pin != null || ownership == OwnershipPolicy.EXACT_PRINTING
    if (requested.language != supplied.language) return OwnershipCompatibility.INELIGIBLE
    if (finish is Knowledge.Known && lotFinish is Knowledge.Known && finish.value != lotFinish.value) return OwnershipCompatibility.INELIGIBLE
    if (exact && requestedPrinting != null && suppliedPrinting != null && requestedPrinting != suppliedPrinting) return OwnershipCompatibility.INELIGIBLE
    if (requestedPrinting in conflictingPrintings || suppliedPrinting in conflictingPrintings) return OwnershipCompatibility.INDETERMINATE
    val samePrinting = requestedPrinting != null && requestedPrinting == suppliedPrinting
    if (exact) {
        if (!samePrinting) return OwnershipCompatibility.INDETERMINATE
    } else {
        val requestedOracle = (requested.oracleId as? Knowledge.Known)?.value
        val suppliedOracle = (supplied.oracleId as? Knowledge.Known)?.value
        if (requestedOracle != null && suppliedOracle != null) {
            if (requestedOracle != suppliedOracle) return OwnershipCompatibility.INELIGIBLE
        } else if (!samePrinting) return OwnershipCompatibility.INDETERMINATE
    }
    return when (finish) {
        is Knowledge.Absent -> OwnershipCompatibility.ELIGIBLE
        is Knowledge.Unknown -> OwnershipCompatibility.INDETERMINATE
        is Knowledge.Known -> if (lotFinish is Knowledge.Known) OwnershipCompatibility.ELIGIBLE else OwnershipCompatibility.INDETERMINATE
    }
}
