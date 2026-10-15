# Android scoped vault — source and bounded host integration

Source `fe0c142633d88190e9eb505fac1b297e34d3cc46`, Android tree `092599a583e62a9943a1a1f8dcc46bcab96ff9a9`. Existing H07; no whole-requirement/provider/release acceptance.

Real `AndroidSecretVault` implements put/read/remove with AndroidKeyStore AES256-GCM, exact provider/origin/purpose identities, bounded authenticated envelope, checked AtomicFile commit/removal, explicit key-loss recovery, typed static errors and cancellation. Ciphertext only in noBackupFilesDir; no plaintext fallback. Lazy actual AppGraph binding does not access Keystore at app creation. Existing String API cannot guarantee immutable-string erasure; temporary plaintext bytes are erased. Single-process mutex, no multiprocess claim.

Independent Astra READY02 and SOURCE02/shared composition/native-workflow ACK. READY01 AtomicFile silent-failure HOLD required verified sync/readback/recovery/deletion. SOURCE01 required declared host context API and midread cancellation guard; both fixed. Worker original exact8 commit `28e44b33840e5c72a6823aed3acca74a7ad91fbb`, parent `6601f557df26ae7d953a957a19636953157738a6`, clean original retained, parent/path/liveblob verification `/tmp/cl-android-vault-01/verified-return.json`; root integration `e3f0691`. Claim released.

## Executed local gate

JDK17/pinned existing Gradle/AGP/dependencies, host Robolectric SDK34 matching established app/shell harness. Exact command:

```sh
bash android-app/gradlew -p android-app --offline --console=plain --max-workers=2 \
 :data:vault:testDebugUnitTest :data:vault:lintDebug :data:vault:assembleDebugAndroidTest \
 :app:testDebugUnitTest --tests '*OfflinePromptAppIntegrationTest'
```

Assembled-attempt2: BUILD SUCCESSFUL in2m28s, **15tests/3XML/0failures/errors/skips** =9actual task-local file/JCE/fault/cancel/conservation +5envelope tests +1existing real MainActivity/AppGraph/Room/clipboard offline workflow regression with lazy vault bound. Vault lint and instrumentation APK compilation passed. All714 tracked Android source pins frozen. Host JCE/injected backend is not AndroidKeyStore proof; compiled instrumentation is not executed native tests.

Evidence `/tmp/cl-android-vault-01/evidence/assembled-attempt2/{source.json,summary.json,gradle.log,test-results}`; runner `/tmp/cl-android-vault-01/run-gate.sh` SHA256 `0fec7804cf9bbe0ae7fabaa8d240b97ff31653203cb3fc9ae017459a5fd94925`; log `8aa6157f8d2b2e10a057f4d061425c71b2b4e8bce923b475e564f2781ee5247c`. Independent Solmedium runtime readback ACK: exact HEAD/tree/714pins/runner/log/freshXML/scope verified; collector SHA256 `ddb2b676bca398fdcbbcb72de7b0e8ff0b6a302875f841763de1f751bcd4da8e`.

Failure lineage: assembled-attempt1 exacte3f0691 FAILED in1m, pure5pass plus1Robolectric classMethod initialization failure; ninehost methods did not execute. SDK36 host runtime requiresJava21, installed accepted tupleJava17. Corrected only host @Config36→34 atfe0c142 after observing actual existing sdk34 properties; no production/native change or repin, focused independent sourceACK. Original evidence retained. The rerun tests the same invariants; no checks removed.

## Native and remaining acceptance

Exactfe0c142 pushed to isolated `agent/cx-native-vault-20261015`; focused API36 AndroidKeyStore run **38799223425** FAILED infrastructure provisioning: native APK compiled; sdkmanager emulator download reported Error on ZipFile unknown archive, before emulator boot or any native execution; four authored tests remain unrun. General K2 run38799223673 intentionally skipped by existing native-branch guard; not native success. Continuity38799223796 separate. Job116643566955 log retained /tmp/cl-android-vault-01/evidence/native-attempt1/job.log. Strict verifier correctly failed missing executed XML; no artifact uploaded because no reports existed. Diagnose installed-emulator reuse before next attempt; no source/runtime bug verdict. Read actual job/XML/artifact before credential-use acceptance. Existing native data job settings and old failure lineage unchanged.

Provider profile/key UI and charged generation, reinstall/backup exclusion/recovery, comprehensive security/native/min23/Fold7/accessibility/performance/signing and finished APK acceptance remain open. No real keys/accounts/paid requests used; no APK delivery or main merge.

Native infrastructure repair SOURCE ACK (Solmedium): use installed executable emulator only when version probe succeeds; otherwise require install+successful probe, then separately install same API36defaultx86_64 image/platformtools. No product/native/test assertion change. Add workflow itself to existing native-branch-only path filter so reviewed provisioning changes can be executed through authorized branch push; general K2 skip unchanged. YAML/Bash syntax and branch/path checks pass; hosted availability/boot/native result still unverified. No blind same-source rerun.
