# R-12 Secure key storage per platform

## Question

How should Command Library encrypt user API keys on Android and Windows across S1 to S6, while excluding them from backups? Sources below were accessed 2026-10-11 through the web connector. Recommendations and scores are design judgments, not vendor guarantees. This audit replaces the earlier lengthy draft and removes unconfirmed version counts and implementation-size estimates.

## Findings

### Android platform and lifecycle

- Android Keystore holds cryptographic keys, not arbitrary API-token strings. Generate an AES key with `KeyGenerator` and `KeyGenParameterSpec` using the `AndroidKeyStore` provider; encrypt the token separately. Keystore material remains non-exportable. Hardware backing depends on device and algorithm support; inspect `KeyInfo`, rather than promise hardware backing on every phone. StrongBox is optional from Android 9, slower and resource constrained; unsupported requests require fallback. Authentication can be required for a period after unlocking or per operation, so biometric binding does not inherently mean a prompt for every API request. Source: https://developer.android.com/privacy-and-security/keystore (accessed 2026-10-11).
- Google recommends AES with 256-bit keys, including GCM. Jetpack Security Crypto APIs were deprecated in stable 1.1.0, with no subsequent releases planned. Release notes explicitly favor platform APIs and direct Android Keystore use. Do not begin a new dependency on `EncryptedSharedPreferences`. Sources: https://developer.android.com/privacy-and-security/cryptography and https://developer.android.com/jetpack/androidx/releases/security (accessed 2026-10-11).
- Tink remains an alternative cryptographic library, with Android integration and Apache-2.0 licensing. It does not make hardware support universal or solve backup exclusion automatically. Pin the chosen artifact and inspect its Keystore integration before adoption; exact current artifact version and added APK size are UNVERIFIED in this audit. Source: https://github.com/tink-crypto/tink-java (accessed 2026-10-11).
- Uninstall clears an app's Keystore namespace: AOSP describes namespace clearing as an uninstall maintenance operation. An ordinary same-package update is expected to retain the namespace, but update survival was not independently established as a documented guarantee in this audit. Test a signed release-to-release update. Reinstall or restoration onto another device must support re-entry rather than assume the encryption key survives. Sources: https://source.android.com/docs/security/features/keystore and https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences (accessed 2026-10-11; uninstall conclusion follows the documented namespace-clearing operation).

### Windows APIs, limits and packaging

- DPAPI `CryptProtectData` defaults to the current user's logon credentials and normally the same computer; roaming profiles are an exception. Do not use `CRYPTPROTECT_LOCAL_MACHINE`, which lets other users on that computer decrypt. Optional entropy must match during decryption; a constant bundled with the app is not a separate secret. Desktop and UWP availability is documented, so MSIX is not required for a JVM desktop application calling the Win32 API. No small per-token size limit was established from the function documentation: UNVERIFIED maximum input size. Source: https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata (accessed 2026-10-11).
- Credential Manager uses `CredWriteW` and `CredReadW`; generic credential blobs have a 5*512-byte maximum. `CRED_PERSIST_LOCAL_MACHINE` means persistence across this user's sessions on this computer, not sharing with every user. Enterprise persistence can roam. Win32 desktop availability does not require MSIX. Use an application-specific target name and one credential per provider if tokens fit. Sources: https://learn.microsoft.com/en-us/windows/win32/api/wincred/ns-wincred-credentialw and https://learn.microsoft.com/en-us/windows/win32/api/wincred/nf-wincred-credwritew (accessed 2026-10-11).
- WinRT `PasswordVault` supports desktop apps as well as WinUI/UWP, has a 20-credential limit, and can roam with account settings. It is unsuitable for the default local-only vault. Exact behavior for an unpackaged JVM using a particular WinRT binding is UNVERIFIED; vendor desktop support is not evidence of a turnkey Java binding. Source: https://learn.microsoft.com/en-us/windows/apps/develop/security/credential-locker (accessed 2026-10-11).
- JNA's `Crypt32Util` supplies protect/unprotect wrappers. This provides the S1 JVM route without WinRT. Packaged-runtime behavior and required jlink modules are UNVERIFIED until a Windows packaged-distribution smoke test succeeds; do not mandate an untested module list. Source: https://raw.githubusercontent.com/java-native-access/jna/master/contrib/platform/src/com/sun/jna/platform/win32/Crypt32Util.java (accessed 2026-10-11).

### Candidate stacks and maintenance

Scores compare secure storage only, on a 0 to 5 scale; they do not decide the overall stack. A current repository page establishes implementation and license, not ongoing maintenance guarantees.

