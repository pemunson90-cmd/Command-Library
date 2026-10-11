# R-15 CI/CD, signing, Windows packaging, screenshot tests

Path: `docs/gauntlet/research/R-15-ci-cd.md`. Task card: `coordination/tasks/cc-research-15.md`. Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for every source below is 2026-10-11 unless stated otherwise. Status: COMPLETE WITH EXPLICIT VERIFICATION LIMITS. The previous interrupted report was audited and completed on 2026-10-11; retained readings are distinguished from newly checked sources.

## Question
How does GitHub Actions build a release-signed Android APK and a Windows package for each candidate stack (S1 KMP + Compose Multiplatform, S2 Flutter, S3 .NET MAUI, S4 Avalonia, S5 React Native + RN Windows, S6 Tauri 2), run tests and screenshot tests per size class, and enforce the placeholder scan, all without a local machine?

Source conventions. "GitHub Docs source" means the raw Markdown in https://github.com/github/docs read through raw.githubusercontent.com (the primary text behind docs.github.com). "Microsoft docs source" means raw Markdown in https://github.com/MicrosoftDocs read the same way. Pages fetched through a summarizing tool are marked "summarizing fetch" and treated as one notch weaker than raw text.

Current project rulings: public repository; existing signing key; Codex sole orchestrator through Gate 3. These come from the task assignment, not external product documentation.

## Findings

### A. GitHub Actions: billing, runners, artifacts, releases

- Standard GitHub-hosted runner minutes are free in public repositories. Quote: "GitHub Actions usage is **free** for **self-hosted runners** and for **public repositories** that use standard GitHub-hosted runners." Also: "Larger runners are always charged for, even when used by public repositories". Source: https://raw.githubusercontent.com/github/docs/main/content/billing/concepts/product-billing/github-actions.md (GitHub Docs source, accessed 2026-10-11).
- Private-repository fallback (D-02 if Pat picks private): Free plan includes 2,000 minutes per month, 500 MB artifact storage, 10 GB cache per repository; Pro 3,000 minutes, 1 GB. Source: https://raw.githubusercontent.com/github/docs/main/data/reusables/billing/actions-included-quotas.md (accessed 2026-10-11).
- Per-minute list prices for standard runners (apply to private repos beyond quota): Linux 2-core x64 $0.006, Windows 2-core x64 $0.010, Linux 2-core arm64 $0.005, macOS 3 or 4 core $0.062. GitHub rounds each job up to the whole minute. Source: https://raw.githubusercontent.com/github/docs/main/data/reusables/billing/actions-standard-runner-prices.md and https://raw.githubusercontent.com/github/docs/main/content/billing/reference/actions-runner-pricing.md (accessed 2026-10-11). The old "minute multiplier" page now redirects to this per-minute SKU page (`redirect_from: /billing/reference/actions-minute-multipliers` in the same file), so the Windows 2x and macOS 10x multipliers are no longer stated in current docs; the dollar table is the current statement (Windows is about 1.7x Linux and macOS about 10x Linux, my arithmetic from the table). For a public repo none of this is charged. No candidate stack needs macOS (no iOS target in GAUNTLET.md section 2), so the macOS rate is moot.
- Storage overage for private repos: shared artifact/Packages storage $0.25 per GB-month, Actions cache $0.07 per GB-month. Source: github-actions.md source above (accessed 2026-10-11).
- Android emulator acceleration: "GitHub-hosted Linux runners support hardware acceleration for Android SDK tools, which makes running Android tests much faster and consumes fewer minutes." The text does not say KVM by name. Source: https://raw.githubusercontent.com/github/docs/main/content/actions/reference/runners/github-hosted-runners.md (accessed 2026-10-11).
- Limits: GitHub-hosted job execution time 6 hours; workflow run 35 days; job matrix 256 jobs; Free plan 20 concurrent jobs (5 macOS); each workflow file must be 500 KB or smaller. Source: https://raw.githubusercontent.com/github/docs/main/content/actions/reference/limits.md (accessed 2026-10-11).
- Artifacts: logs and artifacts are kept 90 days by default; for public repos the retention can be set between 1 and 90 days; `actions/upload-artifact` `retention-days` must be between 1 and 90 inclusive. Starting 2026-10-01 the retention policy also applies to checks, workflow runs and commit statuses (before that they were kept 400+ days). Sources: https://raw.githubusercontent.com/github/docs/main/data/reusables/actions/about-artifact-log-retention.md and https://raw.githubusercontent.com/actions/upload-artifact/main/README.md (accessed 2026-10-11).
- Artifact and log visibility: "People who are signed into GitHub and have read access to a repository can download workflow artifacts." Also: "You must be logged in to a GitHub account to view workflow run information, including for public repositories." In a public repo that means any signed-in GitHub account can read logs and download artifacts. Sources: https://raw.githubusercontent.com/github/docs/main/content/actions/how-tos/manage-workflow-runs/download-workflow-artifacts.md and https://raw.githubusercontent.com/github/docs/main/content/actions/how-tos/monitor-workflows/use-workflow-run-logs.md (accessed 2026-10-11). Consequence: never put key material, even encrypted, in a log, job summary or artifact of a public repo unless the encryption is strong and the key is not guessable.
- Logs and job summaries do not count against artifact storage: "If you run a workflow that generates many log files and a long job summary, these files do not count towards the repository owner's artifact storage allowance." Source: github-actions.md source above (accessed 2026-10-11).
- Creating a release from a workflow with no pre-existing tag (this cloud session cannot push tags): `gh release create <tag>`: "If a matching git tag does not yet exist, one will automatically get created from the latest state of the default branch. Use --target to point to a different branch or commit for the automatic tag creation." Assets are passed as file arguments; `--generate-notes` builds notes via the Release Notes API. `contents: write` is the proposed release-job permission; that `gh release create` succeeds with it is my reading and is UNVERIFIED until exercised in a Gate 3 run. Source: https://raw.githubusercontent.com/cli/cli/trunk/pkg/cmd/release/create/create.go (accessed 2026-10-11).
- Release limits: up to 1000 assets per release, each file under 2 GiB, no total size or bandwidth limit. Sources: https://raw.githubusercontent.com/github/docs/main/content/repositories/releasing-projects-on-github/about-releases.md, https://raw.githubusercontent.com/github/docs/main/data/variables/releases.yml, https://raw.githubusercontent.com/github/docs/main/data/variables/large_files.yml (accessed 2026-10-11).
- Immutable releases: when repo-level release immutability is enabled, tags and assets of a published release cannot be modified or deleted; `gh release create` creates a draft, uploads assets, then publishes, so protection starts after publish. Source: gh create.go above (accessed 2026-10-11). Implication: with immutability on, a broken release needs a new version number, not an asset swap.

