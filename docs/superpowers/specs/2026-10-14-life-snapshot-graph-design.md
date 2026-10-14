# Structural snapshot graph guard

Scope: cx-m2-life-snapshot-graph-01, root-owned feature/life. Continuous execution/local-first authorization is recorded in CONTINUE.md. No schema, external source acquisition, DI or UI change. This guard is a prerequisite for later service work, not an operational resume certificate.

## Design

Use an internal pure SnapshotGraphValidator.validate(GameSnapshot): Outcome<CounterRoles>. Derive roles only from explicit CounterRole metadata: exactly one LIFE, at most one COMMANDER_DAMAGE and COMMANDER_TAX. Legacy OTHER-only definitions cannot be guessed from labels and are rejected as structurally incomplete for this guard; the codec continues preserving them. Reuse CounterBoard through a small CounterReducer.validateState entry point; no counter policy duplication. Fail contradictory graphs with CORRUPT_DATA; unsupported snapshot model versions with UNSUPPORTED. No mutation or I/O.

SeatIndex validates 1–6 seats, unique seat/order/commander identities. Do not add a contiguous-order restriction without a contract requiring it. Optional active turn seat must exist. Eliminated seats and their retained counters/designations remain valid. Historical loss evidence is not matched against mutable current definitions.

Clock mode must match settings. NONE has no banks/shared/active/anchor and is paused. SHARED has a shared bank and no seat banks; CHESS/PER_TURN have exactly one bank per roster seat and no shared bank. All bank values are finite, initial nonnegative; finite negative remaining is retained only for ALERT overtime and not silently clamped. PAUSE/ELIMINATE current banks cannot be negative; running active seat/shared banks must be positive (inactive zero seat banks remain valid). Anchors, including paused retained anchors, require finite nonnegative monotonic elapsed. Optional clock owner must exist; running seat clocks need owner and anchor, running shared needs anchor and no seat owner; a paused retained shared owner remains representable. Running clock owner cannot be eliminated. Clock owner and turn owner need not coincide. Fired IDs are unique but need not belong to the current alert projection (durable ledger history is separate).

Elapsed anchors have the same scalar checks. Running elapsed requires an anchor and enabled elapsed settings; turn lineage Unknown/Absent is preserved and does not authorize charging. COMPLETED means both countdown and elapsed paused. Do not impose status PAUSED => both paused without established reducer/service semantics. No UTC ordering is inferred across clock changes; no turnElapsed <= gameElapsed constraint because undo/lineage accounting is separate.

Designation holders/blessed seats must exist; blessing IDs unique. Alerts have unique IDs, same game, and seat targets exist. No requirement that alert timestamp precedes acknowledgment or remains on the current clock fired-ID list.

Known results match game, reference roster seats, and revision <= snapshot revision. Model constructor already checks duplicate/coherent outcomes; incomplete legacy outcomes remain representable. COMPLETED cannot carry a known retracted result; a known nonretracted result requires COMPLETED. A known result's finishedAt must agree with known endedAt; Unknown/Absent result/end metadata remains preserved for legacy reading. Event IDs, when known, are nonnegative; actual event/checkpoint/undo lineage requires the later database guard.

Variants have unique definitions/states with exact coverage and matching versions, unique declared zone/card/action/phase IDs, defined action zone/card references, exact state zone coverage/capacity, known card references in all storage locations/properties, unique property and action-count keys, known action-count IDs within their declared maximumPerPhase and declared current phase when phases exist. Repeated card identities across locations are not prohibited: multiplicity/automation and authentic source semantics are engine responsibilities.

## Alternatives

Store-only inline validation duplicates domain rules and is hard to test. Reusing SeatClockReducer directly incorrectly rejects SHARED and misses NONE coherence. Prefer the focused pure guard and counter reuse. Keep storage integration separate until pure behavior/review passes; then invoke the guard on decoded current snapshots and new initialized state, mapping caller initialization errors to INVALID_INPUT. Do not strengthen codec decoding itself: historical payload preservation remains separate from current graph usability.

## Verification and limits

Observe API-absence and adversarial behavior RED, then pure JVM GREEN. Cover valid legacy unknown metadata, negative life/overtime, partners/own commander damage, eliminated retained state, independent owner, paused anchor, shared running and wall-clock reversal; reject role ambiguity, duplicate/dangling keys, clock/elapsed contradictions, invalid result/alerts/variants. Run actual bundled SQLite store regressions when integrating; run coherent local check/debug build and continuity checks before checkpoint. Independent design/source review required. No Room/concurrency/source authority/live timing/final app acceptance claim.

Independent design review: life_contract_review APPROVE direction; incorporated maximumPerPhase count bound before implementation.

Review refinement: retained shared alert occurrences do not require current SHARED mode; undeclared phase vocabulary does not force empty phase. Enforce these at new action creation only if required. This avoids erasing valid historical/configuration metadata.

Count interpretation: actionCounts is current-phase accounting, matching maximumPerPhase; later engine must reset on phase transitions. No implemented accepted engine defines cumulative semantics. A future cumulative import needs explicit conversion, not silent bypass.

Pure exact-source review: APPROVE, no blocking findings. Local feature:life:test actual5XML/94tests0failure/error/skip after correcting cross-module smartcasts. Storage tests not yet executed (syntax corrected); integration remains pending.

Mutation prerequisite policy addendum: original accepted persistence Timing requires nonALERT clamp/pause and no running shared seatowner. Root found missing guards after event-reader completion; independent boundary/exact-source review APPROVE; new2pure tests observed RED96tests/2fail56s before minimal fix. Added actualSQLite currentcheckpoint corruption cases. Actual local96life/83storage GREEN92s, final wholecheck/debug100s71XML716JVM0failure/error/skip, alignment pass. No archival codec rewrite.
