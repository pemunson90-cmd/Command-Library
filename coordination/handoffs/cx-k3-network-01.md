# cx-k3-network-01 transport handoff

## Latest continuation delta

Focused hosted-compilation correction on `agent/cx-k3-network-01`, previous published head `964af05bd4af142e3101588e1384ac41c96f8414`. Root exact candidate `c0bd1f0` failed hosted run `38321835421`, job `115051510696`, because Android compile stubs do not expose Java 9 `java.net.Authenticator.getDefault()`. Changed only `android-app/data/network/src/test/kotlin/com/architectpsg/commandlibrary/data/network/OkHttpNetworkClientTest.kt` and this owned handoff; no production/shared/config edits.

The JVM-only global-credential fixture now obtains the previous authenticator with `Authenticator::class.java.getMethod("getDefault").invoke(null)` and a nullable Authenticator cast. It retains the actual prior global instance and restores it in the same finally block. All Cookie/Authorization absence, exactly one 401 wire request and zero global authenticator invocations remain unchanged. Reflection does not silently default or skip: failure to obtain the original JVM global authenticator fails the fixture.

Verification actually run: Kotlin compiler-embeddable 2.2.10 compiled actual core-model main sources plus all network main/tests against stdlib 2.2.10, coroutines 1.10.2, JUnit 4.13.2, OkHttp JVM 5.5.0 and Okio JVM 3.18.1; zero diagnostics, exit zero. JUnitCore on host OpenJDK 21.0.12.1: **OK (29 tests), 8.812 seconds, zero failures**. Classes `/workspace/scratch/network-host-api-fix-classes`; `git diff --check` passed. The prior production transport/remediation remains code commit `8a3bf33ee7d84e0f763310754a41c3aad3c17040`; detailed transport evidence/limitations are retained in the previous handoff revision.

Reproduce: `java --add-modules jdk.httpserver -cp '/workspace/scratch/network-host-api-fix-classes:/workspace/scratch/kotlin-probe/lib/*' org.junit.runner.JUnitCore com.architectpsg.commandlibrary.data.network.OkHttpNetworkClientTest`. Compiler command uses K2JVMCompiler `-no-stdlib -no-reflect`, explicitly classpaths the stated jars from `/workspace/scratch/kotlin-probe/lib`, and all `.kt` files under `android-app/core/model/src/main/kotlin` plus `android-app/data/network/src`.

Not run: hosted Android compile/build/lint/native lanes after this correction; no local Android SDK. Optional `-Xjdk-release=8` API-surface compile attempt could not execute because the installed JDK lacks ct.sym; this is not reported as a passed Java8/Android build. Production TLS/native/coalesced-HTTP2 and DNS-lifetime limitations remain unchanged: canceled consumers are bounded, but an uninterruptible platform DNS worker may persist until resolution returns. No final/K3 acceptance claim.

Exact next action: root cherry-picks the focused fix commit containing this delta, reruns the exact-root hosted JVM compile/tests and required Android checks, and proceeds with independent transport review/integration using actual hosted results. No new dependency or public-contract request.
