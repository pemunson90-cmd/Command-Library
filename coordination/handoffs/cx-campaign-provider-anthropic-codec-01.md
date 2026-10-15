# Native Anthropic bounded codec handoff — SOURCE02 NEW5 frozen

Base: `4424646d9be0e797a7d303c341a8e0e69698ac2d`.
Branch: `agent/campaign-provider-anthropic-codec-20261015`.
Worktree: `/workspace/command-library-provider-anthropic-codec-01`.
Canonical assignment/ownership: root `coordination/tasks/cx-campaign-provider-anthropic-codec-01.md` and `coordination/locks/campaign-provider-anthropic-codec.lock`; SOURCE02 exact-base ACK. Only the five locked new paths were written. No commit was made.

`AnthropicMessagesWireCodec()` implements the accepted pure encode/decode API. Encoder requires a canonical ANTHROPIC profile, explicit known canonical positive-year YYYY-MM-DD version and positive maxTokens. It emits POST base-prefix + /messages, exactly three static credential-free headers, required manual model/stream/max_tokens, string user/assistant messages, optional one initial top-level system and explicit stop_sequences. Non-null temperature/topP/seed and tool/extra-header capabilities are unsupported. Nothing installs an SDK default version or reads credentials.

Local JSON/SSE helpers reuse the repository's reviewed Chat Completions bounded algorithms by format-local copy, with native event-name preservation. No external SDK implementation code was copied. The official SDK source at pinned `bcd55fe1d3f0f1240765f8cef04295b67337f55a` was a read-only declaration/reference source; this is not authentic wire, runtime, terms or eligibility evidence.

Decode admits ordered transport metadata/bytes and bounded strict UTF-8/JSON/SSE. Native event name must match data.type. It requires message_start assistant/id/model/empty content/no finish; sequential nonnegative text blocks beginning at 0, no overlap and matching indices; all blocks closed before message_delta; then known reason and message_stop. Nonempty block-start text is emitted verbatim. No content block events are admitted in the message-delta phase. Ping may interleave. Unsupported content/tool/thinking/signature/citation/refusal-detail structures are explicit unavailable outcomes; provider error diagnostics are static. Only message_stop emits terminal TokenChunk and immediately unwinds upstream using cancellation, including inside a nonfinal transport chunk. Reason-only/EOF does not complete. Typed non-success upstream outcomes propagate once; caller cancellation and collector exceptions propagate with upstream finally unwinding.

Usage tracks cumulative exact nonnegative Long input/cache-creation/cache-read/output components internally, retains prior known values on null/omission, rejects component overflow/noninteger/negative/decrease, and retains known zero. inputTokens is known only when all three input components are reported; missing components retain an unknown total. Any all-known input sum overflowing Long rejects the whole usage snapshot as CORRUPT_DATA before component state changes, preserving previously emitted text without terminal success. This prevents the durable usage merger from silently retaining a stale prior known total. cachedInputTokens is cache-read only, output/provider ID direct, cost unknown. **Unimplemented H08 fidelity:** individual uncached and cache-creation components are not separately persisted by current Usage. No full H08 usage, provider, eligibility, legal or billing acceptance is claimed.

Thirteen authored synthetic tests cover request options/static headers/system/unsupported/version/Unicode/escaped-body budgets; byte/UTF-8/BOM/line-ending/multiline/ping splits; block-start text/multiple blocks; cumulative usage zero/null/omission/decrease/overflow/unreported-component unknown total; known-total-to-overflow prefix/typed-failure/no-terminal regression; sequence/index/type/duplicate JSON/unsupported content/errors/EOF; all supported reason mappings; immediate exactly-once terminal unwind; HTTP/typed outcome propagation; byte/structure budgets; caller/in-flight cancellation and collector exceptions.

## Verification

- Compiler/Gradle/JVM tests: **NOT_RUN**, by explicit root-only build-slot instruction.
- Network/provider/SDK execution: **NOT_RUN**.
- Read-only source inspection and `git diff --check --no-index /dev/null <each new Kotlin file>`: no whitespace diagnostics.
- No children, shared edits, builds, commits, pushes, reset/clean/stash or process kills.

## Frozen source/test SHA-256

```text
1a68fc8919378d1e6b68707bd4b706679fdeb298df3cd26734f9854983cadd20  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/AnthropicMessagesWireCodec.kt
24dbbd7a71b1e3fa776839587b6b5255c36c83b0b30a4a71119265c1ebaeb0ed  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/AnthropicMessagesJson.kt
601689d741eb49c1e5f15d767baf98bf72bc10fbfba54a466dd111c4abbdf610  android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/AnthropicMessagesSse.kt
d677aca5bdab790ecc88d81e1981d707c1304c6def1f54afa601deb3b842dd42  android-app/feature/ai/src/test/kotlin/com/architectpsg/commandlibrary/feature/ai/AnthropicMessagesWireCodecTest.kt
```

The complete five-path manifest, including this handoff's SHA-256, is returned to root outside this self-referential document. This file and all four source/test files are frozen pending root/source review; subsequent corrections must explicitly replace the freeze and hashes.

## Latest continuation delta

SOURCE02 replaces the SOURCE01 freeze after independent source review HOLD: all-known arithmetic input-sum overflow now fails the entire usage event as CORRUPT_DATA, rather than emitting Unknown that durable merging could replace with a stale known total. Component updates commit only after all validations. The original overflow fixture now requires failure; a focused regression starts at known total 5, emits prefix text, then supplies increasing individually valid Long counters whose sum overflows and requires typed failure with no terminal success. The non-assistant role fixture now explicitly expects Unavailable/UNSUPPORTED; all other failure assertions remain strict. Thirteen synthetic test methods are authored; compiler/Gradle/tests remain NOT_RUN. No shared contract change.

Original SOURCE01 freeze is preserved by root at `/tmp/cl-provider-anthropic-01/source-snapshots/SOURCE01/codec`. This worker verified the original snapshots still match WireCodec `2d0120740dbfea726fc216baed31bbde8640546863640b594038116e6bc64d4e` and Test `0e63b577dcb5f78749c008e9bfc4c9944f8061d3d0ec66dde944b027f5f71046`. The old source/test files and handoff are untouched in that snapshot.

Root next: independently review exact SOURCE02 hashes, integrate only these five files into its assembled native boundary, then run the single owned native/CC/network/history/app gate. Any compiler/test finding returns to this worker before another freeze.
