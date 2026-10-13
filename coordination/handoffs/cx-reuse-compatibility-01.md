# Latest continuation delta
Branch: agent/cx-reuse-compatibility-01; source/config base0a65c97 (root294e8b3); final worker SHA via git log.
Actual hosted38343151548 SDK35/NATIVE Coil runtime3/4, no skips; file PNG failed ImageDecoder.nativeBinding “Only supported on Android”.
Upstream Robolectric4.17 native ImageDecoder test lines162-164 excludes V+/SDK35 FileSource; exact URL/hash and failed XML hash in JSON.
Moved all4 runtime cases to root-approved androidTest path AndroidJUnit4/ApplicationProvider; API23-safe File/Base64, file input/assertions/no-network unchanged.
Separate real JVM cache test remains; root supplied only test config/lock changes. Pins CSV1.10.0/Coil3.3.0/Kotlin2.2.10 remain.
Actual fresh K2/std2.2.10 instrumentation source compile0 with real AndroidX core1.7.0/ext-junit1.3.0/android-all; cache1/1 and CSV8/8 pass.
AGP instrumentation build/API35 device run not_run; no production acceptance, render proof or new fallback claimed.
Next: root independent review then filtered API35 connectedDebugAndroidTest CoilCompatibilityProbeTest; all4 actual assertions must pass.
