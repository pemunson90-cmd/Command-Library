# Current combined native snapshot r4

Status: FAILED. JVM/build passed; API36 device job failed8 legacy DDL-snapshot assertions, promotion skipped. No rerun. Details below preserve each observed stage.

Frozen `agent/cx-search-rules-integration-r4-20261014` at `9c1573b201fcbd5c5086645eef1b0a9ad09989b9`, parenta35ce66; actual diff against source8908e46 is empty for all Android inputs except the uncompiled `android-app/verification/search-rules-native-snapshot-r4-20261014.md`. Existing workflow/gates unchanged. Local source boundary:129XML1233tests0fail/error/skip, check/lint/allAPKs SUCCESS9m10s and8ZIP16KiBpass; see SEARCH-RULES-INTEGRATION-LOCAL-20261014.md.

Published with exact remote readback. ONE Android run [38657175303](https://github.com/pemunson90-cmd/Command-Library/actions/runs/38657175303) observed queued at2026-10-14T23:57UTC for exact frozen head. Continuity38657175353 queued separately. No hosted pass or device execution claimed yet. Do not rerun/update snapshot silently. Older unpublished r3 remains superseded for scheduling; all prior failed frozen refs/evidence preserved.

Root continues local development on recovery branch, sole writer/final merge authority. Snapshot is verification only, no PR/merge/release/completed APK. Next read actual job outcomes and retain evidence, distinguish source/JVM/device/physical/live-feed acceptance.


Observed2026-10-15UTC: JVM/build116169581526 SUCCESS; emulator API36 default/x86_64 job116172409775 in_progress. Actual fetched JVM log reports129XML/1233tests0failures+errors/0skipped, check build SUCCESS3m33s, Android-test assembly4m28s, debug assembly25s and ZIP verification successful. Counts are log-based; hosted XML artifacts have not been downloaded/extracted. Filtered log retained `/tmp/search-rules-native-jvm-summary.log`. The stage that failed four source tests in frozen9a now passes on corrected9c1573b; no device result is inferred. No rerun or workflow change.


Final observed result: run38657175303 FAILED. Emulator116172409775 failed8 storage migration snapshot assertions; debug promotion116174732366 skipped. Fetched device log reports app1XML20tests0failure/error/skip, network1XML3tests0failure/error/skip, storage1XML35tests8failure/error/0skip; catalog0XML0tests before report completeness assertion. These are log-based partial counts, not extracted XML or full-suite acceptance. Later catalog/rules/shell execution is not established. App's20native cases passed; no whole-device pass or corrected profile acceptance is inferred. Frozen ref preserved; no retry started.

All8 named failures compare legacy DDL after real Room open against old schema snapshots: AcquisitionStorageMigrationTest4, FullCatalogMigrationTest3, OriginCooldownMigrationTest1. Their hand-maintained exclusions stop at life/schema6 and do not exclude schema7 rulings/schema8 receipt additions. Diagnosis is being verified locally against actual exports before a bounded test-only fix; original schema damage is not established. Native excerpts retained `/tmp/search-rules-native-emulator-failure.log`; original fetched logs retained in tool evidence. No artifact extraction or visual inspection was performed. No route/policy bypass.


Device configuration observed in actual log: API36 default x86_64, PAGE_SIZE4096. Visual collection retained0of36 expected captures; no visual acceptance. Continuity38657175353 completed SUCCESS for the same frozen head. The local test-only correction now uses complete captured baseline table identity and retains broad rollback checks; actual host regression reproduces the former failure and rejects changed/missing/new objects on original tables. Independent design/exact-source ACK received; current storage/schema9 local gate pending, no corrected device run scheduled. FullCatalog's former filter also missed schema5 cooldown, so correction addresses all genuinely newer tables rather than another exclusion list.
