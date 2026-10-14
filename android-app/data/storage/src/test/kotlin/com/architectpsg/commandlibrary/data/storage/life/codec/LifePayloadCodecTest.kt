package com.architectpsg.commandlibrary.data.storage.life.codec

import com.architectpsg.commandlibrary.core.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import java.nio.ByteBuffer

class LifePayloadCodecTest {
    private val codec = LifePayloadCodec()
    private val provider = ProviderId("synthetic")
    private val seatId = EntityId("seat")
    private val stamp = UtcTimestamp(Long.MIN_VALUE, 999_999_999)
    private val evidence = Evidence("Synthetic unresolved identity", ResourceUri("synthetic://source"), stamp)
    private fun <T> unknown(): Knowledge<T> = Knowledge.Unknown("Synthetic unknown", SnapshotList.of(evidence))
    private val notice = SourceNotice(provider, "Synthetic notice", unknown(), "Synthetic attribution", ResourceUri("synthetic://notice"))
    private val provenance = Provenance(provider, ResourceUri("synthetic://data"), unknown(), unknown(), stamp,
        Knowledge.Known(UtcTimestamp(Long.MAX_VALUE, 12)), GenerationId("generation"), ContentHash("SHA-256", "abcdef"), notice)
    private val card = CardIdentity(unknown(), Knowledge.Absent("No printing"), ProviderIdentity(provider, "foreign", "raw"),
        Knowledge.Known("名 Synthetic card"), LanguageTag("ja"))
    private val definition = CounterDefinition(EntityId("life"), "Synthetic life", -500, 500, 20,
        CounterLossPolicy.AtOrBelow(0), false, CounterRole.LIFE, DisplayColor(0xffffffffL), CounterPreset.RAD, CounterResetPolicy.ON_TURN_START)
    private val variantId = ProviderIdentity(provider, "synthetic-variant", "v1")
    private val variant = VariantDefinition(variantId, VariantNamespace.CUSTOM, "version", "Synthetic instructions",
        SnapshotList.of(VariantActionDefinition("move", "Move", "Synthetic move", "deck", "play", 3)),
        SnapshotList.of(VariantZoneDefinition("deck", "Deck", 10, true)), SnapshotList.of("phase"),
        SnapshotList.of(VariantCard(ProviderIdentity(provider, "synthetic-card", "c1"), "Synthetic variant card", "Text",
            unknown(), SnapshotList.of("move"), provenance)), provenance, notice)
    private val appearance = SeatAppearance(DisplayColor(0x80000000L), GameArtReference.CachedCard(card, EntityId("asset")),
        ArtFit.CONTAIN, ArtCrop(0.1, 0.2, 0.7, 0.8), SeatOrientation.INVERTED)
    private fun snapshot() = GameSnapshot(GameId(provider, "game"), 1, Revision(7),
        GameSettings(unknown(), 20, SnapshotList.of(definition), SnapshotList.of(variant), SeatClockMode.SHARED, Long.MIN_VALUE,
            GamePresentation(GameLayoutMode.OPERATOR, "synthetic-layout", true), 5, true,
            ElapsedTimerSettings(true, 2.seconds, 5.seconds), GameAlertPreferences(true, true, true)),
        GameStatus.PAUSED, stamp, Knowledge.Known(UtcTimestamp(Long.MAX_VALUE, 23)),
        SnapshotList.of(Seat(seatId, "Synthetic player", EntityId("profile"), Knowledge.Known(DeckReference(DeckId(provider, "deck"),
            Revision(9), "Synthetic deck", true)), 0, SnapshotList.of(CommanderSlot(EntityId("slot"), card, "Synthetic commander")),
            appearance, SeatParticipation.Eliminated(SeatExitKind.CONFIRMED_LOSS, "Confirmed synthetic loss",
                LossEvidence(definition.id, null, -3, CounterLossPolicy.AtOrBelow(0), unknown(), "Synthetic ruling"), stamp))),
        SnapshotList.of(CounterState(definition.id, seatId, null, -3, true)), seatId, Long.MAX_VALUE,
        SeatClockState(SeatClockMode.SHARED, SnapshotList.empty(), null, ClockAnchor(stamp, 4.seconds, EntityId("old-process")),
            true, ClockZeroPolicy.ALERT, ClockUndoPolicy.KEEP_ELAPSED, SnapshotList.of(EntityId("fired")), SharedCountdown(10.seconds, (-2).seconds)),
        SnapshotList.of(VariantState(variantId, "version", SnapshotList.of(VariantZone("deck", SnapshotList.of(variantId))),
            SnapshotList.of(variantId), SnapshotList.empty(), "phase", SnapshotList.of(VariantProperty("text", VariantValue.Text("原文")),
                VariantProperty("integer", VariantValue.Integer(Long.MIN_VALUE)), VariantProperty("flag", VariantValue.Flag(true)),
                VariantProperty("card", VariantValue.Card(variantId))), Long.MAX_VALUE, SnapshotList.of(VariantActionCount("move", 2)))),
        Knowledge.Known(6L), Knowledge.Known(GameResult(EntityId("result"), GameId(provider, "game"), SnapshotList.of(seatId), stamp,
            "Synthetic finish", Revision(7), true)), GameDesignations(seatId, null, "Synthetic initiative", DayNightState.NIGHT,
            SnapshotList.of(seatId)), ElapsedTimerState(12.seconds, 2.seconds, true, null, Knowledge.Known(EntityId("turn"))),
        SnapshotList.of(GameAlert(EntityId("alert"), GameId(provider, "game"), GameAlertTarget.SharedCountdown, stamp, stamp)))
    private fun event(action: GameAction = GameAction.RequestRandom(RandomRequest.Coin(EntityId("request")))) =
        GameEvent(8, snapshot().id, EntityId("group"), action, stamp, Knowledge.Unknown("Undo lineage", SnapshotList.of(evidence)),
            Revision(8), SnapshotList.of(RecordedRandomResult(EntityId("request"), stamp, RecordedRandomValue.Coin(true))))
    private fun creation(): GameCreation {
        val base = snapshot()
        return GameCreation(EntityId("create-operation"), base.settings,
            SnapshotList.of(base.seats.single().copy(participation = SeatParticipation.Active)),
            base.clock.copy(anchor = null, firedAlertIds = SnapshotList.empty(), shared = SharedCountdown(10.seconds, 10.seconds)))
    }
    private fun <T> success(outcome: Outcome<T>): T = (outcome as Outcome.Success<T>).value
    private fun failure(outcome: Outcome<*>, code: FailureCode) = assertEquals(code, (outcome as Outcome.Failure).error.code)

