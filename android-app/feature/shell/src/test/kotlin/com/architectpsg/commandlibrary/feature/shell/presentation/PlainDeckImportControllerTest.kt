@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.architectpsg.commandlibrary.feature.shell.presentation

import com.architectpsg.commandlibrary.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Real retained producer over scripted codec snapshots and a faithful receipt/current-head port. */
class PlainDeckImportControllerTest {
    @Test fun completePhysicalRowsCreateOneOrderedUnresolvedDeckWithoutInferringLanguageOrMergingNames() = runTest {
        val f = PlainImportFixture(this)
        val parsed = physical(
            PlainDeckListLimits.MARKER to null, "" to null,
            "Sideboard" to null, "3 Same" to PlainDeckListEntry(3, "Same", BoardRole.SIDEBOARD),
            "Deck" to null, "2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN), "2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN),
            "Commander" to null, "1 Leader" to PlainDeckListEntry(1, "Leader", BoardRole.COMMANDER),
            "Partner" to null, "1 Ally" to PlainDeckListEntry(1, "Ally", BoardRole.PARTNER),
            "Background" to null, "1 Past" to PlainDeckListEntry(1, "Past", BoardRole.BACKGROUND),
            "Companion" to null, "1 Friend" to PlainDeckListEntry(1, "Friend", BoardRole.COMPANION),
            "Maybeboard" to null, "4 Maybe" to PlainDeckListEntry(4, "Maybe", BoardRole.MAYBEBOARD))
        f.controller.parse(f.input(result = parsed)); runCurrent()
        assertEquals(parsed, f.controller.state.value.input)
        f.controller.name("  Exact imported deck 😃  ")
        f.controller.preview(); runCurrent()
        val preview = checkNotNull(f.controller.state.value.preview)
        assertSame(parsed.rows, preview.input.rows)
        assertEquals(0, f.ids); assertEquals(0, f.clocks); assertTrue(f.creates.isEmpty())
        f.controller.confirm(preview); runCurrent()

