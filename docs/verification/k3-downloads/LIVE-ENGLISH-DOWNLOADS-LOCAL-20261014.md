# English download controls — local integration, 2026-10-14

Source: d27b578 and d30e3a1 plus the containing commit's resource-only punctuation correction. Independent source/security/durability/composition review ACK/APPROVE. Local implementation/functional checks, corrected style check, lint and four APK builds are verified below; no clean whole-check exit is claimed. This is not full K3/v1 acceptance or APK delivery. Actual lazy factory/Room/DataStore boundary evidence remains separately recorded in LIVE-ENGLISH-ACCESS-LOCAL-20261014.md.

## Result and review

Catalog now offers English downloads when its complete root capability exists. Opening reads persisted pages without approving or transferring. A trusted plan displays actual provider/URL/language, unknown source version/date/expected size, finite transfer budget, Wi-Fi requirement and mutable upstream traversal notice. Explicit confirmation re-reads exact persisted plan and revision before approving its intent, then revision-checked start/resume. Stale plans require reload and new confirmation.

The ViewModel owns independent per-job runner/control coroutines and one observed detail. Pause/confirmed cancel remain usable during transfer; deliberately cancelled engine workers re-read authoritative state before suppressing recognized stops. Observation tickets and revision merging reject stale detail/results. Failed history reads do not claim an empty history. List paging uses real cursors. Navigation does not cancel transfers or promise OS background execution.

Only newly completed approved transfers automatically install; recovered completed jobs require explicit installation. Installs serialize through the same session's pinned verified-artifact importer. Failure retains actual installed data and completed artifacts for retry. Acknowledged success refreshes catalog/search. Stop-install joins cancellation and re-reads durable truth, including activation before acknowledgement, without manufacturing success. Labelled scrolling48dp controls, dismissible busy modals, nested Back and narrow/double-font cases are covered.

Independent life_contract_review ACK/APPROVE found no source blocker in exact-plan consent/CAS, independent controls, cancellation reconciliation, stale-data guards, serialized installs, existing pinned delegation and UI/lifecycle copy. Its suggested ambiguous-commit regression was added and passed. The small failed-history/empty correction is also covered. No unsupported feed controls or production placeholders were added.

## Actual verification

Initial focused :feature:shell:testDebugUnitTest filtered to DownloadsControllerTest/DownloadsUiTest passed1m42s/34tasks17executed17up-to-date,8controller+3UI/2XML0failures/errors/skips. A ninth controller case covers cancellation after activation and a fourth UI case covers failed initial history reads.

Managed whole check/lint/app-debug/appTest/shellTest/storageTest invocation completed9m22s/335tasks36executed299up-to-date at stable Android tree ac6dbe3. Coherent110XML:1,031tests/1failure/0errors/0skips. All9download controller tests0.359s and4sharedUI tests10.470s passed. Sole failure was ExternalizedStringsTest rejecting an em dash in downloads_interrupted. Corrected to plain sentences without logic changes. Full boundary XML was preserved under /tmp/live-english-downloads-boundary-xml before the filtered follow-up, so the current filtered shell XML must not be summed as a fresh whole-suite result. Log /tmp/live-english-downloads-integration-managed.log.

Follow-up :feature:shell:testDebugUnitTest --tests '*ExternalizedStringsTest' plus :app:lintDebug :feature:shell:lintDebug and app-debug/appTest/shellTest/storageTest assembly SUCCESS3m32s/390tasks48executed342up-to-date. Actual3style tests/1XML0failures/errors/skips0.304s. Unchanged functional tests were not repeated for this resource-only correction under D-28. Log /tmp/live-english-downloads-copy-and-package.log. Earlier full command and detached retry were cancelled after command-client disconnections; daemon diagnostics distinguish those interruptions from the actual style failure. No test expectation was weakened.

## Internal packaging proof

All four files individually passed zipalign -c -P16 4 with exit0. App manifest contains INTERNET and ACCESS_NETWORK_STATE. App DEX contains AppEnglishCatalogAccess/DownloadsController/DownloadsScreenKt; appTest DEX contains the unshadowed EnglishCatalogAccessIntegrationTest wrapper and shellTest DEX contains DownloadsUiTest. These are internal debug validation artifacts, not a finished-app delivery.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| app-debug |26776528|429e178bb3651b05039dbfb230b7c16f6ad47380eb8d2d24890b0f556efb3cf6|
| app-debug-androidTest |1478420|df34b3907b858cfbd5dab6bb648888e46071a8ae36a5c7cdc368f324d5deb253|
| shell-debug-androidTest |15280594|c2a858248a09615bd341edeb9f6aed845442b8331111a95587a6bed2175756df|
| storage-debug-androidTest |10273143|2a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d|

## Remaining evidence and scope

Controller/UI fixtures are explicitly synthetic, with no wire, publisher corpus or verified file bytes. Their successful installs prove presentation orchestration, not physical transport or production pinned activation. Separate real factory/Room/DataStore host checks cover admission/cache/recovery/unapproved/unverified denial. The scoped host filesystem bridge does not establish regular artifact verification or successful pinned installation. Actual approved transfer-to-install, process kill, full English corpus, physical Fold7/TalkBack and final signed acceptance remain unproven. Other feeds, rules/rulings, Full/Lean and full K3/M2–M9 remain open; GameService stays unbound. One combined modern native run is useful for the new real root/permission/data boundary when available; do not run emulator jobs per small feature.

## Frozen modern native run, pending

Pushed exact tested source d7e864f65e500c3ac526eedca78a90c740aac601 (Android tree bb9e7cc3939f6badd8df0a5bd5b6739820ed178d) to isolated agent/cx-english-downloads-integration-20261014 without modifying CI or moving main. Actions GET observed Android K2 build/emulator run38621711127 and continuity38621711056 in_progress at19:12UTC. Existing reviewed workflow runs one default API36 lane; compatibility inputs were not enabled. This is one combined check for first real root/network/consent/permission registration and accumulated native probes, not per-feature emulation. It necessarily uses the existing workflow's build prerequisite. No native/test-count/render/physical success is claimed until actual logs/XML are read. Keep this branch frozen; continue parser work on the recovery branch. No merge, public release or finished APK delivery is authorized by this test branch.