    @Test fun snapshotAndProfileRoundtripPreserveAllSyntheticMetadata() {
        val original = snapshot()
        val encoded = success(codec.encodeSnapshot(original))
        assertEquals(original, success(codec.decodeSnapshot(encoded)))
        assertEquals(encoded, success(codec.encodeSnapshot(original)))
        val draft = PlayerProfileDraft("Synthetic profile", 20, SnapshotList.of(definition), appearance,
            original.seats.single().commanders, 5)
        assertEquals(draft, success(codec.decodeProfile(success(codec.encodeProfile(draft)))))
    }
    @Test fun finiteDurationExtremesAndUnitTransitionsRoundtripExactly() {
        val largest = (Long.MAX_VALUE / 2 - 1).milliseconds
        for (value in listOf(Duration.ZERO, 1.nanoseconds, (-1).nanoseconds, 4_611_686_018_426_999_999L.nanoseconds,
            4_611_686_018_427_000_000L.nanoseconds, largest, -largest)) {
            assertTrue(value.isFinite())
            val s = snapshot().copy(clock = snapshot().clock.copy(shared = SharedCountdown(largest, value)))
            assertEquals(s, success(codec.decodeSnapshot(success(codec.encodeSnapshot(s)))))
        }
        val invalid = snapshot().copy(clock = snapshot().clock.copy(anchor = ClockAnchor(stamp, Duration.INFINITE, EntityId("process"))))
        failure(codec.encodeSnapshot(invalid), FailureCode.INVALID_INPUT)
    }
    @Test fun alternateClockArtParticipationAndAlertBranchesRetainState() {
        for (mode in SeatClockMode.entries) {
            val base = snapshot()
            val time = SeatTime(seatId, 10.seconds, (-1).nanoseconds)
            val changed = base.copy(settings = base.settings.copy(clockMode = mode),
                seats = SnapshotList.of(base.seats.single().copy(participation = SeatParticipation.Active,
                    appearance = appearance.copy(art = GameArtReference.Local(LocalArtReference(EntityId("local-art"),
                        "private/synthetic.png", ContentHash("SHA-256", "123abc")))))),
                clock = base.clock.copy(mode = mode, seats = if (mode == SeatClockMode.SHARED || mode == SeatClockMode.NONE)
                    SnapshotList.empty() else SnapshotList.of(time), shared = if (mode == SeatClockMode.SHARED) base.clock.shared else null,
                    zeroPolicy = ClockZeroPolicy.PAUSE, undoPolicy = ClockUndoPolicy.RESTORE_TIME),
                alerts = SnapshotList.copyOf(listOf(GameAlertTarget.SeatClock(seatId), GameAlertTarget.SharedCountdown,
                    GameAlertTarget.TurnTimer, GameAlertTarget.GameTimer).mapIndexed { index, target ->
                    GameAlert(EntityId("alert-$index"), base.id, target, stamp, null) }))
            assertEquals(changed, success(codec.decodeSnapshot(success(codec.encodeSnapshot(changed)))))
        }
    }
    @Test fun everyActionAndRecordedRandomBranchRoundtrips() {
        val id = EntityId("request")
        val actions = listOf<GameAction>(GameAction.AdjustCounter(seatId, definition.id, null, -2),
            GameAction.SetCounterHidden(seatId, definition.id, true), GameAction.RenameSeat(seatId, "New name"),
            GameAction.ReorderSeats(SnapshotList.of(seatId)), GameAction.SwitchTurn(seatId), GameAction.ClockPause(false),
            GameAction.Variant(variantId, "move", SnapshotList.of(variantId)), GameAction.Finish(SnapshotList.of(seatId), "Finished"),
            GameAction.Reopen("Reopened"), GameAction.Group(SnapshotList.of(GameAction.ClockPause(true)), EntityId("hold")),
            GameAction.RequestRandom(RandomRequest.Dice(id, Long.MAX_VALUE, Int.MAX_VALUE)),
            GameAction.RequestRandom(RandomRequest.ContinueDice(id, 3_000_000_000L)),
            GameAction.RequestRandom(RandomRequest.Coin(id)), GameAction.RequestRandom(RandomRequest.SeatPick(id, seatId, true)),
            GameAction.RequestRandom(RandomRequest.SeatOrder(id)))
        for (action in actions) assertEquals(event(action), success(codec.decodeEvent(success(codec.encodeEvent(event(action))))))
        val results = listOf<RecordedRandomValue>(RecordedRandomValue.Dice(6, SnapshotList.of(2, 3), 5, 2, 0, 0),
            RecordedRandomValue.Coin(false), RecordedRandomValue.SeatPick(null, SnapshotList.empty()),
            RecordedRandomValue.SeatPick(seatId, SnapshotList.of(seatId)), RecordedRandomValue.SeatOrder(SnapshotList.of(seatId)))
        for (value in results) {
            val e = event().copy(randomResults = SnapshotList.of(RecordedRandomResult(id, stamp, value)))
            assertEquals(e, success(codec.decodeEvent(success(codec.encodeEvent(e)))))
        }
    }
    @Test fun archiveAndCheckpointRoundtripRetainOriginalReadingAndIndependentSnapshots() {
        val before = snapshot().copy(settings = snapshot().settings.copy(counterDefinitions = SnapshotList.copyOf(List(128) {
            definition.copy(id = EntityId("counter-$it")) })), counters = SnapshotList.copyOf(List(1000) {
            CounterState(definition.id, seatId, null, it, false) }))
        val after = before.copy(revision = Revision(8), lastEventId = Knowledge.Known(8L))
        val archive = GameEventArchive(1, event(), GameActionProvenance(8, before, after,
            GameTimeObservation(3.seconds, stamp, EntityId("original-process"))))
        assertEquals(archive, success(codec.decodeArchive(success(codec.encodeArchive(archive)))))
        val checkpoint = GameCheckpoint(after, Knowledge.Known(8L), EntityId("recovered-process"))
        assertEquals(checkpoint, success(codec.decodeCheckpoint(success(codec.encodeCheckpoint(checkpoint)))))
    }
    @Test fun malformedHeadersTrailingBytesAndEveryTruncationAreCorrupt() {
        val bytes = success(codec.encodeSnapshot(snapshot())).copyBytes()
        failure(codec.decodeSnapshot(SnapshotBytes(bytes + byteArrayOf(0))), FailureCode.CORRUPT_DATA)
        failure(codec.decodeSnapshot(SnapshotBytes(bytes.copyOf().also { it[0] = 0 })), FailureCode.CORRUPT_DATA)
        failure(codec.decodeSnapshot(SnapshotBytes(bytes.copyOf().also { it[7] = 3 })), FailureCode.CORRUPT_DATA)
        failure(codec.decodeProfile(SnapshotBytes(bytes)), FailureCode.CORRUPT_DATA)
        for (size in 0 until bytes.size) failure(codec.decodeSnapshot(SnapshotBytes(bytes.copyOf(size))), FailureCode.CORRUPT_DATA)
    }
    @Test fun malformedUtf8AndOversizedStringPrefixesAreRejectedBeforeAllocation() {
        val bytes = success(codec.encodeSnapshot(snapshot())).copyBytes()
        // First field after the 9-byte frame is the game provider's UTF8 string.
        val malformed = bytes.copyOf().also { it[13] = 0xff.toByte() }
        failure(codec.decodeSnapshot(SnapshotBytes(malformed)), FailureCode.CORRUPT_DATA)
        val impossibleLength = bytes.copyOf().also {
            it[9] = 0x7f; it[10] = 0xff.toByte(); it[11] = 0xff.toByte(); it[12] = 0xff.toByte()
        }
        failure(codec.decodeSnapshot(SnapshotBytes(impossibleLength)), FailureCode.CORRUPT_DATA)
    }
    @Test fun stringsDefinitionsAndFlatGroupsFailVisiblyAtBounds() {
        failure(codec.encodeSnapshot(snapshot().copy(seats = SnapshotList.of(snapshot().seats.single().copy(playerName = "x".repeat(65_537))))), FailureCode.INVALID_INPUT)
        failure(codec.encodeSnapshot(snapshot().copy(seats = SnapshotList.of(snapshot().seats.single().copy(playerName = "\uD800")))), FailureCode.INVALID_INPUT)
        failure(codec.encodeSnapshot(snapshot().copy(settings = snapshot().settings.copy(counterDefinitions = SnapshotList.copyOf(List(129) { definition })))), FailureCode.INVALID_INPUT)
        val nested = GameAction.Group(SnapshotList.of(GameAction.Group(SnapshotList.of(GameAction.ClockPause(true)), EntityId("inner"))), EntityId("outer"))
        failure(codec.encodeEvent(event(nested)), FailureCode.INVALID_INPUT)
        failure(codec.encodeEvent(event(GameAction.Group(SnapshotList.empty(), EntityId("empty")))), FailureCode.INVALID_INPUT)
        failure(codec.encodeEvent(event(GameAction.Group(SnapshotList.copyOf(List(1025) { GameAction.ClockPause(true) }), EntityId("too-big")))), FailureCode.INVALID_INPUT)
    }
    @Test fun totalFrameBudgetsRejectLargePayloadsWithoutTruncating() {
        val text = "x".repeat(65_536)
        val base = snapshot()
        val within = base.copy(seats = SnapshotList.of(base.seats.single().copy(playerName = text)))
        assertEquals(within, success(codec.decodeSnapshot(success(codec.encodeSnapshot(within)))))
        val tooLarge = base.copy(settings = base.settings.copy(variantDefinitions = SnapshotList.of(variant.copy(
            cards = SnapshotList.copyOf(List(70) { variant.cards.single().copy(text = text) })))))
        failure(codec.encodeSnapshot(tooLarge), FailureCode.INVALID_INPUT)
        failure(codec.encodeEvent(event(GameAction.Group(SnapshotList.copyOf(List(270) { GameAction.RenameSeat(seatId, text) }),
            EntityId("oversize")))), FailureCode.INVALID_INPUT)
        failure(codec.decodeSnapshot(SnapshotBytes(ByteArray(4 * 1024 * 1024 + 1))), FailureCode.CORRUPT_DATA)
    }
    @Test fun archivedSnapshotsHaveIndependentByteBudgets() {
        val text = "x".repeat(65_536)
        val base = snapshot()
        fun sized(count: Int) = base.copy(settings = base.settings.copy(variantDefinitions = SnapshotList.of(variant.copy(
            cards = SnapshotList.copyOf(List(count) { variant.cards.single().copy(text = text) })))))
        fun archive(before: GameSnapshot) = GameEventArchive(1, event(), GameActionProvenance(8, before,
            before.copy(revision = Revision(8), lastEventId = Knowledge.Known(8L)), GameTimeObservation(3.seconds, stamp, EntityId("process"))))
        val individuallyValid = sized(35)
        assertTrue(success(codec.encodeSnapshot(individuallyValid)).size > 2 * 1024 * 1024)
        val both = archive(individuallyValid)
        val encoded = success(codec.encodeArchive(both))
        assertTrue(encoded.size > 4 * 1024 * 1024)
        assertEquals(both, success(codec.decodeArchive(encoded)))
        failure(codec.encodeArchive(archive(sized(70))), FailureCode.INVALID_INPUT)
    }
    @Test fun allRemainingActionFamiliesAndExplicitOutcomesRoundtrip() {
        val loss = LossEvidence(definition.id, null, -3, CounterLossPolicy.AtOrBelow(0), unknown(), "Synthetic ruling")
        val outcomes = SnapshotList.of(SeatResult(seatId, SeatResultKind.WIN, null))
        val actions = listOf<GameAction>(GameAction.SetCounter(seatId, definition.id, null, Int.MIN_VALUE),
            GameAction.SetAdjustmentStep(5), GameAction.SetCommanderLifeDeduction(true),
            GameAction.AddCounterDefinition(definition.copy(id = EntityId("custom"), role = CounterRole.OTHER)),
            GameAction.UpdateCounterDefinition(definition), GameAction.EliminateSeat(seatId, SeatExitKind.CONFIRMED_LOSS, "Confirmed", loss),
            GameAction.RestoreSeat(seatId, "Effect permits restore"), GameAction.DismissLossCandidate(loss, seatId, "Effect prevents loss"),
            GameAction.SetMonarch(null), GameAction.SetInitiative(seatId, "Synthetic initiative"), GameAction.SetDayNight(DayNightState.DAY),
            GameAction.SetBlessing(seatId, true), GameAction.SetSeatAppearance(seatId, appearance),
            GameAction.SetGamePresentation(GamePresentation(GameLayoutMode.TABLETOP, null, false)),
            GameAction.FinishWithOutcomes(outcomes, "Confirmed finish"), GameAction.PassClock(seatId), GameAction.SetElapsedPaused(false),
            GameAction.AcknowledgeGameAlert(EntityId("alert")), GameAction.SetAlertPreferences(GameAlertPreferences(false, true, true)))
        for (action in actions) assertEquals(event(action), success(codec.decodeEvent(success(codec.encodeEvent(event(action))))))
        val original = snapshot(); val result = (original.result as Knowledge.Known).value.copy(seatResults = outcomes)
        val explicit = original.copy(result = Knowledge.Known(result))
        assertEquals(explicit, success(codec.decodeSnapshot(success(codec.encodeSnapshot(explicit)))))
    }
    @Test fun frozenFormatOneSnapshotRetainsAllMetadataAndNoInventedOutcomes() {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/life-codec-v1-snapshot.bin")).use { it.readBytes() }
        assertEquals(1, ByteBuffer.wrap(bytes, 4, 4).int)
        val decoded = success(codec.decodeSnapshot(SnapshotBytes(bytes)))
        assertEquals(snapshot(), decoded)
        assertTrue((decoded.result as Knowledge.Known).value.seatResults.isEmpty())
        val current = success(codec.encodeSnapshot(decoded)).copyBytes()
        assertEquals(2, ByteBuffer.wrap(current, 4, 4).int)
        assertEquals(decoded, success(codec.decodeSnapshot(SnapshotBytes(current))))
    }
    @Test fun creationInputHasStableExactCanonicalBytesAndFormatTwoOnly() {
        val original = creation(); val encoded = success(codec.encodeCreation(original))
        assertEquals(original, success(codec.decodeCreation(encoded)))
        assertEquals(encoded, success(codec.encodeCreation(original)))
        assertNotEquals(encoded, success(codec.encodeCreation(original.copy(operationId = EntityId("other-operation")))))
        failure(codec.decodeCreation(SnapshotBytes(encoded.copyBytes().also { it[7] = 1 })), FailureCode.CORRUPT_DATA)
    }
    @Test fun legacyHeaderCannotIntroduceANewActionTag() {
        val encoded = success(codec.encodeEvent(event(GameAction.SetAdjustmentStep(5)))).copyBytes()
        failure(codec.decodeEvent(SnapshotBytes(encoded.also { it[7] = 1 })), FailureCode.CORRUPT_DATA)
    }
    @Test fun versionTwoArchiveHonorsEachNestedFormatOneSnapshotHeader() {
        val before = requireNotNull(javaClass.getResourceAsStream("/life-codec-v1-snapshot.bin")).use { it.readBytes() }
        val after = before.copyOf()
        val providerLength = ByteBuffer.wrap(after, 9, 4).int
        val gameLengthOffset = 13 + providerLength
        val gameLength = ByteBuffer.wrap(after, gameLengthOffset, 4).int
        val revisionOffset = gameLengthOffset + 4 + gameLength + 4
        ByteBuffer.wrap(after, revisionOffset, 8).putLong(8)
        val knownLastSix = byteArrayOf(1) + ByteBuffer.allocate(8).putLong(6).array()
        val occurrences = (0..after.size - knownLastSix.size).filter { start ->
            knownLastSix.indices.all { after[start + it] == knownLastSix[it] }
        }
        assertEquals("Frozen fixture has one known lastEventId6", 1, occurrences.size)
        ByteBuffer.wrap(after, occurrences.single() + 1, 8).putLong(8)
        val writer = LifeWriter(16 * 1024 * 1024)
        writer.int(0x434c4946); writer.int(2); writer.byte(4); writer.int(1); writer.v(eventWire, event())
        writer.long(8); writer.blob(before, SNAPSHOT_LIMIT); writer.blob(after, SNAPSHOT_LIMIT)
        writer.v(durationWire, 3.seconds); writer.v(timestampWire, stamp); writer.v(entityWire, EntityId("original-process"))
        val archive = success(codec.decodeArchive(SnapshotBytes(writer.finish())))
        assertEquals(snapshot(), archive.provenance.before)
        assertEquals(Revision(8), archive.provenance.afterAction.revision)
        assertTrue((archive.provenance.afterAction.result as Knowledge.Known).value.seatResults.isEmpty())
    }

