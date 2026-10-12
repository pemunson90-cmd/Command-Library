# K3 catalog cleanup durability review

Reviewed code SHA: `ed26b32608ca5eb2a3b9b16d6d7c70574f73bc74`, compared with `86dc00d`, in `/workspace/Command-Library-k3-catalog`.

Verdict: source accepted for this narrow durability patch; no blocking findings. Runtime acceptance remains pending the hosted matrix.

- `LocalCatalog.kt:198–205`: the FTS DELETE now requires the same generation to be `STAGING`. Both FTS deletion and generation deletion run in one real write transaction, so their status checks cannot race an activation transaction. A generation already committed as `ACTIVE` (or later `RETIRED`) survives both statements. A still-staging generation continues through the original cleanup path; the one-second cooperative timeout and recovery failure handling are unchanged.
- `LocalCatalog.kt:148–165`: the vulnerable late-return interval remains explicitly covered: activation can commit before `staging=false`, then cancellation enters cleanup. The new predicate makes cleanup harmless to committed data, and the original cancellation is rethrown. Persisted status remains authoritative.
- `LocalCatalogInstrumentedTest.kt:61–82,113–142`: the decorator delegates to the real transaction and injects its exception only after a successful activation transaction returns. The test checks the injected cancellation, persisted active generation/count, functional FTS lookup, and the same generation plus lookup after close/reopen. The previous unconditional FTS DELETE would make its first lookup assertion fail.

Test limits: this is a synthetic exception at the post-commit return boundary over real storage, not an observed naturally occurring Room/coroutine cancellation race. It starts from an empty catalog and checks one representative FTS lookup; it does not separately assert retired-generation preservation or enumerate every FTS row. Existing precommit cancellation/checksum and cleanup-recovery tests remain unchanged. These limits do not block this predicate correction.

Verification: source/diff inspection only; compilation and instrumentation `not_run` by this reviewer. No code or shared repository files modified.

Latest continuation delta: narrow review completed with no blockers at the exact SHA above. Next action: run the hosted test matrix for the final candidate and retain this source verdict with the actual results before integration.
