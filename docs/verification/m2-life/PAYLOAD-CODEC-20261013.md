# Life payload codec local evidence — 2026-10-13

Recovered codec design from committed requirements and screenshot; no claim of recovering inaccessible original draft verbatim. Independent recovery design and exact-source review APPROVE; no blocking findings.

Implemented typed storage-module roots: GameSnapshot, PlayerProfileDraft, GameEvent, GameEventArchive and GameCheckpoint. Metadata/Knowledge/Evidence/art/variant/provenance/notices retained through explicit constructors and stable tags. ProfileExchange/preferences/whole-history transfer, schema6, service and UI are not implemented by this packet.

API-absence RED: storage compileDebugUnitTestKotlin failed on absent LifePayloadCodec,82s. First local storage/model/life GREEN100s. Reviewer-requested independent archive byte-budget and malformed primitive tests added; final whole-app `bash gradlew --no-daemon --console=plain check :app:assembleDebug` passed in9m3s (335tasks,311executed). Actual63XML JVMreports contain627tests,0failures/errors/skips. New codec tests13,0failures/errors/skips; duration extrema/unit-boundary, complete metadata and action/random branch roundtrips, truncation/header/UTF8/length checks, byte/string/definition/group limits, and two individually>2MiB snapshots accepted together while an individually>4MiB snapshot fails visibly.

Local debug APK18509832bytes SHA256 `9fe70edffa93ff6b007ebdcfdfd5097e37225ff6949632b69543aff711773fe2`; actual SDK36 zipalign `-c -P 16 4` passed. This is an internal debug build, not canonical signing, physical Fold7 or final app acceptance. Existing shell always-true and Coil nullable receiver compiler warnings plus unstripped native debug-library notice retained; no build/test failures.

All product checks above ran locally. Inherited full branch-push trigger unexpectedly queued Android run38477537580 at e86dd16. Narrow independently reviewed recovery-push exclusion910251c retained main/PR/manual execution and gates. GitHub readback confirms old runcancelled and replacement38478212161 skipped; no deliberate emulator dispatch for these model/codec packets.

Schema6 design approved separately; migration/source/SQLite evidence still pending. Next: additive18→24entity migration, actualKSP6 and real local migration/reopen/constraint/CAS/rollback tests, before transactional service. No native/device proof inferred from codec equality.

## Exact codec source hashes

- `LifeBinary.kt` SHA256 `82311f4b3fd5dd94756779656dcb46eb95ba8bbeb99ea7ef0793fe054798e91d`
- `LifeCoreWires.kt` SHA256 `ae623355a5606620f33f29bea861c9fdc8f8bd9bf805caf5d80823ae3ff5acfb`
- `LifeEventWires.kt` SHA256 `5b1a84f3cf4e463a736b4a806f5dfb371ec4c2930933dce35734441a06878bbb`
- `LifePayloadCodec.kt` SHA256 `aa6ce91621327315afaf13b8e667cb50244a057d29bb29773a2fc87236b38cd0`
- `LifeStateWires.kt` SHA256 `b2dff477f9b1a8484c45e8227b1a0fa2efcd88ddafa1f756b437af838c28e269`
- `LifeVariantWires.kt` SHA256 `edbf2a1a376ba4c9baee9b611b6a1c0bc42abec0717cb8f883a7e2264db75b62`
