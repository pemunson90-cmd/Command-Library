# Android life platform sources — reviewed implementation

Pat delegates routine implementation and requests continuous local-first work through completion; no checkpoint stop or unfinished APK delivery. Root retains active development/coordination/final merge. This bounded unit provides real Android sources consumed by an explicit unregistered integration probe. It does not register GameService or weaken retained source/variant prerequisites.

## Accepted design

App-owned AndroidLifeClock implements Clock: UTC uses the existing API23-safe epoch converter, monotonic uses SystemClock.elapsedRealtimeNanos().nanoseconds, including device sleep. Existing catalog SystemUtcClock remains unchanged. ProcessLifeIdentity is a process singleton immutable random UUID EntityId, never persisted or regenerated per graph/service/game. Restart changes identity; existing life recovery pauses ambiguous intervals. SystemGameRandom implements RandomSource using one java.security.SecureRandom.nextInt(positiveBound), with JDK rejection sampling rather than biased modulo. IDs retain RandomUuidIdSource.

No AppGraph/ShellPorts/production module dependency, core port, schema, codec, CI, navigation, cadence or named-source change. Existing pinned JUnit/Robolectric app test setup and test-only app androidTest→life edge support validation. One actual Room integration probe explicitly constructs an UNREGISTERED service over a unique isolated database and real providers; it never installs a partial gameplay port in the app.

## Review and verification boundary

Independent life_contract_review architecture/exact provider source and corrected native-probe review APPROVE. Four provider tests use Robolectric: elapsed time includes simulated deep sleep while uptime does not, UTC wall sampling and pre-epoch floor conversion, stable UUID reads, random positive-bound ranges and rejection of nonpositive bounds. Bounds tests do not statistically establish uniformity. Android shadow sleep is not hardware deep-sleep proof.

The actual Android probe creates/reads a synthetic game, records dice using real random source, undoes without redraw, closes/reopens its own database and checks exact recorded outcome. Undo legitimately reanchors elapsed timing even when previously paused: assert unchanged totals/lineage/paused state and correct process identity/finite new anchor, not whole elapsed DTO equality. Reviewer caught and root corrected that test expectation; no production timing change.

Test-first API-absence RED293s, expected unresolved three providers. Implemented sources; first candidate compiled provider/native probe but failed558s before test methods: SDK36 sandbox requiresJava21. Corrected test to SDK34 matching existing shell/JDK17; focused rerun pending. Whole local gate must pass before accepted completion. Native execution remains not_run, including OS process-kill/physical sleep/API23 runtime. Detailed evidence will record actual results.

## Deferred binding remains HOLD

An earlier draft proposed AppGraph LifePorts.games registration. Reviewer found explicit gates in cx-m2-life-functional-01 and life-action-engine/life-sqlite-commands designs: do not register operational GameService while retained baseline variant/source families remain unsupported. Root accepted HOLD and removed that proposal from this unit. No unconsumed ReadyGraph/factory or weaker readiness rule was introduced. Independent complete PlayerProfileRepository CRUD can be assembled separately; it does not register GameService.
