# Android life platform sources — local verification2026-10-14

Implemented app-internal AndroidLifeClock using elapsedRealtimeNanos (includes device sleep), immutable process-wide UUID EntityId, and JDK SecureRandom bounded draws. Existing catalog SystemUtcClock unchanged. No core/schema/codec/CI/AppGraph/ShellPorts/production dependency change; app test-only life edge supports an explicit unregistered service probe. App GameService binding remains HOLD on retained named-variant/source prerequisites. Independent architecture/exact provider/corrected native-probe review APPROVE; no reviewer build/runtime claim.

## Actual red/failure/green sequence

API-absence RED293s: tests fail to compile on the three missing providers. First candidate558s compiled source/native probe but failed before any provider method: Robolectric SDK36 sandbox requires Java21, accepted environment has Java17. Corrected to SDK34 matching existing shell tests, no toolchain repin. That run318s executed4 methods: sleep/UTC/RNG passed; identity used assertSame on a boxed @JvmInline EntityId, failed despite identical UUID. Corrected both host and native probes to value equality. Production provider code unchanged. Earlier independent review also corrected whole elapsed-state equality after undo: legitimate new clock anchor must be retained while elapsed totals/lineage/paused state persist.

Final whole command: gradlew -p android-app --daemon --console=plain check :app:assembleDebug :app:assembleDebugAndroidTest :data:storage:assembleDebugAndroidTest. BUILD SUCCESSFUL474s/7m54s,409 tasks37executed372up-to-date. Actual XML96 files/954JVM,0failures/errors/skips, including all4 provider methods. Reuse local Gradle daemon for subsequent implementation to avoid repeated startup/JIT overhead; product/toolchain versions unchanged. Logs: /tmp/life-platform-sources-whole.log (executor-local, not durable source evidence).

Four meaningful host-shadow cases: elapsed-realtime includes simulated7s deep sleep while uptime stays unchanged; real wall UTC conversion plus negative epoch milliseconds floor correctly;100 processID reads retain identical UUIDv4 value; real SecureRandom respects1/small/non-power-of-two/Int.MAX_VALUE bounds and rejects0/negative. Range tests do not statistically establish uniformity. Robolectric SDK34 is not API36/API23 device or hardware sleep acceptance.

## Packaged checks

- App APK19718770bytes SHA25604df5b798ee206d297eef75cf2545bab34daa832a8be5b0e9df1e9ec86d6de6e.
- App testAPK1487659bytes SHA2568974d2cba41522badadae30f7d439194aab5246f35cf1135b9497b54a16a0011.
- Storage testAPK10273143bytes SHA2562a940792c5431f2b0d6e65f86c51718b3c2bfb0c558c171d9cb939cd658b2b8d (unchanged).

Each independently passed SDK36.0.0 zipalign -c -P 16 4 exit0. Actual app test DEX contains LifePlatformIntegrationTest and SqlPersistedGameService. Explicit isolated Room probe uses real providers/public service to create/read/record dice/undo/file reopen, asserts exact retained random outcome and corrected timing semantics, closes/deletes only its own unique DB. This probe is compiled and packaged, NOT RUN. No OS process-kill/native provider/hardware deep-sleep/API23/physicalFold7/TalkBack or final release claim. Existing950JVM facade evidence is separate from frozen PR21's908JVM/134native gate; do not mix product heads. No APK delivered.

Next execute reviewed independent profile-library plan inline. Its complete profile CRUD port may be assembled without registering GameService; preserve exact revisions/advanced data/cancellation and honest UI/source limits.