        // Independent complete expected object: not derived from the produced deck or a reducer.
        val entries = listOf(
            expectedEntry("id-2", 3, "Same", BoardRole.SIDEBOARD), expectedEntry("id-3", 2, "Same", BoardRole.MAIN),
            expectedEntry("id-4", 2, "Same", BoardRole.MAIN), expectedEntry("id-5", 1, "Leader", BoardRole.COMMANDER),
            expectedEntry("id-6", 1, "Ally", BoardRole.PARTNER), expectedEntry("id-7", 1, "Past", BoardRole.BACKGROUND),
            expectedEntry("id-8", 1, "Friend", BoardRole.COMPANION), expectedEntry("id-9", 4, "Maybe", BoardRole.MAYBEBOARD))
        val expected = DeckSnapshot(DeckId(LocalDeckLibraryLimits.SOURCE, "id-1"), "  Exact imported deck 😃  ",
            Knowledge.Absent("No format selected"), "", Knowledge.Absent("No folder selected"), SnapshotList.empty(),
            UtcTimestamp(30, 7), UtcTimestamp(30, 7), Revision(0), SnapshotList.copyOf(entries), Knowledge.Absent("No budget set"), null,
            SnapshotList.empty(), SnapshotList.empty(), primer = "", categoryOrder = SnapshotList.empty())
        assertEquals(listOf(PlainImportFixture.Create(expected, EntityId("id-10"))), f.creates)
        assertEquals(listOf(expected, expected), f.validated)
        assertEquals(listOf(expected), f.notices)
        assertEquals(expected, f.controller.state.value.currentHead)
        assertEquals(expected.id, f.controller.state.value.acknowledgedId)
        assertFalse(f.controller.state.value.visible)
        assertEquals(10, f.ids); assertEquals(1, f.clocks); assertEquals(1, f.groups)
    }

    @Test fun syntaxErrorsPreserveAllRawLocationsIssuesAndValidRowsButNeverImportASubset() = runTest {
        val f = PlainImportFixture(this)
        val parsed = physical(PlainDeckListLimits.MARKER to null, "Deck" to null,
            "2 Valid" to PlainDeckListEntry(2, "Valid", BoardRole.MAIN), "" to null,
            "-1 provider://private/raw" to null, complete = false, errors = setOf(4))
        f.controller.parse(f.input(result = parsed)); runCurrent(); f.controller.name("Keep every row")
        assertEquals(parsed, f.controller.state.value.input)
        f.controller.preview(); runCurrent()
        assertNull(f.controller.state.value.preview)
        assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        assertEquals(parsed.rows, f.controller.state.value.input!!.rows)
        assertFalse(f.controller.state.value.problem.toString().contains("provider://"))
        f.assertNoCreation()
    }

    @Test fun incompleteNoCardsAndFalseCompleteErrorInputsNeverGenerateIdsOrWrite() = runTest {
        val inputs = listOf(physical("2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN), complete = false),
            physical(PlainDeckListLimits.MARKER to null, "" to null, "Deck" to null),
            physical("bad raw" to null, complete = true, errors = setOf(0)))
        for (parsed in inputs) {
            val f = PlainImportFixture(this); f.controller.parse(f.input(result = parsed)); runCurrent()
            f.controller.name("No subset"); f.controller.preview(); runCurrent()
            assertNull(f.controller.state.value.preview); assertEquals(parsed, f.controller.state.value.input)
            f.assertNoCreation()
        }
    }

    @Test fun malformedTypedPayloadsLocationsAndMissingPhysicalRowsAreRejected() = runTest {
        val good = physical("2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN))
        val row = good.rows.single()
        val cases = listOf(row.copy(entry = row.entry!!.copy(quantity = 0)), row.copy(entry = row.entry!!.copy(board = BoardRole.SIDEBOARD)),
            row.copy(entry = row.entry!!.copy(name = "Different")), row.copy(entry = null),
            row.copy(location = row.location.copy(rowNumber = 7)), row.copy(location = row.location.copy(byteOffset = null)),
            row.copy(location = row.location.copy(sourceName = "provider://wrong-source")), row.copy(rawText = "2 Same\n"),
            row.copy(entry = row.entry!!.copy(name = "\uD800")))
        for (invalid in cases) {
            val f = PlainImportFixture(this)
            f.controller.parse(f.input(result = good.copy(rows = SnapshotList.of(invalid)))); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
            assertNull(f.controller.state.value.input); f.assertNoCreation()
        }
    }

    @Test fun lineEntryAndAggregateNameBoundsFailBeforeAnyCreateOrGeneratedValues() = runTest {
        val blank = physical("" to null).rows.single()
        val good = physical("2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN))
        val row = good.rows.single()
        val cases = listOf(
            good.copy(rows = SnapshotList.copyOf(List(PlainDeckListLimits.MAX_LINES + 1) { blank })),
            good.copy(rows = SnapshotList.copyOf(List(PlainDeckListLimits.MAX_ENTRIES + 1) { row })),
            good.copy(rows = SnapshotList.of(row.copy(rawText = "x".repeat(PlainDeckListLimits.MAX_LINE_BYTES + 1)))),
            good.copy(rows = SnapshotList.copyOf(List(600) { row.copy(entry = row.entry!!.copy(name = "x".repeat(PlainDeckListLimits.MAX_NAME_BYTES))) })))
        for (parsed in cases) {
            val f = PlainImportFixture(this); f.controller.parse(f.input(result = parsed, streamText = "bounded scripted input")); runCurrent()
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem); f.assertNoCreation()
        }
    }

    @Test fun codecInputIdMismatchIsTypedCorruptionAndNeverAdopted() = runTest {
        val f = PlainImportFixture(this)
        f.parseTransform = { it.copy(inputId = EntityId("other-input")) }
        f.controller.parse(f.input()); runCurrent()
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        assertNull(f.controller.state.value.input); f.assertNoCreation()
    }

    @Test fun newExplicitParseSupersedesCanceledLateCompletionAndInvalidatesOldAuthority() = runTest {
        val f = PlainImportFixture(this); val gate = f.parseGate("first", ignoreCancellation = true)
        f.controller.parse(f.input("first")); runCurrent()
        val firstTicket = f.controller.state.value.formTicket
        val next = physical("1 New" to PlainDeckListEntry(1, "New", BoardRole.MAIN))
        f.controller.parse(f.input("second", next)); runCurrent()
        assertTrue(f.controller.state.value.formTicket > firstTicket)
        val second = f.controller.state.value.input
        gate.complete(Unit); runCurrent()
        assertEquals(second, f.controller.state.value.input)
        assertEquals(EntityId("second"), second!!.inputId)
        assertFalse(f.controller.state.value.parsing)
        assertEquals(listOf(EntityId("first"), EntityId("second")), f.parseCalls)
        f.assertNoCreation()
    }

    @Test fun parseRetryNeverReusesConsumedSingleUseFlowAndCancelReadDoesNotCreateAnything() = runTest {
        val f = PlainImportFixture(this)
        f.controller.parse(f.input()); runCurrent(); f.controller.retry(); runCurrent()
        assertEquals(1, f.collections); assertEquals(1, f.parseCalls.size)
        val gate = f.parseGate("canceled")
        f.controller.parse(f.input("canceled")); runCurrent(); f.controller.cancelParse(); runCurrent()
        gate.complete(Unit); runCurrent()
        assertFalse(f.controller.state.value.parsing); assertNull(f.controller.state.value.input)
        f.controller.retry(); runCurrent(); assertEquals(2, f.collections)
        f.assertNoCreation()
    }

    @Test fun hidingDoesNotCancelParseAndResumeCreatesNoAdditionalRead() = runTest {
        val f = PlainImportFixture(this); val gate = f.parseGate("source")
        f.controller.parse(f.input()); runCurrent(); val ticket = f.controller.state.value.formTicket
        f.controller.close(); assertFalse(f.controller.state.value.visible)
        gate.complete(Unit); runCurrent()
        assertNotNull(f.controller.state.value.input); assertFalse(f.controller.state.value.visible)
        f.controller.resume(); runCurrent()
        assertTrue(f.controller.state.value.visible); assertEquals(ticket, f.controller.state.value.formTicket)
        assertEquals(1, f.collections); f.assertNoCreation()
    }

    @Test fun nameChangesAndNewPreviewReferenceRejectOldOrCopiedConfirmationBeforeAnyIds() = runTest {
        val f = PlainImportFixture(this); val first = f.ready()
        f.controller.name("A different exact name"); assertNull(f.controller.state.value.preview)
        f.controller.confirm(first); runCurrent(); f.assertNoCreation()
        f.controller.preview(); runCurrent(); val current = f.controller.state.value.preview!!
        f.controller.confirm(first); f.controller.confirm(current.copy()); runCurrent(); f.assertNoCreation()
        f.controller.close(); f.controller.confirm(current); runCurrent(); f.assertNoCreation()
        f.controller.resume(); f.controller.confirm(current); runCurrent(); assertEquals(1, f.creates.size)
        assertEquals("A different exact name", f.creates.single().snapshot.name)
    }

    @Test fun invalidOversizedAndMalformedUnicodeNamesKeepRowsAndNeverConsumeGeneratedValues() = runTest {
        for (name in listOf("", " \t", "\uD800", "\uDC00", "x".repeat(LocalDeckLibraryLimits.MAX_NAME_BYTES + 1), "😃".repeat(257))) {
            val f = PlainImportFixture(this); f.controller.parse(f.input()); runCurrent(); val input = f.controller.state.value.input
            f.controller.name(name); f.controller.preview(); runCurrent()
            assertEquals(name, f.controller.state.value.name); assertEquals(input, f.controller.state.value.input)
            assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem); f.assertNoCreation()
        }
    }

    @Test fun exactUtf8NameBoundaryIsPreservedWithoutTrimming() = runTest {
        val f = PlainImportFixture(this); val name = "é".repeat(512)
        val preview = f.ready(name); f.controller.confirm(preview); runCurrent()
        assertEquals(name, f.creates.single().snapshot.name)
    }

    @Test fun generatedIdCollisionMalformedUnicodeAndExcessBytesRefuseBeforeClockOrCreate() = runTest {
        val sequences = listOf(listOf(EntityId("same"), EntityId("same")), listOf(EntityId("deck"), EntityId("entry"), EntityId("deck")),
            listOf(EntityId("\uD800")), listOf(EntityId("x".repeat(LocalDeckLibraryLimits.MAX_ID_BYTES + 1))))
        for (sequence in sequences) {
            val f = PlainImportFixture(this); val preview = f.ready(); f.idValues.addAll(sequence)
            f.controller.confirm(preview); runCurrent()
            assertTrue(f.creates.isEmpty()); assertEquals(0, f.clocks)
            assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
            assertSame(preview, f.controller.state.value.preview); assertFalse(f.controller.state.value.uncertain)
        }
    }

    @Test fun graphValidatorRefusalRunsBeforeCreateAndPreservesWholePreview() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready()
        f.validation = { _, _ -> Outcome.Unavailable(UnavailableReason.UNSUPPORTED, "Do not display backend details provider://secret") }
        f.controller.confirm(preview); runCurrent()
        assertTrue(f.creates.isEmpty()); assertEquals(1, f.validated.size)
        assertEquals(Problem.UNSUPPORTED, f.controller.state.value.problem)
        assertSame(preview, f.controller.state.value.preview); assertEquals(preview.input, f.controller.state.value.input)
        assertNull(f.controller.state.value.acknowledgedId); assertTrue(f.notices.isEmpty())
    }

    @Test fun canceledOwnerBeforeConstructionConsumesNoIdsTimeOrCreateCall() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val f = PlainImportFixture(this, owner); val preview = f.ready(); owner.cancel()
        f.controller.confirm(preview); runCurrent()
        f.assertNoCreation(); assertFalse(f.controller.state.value.submitting)
        assertSame(preview, f.controller.state.value.preview)
    }

    @Test fun cancellationDuringBoundedConstructionDoesNotSubmitPartiallyBuiltDeck() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val f = PlainImportFixture(this, owner); val preview = f.ready()
        f.onId = { if (it == 2) owner.cancel() }
        f.controller.confirm(preview); runCurrent()
        assertTrue(f.creates.isEmpty()); assertEquals(0, f.clocks)
        assertSame(preview, f.controller.state.value.preview); assertFalse(f.controller.state.value.submitting)
        assertTrue(f.notices.isEmpty()); owner.cancel()
    }

    @Test fun hideDuringSubmittedCreateRetainsExactFrozenRequestAndBlocksIncompatibleInput() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); val gate = f.gate("create.before")
        f.controller.confirm(preview); runCurrent(); val request = f.creates.single(); val ids = f.ids
        f.controller.close(); f.controller.name("Must not replace")
        f.controller.parse(f.input("blocked")); f.controller.discardInput(); f.controller.cancelParse(); runCurrent()
        assertEquals(preview.input, f.controller.state.value.input); assertEquals(preview.name, f.controller.state.value.name)
        assertFalse(f.controller.state.value.visible); assertEquals(1, f.parseCalls.size)
        f.controller.resume(); runCurrent(); assertEquals(ids, f.ids); assertEquals(listOf(request), f.creates)
        gate.complete(Unit); runCurrent(); assertEquals(listOf(request), f.creates); assertEquals(1, f.groups)
        assertEquals(1, f.notices.size)
    }

    @Test fun lostAcknowledgementAndLaterNegativeResponseNeverEraseFrozenUncertainty() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.afterUnknown = true
        f.controller.confirm(preview); runCurrent(); val exact = f.creates.single(); val ids = f.ids
        assertTrue(f.controller.state.value.uncertain); assertNull(f.controller.state.value.acknowledgedId)
        f.createResponse = failure(FailureCode.INVALID_INPUT)
        f.controller.retry(); runCurrent()
        assertTrue(f.controller.state.value.uncertain); assertEquals(Problem.INVALID_INPUT, f.controller.state.value.problem)
        f.controller.discardInput(); f.controller.name("Do not replace"); f.controller.parse(f.input("ignored"))
        assertSame(preview, f.controller.state.value.preview)
        f.createResponse = null; f.controller.retry(); runCurrent()
        assertEquals(listOf(exact, exact, exact), f.creates)
        assertEquals(ids, f.ids); assertEquals(1, f.clocks); assertEquals(1, f.groups)
        assertEquals(1, f.notices.size); assertFalse(f.controller.state.value.uncertain)
    }

    @Test fun cancellationBeforeOrAfterCreateEffectKeepsSameRequestForExactRetry() = runTest {
        for (after in listOf(false, true)) {
            val f = PlainImportFixture(this); val preview = f.ready(); f.cancelCreate = if (after) "after" else "before"
            f.controller.confirm(preview); runCurrent(); val exact = f.creates.single(); val ids = f.ids
            assertTrue(f.controller.state.value.uncertain); assertTrue(f.notices.isEmpty())
            assertEquals(if (after) 1 else 0, f.groups)
            f.controller.retry(); runCurrent()
            assertEquals(listOf(exact, exact), f.creates); assertEquals(ids, f.ids)
            assertEquals(1, f.clocks); assertEquals(1, f.groups); assertEquals(1, f.notices.size)
        }
    }

    @Test fun thrownCreateAfterDispatchIsUncertainAndRetriesWithoutRegeneratingAnything() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.throwCreate = true
        f.controller.confirm(preview); runCurrent(); val exact = f.creates.single(); val ids = f.ids
        assertEquals(Problem.UNEXPECTED, f.controller.state.value.problem); assertTrue(f.controller.state.value.uncertain)
        f.controller.retry(); runCurrent()
        assertEquals(listOf(exact, exact), f.creates); assertEquals(ids, f.ids); assertEquals(1, f.clocks)
    }

    @Test fun definitiveInitialStorageRefusalKeepsCompleteEditablePreviewWithoutInventingSuccess() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.createResponse = failure(FailureCode.INVALID_INPUT)
        f.controller.confirm(preview); runCurrent()
        assertFalse(f.controller.state.value.uncertain); assertSame(preview, f.controller.state.value.preview)
        assertEquals(preview.input, f.controller.state.value.input); assertNull(f.controller.state.value.acknowledgedId)
        assertEquals(0, f.groups); assertTrue(f.notices.isEmpty())
        val ids = f.ids; f.controller.retry(); runCurrent(); assertEquals(ids, f.ids); assertEquals(1, f.creates.size)
        f.controller.name("New explicit name"); assertNull(f.controller.state.value.preview)
    }

    @Test fun fullCreateAckMismatchStaysUnknownWithoutLoadingOrNotification() = runTest {
        val mutations: List<(DeckSnapshot) -> DeckSnapshot> = listOf(
            { it.copy(id = DeckId(LocalDeckLibraryLimits.SOURCE, "different")) }, { it.copy(name = "Changed") },
            { it.copy(revision = Revision(1)) }, { it.copy(createdAt = UtcTimestamp(31)) }, { it.copy(modifiedAt = UtcTimestamp(31)) },
            { it.copy(tags = SnapshotList.of("Not supplied")) }, { it.copy(categoryOrder = null) },
            { it.copy(entries = SnapshotList.copyOf(it.entries.map { row -> row.copy(notes = "Not projected") })) })
        for (mutate in mutations) {
            val f = PlainImportFixture(this); val preview = f.ready(); f.ackTransform = mutate
            f.controller.confirm(preview); runCurrent(); val exact = f.creates.single(); val ids = f.ids
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem); assertTrue(f.controller.state.value.uncertain)
            assertTrue(f.loads.isEmpty()); assertTrue(f.notices.isEmpty()); assertNull(f.controller.state.value.acknowledgedId)
            f.ackTransform = { it }; f.controller.retry(); runCurrent()
            assertEquals(listOf(exact, exact), f.creates); assertEquals(ids, f.ids); assertEquals(1, f.notices.size)
        }
    }

    @Test fun knownAckReadFailureRetryOnlyLoadsAndExposesAcknowledgedIdSeparatelyFromHead() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.loadResponse = Outcome.Unknown("Secret backend reason", SnapshotList.empty())
        f.controller.confirm(preview); runCurrent()
        val ack = f.creates.single().snapshot; val ids = f.ids
        assertEquals(ack.id, f.controller.state.value.acknowledgedId); assertNull(f.controller.state.value.currentHead)
        assertTrue(f.controller.state.value.awaitingFresh); assertFalse(f.controller.state.value.uncertain)
        assertTrue(f.notices.isEmpty())
        f.controller.discardInput(); f.controller.name("Blocked"); f.controller.close(); f.controller.resume()
        f.controller.retry(); runCurrent(); assertEquals(1, f.creates.size); assertEquals(ids, f.ids)
        assertEquals(listOf(ack.id, ack.id), f.loads)
        f.loadResponse = null; f.controller.retry(); runCurrent(); f.controller.retry(); runCurrent()
        assertEquals(1, f.creates.size); assertEquals(1, f.clocks); assertEquals(ids, f.ids)
        assertEquals(listOf(ack), f.notices)
    }

    @Test fun revisionZeroChangedContentOrTimeCannotImpersonateExactCreatedHead() = runTest {
        val mutations: List<(DeckSnapshot) -> DeckSnapshot> = listOf(
            { it.copy(name = "Same revision changed") }, { it.copy(description = "Changed") },
            { it.copy(modifiedAt = UtcTimestamp(31)) }, { it.copy(primer = "Changed") }, { it.copy(categoryOrder = null) },
            { it.copy(entries = SnapshotList.copyOf(it.entries.map { e -> e.copy(quantity = 7) })) })
        for (mutate in mutations) {
            val f = PlainImportFixture(this); val preview = f.ready(); f.loadTransform = mutate
            f.controller.confirm(preview); runCurrent()
            assertTrue(f.controller.state.value.awaitingFresh); assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
            assertNull(f.controller.state.value.currentHead); assertTrue(f.notices.isEmpty()); assertEquals(1, f.creates.size)
            f.loadTransform = { it }; f.controller.retry(); runCurrent()
            assertEquals(1, f.creates.size); assertEquals(1, f.notices.size)
        }
    }

    @Test fun wrongIdentityChangedCreationAndRegressedTimesAreRejectedEvenForLaterRevision() = runTest {
        val mutations: List<(DeckSnapshot) -> DeckSnapshot> = listOf(
            { it.copy(revision = Revision(2), id = DeckId(LocalDeckLibraryLimits.SOURCE, "wrong")) },
            { it.copy(revision = Revision(2), createdAt = UtcTimestamp(29)) },
            { it.copy(revision = Revision(2), modifiedAt = UtcTimestamp(30, 6)) },
            { it.copy(revision = Revision(2), name = "\uD800") })
        for (mutate in mutations) {
            val f = PlainImportFixture(this); val preview = f.ready(); f.loadTransform = mutate
            f.controller.confirm(preview); runCurrent()
            assertTrue(f.controller.state.value.awaitingFresh); assertTrue(f.notices.isEmpty())
            assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem); assertEquals(1, f.creates.size)
        }
    }

    @Test fun graphValidationRejectsLoadedCurrentHeadThenLaterValidHeadIsAcceptedOnceReadOnly() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready()
        f.validation = { _, _ -> if (f.validated.size > 1) failure(FailureCode.INVALID_INPUT) else Outcome.Success(Unit) }
        f.controller.confirm(preview); runCurrent(); val ack = f.creates.single().snapshot
        assertTrue(f.controller.state.value.awaitingFresh); assertTrue(f.notices.isEmpty())
        assertEquals(Problem.CORRUPT_DATA, f.controller.state.value.problem)
        val later = ack.copy(name = "A genuine later head", revision = Revision(2), modifiedAt = UtcTimestamp(40),
            entries = SnapshotList.copyOf(ack.entries.map { it.copy(quantity = 6, notes = "Later edited") }))
        f.heads[ack.id] = later; f.validation = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
        val ids = f.ids; f.controller.retry(); runCurrent(); f.controller.retry(); runCurrent()
        assertEquals(listOf(later), f.notices); assertEquals(later, f.controller.state.value.currentHead)
        assertEquals(ack.id, f.controller.state.value.acknowledgedId)
        assertEquals(1, f.creates.size); assertEquals(ids, f.ids); assertEquals(1, f.clocks)
    }

    @Test fun cancellationAfterKnownAckKeepsReadOnlyRecoveryAndNeverResubmitsCreate() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.cancelLoad = true
        f.controller.confirm(preview); runCurrent(); val ids = f.ids
        assertTrue(f.controller.state.value.awaitingFresh); assertFalse(f.controller.state.value.uncertain)
        assertNotNull(f.controller.state.value.acknowledgedId); assertTrue(f.notices.isEmpty())
        f.controller.retry(); runCurrent()
        assertEquals(1, f.creates.size); assertEquals(ids, f.ids); assertEquals(1, f.notices.size)
    }

    @Test fun notificationFailureCannotRestoreAuthorityOrNotifyAgain() = runTest {
        val f = PlainImportFixture(this); val preview = f.ready(); f.notice = { error("Do not display provider://secret") }
        f.controller.confirm(preview); runCurrent(); f.controller.retry(); runCurrent(); f.controller.confirm(preview); runCurrent()
        assertEquals(Problem.INTERNAL, f.controller.state.value.problem)
        assertEquals(1, f.creates.size); assertEquals(1, f.notices.size)
        assertNotNull(f.controller.state.value.currentHead)
    }

    @Test fun explicitDiscardCancelsOnlyUnsubmittedReadAndRetiresItsFormTicket() = runTest {
        val f = PlainImportFixture(this); val gate = f.parseGate("source", ignoreCancellation = true)
        f.controller.parse(f.input()); runCurrent(); val ticket = f.controller.state.value.formTicket
        f.controller.discardInput(); gate.complete(Unit); runCurrent(); f.controller.resume()
        assertTrue(f.controller.state.value.formTicket > ticket)
        assertFalse(f.controller.state.value.visible); assertNull(f.controller.state.value.input)
        assertFalse(f.controller.state.value.parsing); f.assertNoCreation()
    }
}