    private fun versionTwoGoldenCases(): List<Pair<String, (SnapshotBytes) -> Unit>> {
        val cases = ArrayList<Pair<String, (SnapshotBytes) -> Unit>>()
        fun <T> add(name: String, value: T, decode: (SnapshotBytes) -> Outcome<T>) {
            cases += name to { bytes -> assertEquals(value, success(decode(bytes))) }
        }
        val before = snapshot()
        add("snapshot", before, codec::decodeSnapshot)
        add("creation", creation(), codec::decodeCreation)
        add("checkpoint", GameCheckpoint(before, Knowledge.Known(6L), EntityId("golden-process")), codec::decodeCheckpoint)
        add("profile", PlayerProfileDraft("Synthetic profile", 20, SnapshotList.of(definition), appearance, before.seats.single().commanders, 5), codec::decodeProfile)
        val id = EntityId("request")
        val random = listOf(
            Triple("dice", RandomRequest.Dice(id, 2, 6), RecordedRandomValue.Dice(6, SnapshotList.of(2,3), 5, 2, 0, 0)),
            Triple("continue", RandomRequest.ContinueDice(id, 1), RecordedRandomValue.Dice(6, SnapshotList.of(6), 6, 10_001, 1, 0)),
            Triple("coin", RandomRequest.Coin(id), RecordedRandomValue.Coin(true)),
            Triple("pick", RandomRequest.SeatPick(id, seatId, false), RecordedRandomValue.SeatPick(seatId, SnapshotList.of(seatId))),
            Triple("order", RandomRequest.SeatOrder(id), RecordedRandomValue.SeatOrder(SnapshotList.of(seatId))))
        for ((name, request, value) in random) {
            val original = event(GameAction.RequestRandom(request)).copy(randomResults = SnapshotList.of(RecordedRandomResult(id, stamp, value)))
            add("event-$name", original, codec::decodeEvent)
            val after = before.copy(revision = Revision(8), lastEventId = Knowledge.Known(original.id))
            add("archive-$name", GameEventArchive(1, original, GameActionProvenance(original.id, before, after,
                GameTimeObservation(1.seconds, stamp, EntityId("golden-process")))), codec::decodeArchive)
        }
        return cases
    }
    @Test fun actualVersionTwoGoldenBytesStillDecode() {
        for ((name, validate) in versionTwoGoldenCases()) {
            val bytes = requireNotNull(javaClass.getResourceAsStream("/life-codec-v2/$name.bin")) { "Missing pinned v2 fixture $name" }.use { it.readBytes() }
            assertEquals(2, ByteBuffer.wrap(bytes).getInt(4))
            validate(SnapshotBytes(bytes))
        }
    }
}