| Stack | Android and Windows path | Maintenance/license evidence | Score |
|---|---|---|---|
| S1 KMP + Compose | Direct Android Keystore; JNA DPAPI on desktop JVM. KSafe is an alternative on both, with AES-GCM and Windows DPAPI; explicitly choose a no-backup Android directory and reject any fallback that fails the OS-backed requirement. | JNA Apache-2.0 OR LGPL-2.1-or-later; KSafe Apache-2.0. Current project and JVM protection docs inspected. | 4 |
| S2 Flutter | `flutter_secure_storage` supports Android and Windows; Android default RSA-OAEP wrapping plus AES-GCM. Current Windows source stores a 16-byte AES key in generic Credential Manager and ciphertext under RoamingAppData. | Registry lists 11.2.0, recently published, BSD-3-Clause. Requires C++ ATL for Windows builds. | 5 |
| S3 .NET MAUI | Built-in `SecureStorage`: Android EncryptedSharedPreferences; Windows DataProtectionProvider. Prefer replacing Android's deprecated dependency with native Keystore integration. | Current docs and Android source inspected; MAUI MIT. Deprecation is a dependency risk, not proof the storage is broken. | 3 |
| S4 Avalonia | Windows `.NET ProtectedData` current-user DPAPI; Android native .NET bindings around Keystore. | Avalonia MIT. No maintained built-in Android-and-Windows secret store established; absence remains UNVERIFIED after library/documentation search. | 3 |
| S5 RN + RN Windows | `react-native-keychain` covers Android/iOS. Windows DPAPI requires a native module unless a suitable maintained wrapper is verified. | Keychain MIT. README does not establish Windows support; no maintained Windows wrapper was confirmed. | 1 |
| S6 Tauri 2 | Rust `keyring-core` with Android and Windows native stores is a candidate. Android store encrypts SharedPreferences with Keystore; Windows store uses Credential Manager. | Store projects use MIT OR Apache-2.0. Exact pinned Tauri-mobile integration and release compatibility remain UNVERIFIED. Stronghold is a password-protected vault, not evidence of platform-Keystore use. | 3 |

Sources for S1: https://github.com/ioannisa/KSafe , https://raw.githubusercontent.com/ioannisa/KSafe/main/docs/JVM_PROTECTION.md and https://raw.githubusercontent.com/java-native-access/jna/master/LICENSE (accessed 2026-10-11).

Sources for S2: https://pub.dev/packages/flutter_secure_storage and https://raw.githubusercontent.com/juliansteenbakker/flutter_secure_storage/develop/flutter_secure_storage_windows/windows/flutter_secure_storage_windows_plugin.cpp (accessed 2026-10-11). The Windows observations concern the development branch; exact parity with the resolved stable Windows subpackage is UNVERIFIED and must be checked when pinning dependencies. Credential target names must be checked for app isolation.

Sources for S3: https://learn.microsoft.com/en-us/dotnet/maui/platform-integration/storage/secure-storage?view=net-maui-10.0 and https://github.com/dotnet/maui/blob/main/src/Essentials/src/SecureStorage/SecureStorage.android.cs (accessed 2026-10-11).

Sources for S4: https://learn.microsoft.com/en-us/dotnet/api/system.security.cryptography.protecteddata and https://raw.githubusercontent.com/AvaloniaUI/Avalonia/master/licence.md (accessed 2026-10-11).

Sources for S5: https://github.com/oblador/react-native-keychain and https://raw.githubusercontent.com/oblador/react-native-keychain/master/LICENSE (accessed 2026-10-11).

Sources for S6: https://raw.githubusercontent.com/open-source-cooperative/android-native-keyring-store/main/README.md , https://raw.githubusercontent.com/open-source-cooperative/windows-native-keyring-store/main/README.md and https://v2.tauri.app/plugin/stronghold/ (accessed 2026-10-11).

### Backup exclusion

Android excludes `noBackupFilesDir` automatically. For libraries using files or SharedPreferences, supply both legacy `fullBackupContent` exclusions and Android 12+ `dataExtractionRules`, explicitly covering cloud backup and device transfer. `allowBackup="false"` alone does not prevent device transfer on every manufacturer's device. Exclude the actual resolved library filename, not a guessed name. Source: https://developer.android.com/identity/data/autobackup (accessed 2026-10-11).

Proposed rules for a custom fallback file at `filesDir/secrets/`, not a claim that this path is the same as `noBackupFilesDir`:

```xml
<!-- Android 12+ -->
<data-extraction-rules>
  <cloud-backup><exclude domain="file" path="secrets/"/></cloud-backup>
  <device-transfer><exclude domain="file" path="secrets/"/></device-transfer>
</data-extraction-rules>
<!-- Separate Android 11-and-lower resource -->
<full-backup-content><exclude domain="file" path="secrets/"/></full-backup-content>
```

Wire the resources through `android:dataExtractionRules` and `android:fullBackupContent`. For plugin SharedPreferences use `domain="sharedpref"` and its actual XML filename. Framework-specific Tauri manifest customization is UNVERIFIED after reading the plugin documentation and must be resolved before implementation. Syntax source: https://developer.android.com/identity/data/autobackup (accessed 2026-10-11).

