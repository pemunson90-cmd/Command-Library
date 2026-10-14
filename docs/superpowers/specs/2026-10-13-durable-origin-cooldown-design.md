# Durable origin cooldown

Pat requires continuous local-first progress with checkpoints, stopping only for missing research/questions. Scope is the already-approved source/transport acquisition safeguard, not production registration. Retained PERSONAL-SOURCE-REVIEW requires Scryfall429 at least30s and longer Retry-After, explicit retries, no automatic request replay. Current LocalAcquisition loses its wait at processdeath and mislabels >24h/infinite waits as SOURCE_MISMATCH. The existing Clock interface provides UTC and process monotonic duration but no authenticated wallclock/boot epoch.

## Chosen policy

Persist a cooldown interval independently of jobs for each canonical validated HTTPS origin. A fresh engine replays the entire retained interval using monotonic time. This deliberately permits an extra full wait after restart; UTC forward/rollback cannot shorten it. Do not invent a secure UTC or boot identity. Finite >24h or indefinite durations fail the current execution with TRANSPORT immediately, retaining the origin block. A finite local monotonic deadline eventually elapses; repeated restarts can repeat the wait. Indefinite remains fail closed. No public state/API change, automaticretry, implicit refresh, generic user bypass or source authority from caller URLs.

## Private persistence

Add Room schema5 acquisition_origin_cooldown: origin TEXT NOTNULL primarykey; revision INTEGER NOTNULL; interval_seconds INTEGER nullable; interval_nano INTEGER nullable. Both null means indefinite; mixednull, negative interval, invalid nanos, revision<1 are corrupt and must deny. Positive finite interval is at least30s; seconds=0,nanos=0 is an explicit cleared tombstone. Tombstones retain the last generation and are never deleted. No job FK: deleting/recovering a job must not clear provider throttling. Canonical key is https://lowercase-host:effective-port, bounded512bytes, from an already-validated actual wire target. Exact origin scope matches existing transport; English search jobs all use api.scryfall.com. This is not a new shared wildcard/cross-origin provider policy.

Additive Migration4To5 creates only this table. Preserve all17 schema4entities, SQL and FTS. Root registers entity/migration/version and retains actual KSP5export. Old migration tests expecting current4 update to current5 without weakening their historical preservation checks. Life draft's separate future migration must use next available version6, never overwrite5.

## Internal helper

LocalOriginCooldown(Database,Clock), methods suspend record(ResourceUri,Knowledge<Duration>,CancelToken):Outcome<Unit> and remaining(ResourceUri,CancelToken):Outcome<Duration>. Internal coroutine mutex protects anchors and mutation, no monitor held across suspend. No NetworkClient/import of implementation modules.

record atomically reads existing row, stores max(existing,max(30s,Retry-After)) with revision+1 even for a shorter later429, and checks mutation affectedrows. Saturated/unrepresentable durations are indefinite. Revision exhaustion fails closed. Each acknowledged revision anchors the full interval at monotonicNow; remaining can seed lazily after restart. Expired finite row becomes a zero-interval tombstone only by revisionCAS and advances revision on that mutation; stale clear reloads/rechecks the newer interval. New429 advances the retained revision rather than reusing1, avoiding stale-anchor/clear ABA across independent helpers. A tombstone returns zero without an active anchor. Negative/nonfinite monotonic elapsed denies, never clears. Origin corruption/storage query failures return typed failure; never absent-success. Failed record also latches an in-process indefinite deny for that origin, preventing a transient write failure from authorizing a later enqueue. This latch cannot persist across an unavailable-disk restart; report storage failure honestly.

## Engine integration

Inside the metadata callback, immediately after validating genuine429 metadata/actualtarget and BEFORE throwing RateLimited, commit cooldown under the existing consent gate. NonCancellable covers gate acquisition plus persistence using NO_CANCEL; later transfer cleanup must not replace an unpersisted observation with cancellation/WAITING_CONSENT. Outer handleRateLimit only waits/settles an already persisted observation. No artifact content accepted. Preserve conservative transfer reservations/attempt accounting. No database operation is inserted inside synchronous physicalenqueue.

