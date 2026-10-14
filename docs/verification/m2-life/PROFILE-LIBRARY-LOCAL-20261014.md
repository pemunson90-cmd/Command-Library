# Player profile library — local progress/evidence2026-10-14

Architecture/composition independently APPROVE: complete PlayerProfileRepository CRUD is independent of the retained GameService binding gate. Full gameplay/source/variant/advanced profile editor/application acceptance remains incomplete. Root implements the reviewed plan inline and retains active ownership/final merge. No APK delivery.

## Current status

Controller, accessible profile CRUD UI/navigation and actual AppGraph profile repository are implemented and independently source-approved. Focused18controller/UI tests and full98shell tests pass with0failures/errors/skips; shelllint passes. Actual shared AppGraph/Room integration test passes21.323s (containing60s/1m command),1test0failures/errors/skips. It exercises real cached Application ports/SQLite file, exact profile create/read/list/update, stale CAS rejection with unchanged readback, delete/missing read, and ownrow-only cleanup. No new fake database or production seam. GameService remains unbound. Whole-project check/debug plus app/shell/storage testAPK compilation passed616s/10m16s,441tasks59executed382up-to-date. Current99XML report973JVM tests,0failures/errors/skips (includes unchanged cached modules); app5/2XML,shell98/13XML,storage198/21XML. All4APK16KiB ZIP alignment checks exit0. New native/physical execution remains not_run and no finished APK is delivered.

The following sections retain chronological development/diagnostic evidence, not current pending actions.

## Task1: scoped controller verified

ProfilesController implements immutable drafts, retained exact original snapshot/revision, basic edits preserving all advanced fields and replacing only LIFE.initial, signed Int life plus retained counter bounds, positive adjustment step, exact Unicode name, explicit stale reload, actual cursor pagination with generation/job cancellation and revision-aware duplicate merging, confirmation-gated delete, typed Problem mapping through existing callPort, cancellation and double-submit protection. It owns no service/database or detached job. Task1 was verified before screen/app wiring.

APIabsenceRED131s (missing ProfilesController/editor properties). Seven focused tests GREEN88s. Full feature:shell:testDebugUnitTest GREEN290s/4m50s:87tests12XML,0failures/errors/skips,34tasks1executed33up-to-date. Actual advanced fixtures include custom counter metadata, commander/card identity, color/orientation, and exact old revision. Delayed test-only page deliberately ignores cancellation to exercise stale generation defense; all production ports remain real when assembled. UI tests use test-only semantic fixtures, not SQLite proof. Existing actual SQLite profile/facade evidence remains separate.

At the task1 checkpoint UI/app assembly had not yet been completed. See Current status above for the implemented, source-approved slice; whole-app gate is still pending; runtime/physical evidence not_run for this new feature. Commands/spec: docs/superpowers/plans/2026-10-14-life-profile-library.md and corresponding spec.


## Task2 candidate and review in progress

APIabsence UI RED125s preceded implementation. First7-test UI run completed9m2s with6pass/1failure; isolated stale-edit rerun completed7m38s with1failure. Both failed during initial ShellUiHarness.setContent with AppNotIdleException, before stale actions. A captured active SDK34 thread was loading/instrumenting PhoneWindow after185s; this is not evidence that stale behavior passed or failed. A host-only300s Espresso master-idle budget with restoration to60s is being verified; device timeout and assertions stay unchanged.

Independent read-only review confirmed field preservation/revision/paging/optionalport boundaries, and found two corrections: allow an unchanged legacy blank name on numeric-only edit; allow dismissing a busy modal delete while its operation continues, with explicit copy/list progress. Regression tests added before those fixes. Navigation during a gated save must return to actual committed state; leaving a destination does not cancel the shared controller scope. No rollback promise.

Task3 shared AndroidJUnit4 actual Application/Room CRUD/cachedport probe and reuse of storage host-JNI task have been added before wiring. Initial app test attempt failed before tests after10m32s due overlapping Gradle Kotlin script-cache lock, not expected missing-port behavior; remaining Gradle builds run serially. No JNI/app runtime pass inferred. Final source review, whole local checks and affected APK compile/ZIP gates pending. GameService remains unbound; no schema/codec/toolchain/CI change.

Review regression RED observed:9controller tests,2expectedfailures (blank legacy name and pending delete dismissal),70s. Fixes now independently sourceAPPROVE; combined9controller+9UI GREEN run pending. Subsequent app configuration failed eager tasklookup3m36s; actualoutput lookup deferred to execution with declared string taskdependency, no JNI success claim.

