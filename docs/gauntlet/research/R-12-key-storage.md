# R-12 Secure key storage per platform

Path: `docs/gauntlet/research/R-12-key-storage.md`. Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for all sources below is 2026-10-11 unless stated otherwise.

Status: DRAFT in progress. Sections F1 (Android) and F2 (Windows) are written; F3 (per-stack libraries), F4 (terms and licensing), F5 (threat model), F6 (backup exclusion), Options, Recommendation, Risks, Confidence follow.

## Question

How should Command Library encrypt user API keys at rest on Android and Windows, and which libraries do that in each candidate stack (S1 KMP + Compose Multiplatform, S2 Flutter, S3 .NET MAUI, S4 Avalonia, S5 React Native + RN Windows, S6 Tauri 2)? Feeds GAUNTLET.md 2.H (API keys encrypted at rest, never logged or exported), 2.I (backup excludes keys), and the R-01 secure storage criterion.

## Findings

Conventions: "primary" means vendor or project documentation or source code. "Secondary" means a third-party write-up. All accessed 2026-10-11. Page text was read by direct fetch or by streaming `curl | sed | grep` (nothing saved to disk).

### F1. Android platform

F1.1 Android Keystore protects key material from extraction, not from use.
- "The Android Keystore system lets you store cryptographic keys in a container to make them more difficult to extract from the device." "Key material never enters the application process." "If the app's process is compromised, the attacker might be able to use the app's keys but can't extract their key material (for example, to be used outside of the Android device)." "If the Android OS is compromised or an attacker can read the device's internal storage, the attacker might be able to use any app's Android Keystore keys on the Android device, but it can't extract them from the device." Source (primary): https://developer.android.com/privacy-and-security/keystore (accessed 2026-10-11)
- Hardware backing is conditional: "This feature is enabled only if the device's secure hardware supports the particular combination of key algorithm, block modes, padding schemes, and digests the key is authorized to be used with." Check at runtime with `KeyInfo` (`KeyProperties.SecurityLevelEnum.TRUSTED_ENVIRONMENT` or `STRONGBOX` means secure hardware, for apps targeting API 29+). Same source.

F1.2 StrongBox is optional, slower, and not needed for this use.
- "StrongBox is appropriate for applications requiring the highest level of security, particularly those at risk of physical tampering or side-channel attacks. However, it is slower, more resource-constrained, and supports fewer concurrent operations. For most apps, StrongBox is not necessary." AES 128/256 and HMAC-SHA256 are supported in StrongBox. Request it with `KeyGenParameterSpec.Builder.setIsStrongBoxBacked(true)`; the framework throws `StrongBoxUnavailableException` if unsupported, and the documented remedy is to generate the key without StrongBox. Source (primary): https://developer.android.com/privacy-and-security/keystore (accessed 2026-10-11)
- `setIsStrongBoxBacked` and `FEATURE_STRONGBOX_KEYSTORE` were "Added in API level 28". Sources (primary): https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder and https://developer.android.com/reference/android/content/pm/PackageManager (accessed 2026-10-11)
- `KeyGenParameterSpec.Builder.setKeySize` is "Added in API level 23", consistent with AES-GCM Keystore keys being usable from API 23. Source (primary): https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder (accessed 2026-10-11)

F1.3 Authorizations are fixed at key creation and enforced by the Keystore.
- "Once a key is generated or imported, its authorizations can't be changed." User-authentication binding exists (`setUserAuthenticationParameters`, `BiometricPrompt`), and "If a key only supports biometric credentials, the key is invalidated by default whenever new biometric enrollments are added." Source (primary): https://developer.android.com/privacy-and-security/keystore (accessed 2026-10-11). Consequence for this app: an auth-bound key would lock the user out of their API keys on biometric changes and would force a prompt on every AI call. A key that is not auth-bound is the practical choice (see threat model F5).

F1.4 Keys are scoped to the creating app and cleared by the system on uninstall.
- Keystore access "is normally limited to the app or system component that created the key", with the caller's UID included "to disambiguate keys from different apps". The `clear_ns` permission is "used as a maintenance operation when apps are uninstalled." Source (primary): https://source.android.com/docs/security/features/keystore (accessed 2026-10-11). The page does not spell out "uninstall deletes your keys"; that conclusion is inferred from `clear_ns`. Treat "keys are deleted on uninstall" as UNVERIFIED as a plain statement. Tried: the developer.android.com keystore page, the source.android.com keystore page, the Auto Backup and backup overview pages; none states it directly.
- Updates: no source I read states that keys survive an update of the same package signed by the same key. UNVERIFIED as a documented guarantee. It is the behavior that Tink's and Jetpack Security's designs assume (keys persist for the app lifetime), and GAUNTLET.md section 3 plans one permanent release key, so updates keep the same signature.

