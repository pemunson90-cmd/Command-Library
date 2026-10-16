# Responses codec NEW5 frozen handoff

## Latest continuation delta

Implemented the assigned NEW5 draft on `agent/campaign-provider-responses-codec-20261015` in `/workspace/command-library-provider-responses-codec-01`, exact unchanged HEAD/base `62f801013fc3007d280853f5c502aeef84bc7f9b`. All five owned files are untracked draft additions; no commits, shared-path edits, builds, compiler, tests, network or child agents. Freeze state: awaiting independent source review and root exact-file integration.

Public `ResponsesWireCodec()` uses the existing pure `encode`/`decode` signatures. It admits canonical HTTPS Responses/manual-model requests, writes explicit `stream:true` and `store:false`, maps only supplied limits/sampling, maps a single initial SYSTEM to instructions and user/assistant messages to native input role/content objects, and rejects unsupported settings. `store:false` is app privacy policy, not a retention guarantee. No auth/transport/cancel endpoint or external dependency was added.

Dedicated JSON/SSE helpers adapt the repository's own Chat Completions/Anthropic bounded parsing and framing algorithms locally. No external SDK implementation was copied. Source-derived fixture fields and event facts use read-only `/tmp/cl-provider-responses-source-01/SOURCE-FACTS.md` (R10B, official SDK `9998c1a3806afda55cb3923de1f7f49adcff5a7f`) and accepted root SOURCE02 at the exact base. These are generated source evidence and synthetic fixtures, not authentic wire or provider execution.

The decoder keeps strictly increasing nonnegative event sequences with gaps, contiguous transport order/offsets, and ordered bounded item/part lists independent of sparse coordinate magnitude. Data-only/default message SSE selects required JSON type; explicit names must match. Response identities/statuses and contradictory error/incomplete metadata are checked. Only a completed Response with all observed items/parts closed and exact full snapshots emits STOP. Text from part starts and deltas is emitted once; done/final snapshots never add/repartition/reorder text. Incomplete/failed absent output preserves observed state, null is invalid, and supplied output matches every observed message/part and closure status. Nontext, phase commentary, annotation/logprob content, and populated initial snapshots are explicitly unsupported. Terminal/error outcomes unwind upstream before another chunk/EOF; local and collector cancellation/exceptions propagate.

Direct input/output/cache-read usage projects to existing Usage; every reported known total/cache-write/reasoning counter is also validated as an exact nonnegative Long and retained internally to reject decreases. Omitted/null facts retain prior Known values, never invent zero or monetary costs. H08 separate total/cache-write/reasoning/phase durability remains unimplemented; all provider/authentic/terms/native/physical/release/finished-APK holds remain.

21 synthetic test methods cover request privacy/options/system/omissions/budgets; complete source-shaped reference streams; BOM/comments/data-only/default/explicit event names/multiline/bytewise UTF-8+CRLF framing; sparse maximum-Long indices, multiple items/parts, exact snapshots/phase/coordinates/order; lifecycle contradictions; completed/incomplete/failed/error/truncation/DONE/cancelled cases; usage zero/cache/omission/invalid/decreasing counters; JSON and byte budgets; transport metadata; immediate upstream unwinding; active cancellation and downstream exception transparency.

Verification: **NOT_RUN** (compiler/build/tests), per root-only build slot and explicit packet restriction. Source was manually inspected, base/owned additions read back, and SHA-256 values computed; these are not compile/runtime success claims. No interface conflict or shared-file proposal is outstanding. Coordinated the app proof fixture with its worker: initial event sequence10 and sparse output index3 are accepted, immutable final_answer phase required on item snapshots, role/content input fields only, direct9/5/cache2 with validation of total14/cache-write1/reasoning3.

Next action: root independently source-reviews these frozen bytes, then integrates only NEW5 and runs its one assembled risk gate. Do not consume inherited global coordination state from this worktree. Worker will make no edits after this freeze without a new explicit correction packet.

## Frozen source/test SHA-256 manifest

| Owned path | SHA-256 |
| --- | --- |
| `android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/ResponsesWireCodec.kt` | `f50cecedefa08fbc05b142b6ea313cba8abd955771d92ac1fba4ba1f3ee21396` |
| `android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/ResponsesJson.kt` | `af2ccb5482887bb457dce574bee7fb0b1cb5b1b806307d96cda503548a46abfe` |
| `android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/ResponsesSse.kt` | `4eece6ebdaac624f8941e389175e0e6eecfb291287bfcc8578d9cb54b7f73fdc` |
| `android-app/feature/ai/src/test/kotlin/com/architectpsg/commandlibrary/feature/ai/ResponsesWireCodecTest.kt` | `56fc53f9a7d089433745d734160bcb21065919ed3d5ec990d06463df1bac28aa` |

The fifth owned file is this handoff, `coordination/handoffs/cx-campaign-provider-responses-codec-01.md`; its detached SHA-256 is returned to root with the complete NEW5 manifest to avoid a self-referential digest.