Combined candidate completed212s/3m32s:18tests,17pass/1failure,0errors/skips; all9controller tests passed,8/9UI passed including actual stale/reload and both gated navigation/delete-close cases. Remaining busy-save assertion expected1 row but observed0 because waitForText matched the still-visible editor draft before write completion. Test synchronization strengthened across saves: wait for controller editorclosed/notbusy/listfinished before checking actual displayed saved name. Rerun in progress; this does not alter production or weaken assertions.

Corrected focused GREEN completed186s/3m6s:18tests/2XML (9controller+9UI),0failures/errors/skips,34tasks2executed32cached. Exact source corrections independently APPROVE. Full shell test/lint plus expected missingprofile AppGraph test now running serially; app profile assembly/whole gate remain pending.

First fullshell candidate gate142s/2m22s:98tests,96pass/2fail,0errors/skips. Bothintegration expectations identified: ExternalizedStringsTest flags adjacent same-line testtag constants as apparent words; constants separated oneperline without changing detector/resources. Existing compactnavigation test excludedonlyoptionalRules and assumed newProfiles alwayspresent; updatedtoassert bothoptional capabilities absent when noport. New9UI/9controller remainedpassed. Fullshell/lint rerun precedes app test; firstfullshell failure prevented appbehaviorRED/lint execution, so no inference of app/JNI success.

Corrected fullshell98tests13XML0failures/errors/skips verified in current-run XML timestamps15:16:05–15:19:03. Containing417s/6m57s command then observed intended AppGraph RED:1test35.436s, IllegalArgumentException “Actual AppGraph must expose the complete profile repository” after real application graph/database opened. This is expected missingbinding, not a completed CRUD pass. Lint analyzed sources but aggregate report task was not reached before app failure; lint completion still pending.

Task3 production assembly now added: app main life dependency, exact SqlPlayerProfileRepository over same opened DB, LifePayloadCodec/SystemUtcClock/RandomUuidIdSource, named optional ShellPorts capability, existing mutex/success cache untouched. No GameService construction/binding/source-gate relaxation. Focused appCRUD GREEN+shelllint and final exact-source review in progress; whole checks/APKs next.

Final exact-source/publicAPI/UI/durability/composition review APPROVE after actual AppGraph wiring. Focused appprobe1testGREEN21.323s, containing60s command also completed shelllintDebug successfully. Whole check/debug/app+shell+storage testAPK build is active; final XML/APK alignment evidence pending. This app probe is actual hostRoom proof, not device execution or process-restart acceptance.


## Final local gate and packaged evidence

Command: `bash android-app/gradlew -p android-app --daemon --console=plain check :app:assembleDebug :app:assembleDebugAndroidTest :feature:shell:assembleDebugAndroidTest :data:storage:assembleDebugAndroidTest` with accepted JDK17/SDK/Gradle cache. BUILD SUCCESSFUL616s/10m16s;441tasks59executed382up-to-date. Actual99JUnitXML report973tests,0failures/errors/skips. App5tests2XML (platform4+actualgraph1), shell98tests13XML, storage198tests21XML. Existing cached modules are counted as reported, not newly re-executed. Independent exact source/publicAPI/UI/durability/composition review APPROVE; git diff --check clean.

Each artifact independently passed `zipalign -c -P 16 4` (exit0):

| APK | Bytes | SHA-256 |
|---|---:|---|
| app-debug.apk |21223675|9e7dce13a01fc53ddc14010c87055d3aa398ba6560ffa9cc5e4593c5e16254ca|
| app-debug-androidTest.apk |1478420|11d9c2a406e1973036e92a202b8eca27170ddbc54f98eb6ea63cee994d5cf0b3|
| shell-debug-androidTest.apk |13887109|108edb3b9707c50b5948c7e22a47c1dde5991f9fc50847dfcd22f7caaa110f9a|
| storage-debug-androidTest.apk |10273143|2a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d|

DEX independently contains actual SqlPlayerProfileRepository in app, ProfilesAppGraphIntegrationTest in appTest, and ProfilesUiTest in shellTest. Compiled/packaged is not device execution. Actual host Room/JNI proof is separately the passed graph probe. Native23/36/16KiB/OSkill/physicalFold7/TalkBack for this slice remain not_run; prior frozen main evidence is not borrowed. Advanced counter/commander/appearance editing, profile application, complete gameplay/source/variant/platform acceptance and full Androidv1 remain open. No finished APK delivery. Root continues directly into the reviewed bounded custom-counter draft editor.
