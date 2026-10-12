# cx-k3-network-01: shared cancelable transport

Status: assigned; implementation unrun. Parent orch-kotlin-k3-offline-data-01, N02. Internal component work, not complete-app delivery.

Read CONTINUE.md, docs/architecture/network/ARCHITECT.md, KOTLIN_CONTRACTS.md, KOTLIN_OWNERSHIP_MAP.md, docs/verification/k3-downloads/ARCHITECTURE-REVIEW.md and core InfrastructureContracts.kt/Values.kt. Existing core ports are merged; do not change them. Kotlin paths replace historical Dart paths.

Own only android-app/data/network/src/** and coordination/handoffs/cx-k3-network-01.md on agent/cx-k3-network-01 isolated worktree. Build/module/dependency/DI/CI/shared edits stay with root; request changes in handoff.

Implement real HttpURLConnection NetworkClient via factory/constructor, clock-injected metadata/monotonic rate limiter. Bound send and stream; typed errors; HTTP metadata on first/final empty chunk; contiguous offsets/sequences; <=64KiB chunks; no full-file stream buffering. HTTPS origin allowlist, no automatic redirects/caching, max5 redirects, no cross-origin sensitive header forwarding, reject CR/LF header names/values, redact errors, no credentials/body/URLs in failure logs. Non-null endpoint.secret returns KEY_MISSING unless real secret resolver is supplied; no fake authenticated path. AI later resolves vault and explicitly supplies request headers. Explicit Accept-Encoding identity unless caller requests gzip; leave encoding visible/raw for durable Range resume.

Coroutine and CancelToken cancellation must disconnect blocked socket/read within <=1s, not just check before read. Finite connect/read/overall timeouts and close disconnect all live calls; close prevents new requests. Honor descriptive User-Agent and Accept, source limiter <=8/sec; search cap2/sec override identified by Scryfall search path; retry safe GET/HEAD only before stream bytes emitted, never replay POST/charged generation, bounded backoff/Retry-After (429 Scryfall >=30s), no retry401/403/cancel.

Tests use synthetic local HttpServer fixtures: streaming/empty/non2xx, bounds/truncation, HTTP metadata, cancellation while blocked and token-only cancellation, forbidden initial origin/redirect/credential isolation, safe retries vs charged/no-replay, Retry-After and limiter, close. Use injected timing/seams for fast deterministic retry tests. No external charged/network calls in tests. Compiler scratch /workspace/scratch/kotlin-probe/lib can run pure JVM sources/tests; hosted full Android validation is root integration.

Before returning: commit code/tests and replace Latest continuation delta in your handoff with exact verification and next action, push branch. <=10-line summary. No subagents.
