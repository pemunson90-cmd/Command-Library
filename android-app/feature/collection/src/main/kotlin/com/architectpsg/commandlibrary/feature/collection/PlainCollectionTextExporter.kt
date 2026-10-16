package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest

/** Human-readable inventory list only, not a native archive or a promised import grammar. */
public object PlainCollectionTextExporter {
    public fun render(snapshot: InventorySnapshot, includeNotes: Boolean, includeLocations: Boolean,
        cancel: CancelToken): Outcome<ByteArtifact> = try {
        cancel.throwIfCancelled()
        requireInput(snapshot.complete && snapshot.revision.value >= 0 && snapshot.lots.size <= InventoryProjectionLimits.MAX_LOTS)
        timestamp(snapshot.capturedAt)
        val ids = hashSetOf<EntityId>()
        var physical = 0L
        for (lot in snapshot.lots) {
            cancel.throwIfCancelled()
            identifier(lot.id.value, cancel)
            requireInput(ids.add(lot.id) && lot.revision.value >= 0 && lot.quantity >= 0)
            physical = try { Math.addExact(physical, lot.quantity.toLong()) }
            catch (_: ArithmeticException) { reject() }
        }
        // No output builder or byte encoding is allocated before the complete escaped UTF8 count.
        val count = Output(null, cancel)
        write(count, snapshot, physical, includeNotes, includeLocations)
        cancel.throwIfCancelled()
        val builder = StringBuilder(count.bytes)
        val output = Output(builder, cancel)
        write(output, snapshot, physical, includeNotes, includeLocations)
        cancel.throwIfCancelled()
        val bytes = builder.toString().toByteArray(Charsets.UTF_8)
        requireInput(output.bytes == count.bytes && bytes.size == count.bytes)
        cancel.throwIfCancelled()
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = buildString(64) { for (byte in digest) { append(HEX[(byte.toInt() ushr 4) and 15]); append(HEX[byte.toInt() and 15]) } }
        cancel.throwIfCancelled()
        val artifact = ByteArtifact("command-library-collection-r${snapshot.revision.value}.txt", "text/plain;charset=utf-8",
            SnapshotBytes(bytes), ContentHash("SHA-256", hex), null, SnapshotList.of(
                "Human-readable text only; not a native backup or an import format.",
                "Unknown and Absent reasons and evidence are omitted; state labels remain distinct.",
                if (includeNotes) "Private notes included by explicit selection." else "Private notes omitted.",
                if (includeLocations) "Locations included by explicit selection." else "Locations omitted.",
                "Prices, undo history, receipts, settings, credentials and other private archive data are not included."))
        cancel.throwIfCancelled()
        Outcome.Success(artifact)
    } catch (_: Rejected) {
        Outcome.Failure(DomainFailure(FailureCode.INVALID_INPUT,
            "Collection text export requires a complete valid snapshot within text and byte limits.", SnapshotList.empty()))
    }

    private fun write(out: Output, snapshot: InventorySnapshot, physical: Long, notes: Boolean, locations: Boolean) {
        out.literal("Command Library Collection List 1\nInventory revision: ${snapshot.revision.value}\nCaptured: ")
        out.time(snapshot.capturedAt)
        out.literal("\nLot count: ${snapshot.lots.size}\nPhysical quantity: $physical\n")
        out.literal("Private notes: ${if (notes) "included" else "omitted"}\nLocations: ${if (locations) "included" else "omitted"}\n")
        out.literal("Human-readable list; not a native backup or an import format.\nUnknown/Absent reasons and evidence omitted.\n")
        for ((index, lot) in snapshot.lots.withIndex()) {
            out.check()
            out.literal("\nLot ${index + 1}\nLot ID: "); out.quoted(lot.id.value, true)
            out.literal("\nLot revision: ${lot.revision.value}\nQuantity: ${lot.quantity}\nOriginal name: ")
            out.knowledge(lot.identity.originalName) { out.quoted(it) }
            out.literal("\nOracle: ")
            out.knowledge(lot.identity.oracleId) { out.qualified(it.source.value, it.raw) }
            out.literal("\nPrinting: ")
            out.knowledge(lot.identity.printingId) { out.qualified(it.source.value, it.raw) }
            out.literal("\nRaw identity: ")
            val raw = lot.identity.raw
            if (raw == null) out.literal("Not supplied") else {
                out.literal("Known source="); out.quoted(raw.provider.value, true)
                out.literal(" kind="); out.quoted(raw.kind)
                out.literal(" id="); out.quoted(raw.rawId, true)
            }
            out.literal("\nLanguage: "); out.quoted(lot.identity.language.value, true)
            out.literal("\nFinish: "); out.knowledge(lot.finish) { out.literal(it.name) }
            out.literal("\nCondition: ")
            if (lot.condition == CardCondition.UNKNOWN) out.literal("Unknown") else out.literal("Known ${lot.condition.name}")
            out.literal("\nAcquired: "); out.knowledge(lot.acquiredAt) { out.time(it) }
            out.literal("\nNotes: ")
            if (notes) out.quoted(lot.notes) else out.literal("Omitted")
            out.literal("\nLocation: ")
            if (locations) out.quoted(lot.location) else out.literal("Omitted")
            out.literal("\n")
        }
        out.check()
    }