App export design: export an allowlist of decks, collection and ordinary settings, with secrets held outside those repositories. Exclude ciphertext, wrapping keys, vault files and credential identifiers that contain secrets. Verify the unpacked archive with a fake sentinel token. Restore preserves existing device-local secrets and shows missing providers as needing a key. GAUNTLET 2.I saying keys are excluded by default does not itself require implementing an encrypted-key opt-in. Do not add one without a product requirement. This is a proposed design satisfying local GAUNTLET sections 2.H and 2.I.

### Threat model

| Threat | Android Keystore design | Windows current-user DPAPI/Credential Manager |
|---|---|---|
| Other normal apps/users | Android app scope restricts access. | Other Windows users generally excluded; processes running as the same user are not isolated. |
| Backup extraction | Excluded vault plus non-exportable key reduces exposure; restored ciphertext without its key must be discarded or quarantined. | A copied blob normally needs original logon/machine context; roaming-profile and master-key recovery exceptions prevent an absolute guarantee. |
| Stolen unlocked device | No protection if the thief can use the app; optional auth gating improves this. | Unlocked user session can use the credentials. |
| Root/admin or compromised process | Can use accessible keys or steal decrypted token in memory; hardware-backed key extraction resistance does not protect the plaintext API token. | Same-user malware can request decryption or read credentials; storage encryption is not a malware boundary. |

Basis: https://developer.android.com/privacy-and-security/keystore and https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata (accessed 2026-10-11; same-user and unlocked-session consequences are threat-model inferences). None protects token plaintext during an API call, logs, clipboard leakage or an untrusted endpoint. Avoid logging request headers, redact secret types, and keep secrets out of crash payloads. These are application controls, independent of storage choice.

### Terms and licensing

- JNA permits choosing Apache-2.0 rather than LGPL. Apache redistribution requires the license, applicable notices and change notices. Short clause: "You must give any other recipients of the Work or Derivative Works a copy of this License". Sources: https://raw.githubusercontent.com/java-native-access/jna/master/LICENSE and https://www.apache.org/licenses/LICENSE-2.0.txt (accessed 2026-10-11).
- MIT permits use/distribution with attribution: "The above copyright notice and this permission notice shall be included". Applies to the inspected RN Keychain and Avalonia licenses. Sources: https://raw.githubusercontent.com/oblador/react-native-keychain/master/LICENSE and https://raw.githubusercontent.com/AvaloniaUI/Avalonia/master/licence.md (accessed 2026-10-11).
- Flutter's BSD-3-Clause license requires notices in binaries: "Redistributions in binary form must reproduce the above copyright notice" and forbids endorsement without permission. Source: https://raw.githubusercontent.com/juliansteenbakker/flutter_secure_storage/develop/flutter_secure_storage/LICENSE (accessed 2026-10-11).
- Ship third-party notices for the actual resolved dependency tree. Exact licenses of every transitive dependency are UNVERIFIED until a stack and lockfile are selected. Platform API documentation is not an independent license grant; no additional distribution restriction was identified in the cited API pages.

## Options

1. S1: direct Keystore plus JNA current-user DPAPI, behind a platform `SecretStore` interface. Small dependency surface; requires real platform tests.
2. S2: pinned `flutter_secure_storage`, verifying both native implementations and exclusion rules. Best turnkey coverage in this criterion.
3. Other stacks: use the native paths in the table; treat missing Windows wrappers and mobile integration as implementation work rather than declare unsupported capability.

## Recommendation

Android: AES-256-GCM with a Keystore-generated key; fresh nonce per encryption; authenticate provider identity and format version as AAD; store nonce/ciphertext under `noBackupFilesDir`. Do not require StrongBox or promise hardware support. Default auth policy is a product choice; recommend no per-use biometric requirement for background requests. On a permanently missing key or invalid ciphertext, report re-entry; transient failures must not automatically destroy all credentials. Never fall back to plaintext.

Windows: current-user DPAPI via JNA for S1, ciphertext under LocalAppData, no machine-scope flag, no interactive prompt structure. Credential Manager generic credentials are equally defensible for short tokens. Use app-specific names and avoid roaming persistence. S2 can use its plugin after confirming stable-source parity and vault isolation. Release checks: same-device restart/update, reinstall, backup restore, corruption, unavailable vault and Windows packaged-runtime round trip.

## Risks

Hardware availability, OEM Keystore failures, stale dependencies, plugin namespace collisions and backup-rule drift remain concrete implementation risks. No storage path protects against same-user Windows malware or compromised app plaintext. Never treat fixed DPAPI entropy as an application isolation boundary. Unverified items above are targeted Gate 2 integration checks, not reasons to assert zero placeholders or complete secure-storage validation now.

## Confidence

High for native platform recommendations and deprecated Jetpack Security status; medium for framework selection because third-party implementations and packaging must be pinned and tested. No contradiction with GAUNTLET section 10 or Appendix A. Clarify section 2.H: Android offers Keystore, with conditional hardware backing; Windows baseline offers OS current-user protection, not a promised hardware keystore. Backup wording does not require a key-export opt-in.
