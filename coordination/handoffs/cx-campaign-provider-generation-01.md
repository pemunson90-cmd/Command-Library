# Durable provider generation — SOURCE01 return

## Latest continuation delta

- Branch: `agent/campaign-provider-generation-20261015`.
- Unchanged base HEAD: `1dc7a49c87a8981afe0774376d8eb5026284242a`.
- Worktree: `/workspace/command-library-provider-generation-01`.
- Exactly NEW3 paths authored; existing sources, shared contracts/configuration/coordination and HTTP/CC returns untouched. No commit/push.
- Verification: source inspection only. Builds/compiler/tests/network **NOT_RUN**, as delegated. No children spawned.
- Next action: root independently reviews these frozen bytes, integrates only the authorized NEW3 paths, then owns the coherent assembled validation gate. No live-provider/native/full-provider acceptance claimed.

## Behavior

`DurableProviderGeneration(client).run(history, epoch, profile, conversation, input, cancel, onProgress)` retains the exact delegated signature. Pure input admission checks captured prompt/provenance, identity/deck/mode/model/epoch, initial empty provider source, canonical retained HTTPS profile and history payload/string budgets before writes. Existing durable attempt IDs cannot dispatch. A conversation is created once or its exact immutable deck/mode/title verified; append's matching PENDING acknowledgment precedes the sole stream collection. Requests contain exactly one USER message equal to captured `prompt.rendered`, with optional generation parameters null and stop sequences empty.

Streaming checkpoints cumulative strict UTF8 prefixes (256 KiB) with the acknowledged revision/epoch. Request/sequence, unsupported token IDs, usage and acknowledgment anomalies stop collection. Known usage fields, including known zero and separate cached tokens, survive unknown later fields. The first terminal closes upstream immediately before its final history write: STOP becomes COMPLETE; TOKEN_LIMIT/TOOL_CALL/INTERRUPTED become INTERRUPTED; FAILED becomes FAILED. Missing terminal or provider failure persists FAILED while returning the corresponding failure/unavailability/unknown result. No tools execute.

Cancellation and downstream callback failures unwind upstream before an independent NonCancellable history finalization with a 5-second timeout and fresh noncancelled token. INTERRUPTED preserves every admitted received character and known usage. Cancellation/callback exceptions propagate after finalization; no observer callback runs inside that independent cleanup. Any failed/thrown/contradictory history mutation marks uncertainty and prohibits further writes, including cleanup after possible commit. Noncancelled uncertainty returns a static PERSISTENCE failure with the original attempt ID; cancellation still propagates so the caller must reload that ID.

Caller boundary confirmed by root: root has already recovered the shared process epoch, owns explicit consent and supplies a fresh attempt ID for every newly authorized send. This coordinator performs no internal retry. NOT_FOUND is not a receipt permitting a replay of a previously unacknowledged invocation. Root UI forbids a further send after uncertainty until original-ID reload establishes terminal state or explicit abandonment/new conversation-ID/consent. No process-local ID registry substitutes for durable authority.

## Authored synthetic oracles (21; NOT_RUN)

PENDING-before-POST/exact request; no create/append ACK no POST; prewrite immutable validation; exact existing conversation; cumulative CAS/prefixes and trailing unknown usage; partial known usage; nonstop incomplete terminals; truncated/provider-failed response; wrong request/sequence/token IDs/negative counts; exact UTF8 reply bound; coroutine cancellation/socket-close ordering; streaming and PENDING callback failure; returned/thrown ambiguous checkpoint with/without commit; ambiguous append cancellation; terminal checkpoint uncertainty; bounded cancelled cleanup; inconsistent append/checkpoint ACKs; malformed Unicode with remaining budget; existing-ID no replay and explicit fresh-ID retry lineage.

These in-memory oracles exercise coordinator mechanics. Real SQLite, real transport and app integration remain root's assembled gate.

## Source freeze SHA-256

- `android-app/feature/ai/src/main/kotlin/com/architectpsg/commandlibrary/feature/ai/DurableProviderGeneration.kt`: `5b9211e0d2c4fe985eb9503821fc00d730191a7c63b6038f65d6c04652a0b048`
- `android-app/feature/ai/src/test/kotlin/com/architectpsg/commandlibrary/feature/ai/DurableProviderGenerationTest.kt`: `478a5d35ac539ce7046ccfbf1ba436ed691a54ceeae24b5b496ef21c38e52fd9`

Handoff's own SHA-256 is returned separately to avoid a recursive manifest.