    private class Output(private val builder: StringBuilder?, private val cancel: CancelToken) {
        var bytes: Int = 0
            private set
        fun check() = cancel.throwIfCancelled()
        /** Only application-owned ASCII constants and numeric/enum text reach this method. */
        fun literal(value: String) { add(value.length); builder?.append(value) }
        private fun add(size: Int) { requireInput(size <= MAX_BYTES - bytes); bytes += size }
        fun qualified(source: String, id: String) {
            literal("source="); quoted(source, true); literal(" id="); quoted(id, true)
        }
        fun time(value: UtcTimestamp) {
            timestamp(value)
            literal("UTC epoch seconds=${value.epochSeconds} nanoseconds=${value.nanoAdjustment.toString().padStart(9, '0')}")
        }
        fun <T> knowledge(value: Knowledge<T>, known: (T) -> Unit) {
            check()
            // Reasons/evidence are deliberately neither validated nor traversed.
            when (value) {
                is Knowledge.Known -> { literal("Known "); known(value.value) }
                is Knowledge.Unknown -> literal("Unknown")
                is Knowledge.Absent -> literal("Absent")
            }
        }
        fun quoted(value: String, id: Boolean = false) {
            check()
            val rawLimit = if (id) InventoryProjectionLimits.MAX_ID_BYTES else MAX_FIELD_BYTES
            requireInput(value.length <= rawLimit)
            if (id) requireInput(value.isNotBlank())
            var index = 0
            var nextCheck = 0
            var rawBytes = 0
            var fieldBytes = 2 // Quotes count toward the rendered field budget.
            literal("\"")
            while (index < value.length) {
                if (index >= nextCheck) { check(); nextCheck = index + 1024 }
                val start = index
                val first = value[index++]
                val code: Int
                if (first.isHighSurrogate()) {
                    requireInput(index < value.length && value[index].isLowSurrogate())
                    code = Character.toCodePoint(first, value[index++])
                } else {
                    requireInput(!first.isLowSurrogate()); code = first.code
                }
                val width = when { code <= 0x7f -> 1; code <= 0x7ff -> 2; code <= 0xffff -> 3; else -> 4 }
                rawBytes += width
                requireInput(rawBytes <= rawLimit)
                val short = when (code) { 34 -> '"'; 92 -> '\\'; 10 -> 'n'; 13 -> 'r'; 9 -> 't'; else -> null }
                val control = code < 32 || code in 127..159 || code == 0x2028 || code == 0x2029 ||
                    Character.getType(code) == Character.FORMAT.toInt()
                val rendered = if (short != null) 2 else if (control) 6 * (index - start) else width
                requireInput(rendered <= MAX_FIELD_BYTES - fieldBytes)
                fieldBytes += rendered
                add(rendered)
                if (builder != null) {
                    if (short != null) builder.append('\\').append(short)
                    else if (control) for (i in start until index) {
                        val unit = value[i].code
                        builder.append("\\u").append(HEX[(unit ushr 12) and 15]).append(HEX[(unit ushr 8) and 15])
                            .append(HEX[(unit ushr 4) and 15]).append(HEX[unit and 15])
                    } else builder.append(value, start, index)
                }
            }
            literal("\"")
            check()
        }
    }

    private fun identifier(value: String, cancel: CancelToken) {
        requireInput(value.length <= InventoryProjectionLimits.MAX_ID_BYTES && value.isNotBlank())
        // Reuse the exact strict scanner without retaining or encoding the identifier.
        Output(null, cancel).quoted(value, true)
    }
    private fun timestamp(value: UtcTimestamp) = requireInput(value.nanoAdjustment in 0..999_999_999)
    private class Rejected : RuntimeException()
    private fun reject(): Nothing = throw Rejected()
    private fun requireInput(condition: Boolean) { if (!condition) reject() }
    private const val MAX_FIELD_BYTES = 1024 * 1024
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val HEX = "0123456789abcdef"
}
