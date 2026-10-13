# Source-backed execution preflight

Pat requested fewer avoidable errors/build cycles on 2026-10-13. For each bounded implementation task, attach a short brief to its existing task/handoff before edits: exact retained requirements and callable contracts; the relevant version-pinned official API/source or licensed working example; supported runtime/known exclusions; chosen approach; focused verification and remaining unknowns. Reuse the researched pins/examples, investigate only missing facts. A source brief is evidence, not a promise that code cannot fail.

Route narrow missing-source checks to Luna medium, routine implementation to Sol medium, complex durability/security design and independent review to Sol high/Astra when necessary. Do not fan out broad duplicate research or create a separate speculative implementation. Root owns shared contracts/configuration; preserve existing locks and the three-writer limit.

Verification order: actual compatible source compilation and smallest focused checks first; coherent integration check/build next; full native matrix and actual visual/device acceptance after compilation. Local absence of an Android SDK requires the hosted compiler; JVM substitutes cannot establish device codec behavior. Record actual failed evidence and fix the cause without weakening acceptance.

## Concrete CI adjustment

Android K2 first builds `assembleDebugAndroidTest` before executing JVM/check/APK checks. Emulator jobs require successful `jvm-and-assemble`, so instrumentation source compile failures no longer provision all three devices. All original JVM, lint, APK and native test gates remain. This saves failed-run compute; successful full runs may take longer because provisioning follows preflight. One coherent integration push replaces separate repeated matrix requests when candidates are ready.

Official guidance checked 2026-10-13:

- https://developer.android.com/studio/test/command-line — module/variant-specific local and connected tasks, test APK construction/installation before adb instrumentation, actual XML locations.
- https://docs.github.com/en/actions/writing-workflows/choosing-what-your-workflow-does/using-jobs-in-a-workflow — `needs` runs dependent jobs only after successful prerequisite jobs.
- Exact Robolectric4.17 file-decoder exclusion and Coil3.3 path are recorded in PROBE-DECISIONS.json; four unchanged runtime assertions now run on Android rather than an unsupported JVM shadow.
- Exact Compose BOM2026.09.00/ui-unit1.12.1 API confirms DpRect edge subtraction; root corrective review c377542 records the fix without weakening full-target assertions.

Hosted acceptance of the revised pipeline and the two first-batch candidates remains pending; this document does not claim a complete app.
