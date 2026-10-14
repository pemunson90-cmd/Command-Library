# Profile custom-counter editing — local progress2026-10-14

Bounded extension of verified profile library22d2230/checkpoint2958453. Root retains existing life-action-engine/app/shell/shared ownership and final merge; no additional component task or worker. Design brief is in the parent task's replaceable Latest continuation delta; Pat delegated continuous implementation. Existing independent reviewer approved the bounded design, parser/controller and UI source.

Custom OTHER counters only: controller enforces both custom flag and role; existing ID/preset/role/custom/order/untouched fields survive. New definitions append fresh collision-checked UUID; the128-definition codec cap counts reserved counters (LifeCoreWires.profileWire). Signed initial/bounds/threshold validation; initial must be within ordered optional bounds, threshold need not be. Exact unchanged blank legacy label remains valid. Color accepts exactly #AARRGGBB with an explicit unchecked/unset option. Loss/reset changes are explicit user-authored settings, not invented named preset/source authority.

All counter changes remain in the scoped immutable profile draft. Counter Apply/delete never call repository or increment revision. DialogCancel keeps definitions; nestedBack returns countereditor→manager→basicprofile; wholeCancel/reload clears nested state. Only existing wholeprofileSave persists the original revision through the actual repository and synchronizes LIFE.initial. Failures/stale keep the proposed definitions; busy persistence blocks concurrent counter mutations. Lazy keyed manager and scrolling labelled48dp controls support large font; radio choices use selectableGroup after review. Standard/reserved counters remain retained/read-only; no disabled future controls.

APIabsence controller RED22s preceded implementation. Nine new counter-controller plus nine existing profile-controller tests GREEN20s,18tests2XML0failures/errors/skips. UIAPIabsence RED8s preceded controls; six shared UItests GREEN66s/1m6s0failures/errors/skips. Independent source APPROVE for parser/controller/UI, then selectableGroup refinement. The actual app UI→AppGraph→Room probe uses the existing pinned Compose test dependency on app JVM tests, actual activity/application/SQLite and unique owned-row cleanup. Initial pass22.391s and final startup-order-adjusted pass49.485s cover exact custom fields and stable-ID revision-checked re-edit without production injection seams. See the final verified gate below.

Final current-unit whole check/debug/app+shell+storage testAPK builds passed231s/3m51s,441tasks48executed393cached. Actual102XML report989JVM tests0failures/errors/skips (unchanged cached modules included). Four independently checked APKs pass16KiB ZIP alignment. Native/physical execution remains not_run. The baseline973JVM/99XML/fourZIP proof belongs to22d2230 and is not claimed for this dirty extension. GameService remains unbound. Standard preset editing, commander/appearance editing/application and complete M2/Androidv1 remain open; no final APK delivery.

Initial actual app UI→AppGraph→Room probeGREEN22.391s, containing66s command/117tasks14executed103cached. It proves no profile row before wholeSave, exact signed initial/bounds, loss threshold99 outside maximum10, ARGB8000FF00/reset/custom fields, then revision+1 with same counter ID and onlyinitial changed. Final reviewAPPROVE source/UI/durability/composition. After this pass, moved startupnavigation readiness wait before cachedgraphportlookup (test-only ordering refinement); the current whole gate recompiles/reruns that final test version, so initial pass is not borrowed as final adjusted-source evidence. Radio choices now selectableGroup; final fullshell likewise reruns current source. No new publicport/schema/codec/production dependency or GameServicebinding. Test-only Compose dependency reuses alreadypinned library.


## Final verified local gate

Same accepted JDK17/SDK/Gradle tuple. `bash android-app/gradlew -p android-app --daemon --console=plain check :app:assembleDebug :app:assembleDebugAndroidTest :feature:shell:assembleDebugAndroidTest :data:storage:assembleDebugAndroidTest` completedSUCCESS231s/3m51s,441tasks48executed393up-to-date. Current102JUnitXML report989tests,0failures/errors/skips; app6tests3XML, shell113tests15XML, storage198tests21XML. These include unchanged cached modules. Final startup-order-adjusted actualAppUI test passes49.485s, timestamp16:00:45, covering the exact finaltest source. Source/publicAPI/UI/durability/composition independently APPROVE. git diff --check clean.

Each APK independently passed `zipalign -c -P 16 4`,exit0:

| APK | Bytes | SHA-256 |
|---|---:|---|
| app-debug.apk |21230216|a81d6e5720511e905693e9f670d7f6f50d52c597a0c26e7dc0dad2e942baba0b|
| app-debug-androidTest.apk |1478420|a98793e3742d370fd4e2573d8410b13033fc4a4ddb614f81207beff8dea718e9|
| shell-debug-androidTest.apk |15157746|32b9fa3516e4bfd6182090ea63adad65f508bdccf3b3b62d5e3e5d89cea410fc|
| storage-debug-androidTest.apk |10273143|2a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d|

DEX contains ProfileCounterEditor in app, actual ProfileCounterUiIntegrationTest in appTest, and ProfilesCounterUiTest in shellTest. Packaging is not device execution. Actual host SDK34 UI/Room/JNI proof is separate from native23/36/16KiB/OSkill/Fold7/TalkBack, all stillnot_run for this unit. Complete G13/M2/advancedprofile/standardpreset/gameplay/source acceptance remains open. No finished APK delivery; root advances into profilecolor/orientation draft controls while preserving art/fit/crop.
