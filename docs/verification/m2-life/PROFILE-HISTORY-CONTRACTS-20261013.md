# Profile/history/persistence ports — 2026-10-13

Independent Astra design and exact-source APPROVE: LifePersistenceContracts.kt SHA256 `91d692b460ec1ca6ff1503a6c520e84f04ad680e173dd4e862263b13f851c305`; TransferContracts.kt `6e1db2035c9990d2236900fc158acc83a66b3c892c1b65710167da519df76199`; tests `185195b6f124c172042ec3e8d7e2a52183070ea777a0e1bce93204efeca81384`. Reviewer builds/tests not_run.

New immutable player setup validates explicit roles, unique IDs, matching LIFE/preferred-life and positive adjustment step. History filter bounds compare UTC seconds/nanoseconds without subtraction overflow, preserving equal empty intervals and supplied names. A page total cannot be negative or smaller than its returned items. Optional ProfileExchange.lifeSetup requires exact structural agreement with the existing name/life/counter fields while retaining art and app preferences; absent setup retains legacy construction without certifying a complete profile.

PlayerProfileRepository and PersistedGameService are declaration-only ports with no default fake/unsupported implementation or DI registration. Legacy GameService signatures remain. Actual query filtering/cursors/read snapshots, profile identity remapping/deletion safety, compatibility methods, codecs/import repair and transactional persistence remain implementation gates.

Root verified API-absence compile RED separately. Meaningful declaration-only behavior RED executed10cases/6failed invalid-input assertions (59s). Implemented checks then GREEN `:core:model:test :feature:life:test` (51s):54model+81life cases, zero failure/error/skip. Earlier whole-app state-unit580JVM/coherent build and focused35native341b719 evidence are separate exact-source results; neither is claimed as a native run of these unconsumed declarations.