### B. Android signing: existing key, four fixed secrets

Project constraint from the task card (read 2026-10-11): the PKCS12 key already exists, alias `commandlibrary`, RSA 4096. Do not regenerate it. D-01 is `com.architectpsg.commandlibrary`. The certificate SHA-256 in DECISIONS.md is the expected identity, not a newly generated fingerprint. That file and all uploaded secrets were deliberately outside this research brief; the numeric fingerprint is UNVERIFIED here and must be supplied by the orchestrator.

- Use all four fixed secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Decode only into `$RUNNER_TEMP`, supply passwords through environment variables, disable shell tracing, never print properties or environment dumps, and delete key material in an `always()` cleanup. Base64 is encoding, not encryption. GitHub documents secret use through environment variables and warns masking has limits. Sources: https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets and https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions (accessed 2026-10-11). The cleanup and restricted signing job are recommended design, not tested project code.
- Android and Flutter document Gradle `signingConfigs` with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`, bound to the release build. Below replaces their properties-file loading with environment loading. Source: https://docs.flutter.dev/deployment/android (accessed 2026-10-11).

```kotlin
// DESIGN SKETCH, NOT A TESTED FILE: Android application module
android {
    signingConfigs {
        create("release") {
            storeFile = file(requireNotNull(System.getenv("CL_KEYSTORE_PATH")))
            storePassword = requireNotNull(System.getenv("ANDROID_KEYSTORE_PASSWORD"))
            keyAlias = requireNotNull(System.getenv("ANDROID_KEY_ALIAS"))
            keyPassword = requireNotNull(System.getenv("ANDROID_KEY_PASSWORD"))
        }
    }
    buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
}
```

This sketch intentionally fails when signing inputs are absent. Gate 3 must separate unsigned PR/debug builds from the protected release configuration; neither silent unsigned releases nor debug-key fallback are acceptable (design recommendation).

```yaml
# DESIGN SKETCH, NOT A TESTED FILE. Resolve every action to reviewed full SHA.
jobs:
  android-release:
    runs-on: ubuntu-24.04
    environment: release
    permissions: {contents: read}
    steps:
      - uses: actions/checkout@<reviewed-full-SHA>
      - uses: actions/setup-java@<reviewed-full-SHA>
        with: {distribution: temurin, java-version: '21'}
      - name: Decode signing key
        env:
          KEY_B64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
        run: |
          umask 077
          printf '%s' "$KEY_B64" | base64 --decode > "$RUNNER_TEMP/release.p12"
          echo "CL_KEYSTORE_PATH=$RUNNER_TEMP/release.p12" >> "$GITHUB_ENV"
      - name: Assemble release
        env:
          ANDROID_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          ANDROID_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
          ANDROID_KEY_PASSWORD: ${{ secrets.ANDROID_KEY_PASSWORD }}
        run: ./gradlew assembleRelease
      - name: Verify signing identity
        env:
          EXPECTED_SHA256: ${{ vars.ANDROID_SIGNING_CERT_SHA256 }}
        run: |
          set -euo pipefail
          : "${EXPECTED_SHA256:?Copy the D-03 certificate SHA-256 into this variable}"
          apk=app/build/outputs/apk/release/app-release.apk
          verifier="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS_VERSION/apksigner"
          "$verifier" verify --verbose --print-certs "$apk" > "$RUNNER_TEMP/cert.txt"
          expected=$(printf '%s' "$EXPECTED_SHA256" | tr -d ':[:space:]' | tr 'A-F' 'a-f')
          actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' "$RUNNER_TEMP/cert.txt" | tr -d ':[:space:]' | tr 'A-F' 'a-f')
          test "${#expected}" -eq 64
          test "$actual" = "$expected"
      - name: Remove signing key
        if: always()
        run: rm -f "$RUNNER_TEMP/release.p12" "$RUNNER_TEMP/cert.txt"
