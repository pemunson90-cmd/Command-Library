# Reversible mapper review — approve bounded delta; no blockers
Reviewed dirty `ScryfallCodec.kt` / `ScryfallCodecTest.kt` against HEAD `376f02a96f84ac543af5d927de9d4dae0923a011`; no repository edits or independent test execution.
Reviewed SHA-256: codec `fcd51536adaaad1861b44bc303f4a8dcfad8bfdef5ea1cf639ff3a18592b8efe`; test `78507a7f839520de168b75f6e8aaeb6b0820876f02ff342882cc26e07cbc727d`.
Missing/null whole Oracle is accepted only for `reversible_card` with exactly two valid face objects, nonblank bounded Oracle IDs, and nonblank names; malformed present IDs remain rejected.
The nullable canonical result creates no invented whole-card identity or borrowed canonical fields; printing Oracle stays Unknown and source face order stays intact.
Each printing face retains source-qualified Oracle and raw provider identity through OTHER; canonical face identity/index remains Unknown, including when both sides name the same Oracle.
Legacy `mapped` still rejects records without whole Oracle; ordinary canonical mapping and printing projections retain their existing behavior. Full raw source retention remains the importer's responsibility.
Tests cover printing-only mapping, face order, unknown canonical face, legacy rejection, unsupported layout, missing face Oracle, and repeated Oracle; optional additions are wrong face counts, identity bounds, and exact OTHER/raw identity assertions. A named result would improve clarity over Pair.
Root reports 22 catalog JVM tests passing; full disk-backed probe is pending. This review claims no full-import, database, UI, Android, or milestone acceptance; next action is finish the probe and implement reviewed bulk persistence/membership.
