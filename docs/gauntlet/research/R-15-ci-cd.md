# R-15 CI/CD, signing, Windows packaging, screenshot tests

Path: `docs/gauntlet/research/R-15-ci-cd.md`. Task card: `coordination/tasks/cc-research-15.md`. Every claim carries its source URL and access date. Anything not confirmed by a source is marked UNVERIFIED. Access date for every source below is 2026-10-11 unless stated otherwise. Status: IN PROGRESS (sections are filled in order; sections marked PENDING are not yet written).

## Question
How does GitHub Actions build a release-signed Android APK and a Windows package for each candidate stack (S1 KMP + Compose Multiplatform, S2 Flutter, S3 .NET MAUI, S4 Avalonia, S5 React Native + RN Windows, S6 Tauri 2), run tests and screenshot tests per size class, and enforce the placeholder scan, all without a local machine?

## Findings

### A. GitHub Actions: billing, runners, artifacts, releases

- Standard GitHub-hosted runner minutes are free in public repositories. Quote: "GitHub Actions usage is **free** for **self-hosted runners** and for **public repositories** that use standard GitHub-hosted runners." Also: "Larger runners are always charged for, even when used by public repositories". Source: https://raw.githubusercontent.com/github/docs/main/content/billing/concepts/product-billing/github-actions.md (GitHub Docs source, accessed 2026-10-11). Rendered page: https://docs.github.com/en/billing/managing-billing-for-your-products/managing-billing-for-github-actions/about-billing-for-github-actions (accessed 2026-10-11, via a summarizing fetch tool, so the raw source above is the primary quote).
- Private-repository fallback (D-02 if Pat picks private): Free plan includes 2,000 minutes per month, 500 MB artifact storage, 10 GB cache per repository; Pro 3,000 minutes, 1 GB. Source: https://raw.githubusercontent.com/github/docs/main/data/reusables/billing/actions-included-quotas.md (accessed 2026-10-11).
- Per-minute list prices for standard runners (apply to private repos beyond quota): Linux 2-core x64 $0.006, Windows 2-core x64 $0.010, Linux 2-core arm64 $0.005, macOS 3 or 4 core $0.062. GitHub rounds each job up to the whole minute. Source: https://raw.githubusercontent.com/github/docs/main/data/reusables/billing/actions-standard-runner-prices.md and https://raw.githubusercontent.com/github/docs/main/content/billing/reference/actions-runner-pricing.md (accessed 2026-10-11). The old "minute multiplier" page now redirects to this per-minute SKU page (redirect_from `/billing/reference/actions-minute-multipliers` in the same file), so the Windows 2x and macOS 10x multipliers are not stated in current docs; the dollar ratio above is the current statement (Windows about 1.7x Linux, macOS about 10x Linux). The ratio is my arithmetic from the table. For a public repo none of this is charged.
- Hosted-runner price cut of up to 39% reportedly took effect 2026-01-01 and the planned $0.002 per minute "cloud platform charge" on self-hosted runners was postponed (secondary reporting, not GitHub's own page). Source: https://www.tenki.cloud/blog/github-actions-runner-pricing-2026 and https://gigazine.net/gsc_news/en/20251219-github-actions-pricing-change (secondary, accessed 2026-10-11). Irrelevant for public repos on standard runners; this project uses no self-hosted runners.
- Storage overage for private repos: shared artifact/Packages storage $0.25 per GB-month, Actions cache $0.07 per GB-month. Source: github-actions.md source above (accessed 2026-10-11).
- Standard runner specs (public repos): `ubuntu-latest` = Ubuntu 24.04, 4 vCPU, 16 GB RAM, 14 GB storage; `windows-latest` = Windows Server 2025, 4 vCPU, 16 GB RAM, 14 GB storage; `macos-latest` = macOS 26 arm64, 3 vCPU (M1), 7 GB. Source: https://docs.github.com/en/actions/reference/runners/github-hosted-runners (accessed 2026-10-11, summarizing fetch). The 4 vCPU/16 GB figure for public repos is as listed on that page; I did not independently verify the public-versus-private split.
- Android emulator acceleration: "GitHub-hosted Linux runners support hardware acceleration for Android SDK tools, which makes running Android tests much faster and consumes fewer minutes." The page does not say KVM by name. Source: https://raw.githubusercontent.com/github/docs/main/content/actions/reference/runners/github-hosted-runners.md (accessed 2026-10-11).
- Job limits: GitHub-hosted job execution time 6 hours; workflow run 35 days; job matrix 256 jobs; Free plan 20 concurrent jobs (5 macOS); a workflow file must be 500 KB or smaller. Source: https://raw.githubusercontent.com/github/docs/main/content/actions/reference/limits.md (accessed 2026-10-11).
- Artifacts: logs and artifacts are kept 90 days by default; for public repos the retention can be set to 1 to 90 days; `actions/upload-artifact` `retention-days` must be between 1 and 90 inclusive. Starting 2026-10-01 the retention policy also applies to checks, workflow runs and commit statuses (before that they were kept 400+ days). Sources: https://raw.githubusercontent.com/github/docs/main/data/reusables/actions/about-artifact-log-retention.md and https://raw.githubusercontent.com/actions/upload-artifact/main/README.md (accessed 2026-10-11).
- Artifact visibility: "People who are signed into GitHub and have read access to a repository can download workflow artifacts." In a public repo that is every signed-in GitHub user. Source: https://raw.githubusercontent.com/github/docs/main/content/actions/how-tos/manage-workflow-runs/download-workflow-artifacts.md (accessed 2026-10-11). Consequence: never upload key material, even encrypted, as an artifact of a public repo without extra protection.
- Job summaries and logs do not count against artifact storage: "If you run a workflow that generates many log files and a long job summary, these files do not count towards the repository owner's artifact storage allowance." Source: github-actions.md source above (accessed 2026-10-11).
- Runner image `windows-2025` (image version 20261004.281.1 at access time) preinstalls: WiX Toolset 3.14.1.8722, Inno Setup 6.7.1, Visual Studio Enterprise 2022 17.14 with the MAUI Android workload and Android NDK components, Windows SDK 10.0.26100.0, .NET SDKs 8.0, 9.0 and 10.0, Java 8/11/17 (default)/21/25 (JAVA_HOME_xx_X64 variables), Node 22.23.3, Rust/Cargo 1.99.0, Kotlin 2.4.20, Android SDK (command-line tools 16.0, emulator 37.2.12, build-tools 34 to 37, platforms android-34 to 37.2), Android NDK 27.3.13750724. Flutter did not appear in the tool lists I read (UNVERIFIED as absent; I read the language, tools and Android sections only). Source: https://raw.githubusercontent.com/actions/runner-images/main/images/windows/Windows2025-Readme.md (accessed 2026-10-11).
- Runner image `ubuntu-24.04` preinstalls: Gradle 9.8.0, Kotlin 2.4.20, Java 8/11/17 (default)/21/25, Rust 1.99.0, .NET SDKs 8/9/10, Android SDK platforms 34 to 37.2 and build-tools 34 to 37. Source: https://raw.githubusercontent.com/actions/runner-images/main/images/ubuntu/Ubuntu2404-Readme.md (accessed 2026-10-11).
- Creating a release from a workflow with no pre-existing tag (this cloud session cannot push tags): `gh release create <tag>` "If a matching git tag does not yet exist, one will automatically get created from the latest state of the default branch. Use --target to point to a different branch or commit for the automatic tag creation." Assets are passed as file arguments; `--generate-notes` builds notes via the Release Notes API. It runs with the workflow's `GITHUB_TOKEN` when the job declares `permissions: contents: write` (the permission key is my reading of the standard Actions permission model; the `gh` help text does not state it, so the exact key is UNVERIFIED until the pipeline is exercised at Gate 3). Source: https://raw.githubusercontent.com/cli/cli/trunk/pkg/cmd/release/create/create.go (accessed 2026-10-11).
- Release limits: up to 1000 assets per release, each file under 2 GiB, no total size or bandwidth limit. Sources: https://raw.githubusercontent.com/github/docs/main/content/repositories/releasing-projects-on-github/about-releases.md, https://raw.githubusercontent.com/github/docs/main/data/variables/releases.yml, https://raw.githubusercontent.com/github/docs/main/data/variables/large_files.yml (accessed 2026-10-11).
- Immutable releases: when repo-level release immutability is enabled, tags and assets of a published release cannot be modified or deleted; `gh release create` creates a draft, uploads assets, then publishes, so protections apply only after publish. Source: gh create.go above (accessed 2026-10-11). Implication: if immutability is on, a broken release needs a new version number, not an asset swap.

### B. Android release signing
PENDING

### C. Windows packaging per stack
PENDING

### D. Windows code signing and SmartScreen
PENDING

### E. Tests in CI
PENDING

### F. Screenshot testing per size class and posture
PENDING

### G. Placeholder scan
PENDING

### H. Dependency pinning and caching
PENDING

## Options
PENDING

## Recommendation
PENDING

## Risks
PENDING

## Confidence
PENDING
