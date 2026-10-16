package com.architectpsg.commandlibrary.feature.collection

import com.architectpsg.commandlibrary.core.model.*
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

/** Pure synthetic readable-list oracles; real destination writing is a separate root app gate. */
class PlainCollectionTextExporterTest {
    private fun lot(id: String = "lot-a", quantity: Int = 2): OwnedLot = OwnedLot(EntityId(id),
        CardIdentity(Knowledge.Known(OracleId(ProviderId("oracle-source"), "oracle-id")),
            Knowledge.Known(CardId(ProviderId("printing-source"), "printing-id")),
            ProviderIdentity(ProviderId("foreign-source"), "raw-kind", "raw-id"), Knowledge.Known("Saved original name 日本語 😀"), LanguageTag("ja")),
        Knowledge.Known(Finish.FOIL), CardCondition.NEAR_MINT, "PRIVATE-LOCATION", quantity, "PRIVATE-NOTES",
        Revision(2), Knowledge.Known(UtcTimestamp(-1, 7)))
    private fun inventory(vararg lots: OwnedLot) = InventorySnapshot(Revision(9), UtcTimestamp(1700000000, 4), SnapshotList.of(*lots), true)
    private fun render(snapshot: InventorySnapshot, notes: Boolean = false, locations: Boolean = false,
        cancel: CancelToken = noCancel): Outcome<ByteArtifact> = PlainCollectionTextExporter.render(snapshot, notes, locations, cancel)
    private fun artifact(result: Outcome<ByteArtifact>): ByteArtifact = (result as Outcome.Success).value
    private fun text(result: Outcome<ByteArtifact>) = artifact(result).bytes.copyBytes().toString(Charsets.UTF_8)
    private fun invalid(result: Outcome<*>) = assertEquals(FailureCode.INVALID_INPUT, (result as Outcome.Failure).error.code)

    @Test fun distinctOrderedLotsWithSameIdentityAndZeroQuantityAreNeverMerged() {
        val first = lot(quantity = 4)
        val second = first.copy(id = EntityId("lot-b"), quantity = 0, revision = Revision(3))
        val original = inventory(first, second)
        val before = original.copy()
        val result = render(original)
        val output = text(result)
        assertTrue(output.startsWith("Command Library Collection List 1\nInventory revision: 9\nCaptured: UTC epoch seconds=1700000000 nanoseconds=000000004\n"))
        assertTrue(output.contains("Lot count: 2\nPhysical quantity: 4\n"))
        assertTrue(output.contains("Lot 1\nLot ID: \"lot-a\"\nLot revision: 2\nQuantity: 4\n"))
        assertTrue(output.contains("Lot 2\nLot ID: \"lot-b\"\nLot revision: 3\nQuantity: 0\n"))
        assertTrue(output.indexOf("Lot ID: \"lot-a\"") < output.indexOf("Lot ID: \"lot-b\""))
        assertTrue(output.contains("Oracle: Known source=\"oracle-source\" id=\"oracle-id\""))
        assertTrue(output.contains("Printing: Known source=\"printing-source\" id=\"printing-id\""))
        assertTrue(output.contains("Raw identity: Known source=\"foreign-source\" kind=\"raw-kind\" id=\"raw-id\""))
        assertTrue(output.contains("Acquired: Known UTC epoch seconds=-1 nanoseconds=000000007"))
        assertEquals(before, original)
        assertSame(first, original.lots[0])
    }

