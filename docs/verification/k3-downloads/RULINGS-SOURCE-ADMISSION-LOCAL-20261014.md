# Rulings source admission and retained installation chain

Status: implemented locally; focused integration passes. Independent production source review ACK; final exact-source/test evidence review and broad integration gate pending at this snapshot. No production UI/root ruling capability is bound yet. Source authenticity/full-feed/device acceptance remains separate.

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

Broad `check lintDebug assembleDebug assembleDebugAndroidTest` is running in `/tmp/rulings-source-admission-boundary.log` at this snapshot. Do not report it passing until completion/XML/artifacts are inspected. Prior1075whole/APK evidence at5ea27c9 predates schema8 and this integration. Current filtered XML counts above are not a fresh whole-suite count.

## Limits and next action

No real Scryfall TLS/full-corpus validation: supported environment policy still denies api.scryfall.com/data.scryfall.io, and no bypass was attempted. Synthetic transport invokes the actual factory dispatch guard but supplies synthetic bodies; it does not establish publisher authenticity. No native execution or new APK acceptance is claimed here. No frozen hosted branch moved or duplicate run started.

Finish broad local source-security/schema integration gate and final exact-source review, checkpoint its exact evidence, then bind ruling capability/offline reader through the same root acquisition owner and implement actual download/ruling UI. Full Android v1 remains incomplete; no unfinished APK delivery.
