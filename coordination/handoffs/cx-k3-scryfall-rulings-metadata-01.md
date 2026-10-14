# Scryfall rulings metadata decoder handoff

Status: implemented; rejection-boundary test follow-up completed 2026-10-14.
Branch: `component/scryfall-rulings-metadata-20261013`.
Base: `20a92836a11c70dc11776fa469688c0e1dcc6b4e`.

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

This isolated branch contains the decoder, its test, this handoff and dedicated test-task ownership records. Completed test ownership is released; no active component writer remains. Its inherited global coordination records are historical; it does not establish a new orchestrator lease. The other chat owns life/shared-countdown work and current global coordination. Integrate this component through that root's normal review/versioning flow, without replacing its current handoff or lease.

Next: connect fixed official discovery retrieval to authority issuance and bounded gzip installation only after validating each of those boundaries. Decoder completion does not establish production discovery readiness.

## Latest continuation delta

Completed 2026-10-14T00:26:41Z: [boundary-test task](../tasks/cx-k3-scryfall-rulings-boundary-tests-02.md) added six meaningful test methods. UTF-8 key/string/body limits, nesting and surrogate validation, configurable exact Long ceilings, malformed field/list shapes, Gregorian dates, pre-epoch fractions and timezone limits are now directly exercised. Production source is unchanged from the reviewed source hash above.

Actual focused command: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest --tests '*ScryfallRulingsMetadataCodecTest'`. BUILD SUCCESSFUL in 20s; XML reports 13 tests, zero failures/errors/skips. Python's independent proleptic Gregorian datetime conversion checked the four timestamp expectations. `git diff --check` passed. No new full-suite/lint/emulator/live-source/review run is claimed; the preceding evidence describes the original implementation.

Current test SHA-256: `d964577199e7a8b885d80965e9d54a7838fcbf31df4a5808fd9a44e6690ec251`. Claim was published in `6f57ef5`; dedicated lock released on completion. Next: integrating root consumes this branch's code and component evidence, without importing its historical global pointers. Trusted retrieval and installation remain unimplemented.