private class PlainImportFixture(private val test: TestScope, owner: CoroutineScope = test) : PlainDeckListCodec, LocalDeckLibrary {
    data class Create(val snapshot: DeckSnapshot, val operation: EntityId)
    val creates = mutableListOf<Create>(); val loads = mutableListOf<DeckId>(); val notices = mutableListOf<DeckSnapshot>()
    val parseCalls = mutableListOf<EntityId>(); val validated = mutableListOf<DeckSnapshot>()
    val heads = mutableMapOf<DeckId, DeckSnapshot>(); private val receipts = mutableMapOf<EntityId, Create>()
    private val replies = mutableMapOf<EntityId, PlainDeckList>()
    private val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
    private val parseGates = mutableMapOf<EntityId, Pair<CompletableDeferred<Unit>, Boolean>>()
    val idValues = ArrayDeque<EntityId>()
    var ids = 0; var clocks = 0; var groups = 0; var collections = 0
    var onId: (Int) -> Unit = {}
    var parseTransform: (PlainDeckList) -> PlainDeckList = { it }
    var createResponse: Outcome<DeckSnapshot>? = null
    var loadResponse: Outcome<DeckSnapshot>? = null
    var ackTransform: (DeckSnapshot) -> DeckSnapshot = { it }
    var loadTransform: (DeckSnapshot) -> DeckSnapshot = { it }
    var validation: (DeckSnapshot, CancelToken) -> Outcome<Unit> = { _, cancel -> cancel.throwIfCancelled(); Outcome.Success(Unit) }
    var notice: (DeckSnapshot) -> Unit = {}
    var afterUnknown = false; var throwCreate = false; var cancelCreate: String? = null; var cancelLoad = false
    val controller = PlainDeckImportController(owner, this, this, StandardTestDispatcher(test.testScheduler),
        { ids++; onId(ids); if (idValues.isEmpty()) EntityId("id-$ids") else idValues.removeFirst() },
        { clocks++; UtcTimestamp(30, 7) },
        { deck, cancel -> validated.add(deck); validation(deck, cancel) }, { notices.add(it); notice(it) })
    fun input(id: String = "source", result: PlainDeckList = physical("2 Same" to PlainDeckListEntry(2, "Same", BoardRole.MAIN)),
        streamText: String = result.rows.joinToString("\n") { it.rawText }): TransferInput {
        val inputId = EntityId(id)
        replies[inputId] = result.copy(inputId = inputId)
        val bytes = streamText.toByteArray(Charsets.UTF_8)
        var consumed = false
        return TransferInput(inputId, "plain.txt", Knowledge.Known("text/plain"), Knowledge.Known(bytes.size.toLong()), flow {
            check(!consumed) { "Single-use input collected twice" }; consumed = true; collections++
            emit(ByteChunk(0, 0, SnapshotBytes(bytes), null, true))
        })
    }
    fun ready(name: String = "Imported deck"): PlainDeckImportPreview {
        controller.parse(input()); test.runCurrent(); controller.name(name); controller.preview(); test.runCurrent()
        return checkNotNull(controller.state.value.preview)
    }
    fun gate(stage: String) = CompletableDeferred<Unit>().also { gates[stage] = it }
    fun parseGate(id: String, ignoreCancellation: Boolean = false) = CompletableDeferred<Unit>().also { parseGates[EntityId(id)] = it to ignoreCancellation }
    private suspend fun wait(stage: String) { gates.remove(stage)?.await() }
    fun assertNoCreation() { assertTrue(creates.isEmpty()); assertEquals(0, ids); assertEquals(0, clocks); assertTrue(notices.isEmpty()) }
    override suspend fun parse(input: TransferInput, cancel: CancelToken): Outcome<PlainDeckList> {
        parseCalls.add(input.id)
        input.chunks.collect { cancel.throwIfCancelled(); it.bytes.copyBytes() }
        val value = replies.getValue(input.id)
        parseGates.remove(input.id)?.let { (gate, ignores) -> if (ignores) withContext(NonCancellable) { gate.await() } else gate.await() }
        return Outcome.Success(parseTransform(value))
    }
    override fun render(deck: DeckSnapshot, cancel: CancelToken): Outcome<PlainDeckListExport> = error("No export capability invoked")
    override suspend fun create(initial: DeckSnapshot, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> {
        val call = Create(initial, operationId); creates.add(call); wait("create.before")
        if (throwCreate) { throwCreate = false; error("Interrupted create transport") }
        if (cancelCreate == "before") { cancelCreate = null; currentCoroutineContext().cancel(); yield() }
        createResponse?.let { return it }
        val original = receipts[operationId]
        if (original != null && original != call) return failure(FailureCode.INVALID_INPUT)
        if (original == null) {
            if (initial.id in heads) return failure(FailureCode.INVALID_INPUT)
            receipts[operationId] = call; heads[initial.id] = initial; groups++
        }
        wait("create.after")
        if (cancelCreate == "after") { cancelCreate = null; currentCoroutineContext().cancel(); yield() }
        if (afterUnknown) { afterUnknown = false; return Outcome.Unknown("Lost create acknowledgement", SnapshotList.empty()) }
        return Outcome.Success(ackTransform(receipts.getValue(operationId).snapshot))
    }
    override suspend fun load(id: DeckId, cancel: CancelToken): Outcome<DeckSnapshot> {
        loads.add(id); wait("load")
        if (cancelLoad) { cancelLoad = false; currentCoroutineContext().cancel(); yield() }
        loadResponse?.let { return it }
        return Outcome.Success(loadTransform(heads.getValue(id)))
    }
    override suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<DeckSummary>> = error("No listing")
    override suspend fun duplicate(id: DeckId, expected: Revision, name: String, operationId: EntityId, cancel: CancelToken): Outcome<DeckSnapshot> = error("No existing-deck mutation")
}

private fun physical(vararg lines: Pair<String, PlainDeckListEntry?>, complete: Boolean = true, errors: Set<Int> = emptySet()): PlainDeckList {
    var offset = 0L
    val rows = lines.mapIndexed { index, (raw, entry) ->
        val location = RowLocation("plain.txt", index + 1L, offset, null)
        offset += raw.toByteArray(Charsets.UTF_8).size + 1L
        PlainDeckListRow(location, raw, entry, if (index in errors) SnapshotList.of(ImportIssue(location,
            PlainDeckListIssueCodes.INVALID_QUANTITY, IssueSeverity.ERROR, "Static syntax issue", Knowledge.Absent("Needs explicit correction"))) else SnapshotList.empty())
    }
    return PlainDeckList(EntityId("source"), SnapshotList.copyOf(rows), complete)
}
private fun expectedEntry(id: String, quantity: Int, name: String, board: BoardRole): DeckEntry = DeckEntry(EntityId(id),
    CardIdentity(Knowledge.Unknown("Plain list does not identify an Oracle card", SnapshotList.empty()),
        Knowledge.Unknown("Plain list does not identify a printing", SnapshotList.empty()), null, Knowledge.Known(name), LanguageTag("und")),
    quantity, board, Knowledge.Unknown("Physical finish not supplied by plain list", SnapshotList.empty()), "", SnapshotList.empty(), SnapshotList.empty())
private fun failure(code: FailureCode): Outcome.Failure = Outcome.Failure(DomainFailure(code, "Static fixture refusal", SnapshotList.empty()))
