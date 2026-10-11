#!/usr/bin/env bash
# K1 observes build tools only. It neither configures nor compiles an application.
set -euo pipefail
lock_path="${1:-config/kotlin-android-candidate-lock.json}"
test -f "$lock_path"
jdk_path="${JAVA_HOME_17_X64:?Hosted image must provide JDK17}"
export JAVA_HOME="$jdk_path"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac_version="$(javac -version 2>&1)"
printf '%s\n' "$javac_version"
grep -Eq '^javac 17\.' <<< "$javac_version"

gradle_version="$(jq -er '.candidates.gradle.version' "$lock_path")"
gradle_sha="$(jq -er '.candidates.gradle.sha256' "$lock_path")"
[[ "$gradle_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
[[ "$gradle_sha" =~ ^[0-9a-f]{64}$ ]]
probe_dir="$(mktemp -d "${RUNNER_TEMP:?}/command-library-k1.XXXXXX")"
archive="$probe_dir/gradle.zip"
curl --fail --location --silent --show-error --retry 2 --max-time 240 \
  "https://services.gradle.org/distributions/gradle-${gradle_version}-bin.zip" --output "$archive"
printf '%s  %s\n' "$gradle_sha" "$archive" | sha256sum --check --status
unzip -q "$archive" -d "$probe_dir"
"$probe_dir/gradle-$gradle_version/bin/gradle" --no-daemon --version

sdk_path="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk_path"
sdkmanager_path="$sdk_path/cmdline-tools/latest/bin/sdkmanager"
test -x "$sdkmanager_path"
"$sdkmanager_path" --version
test -f "$sdk_path/platforms/android-36/android.jar"
test -x "$sdk_path/build-tools/36.0.0/aapt2"
test -x "$sdk_path/build-tools/36.0.0/apksigner"
test -x "$sdk_path/platform-tools/adb"
"$sdk_path/build-tools/36.0.0/aapt2" version
"$sdk_path/platform-tools/adb" version
printf '%s\n' 'PASS: JDK17 compiler, checksum-verified Gradle, Android36/build-tools36 and platform tools are available.'
printf '%s\n' 'No Kotlin project, Compose/Room/KSP compilation, FTS5 runtime, signing or app/device test was run.'