```

`apksigner verify --verbose --print-certs` is documented. The output parsing, single-signer assumption, module path, Gradle task, and pinned `ANDROID_BUILD_TOOLS_VERSION` require Gate 3 validation; the certificate comparison must fail closed. The non-secret expected fingerprint must be checked against D-03 during implementation. Source: https://developer.android.com/tools/apksigner (accessed 2026-10-11).

- Stack paths: S1 uses Gradle `assembleRelease`; S2 uses `flutter build apk --release` with the same Android Gradle signing block; S5 has an Android Gradle project as well. .NET S3/S4 require .NET Android signing properties or post-build `apksigner`; Tauri S6 documents mobile signing configuration. Exact S3-S6 workflow wiring is UNVERIFIED in this research, not assumed interchangeable. Sources: https://docs.flutter.dev/deployment/android, https://learn.microsoft.com/en-us/dotnet/maui/android/deployment/publish-cli, https://v2.tauri.app/distribute/sign/android/ (accessed 2026-10-11; S3/S6 retained source links from the interrupted report).
- Historical creation route, informational only: a JDK on Pat's Windows PC provides `keytool`; an explicit command can use `-genkeypair -storetype PKCS12 -keyalg RSA -keysize 4096 -validity 10000 -alias commandlibrary`, with password prompts rather than literal arguments. The task's existing key makes this step unnecessary. Sources: https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html and https://adoptium.net/installation/windows/ (accessed 2026-10-11, retained source readings). Do not infer that an unlisted RSA key size is unsupported merely because an example lists other sizes.
- Workflow key generation is a poor backup strategy: repository secret metadata cannot reveal secret values later, so an independent safe backup is needed. A generation workflow additionally needs credentials authorized to write secrets and a secure delivery channel. Do not create a new key or export it to public artifacts. Source: https://docs.github.com/en/rest/actions/secrets (accessed 2026-10-11, retained API reading). Recommendation: Pat keeps the delivered key and password in independent encrypted backups and validates the first CI signature and update installation.
- Reuse alternative, not selected: load an existing keystore with its actual alias/passwords and check its certificate before using it. Android update signing identity continuity matters; supported signing-key rotation must be evaluated before declaring that every key change requires a new package ID. Source: https://developer.android.com/studio/publish/app-signing (accessed 2026-10-11, retained reading).

### C. Windows packaging per stack

C0. Cross-stack facts (accessed 2026-10-11)
- Build Windows packages on a standard Windows hosted runner and install/pin the selected JDK, .NET SDK, WiX or installer tooling explicitly. Current runner image contents are documented, but exact rolling preinstalled versions and PATH discovery are UNVERIFIED in this audit. Source: https://github.com/actions/runner-images/blob/main/images/windows/Windows2025-Readme.md (accessed 2026-10-11, interrupted source reading; live contents not rechecked).
- Microsoft Store route is not needed for personal use. Reference: "If you publish your app as an MSIX package through the Microsoft Store, code signing is free and handled for you automatically." Source: https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/docs/hub/apps/package-and-deploy/code-signing-options.md (Microsoft docs source, ms.date 04/20/2026).

C1. S1 Kotlin Multiplatform + Compose Multiplatform desktop
- Formats: Windows `.exe` (`TargetFormat.Exe`) and `.msi` (`TargetFormat.Msi`) through the Compose Gradle plugin, which wraps `jpackage`. Gradle tasks: `package<FormatName>`, `packageDistributionForCurrentOS`, `createDistributable` (app image with no installer), `packageUberJarForCurrentOS`, `runDistributable`. Quote: "Cross-compilation is currently not supported, meaning you can build the specific format using the corresponding compatible OS only." JDK: "jpackage, which requires a JDK version not lower than JDK 17". Output: `${project.buildDir}/compose/binaries`. MSI/EXE version format `MAJOR.MINOR.BUILD` with MAJOR and MINOR max 255 and BUILD max 65535. `upgradeUuid` "must remain constant for a single application". Source: https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html (summarizing fetch). Consequence: the Windows package job must run on `windows-latest`; the version name (for example `1.2.345`) must fit those limits, so a CI run number as BUILD works up to 65535.
- jpackage itself: valid `--type` values `app-image`, `exe`, `msi`; "Each format must be built on the platform it runs on, there is no cross-platform support"; Windows options `--win-menu`, `--win-per-user-install`, `--win-upgrade-uuid`, `--win-dir-chooser`, `--win-shortcut`; WiX 3 and WiX 4+ sources are both handled (the spec mentions a converter "from WiX v3 to v4 schema when WiX v4 or newer is used"), minimum WiX version not stated on the JDK 25 page. Source: https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html (summarizing fetch). The Compose docs page I read does not mention WiX or Windows signing, so whether the plugin has a Windows signing DSL is UNVERIFIED; plan on signing the produced MSI/EXE in a later workflow step.
- Portable format: `createDistributable` produces an app image folder that can be zipped as a portable ZIP (my reading of "final application image without creating an installer"; the zip step is mine).
- Conveyor (Hydraulic Software), an alternative packager for JVM apps including Compose Desktop. Capabilities: packages for Windows, macOS and Linux "from any Linux build agent", signs from any OS, has a GitHub Action, bundles a jlinked JVM, scaffolds Compose Desktop projects. Source: https://conveyor.hydraulic.dev/22.0/ (summarizing fetch). Windows outputs include MSIX (`conveyor make windows-msix`) and an EXE installer; Windows updates use the MSIX update engine ("Windows checks every eight hours" by default). Source: https://conveyor.hydraulic.dev/latest/configs/windows/ (summarizing fetch, page labeled as an older version).
- Conveyor terms. Pricing page: "Any project released under an OSI-approved license can use Conveyor gratis." "Packages that update from localhost are free." Standard $45/month, charged monthly excluding VAT, includes three apps; Source tier contact sales. Source: https://hydraulic.dev/pricing.html (summarizing fetch). FAQ: free if the update site is localhost, and for open source "when you set the `app.vcs-url` key to your source repository"; open source apps should say they are packaged with Conveyor and link to the site. Source: https://conveyor.hydraulic.dev/22.0/faq/ (summarizing fetch). Not stated on the pages I read: whether a private-repo personal app that never sets a non-localhost update site must pay (UNVERIFIED; the pricing page lists sales@hydraulic.dev). Implication: the public D-02 ruling still needs an OSI license to satisfy the open-source gratis route; localhost is separately free. The quoted $45/month tier is an interrupted reading, not a fresh pricing confirmation. It is not needed to win: jpackage MSI is free and its WiX is already on the runner.

C2. S2 Flutter
- `flutter build windows` produces the executable under `build\windows\runner\Release` per the docs page; MSIX via the `msix` pub package (a `.pfx` certificate is needed to distribute from your own website: "For this option, you need to give your application a digital signature in the form of a `.pfx` certificate"); or a hand-built ZIP of the `.exe`, DLLs and `data` directory (plus `msvcp140.dll`, `vcruntime140.dll`, `vcruntime140_1.dll` for app-local VC++ runtime); or an installer such as Inno Setup or WiX. "Distributing your application by self hosting it on a website requires a certificate signed by a Certificate Authority known to Windows." Source: https://docs.flutter.dev/platform-integration/windows/building (summarizing fetch). Recent Flutter releases write `build\windows\x64\runner\Release` (my memory; UNVERIFIED).
- Size: not published on the pages read (UNVERIFIED).

C3. S3 .NET MAUI (WinUI 3)
- "Publishing a .NET MAUI app for Windows involves creating an MSIX app package (known as a packaged app), or creating an executable file (known as an unpackaged app)". CLI publishing is supported "for Continuous Integration (CI) scenarios". Docs list packaged-CLI and unpackaged-CLI guides. Source: https://learn.microsoft.com/en-us/dotnet/maui/windows/deployment/overview. The MSIX needs a trusted certificate to install on other PCs (general MSIX behavior; MAUI-specific wording not read, UNVERIFIED).
- Size: not published on the pages read (UNVERIFIED).

C4. S4 Avalonia
- Parcel (Avalonia's commercial packager) produces NSIS installer `.exe`, `.msix` and portable `.zip`, runs "on Windows, macOS, or Linux", signs with Authenticode via PFX, the Windows certificate store, Azure Artifact Signing, Azure Key Vault, AWS KMS, DigiCert, Google Cloud KMS and SSL.com eSigner (JSign needs a JRE off Windows). Source: https://docs.avaloniaui.net/tools/parcel/packaging-for-windows (summarizing fetch). Parcel price and license terms: UNVERIFIED (the page does not state them; a NuGet search snippet said its CLI is "not available in free community license", secondary and unconfirmed).
- Free alternatives: Velopack builds `Setup.exe` (per-user install, no wizard), an optional MSI (`vpk pack --msi`, WiX based) and signs if configured. Source: https://docs.velopack.io/packaging/installer (summarizing fetch). Inno Setup is preinstalled on the runner (section A); a real Avalonia app (Libation) uses an Inno Setup script (secondary, search result). Avalonia's own Windows deployment docs page was not found (https://docs.avaloniaui.net/docs/deployment/windows returned 404; the sitemap lists deployment pages for android, linux, macos, native-aot, webassembly, docker and ios, none for Windows: https://docs.avaloniaui.net/sitemap.xml).
- Size: not published (UNVERIFIED).

C5. S5 React Native + React Native for Windows
- Build requirements: Visual Studio 2022 with ".NET Desktop development", "Desktop development with C++" and "Universal Windows Platform development" workloads, Windows 10 SDK 10.0.22621.0, Developer Mode on, "Develop only on Windows". Source: https://microsoft.github.io/react-native-windows/docs/rnw-dependencies (summarizing fetch). The windows-2025 image's SDK is 10.0.26100.0 and its UWP workload presence is UNVERIFIED (not found in the lines I read).
- Packaging: the documented flow is Visual Studio "Create App Packages" in Release producing an `.appxupload`, which "should be signed with your desired certificate" (https://microsoft.github.io/react-native-windows/docs/app-publishing/, search summary). A pure-CLI MSBuild packaging recipe for CI is UNVERIFIED (the dependencies page sidebar lists "Setup Continuous Integration Pipeline for an RNW App"; not fetched).
- Size: not published (UNVERIFIED).

C6. S6 Tauri 2
- Windows installers: MSI via WiX Toolset v3 and NSIS `-setup.exe`. "`.msi` installers can only be created on Windows as WiX can only run on Windows systems." NSIS from Linux or macOS "is not as straight forward as compiling on Windows directly and is not tested as much". WebView2: downloaded bootstrapper (default, adds 0 MB, needs internet), embedded bootstrapper (about 1.8 MB), offline installer (about 127 MB), fixed version (about 180 MB). "Signing cross compiled Windows installers requires an external signing tool". Source: https://v2.tauri.app/distribute/windows-installer/ (summarizing fetch). "A minimal Tauri app can be less than 600KB in size" is Tauri's claim (https://v2.tauri.app/start/, summarizing fetch), not a measured figure for this app.
- Mobile maturity label: the Tauri start page "gives no explicit maturity or stability label for Android or iOS" (summarizing fetch). UNVERIFIED beyond that.

### D. Windows code signing and SmartScreen

D1. SmartScreen behavior today (Microsoft docs source, ms.date 05/04/2026, https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/docs/hub/apps/package-and-deploy/smartscreen-reputation.md, accessed 2026-10-11)
- Table, first-download behavior: Microsoft Store: no warning. Valid certificate (OV/EV): "Warning - app flagged as unrecognized until reputation accumulates; verified publisher name is displayed". No signature: "Warning - 'Windows protected your PC'; User must choose 'Run anyway' before the app can run. Enterprise policy can prevent continuation entirely." Self-signed: "Same behavior as no signature".
- "EV certificates no longer bypass SmartScreen... Paying a premium for EV solely to avoid SmartScreen warnings is no longer justified."
- Reputation builds per file hash and per signing identity: "When a file is not signed, SmartScreen reputation must build for each new version of your files, starting with zero reputation." For signed files, reputation can carry across versions signed with the same identity. "There is no exact threshold, but it can take several weeks and hundreds of clean installs from a wide audience." For a one-user app that threshold will practically never be met (my inference), so even a signed build keeps showing the first-run prompt for Pat; signing mainly adds a verified publisher name and persistence across versions.
- Smart App Control: "On Windows 11 devices, the Smart App Control feature may supersede SmartScreen Application Reputation. Smart App Control will block execution of unsigned files unless the file has a positive reputation. Smart App Control signature checks apply to all executable files, not just those downloaded from the Internet." This is the main risk for an unsigned build on Pat's PC: if Smart App Control is on, unsigned code is blocked, not just warned. Whether Pat's PC has it on is UNVERIFIED. Microsoft separately says Smart App Control allows apps signed with trusted certificates: https://learn.microsoft.com/en-us/windows/apps/develop/smart-app-control/code-signing-for-smart-app-control (accessed 2026-10-11).

D2. Options, prices, eligibility (Microsoft docs source, ms.date 04/20/2026, https://raw.githubusercontent.com/MicrosoftDocs/windows-dev-docs/docs/hub/apps/package-and-deploy/code-signing-options.md, accessed 2026-10-11 unless stated)
- Microsoft Store MSIX: free, "Worldwide", no SmartScreen warning, "Create a free developer account at storedeveloper.microsoft.com". Store MSI/EXE submissions must be signed by a Trusted Root Program CA (self-signed not accepted). A Store listing implies certification and policies, which is public-release work (GAUNTLET.md section 8), so it is not the personal-use path.
- Azure Artifact Signing (formerly Trusted Signing): "Approximately $9.99/month"; "Organizations: USA, Canada, EU, UK. Individuals: USA and Canada only"; identity validation "plan for a few business days"; "No hardware token required"; works from GitHub Actions; "does not provide instant SmartScreen trust". Plan details from a search summary of the older FAQ: Basic $9.99/month up to 5,000 signatures, Premium $99.99/month up to 100,000, $0.005 per extra signature (UNVERIFIED; the Azure pricing page itself shows "$-" placeholders in my fetch of https://azure.microsoft.com/en-us/pricing/details/artifact-signing/, which does confirm 5,000 and 100,000 included signatures for Basic and Premium).
- Artifact Signing eligibility quote (Microsoft docs source, https://raw.githubusercontent.com/MicrosoftDocs/azure-docs/main/articles/artifact-signing/quickstart.md, accessed 2026-10-11): "Public Trust certificates are available to organizations in the United States, Canada, the European Union, the United Kingdom, Australia, New Zealand, Japan, South Korea, Singapore, Switzerland, Norway, and Israel. Individual developers must be located in the United States or Canada. These geographic restrictions do not apply to Private Trust certificates." And: "For Public Trust individual identity validation, Artifact Signing automatically sources identity details from the Azure billing account associated with the subscription... The billing account must have an Account Type of Individual. The legal name and sold-to address on the billing account must match the information on the government-issued ID used for identity validation." The two pages differ on organization countries (code-signing-options lists four regions, the quickstart lists twelve); the quickstart is more detailed, but both agree individuals are limited to the US and Canada. Pat's country is not stated in any file I was allowed to read, so eligibility is UNKNOWN. Private Trust is "only for Organizations" (same quickstart), so it does not help an individual.
- GitHub integration: `azure/artifact-signing-action@v2` "can only be executed on Windows runners" (windows-2025 and windows-2022 supported; Arm runners not supported), authenticates preferably with OpenID Connect and federated credentials (`azure/login@v3`, secrets `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, job permission `id-token: write`), signs a folder or file list by extension. Source: https://raw.githubusercontent.com/Azure/artifact-signing-action/main/README.md (accessed 2026-10-11). A Microsoft FAQ note says signed files may still show SmartScreen prompts until hash reputation builds (https://raw.githubusercontent.com/MicrosoftDocs/azure-docs/main/articles/artifact-signing/faq.yml).
- OV certificates (DigiCert, Sectigo, GlobalSign): "$150-300/year"; "As of June 2023, the CA/Browser Forum requires private keys for OV certificates to be stored on a hardware security module (HSM) or hardware token", so unattended CI signing needs a CA's cloud HSM option or a token on a self-hosted runner. SmartScreen: same as Artifact Signing. EV: "$400+/year", no longer bypasses SmartScreen "since 2024". Self-signed: "Blocks installation for public users", fine for dev or managed enterprise. Source: code-signing-options.md above. Which CAs offer a GitHub-Actions-compatible cloud signing product, and their current prices, are UNVERIFIED (not researched beyond Microsoft's ranges).
- SignPath Foundation terms require OSI licensing without commercial dual licensing, no proprietary components beyond allowed system libraries, active maintenance, an already-released project, documentation, verifiable builds, MFA, review roles, a code-signing policy, and manual signing approval per release. Terms quote: "Every release needs manual approval for signing." They also require verifiable reputation and reserve acceptance discretion. Public D-02 alone is insufficient. Source: https://signpath.org/terms (accessed 2026-10-11, newly checked primary page).
- Conveyor and Parcel both document "self-signing" or custom signing scripts but neither removes the SmartScreen prompt (sections C and D1).
- Other signing mechanics: sign after packaging and do not modify files afterward ("Do not modify signed files"); timestamping is a separate server setting (Conveyor defaults to Certum per its Windows page). Compose Desktop MSI/EXE would be signed with `signtool` or the Artifact Signing action on the produced file (design, untested).

