# Scryfall rulings metadata decoder handoff

Status: scoped recovery integration locally verified, 2026-10-14; root acceptance pending.
Current integration branch: `component/scryfall-integration-20261014`.
Recovery base: `07fdbd0` (`agent/cx-thread-recovery-20261013`).
Consumed upstream: `0db3298cc752a8433c72416a0f75cfd0fdb45d45` on `component/scryfall-rulings-metadata-20261013`.

## Scope

Adds an internal, unregistered structural metadata decoder and thirteen synthetic fixture tests in feature/catalog. Supports the fixed single-object and list endpoint response shapes, selects exactly one exact `rulings` entry, and validates metadata identity, publication timestamp, exact compressed byte count, optional framing claims, and the proposed exact-host HTTPS JSONL gzip locator policy. Reuses the existing bounded strict JSON parser; adds no dependency.

Candidate fields are source claims, not download authority. Trusted fixed-endpoint retrieval, manual redirects, transport framing validation, streaming decompression, transactional installation, and AppGraph integration remain future work. No caller-supplied JSON or URL gains authority through this decoder. There is no legacy `download_uri` fallback or presumed publisher checksum.

The supplied October 13 discovery profile informed this implementation. Live Scryfall API compatibility remains unverified: the research client reported HTTP 403, and this task performed no external research or live fetch. Exact filename conventions and engineering caps are application policy, not provider guarantees.

## Historical implementation verification (2026-10-13)

- Existing SourceMetadataCodecTest baseline: passed.
- New decoder tests before implementation: expected API-absence compile failure (compile red, not behavioral red).
- Seven focused decoder tests after implementation: passed.
- `:feature:catalog:testDebugUnitTest :feature:catalog:lintDebug`: BUILD SUCCESSFUL in 1m 31s; 167 tests across 17 suites, zero failures, errors, or skips.
- No emulator, device test, or GitHub build requested for this component.
- Independent configured Astra-high source review: APPROVE, no blockers, on the exact source/test hashes below. Review was static; it did not run tests or authenticate live source behavior.

Source SHA-256: `3002685d3a142510e7e226d8bd25fe897d9cc48ddc98d16a66199bb6753368dc`.
Reviewed original test SHA-256: `4dd93202661e8e80813fd24391452a5c90dd80c3228a931fa292d91e2cc908aa`.

## Integration ownership

Recovery root owns global coordination and life work. This integration consumes only the decoder, test and component handoff from upstream; upstream global state, model/life files and test-task records are excluded. The integration task and dedicated lock govern this writer; no orchestrator takeover is claimed.

Next: connect fixed official discovery retrieval to authority issuance and bounded gzip installation only after validating each of those boundaries. Decoder completion does not establish production discovery readiness.

## Historical boundary-test verification (2026-10-14)

Completed 2026-10-14T00:26:41Z: `cx-k3-scryfall-rulings-boundary-tests-02` (retained on the upstream branch) added six meaningful test methods. UTF-8 key/string/body limits, nesting and surrogate validation, configurable exact Long ceilings, malformed field/list shapes, Gregorian dates, pre-epoch fractions and timezone limits are now directly exercised. Production source is unchanged from the reviewed source hash above.

Actual focused command: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest --tests '*ScryfallRulingsMetadataCodecTest'`. BUILD SUCCESSFUL in 20s; XML reports 13 tests, zero failures/errors/skips. Python's independent proleptic Gregorian datetime conversion checked the four timestamp expectations. `git diff --check` passed. No new full-suite/lint/emulator/live-source/review run is claimed; the preceding evidence describes the original implementation.

Current test SHA-256: `d964577199e7a8b885d80965e9d54a7838fcbf31df4a5808fd9a44e6690ec251`. Claim was published in `6f57ef5`; dedicated lock released on completion. Next: integrating root consumes this branch's code and component evidence, without importing its historical global pointers. Trusted retrieval and installation remain unimplemented.

## Latest continuation delta

Recovery integration consumed the exact source/test from upstream0db3298; recorded SHA-256 values match. Claim published before work in b9e10ed. Only scoped source/test/component handoff were consumed; historical global coordination and other model/life changes were not imported.

Actual recovery command: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest :feature:catalog:lintDebug`. BUILD SUCCESSFUL in 3m34s; actual catalog XML: 17 suites, 173 tests, zero failures/errors/skips, including all13 decoder tests. `git diff --check` passed. No emulator, live fetch, new dependency, source modification or new review. Existing strict-source review remains applicable to the unchanged production hash; live compatibility/download authority remain unverified.

Next: recovery root consumes this integration branch's scoped commits and reconciles its canonical board. Source discovery transport/authority and installation remain a separate future task, not this task's unfinished implementation.
