# Life action inputs and codec2 — 2026-10-13

Independent design and exact-source review approved nineteen remaining action request families, explicit seat outcomes with compatible empty defaults, and codec2. Existing reducers still reject actions they do not implement; this packet does not register a persisted service or operational UI.

Codec2 preserves tags1–11, appends tags12–30, and adds canonical GameCreation root6. Decode accepts formats1/2 with each nested frame's own version; format1 rejects new tags/root6. The genuine frozen codec1 snapshot fixture is3133bytes, SHA256 `30399a4a83a2395e16fe3d3b1576937710f07f9b793818870cc174f35da1cd10`. Tests retain metadata, decode legacy results, roundtrip all new requests/outcomes, and read mixed-version archive snapshots.

API-absence RED failed in45s; declaration-only behavior RED had3 failures among70 model cases. Local model/life/storage GREEN passed in2m3s. Final whole-app `check :app:assembleDebug` passed in5m42s:65 XML reports,641 JVM cases,0 failures/errors/skips. Debug APK16KiB ZIP alignment passed; SHA256 `5903350b7af133114b303ebf2dfab0498ed8ac1a54ed1317e29412c29e251d91`.

Future creation retry comparison must preserve original codec version and compare exact bytes within a version, or decoded GameCreation equality across versions. A changed codec header must not invalidate a legitimate original operation. Checked service graph/permission validation and actual persistence remain subsequent work.
