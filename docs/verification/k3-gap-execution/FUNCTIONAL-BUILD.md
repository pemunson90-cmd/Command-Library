# Functional Android build for Fold7

The real offline catalog/rules/settings build is ready for physical Fold7 installation. This is a working-build milestone under D-22/D-23, not fullK3/complete-v1 acceptance. No unimplemented navigation or fake adapters are exposed.

## Exact build and package

- Tested head: d334d9e3a60ac739c038edcc52f18fb72414a068.
- Hosted run: https://github.com/pemunson90-cmd/Command-Library/actions/runs/38309643911 — all4lanes success.
- PR7 merged exact tested source at875ad4f2bfc4b1cf4aa8035c9182618424bfcb73. Root verified the entire android-app tree matches tested head (tree5c8dc9090f2de7c115d9b007da92943f2a12bf79).
- APK bundle: https://github.com/pemunson90-cmd/Command-Library/actions/runs/38309643911/artifacts/11774097672 . Extract app-debug.apk and open it on the Fold7.
- APK SHA256: cdc696e77ba78023dcd2b20e4b02016f68264e4890c62510d6f43a09f5d7c3b6. APK ZIP16KiB alignment passed. This is the debug APK; no permanent-key/public-release claim.

## Actual verification

Hosted256JVMtests reported0failures/errors. Each Android lane executed57native tests with0failures/errors: app4, storage21, catalog9, rules6, shell17. API23 default, API36 default and API36 google_apis_ps16k all passed; the16KiB lane reported actual16384-byte page size. API23 lacks the getconf diagnostic command; its native suite passed, and no measured page-size claim is made for it.

Native evidence covers real app graph/launch/rules import/search/detail without consent mutation; source-version unknown preservation; atomic activation/cancel/changed replay; expected hash/bytes; snapshot pagination/stale cursors/retained entries; close/reopen; corrupt payload rejection; actual exported-schema1 migration preserving every legacy column; Unicode FTS/reference/foreign-key behavior; catalog duplicates/canonical semantics; expanded UI and stored theme/system-bar appearance.

Genuine private source validation: official977752-byte TXT SHA8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca produces4357entries and2072references, all resolved,0duplicates/anomalies.79separate-module pureJVMtests passed. Unicode separators and standard skipped-letter ranges were fixed after genuine evidence, not hidden by dropping assertions. Authentic KSP schema2 SHA88066668f9bf27ec3c4923766f1c18ef696764e8bc54e66ae37a41a1600e99ae is committed; all7schema1entity definitions remain unchanged.

## Phone use and next action

Install the APK, then use Catalog to choose an English card JSON array (up to8MiB/10000printing records), and Rules to choose an English rules TXT (up to8MiB). Data remains installed for offline search/detail and settings persist. No corpus is prebundled; the existing small catalog limits are truthful and enforced. Full-feed/printing/rulings coverage is retained for later work under D-23.

Official rules TXT: https://media.wizards.com/2026/downloads/MagicCompRules%2020260925.txt . Personal validation inputs and a one-genuine-English-card array remain private at /workspace/scratch/k3-genuine-inputs; source payloads are not published in Git/APK/evidence artifacts. The starter array is representative, not full coverage.

Physical Fold7 install/launch, folded/unfolded interaction and real picker-provider behavior have not been run by this executor. This is the exact next action; record actual observations and fix any reported device issue. Emulator evidence does not establish a physical pass. Broader features, polish, updates and complete-product gates remain later, with all requirement/parity obligations retained.
