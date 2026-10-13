# Scryfall rulings metadata decoder handoff

Status: implemented and locally verified, 2026-10-13.
Branch: `component/scryfall-rulings-metadata-20261013`.
Base: `20a92836a11c70dc11776fa469688c0e1dcc6b4e`.

## Scope

Adds an internal, unregistered structural metadata decoder and seven synthetic fixture tests in feature/catalog. Supports the fixed single-object and list endpoint response shapes, selects exactly one exact `rulings` entry, and validates metadata identity, publication timestamp, exact compressed byte count, optional framing claims, and the proposed exact-host HTTPS JSONL gzip locator policy. Reuses the existing bounded strict JSON parser; adds no dependency.

Candidate fields are source claims, not download authority. Trusted fixed-endpoint retrieval, manual redirects, transport framing validation, streaming decompression, transactional installation, and AppGraph integration remain future work. No caller-supplied JSON or URL gains authority through this decoder. There is no legacy `download_uri` fallback or presumed publisher checksum.

The supplied October 13 discovery profile informed this implementation. Live Scryfall API compatibility remains unverified: the research client reported HTTP 403, and this task performed no external research or live fetch. Exact filename conventions and engineering caps are application policy, not provider guarantees.

## Verification

- Existing SourceMetadataCodecTest baseline: passed.
- New decoder tests before implementation: expected API-absence compile failure (compile red, not behavioral red).
- Seven focused decoder tests after implementation: passed.
- `:feature:catalog:testDebugUnitTest :feature:catalog:lintDebug`: BUILD SUCCESSFUL in 1m 31s; 167 tests across 17 suites, zero failures, errors, or skips.
- No emulator, device test, or GitHub build requested for this component.
- Independent configured Astra-high source review: APPROVE, no blockers, on the exact source/test hashes below. Review was static; it did not run tests or authenticate live source behavior.

Source SHA-256: `3002685d3a142510e7e226d8bd25fe897d9cc48ddc98d16a66199bb6753368dc`.
Test SHA-256: `4dd93202661e8e80813fd24391452a5c90dd80c3228a931fa292d91e2cc908aa`.

## Integration ownership

This isolated branch changes only the decoder, its test, and this handoff. Its inherited global coordination records are historical; it does not establish a new orchestrator lease. The other chat owns life/shared-countdown work and current global coordination. Integrate this component through that root's normal review/versioning flow, without replacing its current handoff or lease.

Next: connect fixed official discovery retrieval to authority issuance and bounded gzip installation only after validating each of those boundaries. Decoder completion does not establish production discovery readiness.
