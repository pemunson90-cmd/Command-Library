# cx-campaign-deck-codec-01 — implementation handoff

Branch `agent/campaign-deck-codec-20261015`; exact accepted base `f9fc8d4c1967daaf6d1174ecba3005502c234f85`. Worktree `/workspace/command-library-deck-codec-01`; top/branch/HEAD/common Git directory verified before edits. Only inherited Git environment override was `GIT_PAGER=cat`. No shared source, coordination, model, schema, build, DI, life or registry edits; no push or integration performed.

## Latest continuation delta

Implemented complete public `DeckPayloadCodec : DeckPayloadCodecPort` in the three allowed `data/storage/decks/codec` source files. CLDK unsigned16 version1 preserves the complete snapshot and declared nested field order, including primer, nullable versus explicit-empty category registry, all Knowledge reasons/evidence, source-qualified and raw foreign identity, exact decimal unscaled integer/scale/unit, ordered metadata/references/overrides and package contributions. This is structural serialization and grants no graph, source or persistence authority.

Each call owns its reader/writer state. Snapshot size is checked before copying; UInt32 lengths/counts enter Long before validation/narrowing; collection charges precede list allocation; text/ID Unicode is strict and bounded before encoding allocation; lengths must fit both field cap and remaining bytes before decoder allocation. Decimal precision is checked before unscaled stringification, integer grammar/digit count before BigInteger, and Int.MAX_VALUE scale never expands to plain text. Failure messages contain no exception/user input. API23-compatible in-memory JDK stream methods only; no external IO.

Two allowed test files plus a byte-identical copy of the independent golden cover all populated DTO fields and Knowledge variants, foreign IDs/Unicode/pinned references/packages, stable enum names, deterministic encoding, null/empty categories, zero/native/fiat/tix/exact/huge-scale money, every golden truncation, malformed framing/tags/enums/constructors/UTF, UInt32 fences, exact 8MiB and 100000-element successes, and over-limit encode/decode fences. Decoder regressions separately exercise 4097 in-frame integer digits, frame length4098, unknown DecimalUnit, entry/category caps, and cumulative nested list overflow. A package-quantity probe uses a unique full sentinel to avoid matching `golden-deck`.

The fixed synthetic app-owned 125-byte golden was copied, never regenerated. Hex-file SHA256 `9d8c0c9b7194ce8fa587f940f70f7820a625f765cae0c81bfa4b292628bece35`; decoded golden bytes SHA256 `d72b3abd9d223ce8e0bbc91fbbce384022e3a5ac79e0783894281c84af5f04a8`. It is not authentic vendor interchange data.

## Actual verification

Root granted the sole Gradle slot; no edits occurred during each build. Configured JDK17/Android SDK/shared Gradle cache used; `TMPDIR=/tmp/cl-deck-codec-01/tmp`; inherited `JAVA_TOOL_OPTIONS` preserved with task-specific java.io.tmpdir appended. Exact command:

```sh
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 :data:storage:testDebugUnitTest --tests '*DeckPayloadCodecTest' --tests '*DeckPayloadBoundsTest' :data:storage:compileDebugAndroidTestKotlin
```

- r1: SUCCESS 2m28s, 31 executed tasks, production and Android test Kotlin compiled; fresh 15 tests (6 codec + 9 bounds), zero failures/errors/skips. `/tmp/cl-deck-codec-01/evidence/focused-r1.log`, `r1-xml/`.
- r2: FAILED 17s at unit-test compilation, excess closing parenthesis in the newly added raw-presence fixture; no new runtime results claimed. Corrected by naming the rawIdentity fixture. `/tmp/cl-deck-codec-01/evidence/focused-r2.log` retained.
- r3 FINAL: SUCCESS 8s, 31 tasks (3 executed, 28 up-to-date); fresh **18 tests** (6 codec + 12 bounds), **0 failures, 0 errors, 0 skips** across two XML files. Android test Kotlin up-to-date from the successful r1 compilation; production source unchanged. `/tmp/cl-deck-codec-01/evidence/focused-r3.log`; exact XML copies under `/tmp/cl-deck-codec-01/evidence/final-xml/`.
- `git diff --check` passed. Fixed golden source/copy hashes match. Initial API-absence/red run was explicitly waived by root under D28; meaningful final regressions were executed.

Final source SHA256:

| File | SHA256 |
| --- | --- |
| DeckBinary.kt | d787c7cecc9a3c9623b7f4c7f8b4678f0188977dc7951afd43655dbd318a6f6c |
| DeckPayloadCodec.kt | 1705a840dc2efccfb64424ef31015a9847a34d44a64a85bec16fe8bc4781cb08 |
| DeckWires.kt | 9c7c0a2e0c2cada2758eb1209955d10cc3329ea32f5372594f7d9d1005445f83 |
| DeckPayloadBoundsTest.kt | 89b0edd5293dc2ef0d71d52cb2e42d0047ab6feaa80ee440086ba896b94047f7 |
| DeckPayloadCodecTest.kt | 82db7883b5f5da4262b42d0a77de540e7cee1026158fde62a466d63a3314b157 |

No production defect remains known. Root independent exact-source review and integration remain the next action; root retains persistence/schema design and assembled acceptance. Sole Gradle slot released after r3. Full storage/full project suite, emulator/native/physical execution, real persistence/Room/CAS/undo/restore/transfer/source-license acceptance, APK and runtime memory/performance acceptance are **not_run** for this packet. The 8MiB bound is on wire bytes and is not an equal backing-buffer/heap bound. Frozen native `9c1573b` failure remains unrelated and unchanged. No persistence/feature/device acceptance claim.