### E. CI tests and screenshots

All following sources were accessed 2026-10-11. Commands and the proposed matrix are design recommendations; none has run against this project yet.

| Stack | Unit and UI smoke approach | Screenshot approach and limitation | Sources |
|---|---|---|---|
| S1 | Kotlin/JVM/common tests plus Android Compose instrumentation; Compose Multiplatform UI tests on desktop | Roborazzi Android uses Robolectric native graphics; its current README also documents Compose Desktop JVM capture without Robolectric and preview-generated tests. This corrects the assumption that Roborazzi is Android-only. | https://kotlinlang.org/docs/multiplatform/compose-test.html ; https://github.com/takahirom/roborazzi |
| S2 | `flutter test` for unit/widget tests; `integration_test` on Android emulator and Windows desktop | `matchesGoldenFile` for widget goldens. Set viewport dimensions and fake display features in the test fixture; load fonts and maintain platform-specific baselines. Windows fonts/rendering can differ from Linux. | https://docs.flutter.dev/testing/integration-tests ; https://api.flutter.dev/flutter/flutter_test/matchesGoldenFile.html ; https://api.flutter.dev/flutter/flutter_test/TestFlutterView/physicalSize.html ; https://api.flutter.dev/flutter/widgets/MediaQueryData/displayFeatures.html |
| S3 | `dotnet test` for isolated domain logic; Microsoft documents Appium Android/Windows UI tests | Appium screenshots of running app; headless MAUI golden rendering and simulated hinge API remain UNVERIFIED after a docs search. | https://learn.microsoft.com/en-us/dotnet/maui/deployment/ui-testing ; https://learn.microsoft.com/en-us/dotnet/maui/deployment/unit-testing |
| S4 | `dotnet test` with Avalonia headless xUnit/NUnit; Windows Appium for native UI smoke | Headless renderer with Skia frame capture; native controls still need real platform testing. Posture injection would be an application interface, not a verified built-in foldable simulator. | https://docs.avaloniaui.net/docs/testing/setting-up-the-headless-platform ; https://docs.avaloniaui.net/docs/testing/ui-testing-with-appium |
| S5 | Jest for JS/domain/component tests; RNW repository documents WebDriverIO and its automation package | RNW framework captures rendered composition trees; this is not a demonstrated pixel-golden solution for this app. Android and Windows screenshot pipeline plus synthetic posture are UNVERIFIED. | https://reactnative.dev/docs/testing-overview ; https://github.com/microsoft/react-native-windows/blob/main/docs/e2e-testing.md |
| S6 | Rust unit tests plus web frontend tests; Tauri WebDriver desktop integration | Tauri WebDriver supported on Windows/Linux, mobile WebDriver not supported. Browser screenshots of web frontend alone miss native integration. Android Appium alternative and hinge simulation remain UNVERIFIED. | https://v2.tauri.app/develop/tests/ ; https://v2.tauri.app/develop/tests/webdriver/ |

