# Rulings source admission and retained installation chain

Status: source1a4f43b implemented and independently ACK; focused and broad local integration gates pass. No production UI/root ruling capability is bound yet. Source authenticity/full-feed/device acceptance remains separate.

## Implemented boundary

The existing AndroidCatalogServices factory now composes fixed ruling metadata discovery and issued-file profiles with the unchanged English profile behind one LocalAcquisition engine, transport guard, artifact directory and caller-owned recovery lifecycle. Factory creation never recovers jobs; AppEnglishCatalogAccess remains the single root recovery owner. No hidden request or approval is introduced.

AndroidRulingsSourceAdmission accepts only a metadata job ID. It checks current completed durable job/artifact/consent under the actual Android private locator pin, reads contiguous bounded raw chunks, verifies byte count/hash and strict UTF8, and saves the complete receipt before publishing small synchronous profile facts. A validated existing receipt survives original job/artifact retention. Unknown acknowledgement publishes no authority. File planning/execution requires the second exact-intent approval through the existing acquisition/settings path.

AndroidRulingsVerifiedInputBinding uses that same engine and pin owner. It rechecks the actual completed file job, full durable receipt, exact cached receipt facts, canonical file plan and artifact/consent projections before passing bytes and a nonnull trusted context to the importer. Its public import surface accepts only job ID.

Version3 installation persists the complete receipt, exact file plan and prior installation envelope as embedded objects, once each. Dedicated3MiB bounds and depth48 apply without widening acquisition or legacy128KiB payload policy. Receipt2MiB+128KiB/raw1MiB and embedded plan/installation128KiB limits remain independent. Reader and lost-commit acknowledgement use version-specific SQL CASE guards before text materialization. The acknowledgement compares the exact already validated envelope, version, count and active pointer within its bounded pin lifetime; it does not reparse3MiB during the500ms acknowledgement window. Existing synthetic/internal v2 input and v1/v2 reads remain compatible. Real Android binding always supplies v3 proof.

## Actual local checks

- First gate:217catalog tests, one stale generated SAM implementation caused AbstractMethodError after the internal callback arity changed. Forced only catalog test Kotlin recompilation (`compileDebugUnitTestKotlin --rerun`); no product workaround.
- Next build failed app host fixture compilation because Android37 lacks a public NetworkCapabilities.addCapability mutator. Corrected host setup to the actual Robolectric shadow API.
- Corrected catalog gate passed all217. Seven app cases then had two failures at actual private locator admission. Temporary synthetic-only diagnostic stack output identified ArtifactLocatorRegistry.register line129 rejecting host fixture ownership/mode. Diagnostic output was removed from production before the final run.
- Host fixture now creates actual0700 filesDir and aligns Robolectric process/package UID to the real host Unix UID. Production private ownership/mode checks are unchanged. The device wrapper uses actual Android OS/identity and has no host bridge.
- Final focused gate SUCCESS36s,144tasks8executed136cached:4catalog v3 cases2.336s +5new actual Room/DataStore/Android pin/factory cases14.782s +4existing root recovery/ownership cases1.274s;3XML,13tests,0failure/error/skip. App instrumentation counterparts compile. Log `/tmp/rulings-source-admission-pins.log`.
- New checks cover full chain retained after temporary jobs/receipts disappear, legacy metadata bound, exact3MiB/one-over payload, corrupt versions/receipt/file plan/artifact, v3 lost activation ACK inside pin, two approvals, exact rehydration, no second recovery, same-host redirect rejection before synthetic enqueue, malformed UTF8, inconclusive receipt-save nonpublication followed by exact reconciliation, and durable receipt/cache drift rejection before staging.

Broad `check lintDebug assembleDebug assembleDebugAndroidTest` SUCCESS4m59s,588tasks80executed508up-to-date. Exact source1a4f43b;119XML/1096tests,0failure/error/skip. Included app19/6XML, catalog218/22XML and storage205/23XML. Unchanged cached tests are reused under D-28. Snapshot `/tmp/rulings-source-admission-boundary-xml`, log `/tmp/rulings-source-admission-boundary.log`; all8actual APKs pass ZIP16KiB alignment (not runtime/ELF/device evidence). Main DEX contains actual AndroidRulingsSourceAdmission/v3 codec; test APK includes the five shared source cases and excludes host directory shadow. Independent exact-source/test review ACK1a4f43b, no blockers. Prior1075gate remains historical.

| APK | Bytes | SHA256 |
| --- | ---: | --- |
| app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk | 1478420 | 9f817cac450aa4bfc6fd46cf711b96dfe61e0fca58d4c3370e85ffeff1a3406e |
| app/build/outputs/apk/debug/app-debug.apk | 26776528 | 57ca8aa7e2e0e2ea026f4d391213c080ea1a95b5ac565b23ce58c3a59b2d52a8 |
| data/network/build/outputs/apk/androidTest/debug/network-debug-androidTest.apk | 11621034 | e5c5456faa1f8a4bb207972fc36461f4549d4f876fa03e272df5d93fe773aa4c |
| data/storage/build/outputs/apk/androidTest/debug/storage-debug-androidTest.apk | 10284091 | b284f78d5dc2b464bc459c6c2e3e5459dbb1a872f53542f91ac6245840343d09 |
| feature/catalog/build/outputs/apk/androidTest/debug/catalog-debug-androidTest.apk | 11167355 | 8a8463e1cca6b0d370b0576642c6d41a9044230cf82d7f5f97e9cc024bcf8d81 |
| feature/rules/build/outputs/apk/androidTest/debug/rules-debug-androidTest.apk | 9856786 | ba4a3ec4aaa0af83e88f1c3ac21fa19e1889e04e24624876ce6a6a6a85c6b304 |
| feature/settings/build/outputs/apk/androidTest/debug/settings-debug-androidTest.apk | 2837597 | ceda3ac0dc2fb6d67a4353ebbf0c53e6e95ddda5c735f257c3d875c7aa837a33 |
| feature/shell/build/outputs/apk/androidTest/debug/shell-debug-androidTest.apk | 16067391 | c24bea1ca468aea266bd4499597c6b6a146e70343672c15366bb65ee81c4bcb5 |

## Limits and next action

No real Scryfall TLS/full-corpus validation: supported environment policy still denies api.scryfall.com/data.scryfall.io, and no bypass was attempted. Synthetic transport invokes the actual factory dispatch guard but supplies synthetic bodies; it does not establish publisher authenticity. Debug/test packaging is verified above; native execution, final package/release acceptance and physical Fold7 remain unrun. No frozen hosted branch moved or duplicate run started.

Next implement docs/superpowers/plans/2026-10-14-rulings-root-access.md: bind ruling capability/offline reader through the same root acquisition owner and implement actual download/ruling UI. Full Android v1 remains incomplete; no unfinished APK delivery.