Both remote and English page paths call new handleRateLimit(current owned row,metadata,cancel). For finite<=24h, retain current explicit wait behavior but each <=100ms tick reloads current job/consent under gate; revoked consent/network/WiFi settles existing WAITING_* promptly. Wait releases gate between ticks. Longer/indefinite rows immediately yield TRANSPORT, not SOURCE_MISMATCH. On finite expiry, current429 execution still becomes FAILED/TRANSPORT and does not retry automatically.

At authorizeWireDispatch, after exact registeredcontext/currentjob/consent/target checks and before synchronous enqueue, query remaining under shared gate. A nonzero/infinite cooldown denies this physicaldispatch as TRANSPORT without waiting under gate/searchStarts. A user can explicitly resume later; no new request is issued. Recovery/job removal leaves active rows and tombstones intact; lazy monotonic anchoring suffices and avoids unbounded recovery table scans.

Existing network limiter remains defense in depth. Factory remains unregistered; this does not resolve notice/disk reserve/stagingWAL/scheduler/removal/install fencing or prove actual OkHttp-engine composition.

## Evidence and tests

Use existing Clock/Database/SqlRequest/DbTransaction APIs and bundled SQLite2.7.1; no newdependency/SDKfeature. Actual local Gradle9.6/AGP9.4.1/Kotlin2.2.10/min23 environment is available. Pure JVM helper tests should use real bundledSQLite reopen, not assert SQLfake storage truth. Existing engine virtualtime fixture remains synthetic network/SQL characterization and must be labeled.

Tests-first: root engine behavior RED for >24h/infinite TRANSPORT and prompt consent-revocation. Helper APIabsence/compiler RED distinct from meaningful behavioral RED; establish stub oldmissing behavior before implementation if needed. Helper actualSQLite tests: minimum/longer/infinite, successive shorter/longer updates/revisions, originisolation, freshhelper/reopen replaysfullinterval, UTCjumps do notshorten, expiry/recreation with staleCAS acrossindependenthelpers cannotclear a newer429, corruptrows, canceled operations and transientwritefail deny. Native realRoom migration/reopen preserves17legacyentities and origin row lifetime; compile locally, execute next coherent device batch. No native claim from testAPKcompilation.

## Honest boundary

A process may die after receiving an external429 but before its durable commit. Eliminating that response/commit crashwindow requires a transport-level durable handoff beyond this narrowpacket. Cancellation is bridged once metadata reaches the engine, but processdeath/diskfailure cannot be called solved. Independent Astra architecture/durability review before source changes and exact-source review after localverification remain mandatory. User delegated routine design choices; no redundant permission gate.

Independent Astra design corrections adopted: durable tombstones prevent revision ABA; record inside validated metadata callback before RateLimited with NonCancellable gate acquisition+NO_CANCEL persistence. Lock order consent gate → helper coroutine mutex → database; no helper operation calls back into consent. Initial design verdict BLOCK now resubmitted with both corrections.

Revised design independently Astra-approved for implementation after both corrections. Exact source and actual migration/runtime evidence remain required; no reviewed waiver of other production gates.

Follow-on source-approved pre-admission: known originblock is checked under consentgate before restart/jobmutation, skipping VERIFYING because it requires no request. Keep laterpolicy and finalwireguard. ActualRED2assertions confirmed start/resume mutatedduringknownblock; amendedengine SHA55949c324e945e62c19cf841e1003fd40c229d5515891ef8bd9c390ada74a6bd Astra-approved. Guarantee is limited to cooldownknownatprecheck, not subsequent responses/redirecttarget. Final localcoherent569JVM allpass; twoRoomnativecasesnot_run.