S1 alternatives: Paparazzi renders Android Views/Compose without a device using layoutlib, with configurable device sizing; it does not prove Windows rendering. Official Compose Preview Screenshot Testing is host-side but experimental/alpha. Its current docs describe test-suite integration and evolving configuration, so pin a compatible AGP/engine combination instead of taking an old standalone-plugin snippet as stable. Sources: https://github.com/cashapp/paparazzi and https://developer.android.com/studio/preview/compose-screenshot-testing (accessed 2026-10-11).

Proposed fixtures: compact 360x800 dp, medium 700x1000 dp, expanded 900x800 dp, supplemental large 1200x900 dp (width >=1200 dp), supplemental extra-large 1600x1000 dp (width >=1600 dp), and compact-height 900x400 dp, plus horizontal HALF_OPENED tabletop and vertical HALF_OPENED book fixtures with explicit hinge bounds. These are proposed test dimensions, not asserted device models. Android WindowManager supplies test `FoldingFeature`, `TestWindowLayoutInfo`, and `WindowLayoutInfoPublisherRule`; inject geometry/posture through a platform abstraction for host-side screenshots. A screenshot of a wide window alone does not test hinge avoidance or fold/unfold state retention. Sources: https://developer.android.com/training/testing/different-screens/tools and https://developer.android.com/reference/kotlin/androidx/window/testing/layout/package-summary (accessed 2026-10-11). Host screenshot compatibility with the final posture adapter is UNVERIFIED until the chosen-stack spike.

