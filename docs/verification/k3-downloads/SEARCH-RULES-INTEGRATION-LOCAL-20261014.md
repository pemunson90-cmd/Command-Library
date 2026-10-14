# Combined search and rules local boundary

Source: `8908e4688bea650ac8e8a33874ecfa7f6e2f8e40`; docs-only successor38e9c58 has identical Android inputs. Completed2026-10-14 UTC. This meaningful integration boundary includes private artifact permission recovery, typed query parsing, indexed Oracle text, local name suggestions and exact installed glossary links, alongside prior ruling source/root/UI and profile Back-test corrections.

Actual fresh single-use Gradle daemon under umask0022, JDK17 and the configured local Android SDK:

```
bash android-app/gradlew -p android-app --no-daemon --console=plain check assembleDebug assembleAndroidTest
```

SUCCESS9m10s;592tasks114executed478up-to-date. All129XML reports contain1233tests, zero failures/errors/skips. `check` includes local lint. Reports copied before any focused rerun to `/tmp/search-rules-boundary-xml`; full log `/tmp/search-rules-integration-boundary.log`. Existing unchanged outputs may be up-to-date; this is not a claim that every test reexecuted.

All eight assembled APKs pass build-tools36 `zipalign -c -P 16 4`. Actual hashes/sizes below; local manifest `/tmp/search-rules-boundary-apks.json`. ZIP alignment does not prove device page size or execution.

| APK | Bytes | SHA-256 |
|---|---:|---|
| `android-app/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 1478420 | `67bc4e7ba003d69f108ec271e30f03ae39ec7e37d5138f0377b19cdbd86df8a4` |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 26776528 | `df40ec726552a1eb54fd820b007d88173a5462cc219012ad4c1996b59b7da2bb` |
| `android-app/data/network/build/outputs/apk/androidTest/debug/network-debug-androidTest.apk` | 11625458 | `021f8e692e56c5929ffb6b7774cb9091eb6ad0b286c63536c31dc323aa9c895b` |
| `android-app/data/storage/build/outputs/apk/androidTest/debug/storage-debug-androidTest.apk` | 10287445 | `d5ed7e99f43c1dae717cbe4a4aa99ba34ed625fac2061a9930e3d65212945f26` |
| `android-app/feature/catalog/build/outputs/apk/androidTest/debug/catalog-debug-androidTest.apk` | 11172273 | `96aaf88346c7ed49d79680122d421caf98dc7aa2615709252a11ad5928d3b438` |
| `android-app/feature/rules/build/outputs/apk/androidTest/debug/rules-debug-androidTest.apk` | 9866817 | `6d03383b98902597b77cc681d828adfd50650775dd92c9e59450a1ae0662fae0` |
| `android-app/feature/settings/build/outputs/apk/androidTest/debug/settings-debug-androidTest.apk` | 2842148 | `134cacac1ba51dd783e2090e684631ecbbe3e2f59460aeb20716ccdb78ab74a6` |
| `android-app/feature/shell/build/outputs/apk/androidTest/debug/shell-debug-androidTest.apk` | 15345302 | `4ca0cf2d669b7d01a0c03e5bbf7debff1b5cf0a1e88f2da86146dd4c2b7944b5` |

Independent reviews for the changed public contracts, source identity and permission/durability boundaries are recorded in the component reports. No new source changes occurred during this gate. No device, live publisher TLS/full-feed, physical Fold7, performance, canonical release or completed-app acceptance is claimed. The previous frozen9a2f625 native snapshot failed before device execution and remains unchanged; unpublished older local r3 is superseded for scheduling. Next: ONE new current isolated snapshot using the existing modern API36 workflow, then continue local implementation.

Publication authentication was restored by the environment restart; source8908e46 and docs38e9c58 were pushed with exact remote readback. Earlier authentication-failure reports describe their original measurement time, not a current blocker.

The older private genuine CR path `/workspace/scratch/k3-genuine-inputs/wizards-comprehensive-rules-20260925.txt` is absent on current readback. Historical parser/hash evidence remains retained; no fresh genuine-corpus validation was performed or inferred.
