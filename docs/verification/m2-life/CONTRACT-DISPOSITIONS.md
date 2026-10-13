# Life handoff contract dispositions

Root disposition of requests 1–14 in `coordination/handoffs/cc-m2-life-domain-01.md`. Product rulings are D-25 in `docs/gauntlet/DECISIONS.md`; other choices below are delegated technical decisions. These are implementation obligations, not claims that the public contracts or service already implement them.

| Request | Decision and next implementation obligation |
|---|---|
| 1 | Accept durable counter-role identity. Persist explicit LIFE, COMMANDER_DAMAGE, COMMANDER_TAX, OTHER roles; validate bindings, never infer roles from labels. |
| 2 | Retain both fields and require initialLife to equal the bound life definition's initial value at creation/import/resume. |
| 3 | D-25: ask before eliminating because effects can prevent loss. Persist confirmed eliminate/restore actions and reasons; log and undo them while preserving counters/history. Loss and timeout reports remain candidates. |
| 4 | Retain per-seat/per-counter hiding covering its slots. No extra per-slot action is required for this scope. |
| 5 | Pat delegated semantics. Root defines SHARED as one global game countdown, charged once while running. Implement its separate bank and tests before exposing the mode. Per-seat CHESS remains available. |
| 6 | Retain settings/state clock modes and enforce equality at creation/import/resume. |
| 7 | Add durable game and turn elapsed timers with explicit settle, pause and restart behavior, separate from countdown banks. |
| 8 | Add a clock-priority pass distinct from SwitchTurn: no turn increment and no PER_TURN refill. Distinguish turn owner from running clock seat. |
| 9 | Add durable alert acknowledgement and retain it through history/undo as specified by the eventual event model. |
| 10 | Persist settle checkpoints; recover a live clock paused once per new process session. Never charge unknown wall-time gaps. Persist immutable before/after action snapshots and clock reading so undo uses the corrected provenance overload after checkpoints/restart. |
| 11 | Add typed, immutable random-result events with dice faces/sides/sum, coin result, seat/eligible set, event time and batch/remaining relationships. Replay recorded results without drawing again. |
| 12 | D-25: follow CR 903.10a, including damage from the victim's own commander controlled by someone else. Keep each commander slot separate. |
| 13 | Finish settles and pauses the clock in the same transaction as the event, snapshot and result. Completed snapshots cannot retain a running clock. |
| 14 | Persist fired occurrence IDs and alert/event state atomically; key persisted alerts by game and occurrence. Delivery retries must be idempotent. |

Next life task must implement reviewed public contracts, then real GameService/storage and UI under a new ownership lock. The present domain remains internal and unbound; it does not satisfy all G01–G13 by itself.
