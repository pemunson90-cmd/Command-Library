# Provider HTTP metadata — source freeze 02

Branch `agent/campaign-provider-http-20261015`; worktree `/workspace/command-library-provider-http-01`; unchanged base/HEAD `1ca7770199bdc5d19ac933ebde73825f9f491f74`. Canonical root task/lock, rather than inherited claim copies, authorize exactly this adapter, new test class, and handoff.

## Latest continuation delta

SOURCE02 resolves independent SOURCE01 fixture review: safe GET budget rejection now uses a five-second request timeout and six-second initial/maximum retry delay. This allows the local response to arrive, then deterministically rejects the retry against the request deadline without sleeping. Production source is unchanged from SOURCE01.

The existing adapter now attaches optional scalar `HttpFailureMetadata` to private transport/retryable failures and final `DomainFailure`. Verified non-2xx status and parsed Retry-After survive direct send/stream failures and retry-delay budget rejection. Absent remains absent; valid huge values remain known positive infinity; charge is always unknown. Malformed Retry-After retains the existing static fail-closed error with null HTTP metadata. Pre-dispatch validation and pre-response I/O do not invent an HTTP status.

No retry classification, attempt policy, hidden OkHttp follow-up defense, redirect, acquisition opt-in 429 success path, response/error-body handling, cancellation, limiter, credential policy, constructor or dependency was changed. Metadata contains no body, URI, arbitrary headers or credential values.

Seven synthetic real-loopback tests cover charged POST send401/absent and stream503/zero (one physical attempt), stream429/known and overflow, safe GET budget rejection with absent/known/overflow Retry-After, malformed Retry-After without early retry, invalid input without dispatch, and a socket closed before HTTP response without forged facts or charged POST replay. Privacy sentinel assertions cover body/header/URI/credential leakage. These are local mechanics fixtures, not authentic external provider or billing evidence; existing TLS/security/acquisition regressions remain for root's assembled gate.

Verification: `git diff --check` PASS (source whitespace only). Compilation, Gradle, lint, test execution and network calls: **NOT_RUN**, per root-only build slot. No children, commits, pushes, or shared coordination edits. Source inspection caught and corrected the fixture's initial invalid-header construction to use a transport-rejected control character accepted by the core value constructor; no execution claim.

Exact source hashes:
- `90cc6a6aebb4ce78b67c760e8ac5aa655c7098c6a5968a63c75804e83bc5b7ac` — `android-app/data/network/src/main/kotlin/com/architectpsg/commandlibrary/data/network/OkHttpNetworkClient.kt`
- `28387eb46efc749b8ba5fef3cd99bca036ddc29decf102bcd261091e517b00cf` — `android-app/data/network/src/test/kotlin/com/architectpsg/commandlibrary/data/network/HttpFailureMetadataTest.kt`

Full exact-three-path SHA-256 manifest (including this handoff): `/tmp/cl-provider-http-worker-01/source-freeze.sha256`. Next action: independent source review, then root exact-three-path commit permission; recheck canonical lock, branch/HEAD, paths and frozen hashes before staging. Root owns integration and the one assembled risk gate. No runtime acceptance is claimed.
