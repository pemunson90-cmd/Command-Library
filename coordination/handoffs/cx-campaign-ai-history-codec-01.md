# H08 history content codec — source freeze SOURCE02

Owner: /root/implement_ai_history_codec. Branch `agent/campaign-ai-history-codec-20261015`, worktree `/workspace/command-library-ai-history-codec-01`, exact unchanged HEAD/base `a9e746b91374b36e99d4748b2ecb6a8c98558d50`. Canonical root task/parent and active lock were read from `/workspace/Command-Library` before editing. Only the exact NEW5 owned paths changed. No shared files, dependencies, schema, DI or production consumer changes.

Public zero-arg `AiHistoryPayloadCodec : AiHistoryPayloadCodecPort` implements CLAH magic, format1, immutable identity/source/prompt/provenance/mode/provider/model/retry/raw/usage. Revision/ordinal/owner/status/start/finish never enter bytes; explicit metadata joins require identical immutable identities/source and canonical terminal/unfinished state. Offline requires COMPLETE/no provider identity/retry/owner and Unknown usage. Provider requires explicit model/profile/owner even terminal; PENDING additionally requires empty raw content and Unknown usage. Prompt template ID matches mode; historical positive versions and deck revision zero survive. Wall-clock order is unrestricted.

Component-local binary follows existing deck codec primitives: strict UTF8 sizing and malformed UTF16 rejection before encoder allocation, total output reservation before strings, unsigned length/remaining checks before decode allocation, per-field and collection limits, aggregate elements1024, cumulative binding-value budget4MiB, total frame8MiB including duplicated bindings. Every Knowledge variant/reason/evidence is preserved. Usage counts nonnegative with cached tokens independent; exact BigDecimal scale/precision/native/fiat/tix retained and bounded, no Double. Hashes require SHA-256/64 lowerhex. Diagnostics are static and omit exception/body text. Future format is UNSUPPORTED; malformed frames/data/metadata CORRUPT_DATA; invalid encode INVALID_INPUT.

Focused tests authored: multilingual exact prompt/bindings/provenance/snapshot/historical version, absent/unknown/knownzero/cached/native-decimal usage, metadata-only content byte equality and recovery reconstruction, metadata mismatch, truncation/trailing/version/tag/UTF8/hostile length, Unicode/string/binding/cumulative/whole-output/evidence budgets, negative counts/hash rejection. One large artifact is produced by actual LocalOfflineDeckPrompts with actual DeckSnapshotValidator, no catalog I/O, and roundtripped as offline content; private notes and Unicode exercise rendered+binding duplication.

Verification: source/API/local-pattern inspection performed; `git diff --check` performed (new untracked files additionally inspected directly). All compiler/Gradle/KSP/lint/tests/runtime/network = **NOT_RUN** by root instruction. No commits/pushes/children/process intervention. Source freeze is reviewable, not a runtime acceptance claim.

Next action: root independent source review of these exact blobs, then root-authorized assembled compile/tests (including the actual-engine artifact test) and any requested bounded corrections. Root alone owns integration/commit permission. Worker temp directory `/tmp/cl-ai-history-codec-worker-01`.

## Latest continuation delta

SOURCE02 addresses independent Astra SOURCE01 HOLD: codec now rejects provider PENDING with nonempty raw or Known/Absent usage. Partial-content lifecycle fixture uses STREAMING and verifies unchanged bytes plus retained unknown usage on INTERRUPTED metadata recovery. Added valid pending roundtrip, invalid pending content/usage encode and forged-PENDING decode rejection, and empty-pending recovery byte preservation. Static inspection only; compiler/tests remain NOT_RUN. No commits or shared edits. Next action: root independent SOURCE02 review, then root assembled gate.

## Source SHA256

- `ce552af55e9999458c25d72b87677824a41651ab1dfef634eae8d34e141430f3` `android-app/data/storage/src/main/kotlin/com/architectpsg/commandlibrary/data/storage/ai/codec/AiHistoryPayloadCodec.kt`
- `902ca38c1d7d569c9ae30406581597a37a6223382803bd5b8ca49a24c3014abc` `android-app/data/storage/src/main/kotlin/com/architectpsg/commandlibrary/data/storage/ai/codec/AiHistoryBinary.kt`
- `d3c4d2fcc2f867a6a7b70e8d94a0a8a97ff9f55f34f5c6e9fc7434adf06c35e5` `android-app/data/storage/src/main/kotlin/com/architectpsg/commandlibrary/data/storage/ai/codec/AiHistoryWires.kt`
- `635ffd6d570b24caac3536eff1ca96a2d9b04fba0ef47fbb50d47e87ff39176c` `android-app/data/storage/src/test/kotlin/com/architectpsg/commandlibrary/data/storage/ai/AiHistoryPayloadCodecTest.kt`

The exact five-path manifest including this handoff is returned separately to avoid a self-referential hash.
