# Ordinary artwork integration — candidate

Root local source314ca0b6; binary producer125274574fbc59e1dec5f72e9c9560f7b62a24e5 integrated unchanged d35c9030. Exact producer hashes/limitations: [frozen handoff](../../../coordination/handoffs/cx-campaign-ordinary-artwork-access-01.md). Root consumerc79d241f and AppGraph binding314ca0b6. These are source checkpoints, not accepted app or release evidence.

Independent reviewer /root/privacy_review independently matched producer hashes and SOURCE ACCEPTED producer and corrected consumer. Original consumer review identified stale dialog callback approving a different/reopened offer and asynchronous navigation fencing. Fixes capture unique review ticket+offer, synchronously check real printing visibility/generation before approval and after port returns, and close artwork on accepted navigation. Regression tests authored. No claimed model tier or unavailable device review.

## Actual verification

- Takeover checkpoint: scripts/check_continuity.py PASS; python3 -m unittest discover -s scripts -p 'test_*.py':31 PASS. Validator fixture output about missing synthetic captures is intentional test output, not real visual acceptance.
- Local source7f822e6c, `./gradlew --version`: FAILED while obtaining pinned Gradle, `java.net.SocketException: Network is unreachable`. Java17 exists; Android SDK/Gradle cache absent. No local Android compile/test ran. Smallest discriminating alternative: actual hosted toolchain, successfully provisioned below.
- [Spec snapshot run41117216590](https://github.com/pemunson90-cmd/Command-Library/actions/runs/41117216590), exactd920c83d3cd87203d921ad8b95b8cc3c1cc129ec: SDK setup succeeded, production baseline compiled; `:feature:shell:compileDebugUnitTestKotlin` FAILED unresolved CardArtworkController (implementation intentionally absent from spec snapshot). Zero executed test XML. This is compilation evidence, not observed behavioral RED. Job124274455053 logs retain exact command/failure.
- Candidate tests authored:11 controller cases,16 producer cases,1 actual AppGraph/Room/DataStore local source/consent case. Counts are not execution. Workflow targets these plus existing printing/settings regressions, then instrumentation Kotlin compilation and shell/app lint. Actual host results PENDING.
- git diff --check and strings XML parse/unique-name check PASS on root candidate. These do not compile Kotlin or execute UI.

## Display/source limits

Full-card Fit, attribution outside image, no disk cache; exact selected-printing IMAGES approval before request; transient state/approval is not restored or replayed. Missing art remains truthful. Independent source review does not establish actual OkHttp redirect/socket behavior, upstream image headers/rights fidelity, successful native JPEG/WebP decode, Compose/device rendering, Fold7/TalkBack/performance, canonical signing/install or whole-D31 acceptance.

## Next privacy packet, source readback only

Settings schema2 outbox/pendingDataRemovals/conditional acknowledge and LocalAcquisition.removeArtifacts are implemented; no production consumer. App root must expose exact-consent raw removal and transactionally delete relevant installed caches under import ownership. Catalog active pointers/FTS require explicit cleanup. Rulings metadata receipts and schema3 embedded receipts bind metadataConsentId separately from file consent: do not acknowledge complete removal while these retained copies remain unexplained. Selected local imports (null consent), decks, collection and games must survive. Review risky deletion semantics before implementation. Do not duplicate the settings codec.

Next action: inspect candidate host result, fix discriminating failure if any, then add essential consent controls and exact-scope drainer while final acceptance remains open.
