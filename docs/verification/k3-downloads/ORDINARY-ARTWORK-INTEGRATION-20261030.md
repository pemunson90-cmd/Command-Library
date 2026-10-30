# Ordinary artwork integration — candidate

Root local source314ca0b6; binary producer125274574fbc59e1dec5f72e9c9560f7b62a24e5 integrated unchanged d35c9030. Exact producer hashes/limitations: [frozen handoff](../../../coordination/handoffs/cx-campaign-ordinary-artwork-access-01.md). Root consumerc79d241f and AppGraph binding314ca0b6. These are source checkpoints, not accepted app or release evidence.

Independent reviewer /root/privacy_review independently matched producer hashes and SOURCE ACCEPTED producer and corrected consumer. Original consumer review identified stale dialog callback approving a different/reopened offer and asynchronous navigation fencing. Fixes capture unique review ticket+offer, synchronously check real printing visibility/generation before approval and after port returns, and close artwork on accepted navigation. Regression tests authored. No claimed model tier or unavailable device review.

## Actual verification

- Takeover checkpoint: scripts/check_continuity.py PASS; python3 -m unittest discover -s scripts -p 'test_*.py':31 PASS. Validator fixture output about missing synthetic captures is intentional test output, not real visual acceptance.
- Local source7f822e6c, `./gradlew --version`: FAILED while obtaining pinned Gradle, `java.net.SocketException: Network is unreachable`. Java17 exists; Android SDK/Gradle cache absent. No local Android compile/test ran. Smallest discriminating alternative: actual hosted toolchain, successfully provisioned below.
- [Spec snapshot run41117216590](https://github.com/pemunson90-cmd/Command-Library/actions/runs/41117216590), exactd920c83d3cd87203d921ad8b95b8cc3c1cc129ec: SDK setup succeeded, production baseline compiled; `:feature:shell:compileDebugUnitTestKotlin` FAILED unresolved CardArtworkController (implementation intentionally absent from spec snapshot). Zero executed test XML. This is compilation evidence, not observed behavioral RED. Job124274455053 logs retain exact command/failure.
- Candidate tests authored:11 controller cases,16 producer cases,1 actual AppGraph/Room/DataStore local source/consent case. Counts are not execution. Workflow targets these plus existing printing/settings regressions, then instrumentation Kotlin compilation and shell/app lint. First candidate run41118350830 exact3d87bf3a FAILED after successful shell production/test compilation: Robolectric class sandbox SDK36 requiresJava21(haveJava17), before any artwork method. Artifact13006004131 SHA2569d9245752ba970fdaa424eb0b71e5333e0799d0b1ef4657a07b55b29bc9125dc downloaded and XML inspected: PrintingSelectionControllerTest10/settings6 pass; artwork class1 sandboxfailure, no producer/AppGraph run, later lint/nativecompile skipped. Fixture-only repair usesSDK34, matching existing host patterns; all behavior assertions retained. No JVM34 result implies Android36/native execution. Next candidate results PENDING.
- git diff --check and strings XML parse/unique-name check PASS on root candidate. These do not compile Kotlin or execute UI.

## Display/source limits

Full-card Fit, attribution outside image, no disk cache; exact selected-printing IMAGES approval before request; transient state/approval is not restored or replayed. Missing art remains truthful. Independent source review does not establish actual OkHttp redirect/socket behavior, upstream image headers/rights fidelity, successful native JPEG/WebP decode, Compose/device rendering, Fold7/TalkBack/performance, canonical signing/install or whole-D31 acceptance.

## Next privacy packet, source readback only

Settings schema2 outbox/pendingDataRemovals/conditional acknowledge and LocalAcquisition.removeArtifacts are implemented; no production consumer. App root must expose exact-consent raw removal and transactionally delete relevant installed caches under import ownership. Catalog active pointers/FTS require explicit cleanup. Rulings metadata receipts and schema3 embedded receipts bind metadataConsentId separately from file consent: do not acknowledge complete removal while these retained copies remain unexplained. Selected local imports (null consent), decks, collection and games must survive. Review risky deletion semantics before implementation. Do not duplicate the settings codec.

Next action: inspect candidate host result, fix discriminating failure if any, then add essential consent controls and exact-scope drainer while final acceptance remains open.

## Consent review/revoke candidate

Local source0792f0e67d9987a80beabfa7c3707747f1b3f548, Android treebac8c223f5f5106e0ee467215a321464cb9f8b1e. Existing SettingsRepository CAS only; explicit exact ticket+record review and RevokeConsent(removeDownloadedData=false). Five-record pages, full exact source in confirmation, truthful retained-data wording, failure/unknown/stale reload without automatic replay. Accepted navigation and ON_STOP close synchronously; ON_START reads only. Independent /root/privacy_review SOURCE ACCEPT after initial missing synchronous navigation fence was repaired. Seven controller cases authored, NOT_RUN; no deletion/drainer or whole-privacy acceptance. Root focused candidate workflow now requires actual XML for six suites with zero failure/error/skip before later compile/lint checks.
