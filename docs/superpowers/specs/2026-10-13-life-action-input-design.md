# Remaining life action inputs and codec2

Implement the remaining typed requests already required by the reviewed life persistence design. Independent recovery design review approved this bounded packet. It declares inputs and preserves replay payloads; reducer/service behavior and UI remain later work.

## Model shapes

Append SetCounter, SetAdjustmentStep, SetCommanderLifeDeduction, AddCounterDefinition, UpdateCounterDefinition, EliminateSeat, RestoreSeat, DismissLossCandidate, SetMonarch, SetInitiative, SetDayNight, SetBlessing, SetSeatAppearance, SetGamePresentation, FinishWithOutcomes, PassClock, SetElapsedPaused, AcknowledgeGameAlert and SetAlertPreferences to GameAction. Signatures follow the parent persistence design. Exact counter deltas, role/ID permissions, group policy, game membership, clock eligibility and occurrence lineage are checked by the future service, not fabricated by declarations.

Adjustment step is positive. Elimination/restore/dismiss/finish reasons are nonblank. Explicit finish has a nonempty list of unique seat outcomes and cannot mix WIN and DRAW. LOSS does not imply an eliminated participation record, so optional exit reasons remain optional.

Append GameResult.seatResults default-empty. Empty retains historical winner-only data without inventing losses/exits. When present, outcomes have unique seat IDs, winners are unique and match the WIN IDs exactly; DRAW cannot coexist with WIN. The service must check exactly one outcome per actual seat.

## Codec compatibility and idempotence

Writers emit binary format2. Readers accept explicit formats1 and2 only, reject newer values, and reconstruct format1 results with empty outcomes. Each nested framed snapshot uses its own header version. Existing action tags1–11 remain unchanged; append new tags12–30. Format1 cannot contain those new tags. Add a format2-only GameCreation root6 containing exact operationId/settings/seats/clock, bounded4MiB. Canonical creation bytes contain no service-generated timestamps or identities.

A frozen3133-byte synthetic format1 snapshot captured from the actual accepted codec1 is retained as a test resource, SHA256 `30399a4a83a2395e16fe3d3b1576937710f07f9b793818870cc174f35da1cd10`. Do not regenerate it using the current encoder. Tests prove all metadata remains, old results do not gain invented outcomes, new result/action/creation roots roundtrip and nested old snapshots keep their own interpretation. Bounds and strict malformed-data behavior remain unchanged.

No schema change follows from this wire-format extension; existing schema6 stores versioned opaque bytes. No published version1 live GameService existed. The legacy import/service policy remains separate from structural decoding.
