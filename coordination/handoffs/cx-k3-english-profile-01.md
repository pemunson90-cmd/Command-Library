# cx-k3-english-profile-01 handoff

## Latest continuation delta

Base HEAD: `89e17106d1598c162179b7530a0766030e4a7d4d`. New uncommitted component files only:

- `android-app/feature/catalog/src/main/kotlin/com/architectpsg/commandlibrary/feature/catalog/TrustedEnglishSearchProfile.kt` SHA-256 `62c88517aa2728fda77877af33f2182565d4784ed3d695d4ba56940fba1838cf`
- `android-app/feature/catalog/src/test/kotlin/com/architectpsg/commandlibrary/feature/catalog/TrustedEnglishSearchProfileTest.kt` SHA-256 `0e5f40d969fd792a07dc4a92c99fefb08b6cf30add74d73865051543b8c6aa39`

The internal profile generates the fixed English search request with bounded caller display label and informational Wi-Fi preference. It compares a fresh canonical request for typed `INVALID_INPUT` admission, rejecting caller changes to scope, policy IDs, limits, checksum, source version/date, expected bytes or notice. Endpoint selection revalidates the request and accepts only codec-valid same-scope page URIs within 2,858 pages. It supplies the fixed no-secret, no-redirect-origin endpoint and single-attempt retry policy. The engine separately enforces actual-versus-requested wire URI equality; this helper alone does not enqueue or grant consent.

Verification, run centrally by root from `android-app`:

- RED, before implementation: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest --tests '*TrustedEnglishSearchProfileTest*'` exited 1 at `compileDebugUnitTestKotlin` because `TrustedEnglishSearchProfile` was unresolved, with cascading inference errors. No runtime test ran in RED.
- First GREEN: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest :feature:catalog:lintDebug` exited 0. Catalog tests and lint passed; lint had zero errors and retained warnings.
- Final GREEN after a comment correction and page-2859 denial test: `bash gradlew --no-daemon --console=plain :feature:catalog:testDebugUnitTest` exited 0; 15 XML reports, 141 catalog tests, 0 failures/errors/skips, including 4 profile methods. `git diff --check` passed.
- Independent Astra-high review: APPROVE of final production helper SHA above, no blockers. Reviewer kept factory, physical wire consent, User-Agent, JSON request header, redirect/pacing, disk reserve and staging gates as separate work. No native matrix was run for this helper; hosted candidate `89e1710` predates it and is not its verification.

Exact next action: root owns the separately reviewed guarded factory/AppGraph consumer step. Wire this profile's request validator and endpoint selector only through the sole guarded composition, then verify consent, physical enqueue, headers, pacing, and disk checks at their actual enforcement sites. Keep bulk, Oracle, rulings, rules and scheduler outside this English search profile.
