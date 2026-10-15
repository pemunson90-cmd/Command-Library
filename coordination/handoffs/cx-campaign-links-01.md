# cx-campaign-links-01 — implementation return

Reviewed packet r1 implemented on `agent/campaign-links-20261015` in `/workspace/command-library-links-01`. Exact dispatch base: `fcec0a59d93ec3d763799d8c1e79fee828f0c5d9`. Tested implementation commit: `244f6a390670afd0a701df37a317d9dc481ef887`. This handoff-only successor contains no product changes; root receives its exact commit ID with the return. Canonical root retains ownership/integration authority; worker did not edit or release any lock.

## Latest continuation delta

Both existing `CardLinks` methods now have a pure JVM implementation. `LocalCardLinks.resolve` produces an atomic enabled-target batch in preference order, validates IDs/templates/origins, and gates every selected-printing path on the known exact source-qualified whole-card Oracle relation. Known mismatch is `SOURCE_MISMATCH`; unknown/absent relation remains `Unknown`. Missing supplied/template data is typed unavailable with a safe target ID. No alternate printing is selected.

Only the six reviewed supplied fields are recognized: `scryfall_uri`, `related_uris.gatherer`, `related_uris.edhrec`, `purchase_uris.tcgplayer`, `purchase_uris.cardmarket`, `purchase_uris.cardhoarder`. Original safe URLs, attribution/provenance and source-qualified printing IDs are preserved. Cardmarket warns that its supplied link may be inexact. API `uri`, Infinite article/deck links, unproven vendor slugs and inferred ID routes are never chosen. `allowSearchFallback` cannot manufacture an unverified route.

Explicit user templates obey `preferSelectedPrinting`, duplicate-free supported token allowances, required-value availability, component UTF-8 encoding, static-percent preservation and 16KiB UTF-8 input/output bounds. No scheme/authority tokens, raw backslashes/controls, userinfo, malformed escapes/ports, unsafe schemes or invalid hosts are accepted. Resolve enforces explicit exact scheme/lowercase ASCII host/effective-port origins; template alone validates a safe absolute HTTP(S) URL. Template links have null selected printing/provenance and an explicit user-configured, non-exact warning. Conflicting duplicate supplied links fail, including ambiguous differing provenance. Identical duplicate records are accepted as one unambiguous supplied value.

Owned diff: four new files under `android-app/feature/links/src/{main,test}/kotlin/com/architectpsg/commandlibrary/feature/links/`: `LocalCardLinks.kt`, `LinkTemplateRenderer.kt`, `LocalCardLinksTest.kt`, `LinkTemplateRendererTest.kt`; implementation totals 613 added lines. This handoff is the only coordination write. No root/shared/build/DTO/UI/schema/settings/DI/registry changes.

Verification actually run after root granted the sole Gradle slot:

```text
TMPDIR=/tmp/cl-links-01/tmp
JAVA_TOOL_OPTIONS=<inherited value plus final -Djava.io.tmpdir=/tmp/cl-links-01/tmp>
bash android-app/gradlew -p android-app --offline --console=plain :feature:links:test
```

Configured inherited JDK17/Android/Gradle environment retained. Build and test process logs confirm the final task-local Java temporary directory property. Sources remained unchanged during the build. First focused batch passed, no repair/rerun needed: **BUILD SUCCESSFUL in 1m17s**, exit0; **35 tests, 0 failures, 0 errors, 0 skipped**. Resolver23; renderer12. Gradle slot was released to root after extracting XML. No concurrent Gradle/cache writer was started by this worker.

Evidence retained locally:

- `/tmp/cl-links-01/evidence/gradle-links-01.log`
- `/tmp/cl-links-01/evidence/TEST-com.architectpsg.commandlibrary.feature.links.LocalCardLinksTest.xml`
- `/tmp/cl-links-01/evidence/TEST-com.architectpsg.commandlibrary.feature.links.LinkTemplateRendererTest.xml`
- `/tmp/cl-links-01/evidence/xml-counts.txt`

XML copied from this worktree's `android-app/feature/links/build/test-results/test`; the copies describe the tested implementation commit exactly. `git diff --cached --check` passed before implementation commit. Self-review checked all owned source/tests, safe URI parsing and exact origins, raw IDs/component encoding, atomic missing-target behavior, printing relation/provenance qualification, disabled targets, duplicate/configuration bounds and non-exact template warnings. Synthetic fixtures are labeled; no live vendor result or policy evidence was invented.

Not run/accepted: independent root/Astra source review; assembled consumer/preferences/printing-selection behavior; full F01/AC-F01/PX06-061 acceptance; live HTTP/redirect/vendor/terms checks; device/native/physical Fold7/performance/full-suite checks. This completes the reviewed supplied/template producer packet only. R17 route and policy limitations remain; no full feature, seller exactness or finished-APK claim follows. Existing frozen9c1573b failures were untouched.

Next action: root independently review the exact implementation/source boundary and consumer integration, integrate only the four owned source/test files plus this handoff, and run the next coherent assembled consumer check under the serialized Gradle slot. No worker push/merge/reset/clean/stash or modification of another writer's processes occurred.
