package com.architectpsg.commandlibrary.core.model

import kotlinx.coroutines.flow.Flow

/** Immutable reusable setup; applying it must allocate fresh per-game seat/slot identities. */
public data class PlayerProfileDraft(
    public val name: String,
    public val preferredLife: Int,
    public val counterDefinitions: SnapshotList<CounterDefinition>,
    public val appearance: SeatAppearance,
    public val commanders: SnapshotList<CommanderSlot>,
    public val adjustmentStep: Int = 1,
) {
    init {
        require(adjustmentStep > 0) { "Adjustment step must be positive" }
        require(commanders.map { it.id }.toSet().size == commanders.size) { "Profile commander-slot identities must be unique" }
        require(counterDefinitions.map { it.id }.toSet().size == counterDefinitions.size) { "Profile counter-definition identities must be unique" }
        require(counterDefinitions.count { it.role == CounterRole.LIFE } == 1) { "Exactly one explicit LIFE definition is required" }
        require(counterDefinitions.count { it.role == CounterRole.COMMANDER_DAMAGE } <= 1) { "Commander-damage role must be unique" }
        require(counterDefinitions.count { it.role == CounterRole.COMMANDER_TAX } <= 1) { "Commander-tax role must be unique" }
        require(counterDefinitions.single { it.role == CounterRole.LIFE }.initial == preferredLife) { "LIFE initial must agree with profile preferred life" }
    }
}
public data class PlayerProfileSnapshot(public val id: EntityId, public val revision: Revision, public val value: PlayerProfileDraft)

/** Bounds describe [fromInclusive, untilExclusive); query matching/cursors belong to the repository. */
public data class GameHistoryFilter(
    public val playerName: String? = null,
    public val deckId: DeckId? = null,
    public val deckName: String? = null,
    public val fromInclusive: UtcTimestamp? = null,
    public val untilExclusive: UtcTimestamp? = null,
) {
    init {
        require(fromInclusive == null || untilExclusive == null ||
            fromInclusive.epochSeconds < untilExclusive.epochSeconds ||
            (fromInclusive.epochSeconds == untilExclusive.epochSeconds && fromInclusive.nanoAdjustment <= untilExclusive.nanoAdjustment)) {
            "History bounds must not be inverted"
        }
    }
}
public data class GameHistoryEntry(
    public val gameId: GameId,
    public val revision: Revision,
    public val startedAt: UtcTimestamp,
    public val endedAt: Knowledge<UtcTimestamp>,
    public val seats: SnapshotList<Seat>,
    public val result: Knowledge<GameResult>,
)
public data class GameHistoryPage(public val games: Page<GameHistoryEntry>, public val totalMatches: Long) {
    init { require(totalMatches >= games.items.size.toLong()) { "History total cannot be negative or smaller than the returned page" } }
}

/** Profile deletion must preserve game history and art still referenced by games. */
public interface PlayerProfileRepository {
    public suspend fun create(value: PlayerProfileDraft, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    public suspend fun read(id: EntityId, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    public suspend fun update(id: EntityId, value: PlayerProfileDraft, expected: Revision, cancel: CancelToken): Outcome<PlayerProfileSnapshot>
    public suspend fun list(page: PageRequest, cancel: CancelToken): Outcome<Page<PlayerProfileSnapshot>>
    public suspend fun delete(id: EntityId, expected: Revision, cancel: CancelToken): Outcome<Unit>
}
/** Stronger typed persistence port; no implementation or DI registration is implied by its declaration. */
public interface PersistedGameService : GameService {
    public suspend fun create(input: GameCreation, cancel: CancelToken): Outcome<GameSnapshot>
    public suspend fun read(id: GameId, cancel: CancelToken): Outcome<GameSnapshot>
    public suspend fun undo(id: GameId, expected: Revision, cancel: CancelToken): Outcome<GameSnapshot>
    public suspend fun events(id: GameId, page: PageRequest, cancel: CancelToken): Outcome<Page<GameEvent>>
    public suspend fun history(filter: GameHistoryFilter, page: PageRequest, cancel: CancelToken): Outcome<GameHistoryPage>
    public fun observe(id: GameId, cancel: CancelToken): Flow<Outcome<GameSnapshot>>
}
