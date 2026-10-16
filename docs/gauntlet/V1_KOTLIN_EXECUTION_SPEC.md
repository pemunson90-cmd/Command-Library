# Version 1 Kotlin execution specification

D-31 (2026-10-15) controls release sequencing: this complete specification remains the roadmap; only the explicitly approved first-release portions gate the first signed usable Fold7 package. See DECISIONS D-31 and existing execution/coverage release dispositions. Data integrity, source/consent, safe enabled functionality and actual signing/device acceptance remain mandatory.

Status: active execution reference after accepted K0 and authorized K1 (D-20, 2026-10-11). K1 readiness and subsequent Kotlin Android sections may proceed through their current reviewed task cards; each section still requires its stated evidence and review before acceptance. This document does not authorize sync, version-2 Windows work, or public release.

## Controlling scope

Pat's current direction, 2026-10-11:

> Just run with kotlin, but plan for expansion to flutter for windows when v2 launches. It would be a separate file entirely. Then we build sync into it but that's for after it runs period.

It resolves the Android-first rescore question in D-19. Version 1 is a complete Android application written in Kotlin. Version 2 is a separate Flutter-for-Windows implementation begun only after Android version 1 runs. Sync is deferred until after the Windows application runs; no shared-account, cloud, peer-sync, sync schema, or sync transport is part of version 1 or the initial Windows execution plan.

## Decision effects

| Area | Version 1 | Version 2 | After Windows runs |
|---|---|---|---|
| Platform | Android phones, tablets and Fold7 | Windows | Android/Windows data sync evaluation and design |
| Application language | Kotlin | Dart/Flutter in a separate application source tree | Existing platform languages retained |
| User data | Offline local data on Android | Offline local data on Windows | Define migration, identity, conflict and privacy policy before any sync code |
| Packaging | K2 hosted debug APK for compile/install/launch/instrumentation; complete signed release APK and certificate verification at M9 | Windows packaging and accessibility work | Sync release plan only after both offline apps run |
| Shared code | No Flutter/Dart requirement | Do not retrofit Android into Flutter | Contract may be deliberately shared only after an approved sync design |

The former D-07 Flutter choice is superseded for version 1. Flutter research remains historical evidence for version 2. Windows tasks and Windows-only dependencies are excluded from all version 1 locks, CI, implementation cards and acceptance claims.

## Version 1 architecture

Use a Kotlin Android application with Jetpack Compose proposed for presentation, Room/SQLite proposed for local catalog storage, DataStore proposed for preferences, and Android Keystore-backed credential storage proposed for credentials. K1 must separately verify a supported FTS5 integration; no Room/SQLite binding or FTS5 package is accepted merely because this plan names it. Exact SDK, Gradle, Kotlin, AndroidX, Room and native dependency versions are intentionally unpinned until the Kotlin toolchain readiness task verifies current official sources and a hosted build transport.

The Android source layout follows the K1 ownership candidate, subject to its independent review:

```
android-app/
  core/model/              immutable domain values and public DTOs
  core/common/             platform-independent common utilities
  data/<component>/        network, vault and storage adapters
  feature/<component>/     component contracts and Android feature adapters
  app/                     Android entry point and orchestrator-owned bootstrap
  database/registry/        orchestrator-owned migration registry
```

The complete paths, module assignments, shared roots and current Kotlin contract translations are recorded in KOTLIN_OWNERSHIP_MAP.md, KOTLIN_CONTRACTS.md and kotlin-registry.json. These Kotlin documents replace the historical `packages/<component>` ownership and Dart-flavoured signatures for Android v1. Preserve exclusive ownership and all64 requirement IDs and213 parity obligations. Compile the complete20-component public-port surface before consumers are eligible; K2 may instantiate only its accepted working subset. Preserve English card data by default, explicit approval before non-English downloads, foreign owned identities without automatic download, unknown values never represented as zero, full offline rules acquired/imported through permitted user action, and all user data usable offline.

## Execution stages and acceptance boundaries

