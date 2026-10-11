# K1 Kotlin toolchain readiness and architecture translation

Status: in progress. Recovery checkpoint only; not a completed section and not a compatibility approval.

## Recorded environment observation

Observed 2026-10-11 UTC in the active workspace:

- Java runtime: OpenJDK 21.0.12.
- No `javac`, Gradle, Kotlin compiler, Android SDK manager or `adb` executable was found.
- The workspace is a text snapshot without a Git checkout.
- Direct GitHub read/write and outbound HTTPS access are available.

This means local compilation and Android installation cannot be claimed from this environment. K1 must determine whether a hosted Android build transport can provide the required JDK/compiler/SDK before K2 can begin.

## Official metadata observed

All sources retrieved 2026-10-11; values are candidate discovery only.

| Dependency | Source | Observation | Status |
|---|---|---|---|
| Gradle | `https://services.gradle.org/versions/current` | 9.8.1, final; distribution checksum returned by source | candidate only |
| Android Gradle Plugin | Google Maven `com.android.tools.build:gradle` metadata and official AGP 9.4 notes | metadata latest is 9.5.0-alpha09; latest final numeric entry is 9.4.1 | 9.4.1 source-backed candidate only |
| Kotlin Gradle plugin | Maven Central metadata | metadata latest is 2.5.0-Beta1; latest final numeric entry is 2.4.21 | 2.4.21 source-backed candidate only |
| Room runtime | Google Maven `androidx.room:room-runtime` metadata | 2.8.5 reported as latest/release | candidate only |
| Compose BOM | Google Maven `androidx.compose:compose-bom` metadata | 2026.09.00 reported as latest/release | candidate only |

No version above proves compatibility with any other version, Android API level, Room compiler, Kotlin compiler, Compose compiler or FTS5 binding. K1 must use official compatibility documentation and a targeted hosted compile/probe before marking a tuple validated.

## Next K1 action

The latest final AGP and Kotlin candidates have now been extracted and the AGP/JDK/Gradle release-note compatibility information recorded. The candidate lock and ownership map are under independent Astra-high review.

## Candidate artifacts awaiting independent review

- `config/kotlin-android-candidate-lock.json` contains the source-backed candidate tuple. Android Gradle Plugin 9.4.1 is paired with the AGP 9.4 documented Gradle 9.6.0 and JDK 17 requirements. Gradle 9.8.1 was observed as current but is not selected merely for recency.
- `docs/architecture/KOTLIN_OWNERSHIP_MAP.md` maps all twenty historical components and their dependency edges to Kotlin Gradle modules. It does not create modules or relax any coverage obligation.
- Exact Kotlin/AGP integration and the Android FTS5 binding remain unselected/unproven. A documentation-only hosted capability probe cannot prove either; a later targeted Kotlin compile and packaged FTS5 test are required.

## Outstanding K1 evidence

- Select and source-check Android SDK compile/min/target policy against the retained phone, foldable and tablet requirements.
- Verify a current stable DataStore and Compose compiler/plugin path with Kotlin and AGP; record licenses and compatibility source links.
- Verify the KSP/Room compiler path and its license/compatibility boundary before generating any database code.
- Identify an Android-packaged SQLite/FTS5 binding strategy and its redistribution/license status. Room alone is not that proof.
- Draft a concrete hosted capability-probe workflow with only version/configuration checks, immutable action references, no secrets and no application source; obtain independent review before it is committed or run.