Android smoke runner: `ReactiveCircus/android-emulator-runner` installs SDK/emulator/system image and runs a supplied script. Its README shows Linux KVM permission setup and recommends Linux hardware acceleration. Use a pinned action SHA and pinned API/system image, execute `connectedDebugAndroidTest` for S1 or Flutter integration tests for S2, and capture actual emulator screenshots with adb. Exact Gradle task names depend on the module graph. Source: https://github.com/ReactiveCircus/android-emulator-runner (accessed 2026-10-11).

Artifact delivery: upload baseline/current/diff PNGs plus HTML/JUnit output using `actions/upload-artifact`, including failure paths with `if: always()`, named by platform/size/posture. Put the artifact link, fixture names, and pass/fail table in `$GITHUB_STEP_SUMMARY`; do not assume private runner-local image paths render in summaries. Retain screenshots for 30 days by design and release binaries separately. Sources: https://github.com/actions/upload-artifact and https://docs.github.com/en/actions/reference/workflow-commands-for-github-actions (accessed 2026-10-11).

Current Avalonia headless source: https://docs.avaloniaui.net/docs/testing/setting-up-the-headless-platform (accessed 2026-10-11, official indexed page), documenting Skia rendering and `CaptureRenderedFrame`. Microsoft MAUI Appium sample includes Windows-specific test bootstrap: https://learn.microsoft.com/en-us/samples/dotnet/maui-samples/uitest-appium-nunit/ (accessed 2026-10-11, official indexed page). Windows UI automation still needs a hosted-runner spike: Microsoft notes WinAppDriver is no longer actively developed at https://learn.microsoft.com/en-us/windows/apps/develop/testing/ (accessed 2026-10-11).

Headless source-access limit: the newly opened `/docs/testing/headless` URL failed in the web connector; Avalonia headless capability is corroborated by official indexed documentation, while exact current API wiring must be checked in the build spike. The English MAUI UI-testing URL also failed to open; official translated/indexed documentation confirms Appium, not a completed Windows runner session.

### F. Placeholder scan and exact allowlist