| Stage | Intended outcome | Required evidence before advancing | Recommended model |
|---|---|---|---|
| K0 plan audit | This specification, current requirements and task cards agree | Independent audit resolves all contradictions; published checkpoint | GPT-6 Astra low reviewer |
| K1 Kotlin readiness | Exact current Kotlin/Android/Compose/Room toolchain candidate and safe hosted build probe | K0 accepted and K1 authorized; official source URLs, dated candidate lock, source-license notes, no guessed pins | GPT-6 Astra high architect; GPT-6 Luna for mechanical lock drafting |
| K2 foundation | Compiled complete20-component public contracts plus functional debug foundation: tiny real catalog import/search/status, FTS5/storage and vault port | Hosted compile, debug APK artifact/checksum, install/launch and instrumentation evidence; focused tests; no secret output; independent review | GPT-6.1 Sol high implementer, Astra high reviewer |
| K3 offline data | English catalog/rules acquisition/import, consent states and local search | Authentic fixture/source evidence; corpus/import/cancel results | Astra high for importer and correctness design; Sol high for implementation |
| K4 product modules | Life, decks/import, collection/pricing, validation/brackets/links, recommendations and AI in the existing milestone order | Per-module contract, tests, review and checkpoint | Sol high implementation; Astra high only for cross-cutting decisions |
| K5 Android acceptance | Fold7/TalkBack/accessibility, performance, restore and signing evidence across the complete accepted requirement inventory (M9) | Complete signed release APK artifact, checksum and existing signing-certificate verification; full requirement/acceptance trace and manual evidence recorded separately | Astra high for acceptance review; Sol high for remediation |
| W0 Windows planning | Flutter Windows specification in a separate file and separate task queue | Android K5 accepted; explicit version-2 go-ahead | Astra high architect |
| W1 Windows implementation | Flutter Windows app runs with its own offline store and accessibility acceptance | Windows build/install/Narrator evidence | Astra high architecture/review; Sol high implementation |
| S0 sync planning | Decide whether and how the two running apps sync | Explicit user authorization plus privacy, identity, conflict, migration and cost design | Astra high architect/security reviewer |

Use the latest weakest capable model by task: Luna for mechanical documentation/fixture bookkeeping; Sol medium for routine implementation/review; Sol high for complex implementation; Astra high for mandated architecture/security/durability review and difficult cross-module decisions. Standalone API/terms research and design research use regular-chat packets. Request a stronger model when a task changes database durability, import atomicity, encryption/credential handling, identity or sync semantics, cross-module public contracts, performance acceptance, or a source/license conclusion. Keep cloud work to byte-level checks needed for the build; defer unrelated cloud research.

## Replaced and active task sequence

1. `orch-kotlin-plan-audit-01`: audit this scope against SPEC, COVERAGE, architecture contracts, milestones, task cards and continuation files. It performs no application work.
2. `orch-kotlin-toolchain-01`: replace the stale Flutter/Dart readiness task after K0 acceptance and an explicit user execution release. It researches and proposes a Kotlin/Android lock; it must stop before committing an implementation lock until its evidence is independently reviewed. A source-backed candidate permits a later compatibility attempt; only a successful targeted compile/probe can validate a binding.
3. `orch-kotlin-m0-01`: define the functional Android debug foundation task after K1 candidate acceptance and ownership/contract review. Full permanent-key signed release and certificate verification remain M9 acceptance.

`orch-source-fixtures-01` remains a read-only evidence fallback and may be used only when a future accepted task explicitly selects it. It is not an instruction to download product data.

## Current execution boundaries

- No public release or synchronization work under this specification; implementation actions remain limited to the exact active task card and its prerequisites.
- No migration of existing requirements into an asserted Kotlin implementation.
- No version pin is current merely because this document names a technology.
- No Windows task begins before Android version 1 acceptance, and no sync task begins before the Windows app runs and receives explicit authorization.

## Superseded planning artifacts

The following are retained as historical research or obligation inventories and are ineligible as version-1 execution instructions: Flutter/Dart package contracts, pubspec ownership rules, the former `orch-toolchain-01`, Flutter/Windows clauses in former M0 cards, and any requirement wording that calls for a shared Flutter codebase or a Windows artifact during version 1. K0 must identify each active reference by file. K1 must publish its Kotlin replacement map before K2. Product requirements are retained; only their old technology and path instructions are superseded.

## Historical K0 audit checklist

The completed K0 reviewer verified: D-07 is resolved for version 1; Android-v1 entry points name Kotlin; Windows is a separate v2 source tree; sync begins only after Windows runs; source-fixture fallback does not imply an unapproved download; and stale task commands are identified. This checklist describes the historical K0 audit only; it does not constrain authorized K1/K2 execution to planning-only actions.



## D-21 complete-section execution policy

The full Android app is the delivery target. Each internal section has a functional finish line and is not a public or finished-app release. Before a section closes, publish its evidence, independent-review result where required, current state, queue, handoff, journal entry and exact next action; read back the checkpoint and release the lease. A usage-limit checkpoint preserves unfinished work only when labelled incomplete. No placeholder, fake success path or empty production implementation may satisfy a section. A dependency that cannot be built without unavailable tools or approval must name the exact affected requirement and missing capability.

## D-26 execution amendment

Pat approved the research-based professional release plan on2026-10-13 with on-device AI engines/runtime/model management deferred to v1.1. H03/S25 and engine-only acceptance clauses are not v1 gates. Retained prompt export, cloud/custom/LAN AI, grounding/tools/vault/history and all other functional/professional/canonical/Fold7 requirements remain v1. Implement v1 without native inference dependencies or inert model controls; track engines as approved-deferred, never as completed features.
