# K1 hosted capability probe

This is build-environment verification, not application code. Workflow `kotlin-capability.yml` runs only trusted main push/manual dispatch, with contents read, no signing secrets or retained checkout credentials, ten-minute timeout and immutable checkout v7.0.1 commit verified against its official tag.

`scripts/kotlin_capability.sh` selects the runner's JDK17 explicitly and observes both java and javac. It downloads Gradle9.6.0 from the official distribution endpoint, verifies the independently retrieved pinned SHA256 in the candidate JSON, then executes --version. It requires the hosted image's actual Android36 platform, build-tools36.0.0 and executable sdkmanager/aapt2/apksigner/adb; missing capability fails, never becomes success. It neither installs SDKs nor accepts licenses, connects to a device, compiles Kotlin, signs anything, nor initializes a project.

Known immutable inputs: checkout `3d3c42e5aac5ba805825da76410c181273ba90b1`; Gradle9.6.0 checksum `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`. Runner image can change; record actual run/image/tool versions in the report. Logs intentionally contain only tool output and the bounded PASS/boundary lines. No environment dump, credential/config inspection or product data requests.

Sources (2026-10-11): official checkout tag API; https://services.gradle.org/distributions/gradle-9.6.0-bin.zip.sha256; https://github.com/actions/runner-images/blob/main/images/ubuntu/Ubuntu2404-Readme.md. Image manifest supports the proposal but only successful workflow execution establishes availability. Compose/Kotlin/KSP/Room3 and packaged FTS5 remain K2 proof obligations.

Independent review: Luna medium approved the concrete workflow/script before publication. No blocking findings; duplicate javac invocation removed. Push triggers intentionally track executable/configuration changes only, avoiding reruns on prose edits. apksigner presence is checked, not signing behavior. Whole job timeout bounds retries. `bash -n` passes locally; hosted result must still be observed and recorded.
