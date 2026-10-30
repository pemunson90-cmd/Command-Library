# Ordinary artwork NEW5 return — 2026-10-30

## Latest continuation delta

Implemented only the delegated NEW5 source on branch `agent/campaign-ordinary-artwork-access-20261030`, baseline `7f822e6c746cf7411f1f7030b1d08a734b1694aa`, worktree `/workspace/scratch/893bce609ce4/artwork-access`. Root published takeover/TAKEN at `0df0423d`; inherited 2026-10-16 claim files in this isolated baseline are historical, not new shared coordination writes. No commits, pushes, builds, compiler, network requests or children were invoked by this worker. Root retains contracts, AppGraph, consumer, configuration, shared coordination and integration.

Read equivalent AppEnglishCatalogAccess, LocalAcquisition dispatch admission, AndroidCatalogServices connection observation and existing app Robolectric fixtures before implementation. Tests were authored first; the requested execution prohibition means there is no observed failing or passing test cycle.

## Source and authority boundary

`OrdinaryArtworkProfile` prepares local-only English Scryfall printing offers with exact id/provider/non-null requested generation. Parent image list and up to 16 actual face locations supply at most 17 offers; actual indices and face artists (including Absent/Unknown) survive. The model supplies no parent artist field; parent artist remains explicit Unknown. Duplicate variant/location metadata, duplicate face indices, wrong face printing/language and oversized bounded identity/version/artist/image metadata fail closed. Legacy imageUri and inferred URLs are never used.

A uniquely present normal JPEG is preferred; absent normal allows a supplied grid WebP. A malformed present normal rejects instead of falling back. Only exact canonical HTTPS cards.scryfall.io authority, optional literal :443, nonempty absolute path, no credentials, fragments, backslashes, controls, traversal or encoded separator/authority ambiguities pass. Supplied query timestamps remain unchanged. Length-framed SHA-256 binds provider/printing/generation/actual face/variant/exact URI into deterministic bounded id and version. DownloadIntent is IMAGES, Scryfall, English, unknown expected bytes, no pack/checksum.

`AppCardArtworkAccess` never approves or updates settings. Fresh exact-offer equality and exact existing ConsentRecord permit admission. Each request has an identity-bound RequestSpec, GET/no body, correct MIME Accept, identity encoding, accurate application User-Agent, no endpoint secret, one NEVER_AUTOMATIC attempt, 30s budget and 3MiB compressed ceiling. Shared ConsentCoordinator covers fresh lookup, effective settings, actual validated internet/Wi-Fi check and physical enqueue without holding the gate during transfer/decode. A per-load dispatch latch rejects every second enqueue, including exact-URI self-redirect; equal copied specs and alternate destinations fail. Real defaults are OkHttpNetworkClient and Android ConnectivityManager. Internal constructor seams supply transport/connection and decoder only for bounded adapter/concurrency tests.

A structured 100ms monitor cancels active work on consent loss, stale generation or any local non-success; final shared-gate permission check precedes exposure. Permitted is local-only and does not invalidate existing pixels solely because the device goes offline. One token-aware admission mutex remains held through all child/native decode unwinding; no detached jobs/scopes. Per-request clients close after transfer/monitor settle, including cancellation. Unexposed pixels recycle on failure/cancellation, returned pixels do not recycle. No automatic replay/retry/cache/files/database or user-data mutation exists in these internals.

`LocalArtworkDecoder` validates bounded nonempty bytes and JPEG or RIFF/WebP signatures; strict WebP permits VP8/VP8L with optional flag-free VP8X, rejects animation, metadata/unknown chunks, malformed lengths/padding and duplicate image chunks. This intentionally conservative profile can reject otherwise valid static WebP with optional metadata/alpha chunks. Bounds-first BitmapFactory requires positive <=2048 edges and <=2,000,000 pixels, matching MIME, unscaled software ARGB_8888; actual dimensions/config/allocation <=8MiB are rechecked with cancellation checks before/after platform decoding. No transformations or resizing.

## Verification and open gates

Execution: **NOT_RUN** for all 16 Robolectric methods, production compilation, Android native codec, actual TLS/upstream bytes/headers, device/Fold7/rendering/accessibility/performance and release gates. Environment has Java17 but no Android SDK/Gradle cache; root's wrapper attempt is network-blocked. No runtime or upstream fidelity claim follows from authored tests or source inspection.

Authored synthetic tests cover parent/multiface offers, URI and metadata rejection, no implicit wire/approval, stale/tampered offers, Wi-Fi admission versus offline local display permission, spec identity/second dispatch/destination, consent recheck at enqueue, bounded headers/body/signature/codec dimensions, revocation/settings failure/generation replacement, noncooperative decoder ownership, one successful request ownership via an explicit synthetic decoder seam and static WebP chunk rules. They are not authentic upstream fixtures. Test settings writes throw, while production contains no file/persistence calls; no real filesystem mutation instrumentation was executed. Native successful JPEG/WebP decoding, exact decoder-allocation rejection and real OkHttp redirect integration still require executable gates; the guard adapter tests do not claim socket evidence.

Read-only checks performed: owned-path git status, source inspection, SHA-256 manifest and test-method count (16); git diff --check emitted nothing, but new untracked files are not covered by that command. Independent source/security review is required before root integration; root must run actual compilation/tests and preserve all open runtime/native/source acceptance limits.

## Frozen production/test hashes

All paths relative to the worktree above; the handoff's own final digest is returned separately to avoid a self-referential checksum.

| Path | SHA-256 |
| --- | --- |
| android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/AppCardArtworkAccess.kt | 248da7f296f55a292510cefc017f8c95527448cd0380dcb8b0ea489c0e21f3ec |
| android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/OrdinaryArtworkProfile.kt | de085d60373705852e69fbe0607857f20af5a1ffbdf5c6f24a044c387918f92f |
| android-app/app/src/main/kotlin/com/architectpsg/commandlibrary/di/LocalArtworkDecoder.kt | e4a39e9aa91fafbd656a7e50aba03d6b4a98e0779cb43d719e0ae39376937ee9 |
| android-app/app/src/test/kotlin/com/architectpsg/commandlibrary/di/AppCardArtworkAccessTest.kt | 9ae6bcf6bd3b9aa5b3483e8758498b6f6034003b701c329f4f188e784e4f21b7 |

Exact next action: independent frozen-source security/concurrency/decoder review, then root integrates only these NEW5 paths alongside its separate consumer/DI changes, compiles and executes targeted actual tests in the root-owned build slot. Worker source is frozen pending review.