Requirement: GAUNTLET.md section 3 (local brief read 2026-10-11) lists `TODO`, `FIXME`, `XXX`, `placeholder`, `not implemented`, `NotImplementedError`, Kotlin `TODO()`, empty click handlers, lorem ipsum, and stub returns. Scanning all documentation would flag this report and the requirement itself. Define source/build/workflow scan roots explicitly at Gate 2; docs are requirements, not app implementation. Every exclusion beyond those roots needs review. Pattern matching cannot reliably recognize every semantic stub; pair it with review and behavior tests. This is a design finding, not a sourced claim about a stack.

Minimal workflow fragment below is a design sketch, not a tested file. It reads Markdown-backticked `file:line` entries from `docs/gauntlet/IMPOSSIBLE.md` as exact exceptions. Gate 2 must settle the authoritative entry grammar and owned roots. Missing roots are skipped to let a pre-code repository pass; a real build must assert expected source roots exist. Basic grep failures other than “no match” fail the job.

```yaml
# DESIGN SKETCH, NOT A TESTED FILE
- name: Placeholder scan
  shell: bash
  run: |
    set -euo pipefail
    allow=$(mktemp)
    hits=$(mktemp)
    trap 'rm -f "$allow" "$hits"' EXIT
    if [[ -f docs/gauntlet/IMPOSSIBLE.md ]]; then
      { grep -oE '`[^`[:space:]]+:[0-9]+`' docs/gauntlet/IMPOSSIBLE.md || test "$?" -eq 1; } |
        tr -d '\140' | sort -u > "$allow"
    fi
    pattern='TODO|FIXME|XXX|placeholder|not[[:space:]]+implemented|NotImplementedError|lorem[[:space:]]+ipsum|onClick[[:space:]]*=[[:space:]]*\{[[:space:]]*\}|onPressed[[:space:]]*:[[:space:]]*\([^)]*\)[[:space:]]*\{[[:space:]]*\}|return[[:space:]]+(null|Unit)[[:space:]]*;?[[:space:]]*//[[:space:]]*stub'
    for root in app shared composeApp lib src .github/workflows; do
      [[ -d "$root" ]] || continue
      grep -RInIE --exclude-dir=build --exclude-dir=.gradle --exclude-dir=node_modules \
        --exclude=placeholder-scan.yml "$pattern" "$root" >> "$hits" || test "$?" -eq 1
    done
    failed=0
    while IFS= read -r hit; do
      location=$(printf '%s\n' "$hit" | cut -d: -f1,2)
      if ! grep -Fxq -- "$location" "$allow"; then
        printf '%s\n' "$hit"
        failed=1
      fi
    done < "$hits"
    exit "$failed"
```

The named scanner file exclusion avoids its own pattern literals matching themselves; orchestrator must fix the scanner's actual filename and review its contents separately. Multi-line empty handlers and arbitrary fake returns evade this grep sketch. Do not claim it fully enforces the standard without an AST-aware check or reviewer. `IMPOSSIBLE.md` line-number exceptions become stale after edits, deliberately causing review rather than blanket suppression. Source for workflow `run` syntax: https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax (accessed 2026-10-11).

### G. Pinning, caching, and release assembly

- Pin action references by reviewed full commit SHA, including setup and upload actions. A SHA pins the action implementation; separately pin toolchain, packages, emulator image, JDK, and SDK versions. Untrusted PR jobs must not receive release secrets. Source: https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions (accessed 2026-10-11).
- S1: commit Gradle wrapper and its distribution SHA-256; use dependency locks plus verification metadata (SHA-256/SHA-512 or trusted signatures). Automatically bootstrapped checksums still need review against trustworthy upstream information. Sources: https://docs.gradle.org/current/userguide/gradle_wrapper.html , https://docs.gradle.org/current/userguide/dependency_locking.html , https://docs.gradle.org/current/userguide/dependency_verification.html (accessed 2026-10-11).
- Cache Gradle through `gradle/actions/setup-gradle`, with read-only behavior for untrusted branches; never cache `$RUNNER_TEMP` key files or signing properties. Avoid redundant Gradle caching mechanisms. Cache is an optimization, not evidence of integrity. Source: https://github.com/gradle/actions/blob/main/setup-gradle/README.md (accessed 2026-10-11, exact selected version behavior UNVERIFIED).
- S2: commit `pubspec.lock` for an application and pin the Flutter SDK; cache SDK/pub packages without signing files. S3/S4 use NuGet lock files/locked restore; S5 lock JS dependencies; S6 commit Cargo and frontend locks. Exact caching actions for S2-S6 remain UNVERIFIED, not blockers to uncached CI. Sources: https://dart.dev/tools/pub/dependencies , https://learn.microsoft.com/en-us/nuget/consume-packages/package-references-in-project-files#locking-dependencies , https://docs.npmjs.com/cli/commands/npm-ci , https://doc.rust-lang.org/cargo/guide/cargo-toml-vs-cargo-lock.html (accessed 2026-10-11).
- Design release DAG: lint/scan and unit tests first; Linux Android smoke and host goldens; Windows package/native smoke/goldens; restricted Android signing and optional Windows signing; independent certificate/package verification; final release job depends on every acceptance check. Explicitly download only named binary artifacts, create SHA-256 manifests, and use `gh release create TAG assets... --target COMMIT --generate-notes` with `contents: write`. This creates the tag remotely, satisfying the session's no-local-tag-push constraint. Source: https://cli.github.com/manual/gh_release_create (accessed 2026-10-11; CLI behavior verified from source in interrupted report). Project-wide pipeline remains UNVERIFIED until Gate 3.


## Options

| Option | CI/packaging finding | Gate 1 implication |
|---|---|---|
| S1 Compose | First-party Windows jpackage tasks; Android Gradle signing; JVM/Android and Desktop screenshot options | Strong fallback on CI feasibility; choose only after R-01 matrix covers database/LLM/adaptive criteria |
| S2 Flutter | First-party Android and Windows builds; unit/widget goldens and desktop integration tests | Credible alternative; installer composition and native Windows smoke are additional workflow work |
| S3 MAUI | Documented CI Windows publishing and Appium | Feasible, with less verified host screenshot/posture machinery |
| S4 Avalonia | Documented headless tests; Parcel or external installer tooling | Feasible; commercial packager terms need verification or avoid Parcel |
| S5 RN/RNW | Documented Store packaging and framework automation | CLI package/sign/test path remains incomplete in this research |
| S6 Tauri | Windows MSI/NSIS and desktop WebDriver | Mobile WebDriver gap and foldable/native testing remain unresolved |

This table is an assessment based on findings C/E, not the R-01 weighted score or a completed build spike.

## Recommendation

Recommend S1's built-in Windows MSI plus optional portable app-image ZIP, existing-key Android APK signing, Roborazzi Android/Desktop screenshots, Linux emulator smoke, and Windows native smoke as the CI baseline. Keep S2 as a fully credible comparison. R-01 determines the final stack; CI alone provides no sourced reason to disqualify S2-S4.

D-02 is already public: standard hosted runners incur no minute charge, but larger runners and storage choices still need care. D-03 is already complete: install the four delivered secrets, preserve backups, and compare CI's certificate to D-03. Do not generate another key. D-06 recommendation: unsigned MSI/ZIP can be a personal-use interim if Pat accepts Windows policy restrictions; offer Azure Artifact Signing from roughly $9.99/month if Pat is an eligible individual in US/Canada, or investigate SignPath after licensing/reputation/manual approval eligibility. Public repository visibility alone does not make either service available. Do not buy EV solely for SmartScreen.

All recommendations are design judgments derived from A-G, not proof of a successful project pipeline.

## Risks

- No candidate has been built or measured for this app. Published generic package-size claims do not predict the offline card database, assets, models, or runtime footprint. Require actual artifact bytes and installed sizes in Gate 3.
- Host golden tests cannot prove TalkBack/Narrator, real hinge hardware, resize state retention, native menus, or installer behavior. Keep integration and device/manual checks alongside screenshots (E).
- SignPath requires manual approval per release, release history, OSI licensing, and verifiable reputation, not just a public repo. Source: https://signpath.org/terms (accessed 2026-10-11). Its terms must be reconciled with Codex-alone engineering, without assuming Codex can fulfill human approval.
- Source inventories from the interrupted report contain precise rolling runner/library versions that were not revalidated. These were removed where possible. Pin actual compatible versions during architecture; do not assume `windows-latest` is a permanent image (A/C).
- Exact scanner entry grammar, full action SHAs, .NET/RNW/Tauri Android signing paths, Parcel licensing, and artifact size measurements are UNVERIFIED. These need implementation checks or explicit alternatives before declaring CI complete.

## Confidence

High for public standard-runner billing, documented package formats, existing-key signing primitives, and SmartScreen distinctions; primary pages were rechecked on 2026-10-11. Medium for S1/S2 screenshot pipeline feasibility because tools document the features but the combined app fixtures have not run. Low for S3-S6 exact end-to-end release recipes, package sizes, and service acceptance. The interrupted A-D source readings were retained where relevant, rather than falsely presenting them as new build evidence.

No confirmed contradiction with GAUNTLET.md section 10 or Appendix A: cloud-only builds remain feasible. The research corrects stale assumptions: D-02 is public, D-03 key already exists, four secrets are required, Roborazzi supports Compose Desktop, signed binaries can still trigger SmartScreen, and EV does not buy immediate SmartScreen trust. These are findings, not authorization to pass a gate.


## Orchestrator synthesis after R-01

R-01 now recommends Flutter under the complete brief matrix, whereas this CI-only report recommended S1 as a feasible baseline. Both can be true: final stack is D-07, not this criterion alone. If Flutter is ruled, D-06 recommendation becomes an unsigned per-user Inno Setup EXE plus a portable ZIP for personal use, with trusted Windows signing deferred. Inno officially supports nonadministrative installs, single EXE packaging, uninstall and both x64/ARM64. Its license permits use for any purpose, including commercial applications, subject to preserving notices and identifying modified versions. Sources: https://jrsoftware.org/isinfo.php and https://jrsoftware.org/files/is/license.txt (accessed 2026-10-11). Flutter documents collecting the complete binary/DLL/data tree into a ZIP, and requires a PFX for self-hosted MSIX. Source: https://docs.flutter.dev/platform-integration/windows/building (accessed 2026-10-11).

This proposal avoids claiming an unsigned MSIX is installable. Package creation, upgrade/uninstall behavior and VC++ runtime inclusion still need GitHub Actions verification. No installer was built at Gate 1. An unsigned download can trigger SmartScreen or stricter Windows policy; D-06 asks Pat to accept that personal-use tradeoff. The existing Android release key remains independent of Windows Authenticode certificates.

## Current D-03 integration addendum, 2026-10-11

The ledger update committed at 13a26c3 supersedes the four-secret transport described above. Pat confirmed adding one repository secret ANDROID_SIGNING: base64 of compact JSON with keystore_base64, store_password, key_alias and key_password. CI must decode it, register masks for extracted passwords before logging, place the existing keystore only in runner temporary storage, verify the signed APK certificate against D-03 and clean up. No secret values belong in the workflow or repository. Presence and successful use are UNVERIFIED until a signed M0 run. Preserve the existing key; do not regenerate. Source: docs/gauntlet/DECISIONS.md D-03, commit 13a26c3, read 2026-10-11.

Overall stack choice remains R-01, not this topic's standalone CI preference. If D-07 chooses Flutter, use the preceding Inno Setup EXE/portable ZIP addendum. Runtime builds and accessibility probes belong to implementation gates after architecture, not app code during Gate 1-3.