F1.5 Keystore keys do not travel with backups, so restored ciphertext is undecryptable.
- Google's own `EncryptedSharedPreferences` reference: "WARNING: The preference file should not be backed up with Auto Backup. When restoring the file it is likely the key used to encrypt it will no longer be present. You should exclude all EncryptedSharedPreferences from backup using backup rules." Source (primary): https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences (accessed 2026-10-11)
- Tink's source says that when a keyset cannot be decrypted because the master key is missing, "This is an irrecoverable error. You'd have to delete the keyset in Shared Preferences and all existing data encrypted with it." Source (primary, source code): https://raw.githubusercontent.com/tink-crypto/tink-java/main/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java (accessed 2026-10-11)
- Consequence: the ciphertext file must live in a location excluded from both cloud backup and device-to-device transfer (F6), and the app must treat "ciphertext present, Keystore key missing or decryption fails" as "no key stored" (ask the user to re-enter) rather than crash.

F1.6 Auto Backup facts relevant to exclusion.
- Default backed up: shared preferences, files from `getFilesDir()`, database files from `getDatabasePath`, external files dir. "Auto Backup excludes files in directories returned by `getCacheDir()`, `getCodeCacheDir()`, and `getNoBackupFilesDir()`." Limit: "Every app can allocate up to 25 MB of backup data per app user." Source (primary): https://developer.android.com/identity/data/autobackup (accessed 2026-10-11)
- Android Keystore is not mentioned at all on the Auto Backup page. Its own guidance for credentials is Block Store (F1.9).

F1.7 Jetpack Security (`androidx.security:security-crypto`) is deprecated; do not adopt.
- Release notes, 1.1.0-beta01 (June 4, 2025): "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore." The latest version on Google Maven is 1.1.0 (the release page dates it July 30, 2025). Sources (primary): https://developer.android.com/jetpack/androidx/releases/security and https://dl.google.com/android/maven2/androidx/security/security-crypto/maven-metadata.xml (version list ends at 1.1.0) (accessed 2026-10-11)
- Cryptography guide: "All APIs in the security-crypto Jetpack library were deprecated in the stable release of version 1.1.0. There won't be any subsequent releases of this library." Source (primary): https://developer.android.com/privacy-and-security/cryptography (accessed 2026-10-11)
- API reference: `EncryptedSharedPreferences` "Deprecated in 1.1.0 ... This class is deprecated. Use android.content.SharedPreferences instead."; `EncryptedFile` "Use java.io.File instead."; `MasterKey` points to `javax.crypto`. Sources (primary): https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences and https://developer.android.com/reference/androidx/security/crypto/package-summary (accessed 2026-10-11)
- Google's current recommendation is therefore "direct use of Android Keystore" plus the algorithm guidance: "If your app requires greater key security, use the Android Keystore system", and prefer AES with 256-bit keys in GCM mode. Source (primary): https://developer.android.com/privacy-and-security/cryptography (accessed 2026-10-11). I found no official step-by-step replacement guide. The DataStore plus Tink pairing in blogs is community consensus (secondary): https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a and https://blog.kinto-technologies.com/posts/2025-06-16-encrypted-shared-preferences-migration (accessed 2026-10-11). Treat it as UNVERIFIED as an official Google recommendation.

F1.8 Tink on Android.
- `tink-android` 1.23.0 is the latest on Maven Central (metadata `lastUpdated` 2026-07-09); the POM carries the Apache License 2.0 header. Sources (primary): https://repo1.maven.org/maven2/com/google/crypto/tink/tink-android/maven-metadata.xml and https://repo1.maven.org/maven2/com/google/crypto/tink/tink-android/1.23.0/tink-android-1.23.0.pom (accessed 2026-10-11)
- Tink ships a thin class `AndroidKeystore` (Android M, API 23 or newer): `generateNewAes256GcmKey(alias)`, `getAead(alias)`, `deleteKey`, `hasKey`; it generates an AES-256-GCM key in the `AndroidKeyStore` provider. It warns the Keystore "can only handle a limited number of requests in parallel" and that on `BadPaddingException` "the ciphertext is not decryptable and retrying will not help". Source (primary, source code): https://raw.githubusercontent.com/tink-crypto/tink-java/main/src/main/java/com/google/crypto/tink/integration/android/AndroidKeystore.java (accessed 2026-10-11)
- Tink's older `AndroidKeysetManager` now says: "We don't recommend using this class. Instead, directly use AndroidKeystore." and "because Android Keystore is unreliable, we strongly recommend disabling it by not setting any master key URI", adding that Tink "runs a self-test to detect such problems and disables Android Keystore accordingly". Source (primary, source code): https://raw.githubusercontent.com/tink-crypto/tink-java/main/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java (accessed 2026-10-11). This is Google's Tink team stating Keystore reliability varies by device, so the design must tolerate Keystore failure (F1.5 handling).
- Tink repo license: Apache-2.0. Source (primary): https://github.com/tink-crypto/tink-java (accessed 2026-10-11)

F1.9 Block Store is not suitable for this requirement.
- "Block Store allows developers to save and restore up to 16 byte arrays", "maximum 4kb per entry", persists across uninstall/reinstall "If the user enables Backup services", and needs Google Play services; end-to-end encryption needs API 29 and a screen lock. Source (primary): https://developer.android.com/identity/block-store (accessed 2026-10-11). Using it would copy API keys into Google's backup infrastructure, which conflicts with GAUNTLET.md 2.H ("never exported") and 2.I ("keys excluded"). Not recommended.

