# Lazy English catalog access — local boundary, 2026-10-14

Root now supplies optional complete EnglishCatalogAccess at the tail of ShellPorts. Offline AppGraph startup constructs only the lazy owner; its separate mutex initializes the real AndroidCatalogServices/OkHttpNetworkClient, forwards the exact physical WireDispatchGuard authorizer, enables acquisition429 metadata, recovers once and caches only a successful session. Failure/cancellation does not publish a partial session. Verified install resolves catalogSource and delegates to the same factory's pinned VERIFIED_ARTIFACT bulk importer; an empty caller flow is correct only because that branch opens actual pinned bytes internally. Owner close disposes its transport, not shared settings/database. Existing module versions unchanged; permitted root bootstrap network dependency and INTERNET permission added.

Independent public/security/durability/composition source review: APPROVE. Controller/UI batch remains next, so no new download button or finished-app claim yet. Plan: docs/superpowers/plans/2026-10-14-live-english-downloads.md.

## Actual local evidence

Accepted JDK17/SDK/Gradle environment. Final focused invocation:

`bash android-app/gradlew -p android-app --daemon --console=plain :app:testDebugUnitTest --tests '*EnglishCatalogAccessIntegrationTest' --tests '*RealHostDirectoryLinuxTest'`

SUCCESS1m3s,133tasks/2executed/131up-to-date. Actual2XML report6tests,0failures/errors/skips. Four real factory/Room/DataStore cases6.657s; two host filesystem bridge checks18.022s. Cases cover actual offline AppGraph capability/cache, concurrent lazy-session identity, no repeat recovery on cached open, a persisted simulated orphan becoming INTERRUPTED on owner reinitialization, pre-entry cancellation, failed directory initialization retry, unapproved start rejection without consent creation, unverified installation rejection without active-catalog writes, and owner close retaining shared-store access. This is simulated recovery, not a killed Android process.

Initial fixture compilation used Database.execute instead of transaction.execute; corrected. Initial execution44s ran4cases/3failed because pinned Robolectric4.17 ShadowLinux does not implement missing-path/directory descriptor behavior. A temporary direct-factory diagnostic49s established the actual private-directory admission failure and was removed. The scoped bridge's descriptor-after-close check then caught another default stub: ShadowBlockGuardOs.close is a no-op. The final bridge explicitly forwards close and the final six-case pass supersedes prior incomplete cleanup evidence.

## Filesystem scope and remaining evidence

Host-only @Config wrappers use actual Linux NOFOLLOW Unix attributes, real FileChannel directory descriptors, descriptor identity via /proc after path replacement, force(true), and actual close/invalidation/EBADF. Missing, regular, directory and symlink cases are distinct; permission/I/O errors are not converted to missing. JDK reflective access is enabled only for host tests. Both shadows are scoped to the affected host classes; the separate device wrapper runs the same four cases against actual Android Os. No production fallback, no-op durability callback or transport override was added.

This bridge supports the tested existing-directory operations; it is not general Android open/O_CREAT emulation. Non-directory fstat still delegates the default shadow, and unused block-accounting fields are unmodeled. These checks do not prove regular-file artifact-provider verification, a successful verified install, mid-construction cancellation, a physical-enqueue counter, Android ABI/filesystem behavior, genuine full-corpus acquisition or Fold7 acceptance. Exact root wire binding has source review; existing transport/acquisition guard evidence remains separately scoped. No new APK/native/emulator run or finished delivery. Run broader verification at the completed controller/UI integration boundary under D-28.