    @Test fun unknownAbsentAndKnownStatesRemainDistinctWithoutInspectingReasonsOrEvidence() {
        val omittedEvidence = SnapshotList.of(Evidence("PRIVATE-EVIDENCE-\uD800", ResourceUri("private-marker"), null))
        val unknown = lot().copy(identity = lot().identity.copy(oracleId = Knowledge.Unknown("PRIVATE-REASON-\uD800", omittedEvidence),
            printingId = Knowledge.Absent("PRIVATE-ABSENT-\uD800"), originalName = Knowledge.Unknown("PRIVATE-NAME-REASON", omittedEvidence), raw = null),
            finish = Knowledge.Unknown("PRIVATE-FINISH", omittedEvidence), condition = CardCondition.UNKNOWN,
            acquiredAt = Knowledge.Absent("PRIVATE-DATE"))
        val absent = unknown.copy(id = EntityId("absent"), identity = unknown.identity.copy(
            oracleId = Knowledge.Absent("PRIVATE-ORACLE"), printingId = Knowledge.Unknown("PRIVATE-PRINTING", omittedEvidence),
            originalName = Knowledge.Absent("PRIVATE-NAME")), finish = Knowledge.Absent("PRIVATE-FINISH"))
        val output = text(render(inventory(unknown, absent)))
        for (line in listOf("Oracle: Unknown", "Oracle: Absent", "Printing: Unknown", "Printing: Absent",
            "Original name: Unknown", "Original name: Absent", "Finish: Unknown", "Finish: Absent",
            "Condition: Unknown", "Acquired: Absent", "Raw identity: Not supplied")) assertTrue(output.contains(line))
        assertFalse(output.contains("PRIVATE"))
        assertTrue(artifact(render(inventory(unknown))).losses.any { it.contains("reasons and evidence are omitted") })
    }

    @Test fun quotesControlsMultilineAndSupplementaryUnicodeCannotForgeLotHeadings() {
        val name = "quote=\" slash=\\ line=\nLot 999\r\t\u0000\u0085\u2028\u202e 日本語 😀"
        val original = lot().copy(identity = lot().identity.copy(originalName = Knowledge.Known(name),
            oracleId = Knowledge.Known(OracleId(ProviderId("s\nource"), "id\"\\"))))
        val output = text(render(inventory(original)))
        assertTrue(output.contains("Original name: Known \"quote=\\\" slash=\\\\ line=\\nLot 999\\r\\t\\u0000\\u0085\\u2028\\u202e 日本語 😀\""))
        assertTrue(output.contains("Oracle: Known source=\"s\\nource\" id=\"id\\\"\\\\\""))
        assertFalse(output.contains("\nLot 999"))
        assertFalse(output.contains('\u0000'))
        val supplementaryFormat = original.copy(identity = original.identity.copy(originalName = Knowledge.Known("\uDB40\uDC01")))
        assertTrue(text(render(inventory(supplementaryFormat))).contains("Original name: Known \"\\udb40\\udc01\""))
    }

    @Test fun privateNotesAndLocationsAreIndependentlyOptedInAndPreciselyDisclosed() {
        val original = inventory(lot().copy(notes = "PRIVATE-NOTES\nquoted \"text\"", location = "PRIVATE-LOCATION\tbox"))
        for ((notes, locations) in listOf(false to false, true to false, false to true, true to true)) {
            val result = render(original, notes, locations)
            val output = text(result)
            assertEquals(notes, output.contains("PRIVATE-NOTES"))
            assertEquals(locations, output.contains("PRIVATE-LOCATION"))
            assertEquals(notes, artifact(result).losses.contains("Private notes included by explicit selection."))
            assertEquals(locations, artifact(result).losses.contains("Locations included by explicit selection."))
            assertTrue(output.contains(if (notes) "Notes: \"PRIVATE-NOTES\\nquoted \\\"text\\\"\"" else "Notes: Omitted"))
        }
        assertEquals(Revision(9), original.revision)
    }

    @Test fun omittedPrivateFieldsAreNotValidatedOrTraversedAndNeverEchoedOnFailure() {
        val invalidPrivate = lot().copy(notes = "PRIVATE-NOTES-\uD800", location = "PRIVATE-LOCATION-" + "x".repeat(1024 * 1024 + 1))
        assertTrue(render(inventory(invalidPrivate)) is Outcome.Success)
        val notes = render(inventory(invalidPrivate), notes = true)
        val locations = render(inventory(invalidPrivate), locations = true)
        invalid(notes); invalid(locations)
        assertFalse((notes as Outcome.Failure).error.message.contains("PRIVATE"))
        assertFalse((locations as Outcome.Failure).error.message.contains("PRIVATE"))
    }

