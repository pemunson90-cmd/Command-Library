package com.architectpsg.commandlibrary.feature.decks

import com.architectpsg.commandlibrary.core.model.*
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class DeckSnapshotValidatorTest {
    private val token = CountingToken()
    private class CountingToken(private val stop: Int = Int.MAX_VALUE) : CancelToken {
        var polls = 0
        val exception = CancellationException("synthetic cancellation")
        override val isCancelled: Boolean get() = polls >= stop
        override fun throwIfCancelled() { if (++polls >= stop) throw exception }
    }
    private fun entry(id: String = "e", quantity: Int = 5) = DeckEntry(EntityId(id),
        CardIdentity(Knowledge.Unknown("oracle unknown", SnapshotList.empty()), Knowledge.Absent("printing absent"), null,
            Knowledge.Unknown("name unknown", SnapshotList.empty()), LanguageTag("ja")),
        quantity, BoardRole.MAIN, Knowledge.Unknown("finish unknown", SnapshotList.empty()), "notes", SnapshotList.empty(), SnapshotList.empty())
    private fun deck(vararg entries: DeckEntry) = DeckSnapshot(DeckId(ProviderId("foreign"), "deck"), "Deck", Knowledge.Absent("format absent"), "description",
        Knowledge.Absent("folder absent"), SnapshotList.empty(), UtcTimestamp(1), UtcTimestamp(2), Revision(7), SnapshotList.of(*entries),
        Knowledge.Absent("budget absent"), null, SnapshotList.empty(), SnapshotList.empty())
    private fun contribution(packageEntry: String = "pe", target: String = "e", quantity: Int = 1) = PackageContribution(EntityId(packageEntry), EntityId(target), quantity)
    private fun instance(id: String, vararg contributions: PackageContribution, policy: PackageLinkPolicy = PackageLinkPolicy.LINKED) =
        PackageInstance(EntityId(id), EntityId("foreign package"), EntityId("unavailable version"), policy, SnapshotList.of(*contributions))
    private fun packaged(vararg instances: PackageInstance) = deck(entry()).copy(packageInstances = SnapshotList.of(*instances))
    private fun valid(value: DeckSnapshot) { assertEquals(Outcome.Success(Unit), DeckSnapshotValidator.validate(value, token)) }
    private fun invalid(value: DeckSnapshot) {
        val result = DeckSnapshotValidator.validate(value, token)
        assertTrue(result.toString(), result is Outcome.Failure)
        val error = (result as Outcome.Failure).error
        assertEquals(FailureCode.INVALID_INPUT, error.code)
        assertEquals("Invalid deck structural graph.", error.message)
        assertTrue(error.evidence.isEmpty())
    }

    @Test fun validPrimitiveAndEmptyDecksReuseExistingBaseSemantics() {
        valid(deck()); valid(deck(entry()))
        valid(deck(entry()).copy(revision = Revision(Long.MAX_VALUE)))
    }

    @Test fun crossInstanceConservationAcceptsTwoPlusThreeButRejectsTwoPlusFour() {
        valid(packaged(instance("one", contribution(quantity = 2)), instance("two", contribution(quantity = 3))))
        invalid(packaged(instance("one", contribution(quantity = 2)), instance("two", contribution(quantity = 4))))
        valid(packaged(instance("manual remainder", contribution(quantity = 2))))
    }

    @Test fun maximumIntContributionsCannotWrapAcrossInstances() {
        val exact = deck(entry(quantity = Int.MAX_VALUE)).copy(packageInstances = SnapshotList.of(instance("max", contribution(quantity = Int.MAX_VALUE))))
        valid(exact)
        invalid(exact.copy(packageInstances = SnapshotList.of(instance("max", contribution(quantity = Int.MAX_VALUE)), instance("extra", contribution(quantity = 1)))))
        invalid(exact.copy(packageInstances = SnapshotList.of(instance("one", contribution(quantity = Int.MAX_VALUE)), instance("two", contribution(quantity = Int.MAX_VALUE)))))
    }

    @Test fun splitOnePackageEntryAndManyPackageEntriesPerTargetAreValid() {
        val split = instance("split", contribution("same", "a", 2), contribution("same", "b", 3))
        val many = instance("many", contribution("x", "a", 1), contribution("y", "a", 2))
        valid(deck(entry("a", 5), entry("b", 3)).copy(packageInstances = SnapshotList.of(split, many)))
    }

    @Test fun pairUniquenessIsPerInstanceAndRepeatedVersionsAreAllowed() {
        invalid(packaged(instance("one", contribution(quantity = 1), contribution(quantity = 2))))
        valid(packaged(instance("one", contribution(quantity = 2)), instance("two", contribution(quantity = 3))))
    }

    @Test fun missingTargetsAndDuplicateInstanceIdsRejectEvenWhenTotalsWouldFit() {
        invalid(packaged(instance("missing", contribution(target = "absent"))))
        invalid(packaged(instance("duplicate", contribution(quantity = 1)), instance("duplicate", contribution(quantity = 1))))
    }

    @Test fun emptyLinkedAndCopyInstancesAreValidWithNoResolvedRegistry() {
        valid(deck().copy(packageInstances = SnapshotList.of(instance("linked"), instance("copy", policy = PackageLinkPolicy.COPY))))
    }

    @Test fun unknownRichForeignMetadataAndOrderingRemainExactlyUnchanged() {
        val evidence = SnapshotList.of(Evidence("uncertain foreign identity", ResourceUri("foreign://evidence/界"), UtcTimestamp(0)))
        val label = EntryLabel(EntityId("label/界"), " Category ")
        val identity = CardIdentity(Knowledge.Unknown("oracle reason", evidence), Knowledge.Absent("printing reason"),
            ProviderIdentity(ProviderId("unavailable"), "raw kind", " raw value "), Knowledge.Unknown("original name reason", evidence), LanguageTag("x-foreign"))
        val originalEntry = entry().copy(identity = identity, finish = Knowledge.Unknown("finish reason", evidence), labels = SnapshotList.of(label),
            roleOverrides = SnapshotList.of(RoleOverride(null, Knowledge.Unknown("override oracle", evidence), "role", false, UtcTimestamp(9))))
        val originalInstances = SnapshotList.of(instance("second", contribution(quantity = 3), policy = PackageLinkPolicy.COPY), instance("first", contribution(quantity = 2)))
        val original = deck(originalEntry).copy(packageInstances = originalInstances, categoryOrder = SnapshotList.of(label),
            format = Knowledge.Unknown("foreign format", evidence), tags = SnapshotList.of("a", "a", "界"), profileId = EntityId("missing profile"),
            catalog = SnapshotList.of(SnapshotReference(ProviderId("missing catalog"), GenerationId("old generation"), Revision(99), LanguageTag("ja"))), primer = "# Primer")
        val before = original.copy()
        valid(original)
        assertEquals(before, original); assertSame(originalEntry, original.entries.single()); assertSame(originalInstances, original.packageInstances)
        assertSame(identity, original.entries.single().identity); assertEquals(listOf("second", "first"), original.packageInstances.map { it.id.value })
    }

    @Test fun nullAndExplicitCategoryRegistriesKeepExistingMeaning() {
        val label = EntryLabel(EntityId("c"), "Category")
        val original = deck(entry().copy(labels = SnapshotList.of(label)))
        valid(original); valid(original.copy(categoryOrder = SnapshotList.of(label)))
        invalid(original.copy(categoryOrder = SnapshotList.empty()))
        invalid(original.copy(categoryOrder = SnapshotList.of(label.copy(name = "Different"))))
        invalid(deck(entry("a").copy(labels = SnapshotList.of(label)), entry("b").copy(labels = SnapshotList.of(label.copy(name = "Other")))) )
    }

    @Test fun badBaseLabelsOverridesNamesAndTimesRemainGenericInvalidInput() {
        val label = EntryLabel(EntityId("c"), "Category")
        val base = deck(entry())
        val broken = listOf(base.copy(name = " "), base.copy(entries = SnapshotList.of(entry(), entry())),
            deck(entry().copy(labels = SnapshotList.of(label, label))), deck(entry().copy(labels = SnapshotList.of(label.copy(name = " ")))),
            deck(entry().copy(roleOverrides = SnapshotList.of(RoleOverride(EntityId("other"), Knowledge.Absent("none"), "role", true, UtcTimestamp(0))))),
            base.copy(modifiedAt = UtcTimestamp(0)), base.copy(createdAt = UtcTimestamp(2, 1), modifiedAt = UtcTimestamp(2)))
        for (value in broken) invalid(value.copy(packageInstances = SnapshotList.of(instance("empty"))))
    }

    @Test fun malformedOrOversizedIdsAreRejectedAcrossEveryGraphReference() {
        for (id in listOf("x".repeat(1_025), "é".repeat(513), "\uD800")) {
            invalid(deck(entry(id)))
            invalid(deck(entry().copy(labels = SnapshotList.of(EntryLabel(EntityId(id), "Label")))) )
            invalid(deck(entry().copy(roleOverrides = SnapshotList.of(RoleOverride(EntityId(id), Knowledge.Absent("none"), "role", true, UtcTimestamp(0))))))
            invalid(packaged(instance(id)))
            invalid(packaged(instance("i").copy(packageId = EntityId(id))))
            invalid(packaged(instance("i").copy(versionId = EntityId(id))))
            invalid(packaged(instance("i", contribution(packageEntry = id))))
            invalid(packaged(instance("i", contribution(target = id))))
        }
    }

    @Test fun strictUtf8IdentifierBoundaryAcceptsExactly1024Bytes() {
        val id = "😀".repeat(256)
        valid(deck(entry(id)).copy(packageInstances = SnapshotList.of(instance("i", contribution(target = id)))))
        invalid(deck(entry(id + "x")))
        valid(deck(entry("é".repeat(512))))
    }

    @Test fun comparedNamesAreBoundedBeforeReducerWhitespaceAndEqualityScans() {
        val maximum = "n".repeat(DeckPayloadLimits.MAX_TEXT_BYTES)
        valid(deck().copy(name = maximum)); invalid(deck().copy(name = maximum + "n"))
        valid(deck().copy(categoryOrder = SnapshotList.of(EntryLabel(EntityId("c"), maximum))))
        invalid(deck().copy(categoryOrder = SnapshotList.of(EntryLabel(EntityId("c"), maximum + "n"))))
        valid(deck().copy(name = "é".repeat(DeckPayloadLimits.MAX_TEXT_BYTES / 2)))
        invalid(deck().copy(name = "é".repeat(DeckPayloadLimits.MAX_TEXT_BYTES / 2 + 1)))
        invalid(deck().copy(name = "\uD800"))
    }

    @Test fun topLevelEntryCategoryAndInstanceCountBoundariesAreExplicit() {
        val entries = SnapshotList.copyOf(List(10_000) { entry("e$it", 1) })
        valid(deck().copy(entries = entries)); invalid(deck().copy(entries = SnapshotList.copyOf(entries + entry("extra"))))
        val categories = SnapshotList.copyOf(List(1_024) { EntryLabel(EntityId("c$it"), "Category") })
        valid(deck().copy(categoryOrder = categories)); invalid(deck().copy(categoryOrder = SnapshotList.copyOf(categories + EntryLabel(EntityId("extra"), "Category"))))
        val instances = SnapshotList.copyOf(List(10_000) { instance("i$it") })
        valid(deck().copy(packageInstances = instances)); invalid(deck().copy(packageInstances = SnapshotList.copyOf(instances + instance("extra"))))
    }

    @Test fun perEntryListCapsAreCheckedBeforeReducerAllocations() {
        val override = RoleOverride(null, Knowledge.Absent("none"), "role", false, UtcTimestamp(0))
        valid(deck(entry().copy(roleOverrides = SnapshotList.copyOf(List(10_000) { override }))))
        invalid(deck(entry().copy(roleOverrides = SnapshotList.copyOf(List(10_001) { override }))))
        val label = EntryLabel(EntityId("c"), "Category")
        invalid(deck(entry().copy(labels = SnapshotList.copyOf(List(10_001) { label }))))
    }

    @Test fun perInstanceContributionCapAllowsTenThousandDistinctSplitPairs() {
        val entries = SnapshotList.copyOf(List(10_000) { entry("e$it", if (it == 0) 2 else 1) })
        val contributions = SnapshotList.copyOf(List(10_000) { contribution(target = "e$it") })
        val original = deck().copy(entries = entries, packageInstances = SnapshotList.of(instance("i").copy(contributions = contributions)))
        valid(original)
        val tooMany = SnapshotList.copyOf(contributions + contribution(packageEntry = "second", target = "e0"))
        invalid(original.copy(packageInstances = SnapshotList.of(instance("i").copy(contributions = tooMany))))
    }

    @Test fun aggregateVisitedListsAllowExactlyOneHundredThousandElements() {
        val override = RoleOverride(null, Knowledge.Absent("none"), "role", false, UtcTimestamp(0))
        val overrides = SnapshotList.copyOf(List(9_999) { override })
        val entries = List(10) { entry("e$it").copy(roleOverrides = overrides) }
        valid(deck().copy(entries = SnapshotList.copyOf(entries))) // 10 entries + 99,990 overrides
        val first = entries.first().copy(roleOverrides = SnapshotList.copyOf(overrides + override))
        invalid(deck().copy(entries = SnapshotList.copyOf(listOf(first) + entries.drop(1))))
    }

    @Test fun examinedUtf16AggregateIsConservativeAndFinite() {
        val name = "n".repeat(1_024 * 1_024)
        valid(deck().copy(categoryOrder = SnapshotList.copyOf(List(7) { EntryLabel(EntityId("c$it"), name) })))
        invalid(deck().copy(categoryOrder = SnapshotList.copyOf(List(8) { EntryLabel(EntityId("c$it"), name) })))
    }

    @Test fun graphValidityDoesNotPretendToCertifyUnexaminedWireFields() {
        // Description/tags/Knowledge evidence are not used by these graph rules. Codec remains mandatory.
        valid(deck().copy(description = "x".repeat(DeckPayloadLimits.MAX_TEXT_BYTES + 1), tags = SnapshotList.of("\uD800")))
    }

    @Test fun cancellationBeforeWorkPropagatesTheOriginalException() {
        val cancelled = CountingToken(1)
        try { DeckSnapshotValidator.validate(deck(), cancelled); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(cancelled.exception, actual) }
    }

    @Test fun cancellationDuringLongSurrogateScanCannotSkipPollingBoundaries() {
        val cancelled = CountingToken(50)
        val original = deck().copy(name = "x" + "😀".repeat(100_000))
        try { DeckSnapshotValidator.validate(original, cancelled); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(cancelled.exception, actual) }
        assertEquals(50, cancelled.polls); assertEquals(200_001, original.name.length)
    }

    @Test fun cancellationLateInOriginalPackageTraversalAlsoPropagates() {
        val original = deck(entry(quantity = 100)).copy(packageInstances = SnapshotList.of(instance("i").copy(
            contributions = SnapshotList.copyOf(List(100) { contribution(packageEntry = "pe$it") }))))
        val complete = CountingToken(); assertEquals(Outcome.Success(Unit), DeckSnapshotValidator.validate(original, complete))
        val cancelled = CountingToken(complete.polls - 2)
        try { DeckSnapshotValidator.validate(original, cancelled); fail("Expected cancellation") }
        catch (actual: CancellationException) { assertSame(cancelled.exception, actual) }
        assertEquals(100, original.packageInstances.single().contributions.size)
    }
}
