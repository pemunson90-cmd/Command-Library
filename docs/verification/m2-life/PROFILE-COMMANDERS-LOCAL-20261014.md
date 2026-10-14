# Profile commander manager — 2026-10-14

Extends the profile editor after verified appearance source efa6955. Local catalog search supplies literal oracle/name/language and the first supplied provider identity in stored order; printing identity remains explicitly absent. Existing identity survives label edits unchanged. Fresh collision-checked slot UUIDs keep partners separate, including duplicate oracle selections; zero slots are valid. Add/rename/confirmed removal stay draft-only until the existing whole-profile original-revision Save. Busy, stale/failure/reload and nested Back/Cancel boundaries remain intact. Picker search resets/cancels across editor replacement and receives the same catalog-generation notifications as ordinary search; the controller rejects stale/loading/forged selections.

One related implementation batch includes controller/search wiring, scrolling manager/picker/label form, four shared UI cases, six controller cases, and an actual MainActivity/AppGraph/Room rename/remove probe. No public port/schema/dependency/GameService change. No deck-legality, art rendering or named-source certification is implied by selecting a tracking identity.

Independent review caught ambiguous concatenated list identities: colon/hyphen-containing provider/oracle strings could collide. Both row key and select tag now share an injective length-framed String. The UI regression renders four formerly ambiguous identities, finds each and selects the exact last OracleId without persisting.

First focused run failed in2m40s: app executed3 tests, prior counter/appearance passed, new commander probe failed because a test helper tried to scroll a non-scrollable confirmation dialog. Replaced that interaction with direct click, keeping all durable-value assertions. Shell tests had not executed; no shell pass claimed from that run. Final source/UI/durability/composition review APPROVE after both fixes.

The corrected affected-batch run passed: shell Profile* plus SearchControllerTest (64 tests/9XML), and app ProfileCounterUiIntegrationTest (3/1XML), all zero failures/errors/skips. One invocation took3m52s/131tasks,7executed124up-to-date. New commander controllers:6pass/0.329s; shared UI:4pass/17.949s, including actual ID-collision rendering; actual Room commander rename/remove:1pass/2.454s (containing app suite60.437s). This follows D-28; no repeat whole suite, lint/package build or emulator for each edit. Native/device accessibility and actual local-catalog add→Room composition remain separate from fixture catalog selection and real Room rename/remove evidence. No finished APK or full M2/v1 acceptance.


Exact focused command (accepted JDK17/SDK/Gradle environment):

`bash android-app/gradlew -p android-app --daemon --console=plain :feature:shell:testDebugUnitTest --tests '*Profile*' --tests '*SearchControllerTest' :app:testDebugUnitTest --tests '*ProfileCounterUiIntegrationTest'`

This67-test affected boundary is the current evidence; no new whole-project count or APK/native execution claim is made. The previous whole1,001-test/four-APK proof belongs to efa6955. Final independent source/UI/durability/composition review APPROVE; git diff --check clean.