    @Test fun bytesFilenameChecksumAndLossesAreDeterministicAndHaveNoArchiveManifest() {
        val original = inventory(lot())
        val first = artifact(render(original))
        val second = artifact(render(original))
        assertEquals(first, second)
        assertEquals("command-library-collection-r9.txt", first.fileName)
        assertEquals("text/plain;charset=utf-8", first.mediaType)
        assertNull(first.manifest)
        val expected = MessageDigest.getInstance("SHA-256").digest(first.bytes.copyBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(ContentHash("SHA-256", expected), first.checksum)
        assertEquals(5, first.losses.size)
        val max = original.copy(revision = Revision(Long.MAX_VALUE))
        assertEquals("command-library-collection-r9223372036854775807.txt", artifact(render(max)).fileName)
    }

    @Test fun completeEmptyInventoryHasMeaningfulHeaderAndPartialSnapshotIsRejected() {
        val empty = inventory()
        val output = text(render(empty))
        assertTrue(output.contains("Lot count: 0\nPhysical quantity: 0\n"))
        assertFalse(output.contains("\nLot 1\n"))
        invalid(render(inventory(lot()).copy(complete = false)))
    }

    @Test fun duplicateNegativeAndMalformedStoredStructuresFailWithoutPartialArtifact() {
        val row = lot()
        invalid(render(inventory(row, row)))
        invalid(render(inventory(lot().copy(id = EntityId("x".repeat(1025))))))
        invalid(render(inventory(lot().copy(identity = row.identity.copy(originalName = Knowledge.Known("bad\uD800"))))))
        invalid(render(inventory(lot().copy(identity = row.identity.copy(printingId = Knowledge.Known(CardId(ProviderId("s"), "bad\uDC00")))))))
        // Public constructors prevent these values; reflection supplies deliberately corrupt DTO fixtures only.
        val negativeQuantity = lot()
        OwnedLot::class.java.getDeclaredField("quantity").apply { isAccessible = true }.setInt(negativeQuantity, -1)
        invalid(render(inventory(negativeQuantity)))
        val negativeRevision = Revision(2)
        Revision::class.java.getDeclaredField("value").apply { isAccessible = true }.setLong(negativeRevision, -1)
        invalid(render(inventory(lot().copy(revision = negativeRevision))))
        invalid(render(inventory().copy(revision = negativeRevision)))
    }

    @Test fun fieldEscapingAndArtifactByteBudgetsRejectRatherThanTruncate() {
        val maximumAscii = "a".repeat(1024 * 1024 - 2)
        val valid = lot().copy(identity = lot().identity.copy(originalName = Knowledge.Known(maximumAscii)))
        assertTrue(render(inventory(valid)) is Outcome.Success)
        val tooLong = valid.copy(identity = valid.identity.copy(originalName = Knowledge.Known(maximumAscii + "a")))
        invalid(render(inventory(tooLong))) // Quotes count toward the rendered field limit.
        invalid(render(inventory(valid.copy(identity = valid.identity.copy(originalName = Knowledge.Known("\u0001".repeat(175000)))))))
        invalid(render(inventory(valid.copy(identity = valid.identity.copy(originalName = Knowledge.Known("é".repeat(524288)))))))
        val many = inventory(*Array(9) { valid.copy(id = EntityId("lot-$it")) })
        invalid(render(many)) // Each field is valid; the complete UTF8 artifact exceeds8MiB.
        invalid(render(inventory().copy(lots = SnapshotList.copyOf(List(100001) { valid }))))
    }

    @Test fun cancellationPropagatesBeforeWorkAndWithinOddAlignedSupplementaryText() {
        val marker = CancellationException("Synthetic cancellation")
        val immediate = object : CancelToken {
            override val isCancelled = true
            override fun throwIfCancelled(): Unit = throw marker
        }
        try { render(inventory(), cancel = immediate); fail("Cancellation swallowed") }
        catch (actual: CancellationException) { assertSame(marker, actual) }
        var checks = 0
        val during = object : CancelToken {
            override val isCancelled get() = checks >= 100
            override fun throwIfCancelled() { if (++checks >= 100) throw marker }
        }
        val longName = lot().copy(identity = lot().identity.copy(originalName = Knowledge.Known("a" + "😀".repeat(100000))))
        try { render(inventory(longName), cancel = during); fail("Supplementary scan skipped cancellation") }
        catch (actual: CancellationException) { assertSame(marker, actual) }
        assertEquals(100, checks)
    }

    private companion object {
        val noCancel = object : CancelToken {
            override val isCancelled = false
            override fun throwIfCancelled() = Unit
        }
    }
}