### F2. Windows platform

F2.1 DPAPI (`CryptProtectData`, user scope) is the baseline primitive.
- "Typically, only a user with the same logon credential as the user who encrypted the data can decrypt the data. In addition, the encryption and decryption usually must be done on the same computer." "However, a user with a roaming profile can decrypt the data from another computer on the network." With `CRYPTPROTECT_LOCAL_MACHINE`, "any user on the computer on which CryptProtectData is called can use CryptUnprotectData to decrypt the data" (so never set that flag). Optional entropy is "a password or other additional entropy". The function adds a MAC "to guard against data tampering". Library `Crypt32.dll`; minimum client Windows XP. Source (primary): https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata (accessed 2026-10-11)
- The prompt-struct flow is deprecated: "The prompt-based flow controlled by this parameter is deprecated and will be removed in February 2027." Pass `NULL` for `pPromptStruct`. Same source.
- The page states no per-call size limit. UNVERIFIED for any maximum; API keys are well under 1 KB so it does not matter here.
- The page states no packaging prerequisite (it lists "desktop apps | UWP apps"). That DPAPI works from an unpackaged jpackage-style JVM app is shown in practice by KSafe's Windows vault (F3.1) but is not stated by Microsoft; UNVERIFIED as an explicit Microsoft statement.

F2.2 Credential Manager (`CredWrite`/`CredRead`, generic credential).
- `CredentialBlobSize` "cannot be larger than CRED_MAX_CREDENTIAL_BLOB_SIZE (5*512) bytes", which is 2,560 bytes. Generic target name up to 32,767 characters. `CRED_TYPE_GENERIC`: "The credential will not be used by any particular authentication package. The credential will be stored securely but has no other significant characteristics." Persistence: `CRED_PERSIST_LOCAL_MACHINE` is "visible to other logon sessions of this same user on this same computer and not visible to logon sessions for this user on other computers"; `CRED_PERSIST_ENTERPRISE` is also visible "to logon sessions for this user on other computers" (it can roam), so use `LOCAL_MACHINE`. Minimum client: "Windows XP [desktop apps only]". Source (primary): https://learn.microsoft.com/en-us/windows/win32/api/wincred/ns-wincred-credentialw (accessed 2026-10-11)
- The page defines no per-app access control for generic credentials. That any process of the same user can read them is the java-keyring author's claim (secondary, see F3.1 and F5), not a Microsoft statement. UNVERIFIED from Microsoft docs.

F2.3 WinRT `PasswordVault` (Credential Locker).
- "Lockers are specific to a user. Apps running in an AppContainer (for example, UWP apps) can only access the contents of their own locker (for the current user). Apps not running in an AppContainer (for example, regular Desktop apps) can access all the user's lockers, including those of AppContainer apps." Introduced in Windows 10 build 10240. Source (primary): https://learn.microsoft.com/en-us/uwp/api/windows.security.credentials.passwordvault (accessed 2026-10-11)
- The guide describes roaming: "securely store and retrieve user credentials, and roam them between devices with the user's Microsoft account", "you can only store up to 20 credentials per app", usable "in WinUI as well as other desktop apps, such as WPF and WinForms", and "Only use the credential locker for passwords and not for larger data blobs." Source (primary): https://learn.microsoft.com/en-us/windows/apps/develop/security/credential-locker (accessed 2026-10-11)
- Whether roaming actually occurs on current Windows 11 builds: UNVERIFIED (docs claim it; I found no current test). If it does roam, API keys would be copied to the user's other devices, which conflicts with "never exported". That alone argues against `PasswordVault` for API keys. It also has no ready JVM binding, so it adds cost for no gain.

F2.4 JNA gives a JVM app direct access to DPAPI.
- `com.sun.jna.platform.win32.Crypt32Util` exposes `cryptProtectData(byte[] data)`, `cryptProtectData(byte[] data, int flags)` and an overload taking entropy, flags and description. Source (primary, source code): https://raw.githubusercontent.com/java-native-access/jna/master/contrib/platform/src/com/sun/jna/platform/win32/Crypt32Util.java (accessed 2026-10-11)
- `jna-platform` latest is 5.19.1 (Maven metadata `lastUpdated` 2026-06-12). License header: "SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1-or-later". Sources (primary): https://repo1.maven.org/maven2/net/java/dev/jna/jna-platform/maven-metadata.xml and https://raw.githubusercontent.com/java-native-access/jna/master/LICENSE (accessed 2026-10-11)
- On `master`, JNA's `Advapi32.java` contains no `CredRead`/`CredWrite` text (match count 0) and `Wincred.java` returned no matches, so Credential Manager from a JVM needs a library such as java-keyring (F3.1) or hand-written JNA structures. UNVERIFIED for any other branch or release. Method: streamed the raw files through grep.

(F3 to F6 follow in the next revision of this file.)

## Options

(in progress)

## Recommendation

(in progress)

## Risks

(in progress)

## Confidence

(in progress)
