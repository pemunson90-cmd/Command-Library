# Reviewed parallel pilot and local printing/link composition

Source: containing checkpoint commit on recovery, parent integration86f692a. Tested Android source tree `e9e32bfbaa0955b0e84563d99a8974e13ed5caa5`. No main merge/release or finished APK delivery. Campaign r1 remains35tasks/64requirements/213parity; no complete requirement accepted by these partial producers.

## Integrated outcomes

- Deck worker exactbasefcec0a5→71096d9 (implementationa5bccb0), scoped four source/test files plus own handoff, integrated3623bcf/5925081. Real pure atomic edit proposals/projected revisions and exact entry-field diffs; packages/restore/metadata-order diff/persistence/UI remain separate required work. Independent Astra exact-source ACK;13worker tests0failure/error/skip, final worker runtime2m32s.
- Links worker samebase→45b3822 (implementation244f6a3), scoped four source/test files plus own handoff, integrateddf1d323/86f692a. Both existing CardLinks methods actually implemented: supplied URL/attribution, bounded strict origins/UTF8 templates/per-template token allowances, source-qualified printing relation, typed atomic unavailable/error/unknown. No inferred vendor ID route/fake fallback. Independent exact-source ACK and readback;35worker tests0failure/error/skip in1m17s. Worktrees clean/preserved, canonical claims released.
- Root pure `AndroidCatalogServices.localPrintings(database)` constructs the real internal reader without acquisition. AppGraph binds it over existing DB, plus actual LocalCardLinks, through additive optional ShellPorts. Printing selection is local10-row replacement paging, explicit selection, strict source/generation/cursor validation and request tickets. Unknown whole/reversible relation stays unproven; no fabricated Oracle. Existing card UI shows selected printing/finishes and source-provenance links, visible missing destinations and actual browser action only when resolved. All new labels externalized; existing tokens/48dp controls.
- ViewModel-owned controller state survives actual compact/large-font→expanded branch reparenting. Active-generation/search changes invalidate old results; launch re-resolves current immutable inputs and checks ticket after dispatch. All navigation handlers synchronously invalidate visibility before route assignment, closing event-versus-effect late-launch race. No rendering-triggered transport, art download or consent.

## Verification actually executed

Local JDK17.0.16/Gradle9.6.0/SDK37 pinned setup, one serialized Gradle slot; no versions upgraded. Retained logs/XML `/tmp/cl-pilot-integration-01/evidence/`, worker evidence `/tmp/cl-deck-edits-01/evidence/` and `/tmp/cl-links-01/evidence/`. Final10XML aggregate **91tests/0failures/0errors/0skips**, deliberately not a whole-project suite:

| Scope | Tests | XML | Evidence source |
|---|---:|---:|---|
| Deck reducer/diff |13|2|assembled r2, production identical to reviewed worker |
| Supplied/template links |35|2|assembled r2, production identical to reviewed worker |
| Shell |31|4|final r4: SearchController19, RulingsController3, printing controller8, Compose printing flow1 |
| Actual Room root adapters |12|2|assembled r2: English7/ruling5, unchanged by later presentation-only corrections |

r2 command: configured `bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 :feature:decks:test :feature:links:test :feature:shell:testDebugUnitTest --tests '*PrintingSelectionControllerTest' --tests '*PrintingSelectionUiTest' --tests '*SearchControllerTest' --tests '*RulingsControllerTest' :app:testDebugUnitTest --tests '*EnglishCatalogAccessIntegrationTest' --tests '*RulingsSourceIntegrationTest' :feature:shell:lintDebug :feature:catalog:compileDebugAndroidTestKotlin :feature:shell:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin` — SUCCESS2m31s,194tasks17executed177up-to-date. The prior r1 failed compilation on a test referring to nonexistent SyntheticCatalog.initialized; corrected the assertion to actual consent state. Actual Room proof independently checks no acquisition jobs/artifacts/consent, so no invariant was removed to get green.

Source review then required visible provenance/missing-link wording, and root identified a route-event race. r3 verified provenance/resize UI; r4 verified synchronous route correction/new regression. Both use the same shell four test filters plus shell lint and shell/app instrumentation-source Kotlin compilation. r3 SUCCESS3m4s; r4 SUCCESS1m23s,155tasks17executed138up-to-date. No repeated failure with unchanged diagnosis. Frozen hosted native refs/runs were untouched.

The actual Room test imports labeled synthetic local card bytes, reads printing page/exact identity through fresh standalone local factories, resolves its stored URL/attribution via real LocalCardLinks and verifies no acquisition side effect. AppGraph cached bound-port existence is checked separately. This is **not** an actual AppGraph-selected-browser round trip. Compose uses labeled synthetic local ports and simulated sizes; actual browser/provider HTTP, Android instrumentation execution, physical Fold7/TalkBack/performance/screenshots/aesthetic acceptance are not_run.

## Independent review and remaining scope

Actual gpt-6-astra high independently reviewed pilot design/exact worker sources/retained worker XML, local factory/binding, controller/UI and tests. Required source/lifecycle/provenance corrections incorporated; final route patch ACK. Reviewer ran no builds. Root ran verification. Key source hashes: controller275159f54e59c39f97ef9ace633f3d4e1b9b2109c627bd2c7dba41145a6180d9; CardPrintings67d1ea0e9b8dd63c7e99c0476d8f43fc081dd2defcf8bf50dba27301feb20e16; ShellRoot1791ad5ca39733bd1f7867eae0b5383b7d5617ef0aa187bc203ec4dab473d68e.

F01 still needs actual persistent configurable templates/settings, evidence-backed default fallback adapters and external/native/browser acceptance. B01/B02/B05 etc. need actual durable repository/editor/version workflows. Query execution/owned/deck/price context, FullLean, genuine full-source validation and all other mapped work remain open. GameService named-family HOLD remains. Frozen9c1573b native failure is historical and not repaired by this batch. No partial APK delivery.

Observed capacity: root plus two disjoint implementers, one reviewer, serial builds; no usage-limit event or unexpected conflict. No completed unintegrated worker branch remains. Runtime durations above are process durations, not end-to-end wall time, credits, token costs or speedup. Next: root freezes reviewed durable deck model/codec contracts and dispatches only dependency-ready disjoint work from the newly published integration baseline.
