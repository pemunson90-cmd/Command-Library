# Version 1 Kotlin execution specification

Status: planning only. This document authorizes no implementation, toolchain pinning, source download, signing operation, build, release, or sync work.

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
| Packaging | Android signed APK only after the planned acceptance gates | Windows packaging and accessibility work | Sync release plan only after both offline apps run |
| Shared code | No Flutter/Dart requirement | Do not retrofit Android into Flutter | Contract may be deliberately shared only after an approved sync design |

The former D-07 Flutter choice is superseded for version 1. Flutter research remains historical evidence for version 2. Windows tasks and Windows-only dependencies are excluded from all version 1 locks, CI, implementation cards and acceptance claims.

## Version 1 architecture

Use a Kotlin Android application with Jetpack Compose proposed for presentation, Room/SQLite proposed for local catalog storage, DataStore proposed for preferences, and Android Keystore-backed credential storage proposed for credentials. K1 must separately verify a supported FTS5 integration; no Room/SQLite binding or FTS5 package is accepted merely because this plan names it. Exact SDK, Gradle, Kotlin, AndroidX, Room and native dependency versions are intentionally unpinned until the Kotlin toolchain readiness task verifies current official sources and a hosted build transport.

The Android source layout will be established during M0 only after toolchain acceptance:

```
android-app/
  app/                    Android entry point and Compose navigation
  core/                   value types, outcomes, cancellation and clocks
  catalog/                catalog import and local index ports
  storage/                Room/SQLite generation and FTS implementation
  settings/               consent and local preference policy
  test-fixtures/          sanitized authentic and labelled synthetic fixtures
```

This is a planning layout, not a file-creation instruction. The existing `packages/<component>` ownership registry and Dart-flavoured architecture contracts are historical planning artifacts for version 1. Before any consumer implementation, K1/K2 must create and independently review a Kotlin ownership map that assigns every existing product obligation to an `android-app/<module>` path, replaces pubspec assumptions, and preserves exclusive ownership. Existing component requirements continue to apply, but their language-specific contracts must be translated into Kotlin interfaces before a consumer is implemented. The version 1 architecture must preserve the existing product rules: English card data by default, explicit approval before non-English data downloads, foreign owned identities retained without automatic download, unknown values never represented as zero, full offline rules acquired or imported through permitted user action, and all user data usable offline.

## Execution stages and acceptance boundaries

| Stage | Intended outcome | Required evidence before advancing | Recommended model |
|---|---|---|---|
| K0 plan audit | This specification, current requirements and task cards agree | Independent audit resolves all contradictions; published checkpoint | GPT-6 Astra low reviewer |
| K1 Kotlin readiness | Exact current Kotlin/Android/Compose/Room toolchain proposal and safe hosted build probe plan | Explicit user execution release after K0, official source URLs, dated candidate lock, source-license notes, no guessed pins | GPT-6 Astra high architect; GPT-6.1 Sol high for mechanical lock drafting |
| K2 foundation | Compiled Kotlin public contracts plus a tiny local catalog/FTS/status Android skeleton | Hosted build, installable debug APK and recorded launch smoke evidence; focused tests; no secret output; independent review | GPT-6.1 Sol high implementer, Astra high reviewer |
| K3 offline data | English catalog/rules acquisition/import, consent states and local search | Authentic fixture/source evidence; corpus/import/cancel results | Astra high for importer and correctness design; Sol high for implementation |
| K4 product modules | Life, decks/import, collection/pricing, validation/brackets/links, recommendations and AI in the existing milestone order | Per-module contract, tests, review and checkpoint | Sol high implementation; Astra high only for cross-cutting decisions |
| K5 Android acceptance | Fold7/TalkBack/accessibility, performance, restore and signing evidence across the complete accepted requirement inventory | Real APK artifact, checksum, existing signing-certificate verification, full requirement/acceptance trace and manual evidence recorded separately | Astra high for acceptance review; Sol high for remediation |
| W0 Windows planning | Flutter Windows specification in a separate file and separate task queue | Android K5 accepted; explicit version-2 go-ahead | Astra high architect |
| W1 Windows implementation | Flutter Windows app runs with its own offline store and accessibility acceptance | Windows build/install/Narrator evidence | Astra high architecture/review; Sol high implementation |
| S0 sync planning | Decide whether and how the two running apps sync | Explicit user authorization plus privacy, identity, conflict, migration and cost design | Astra high architect/security reviewer |

An executor requests a stronger model instead of continuing when a task changes database durability, import atomicity, encryption/credential handling, identity or sync semantics, cross-module public contracts, performance acceptance, or a source/license conclusion. Routine formatting, fixture bookkeeping, documentation links and isolated unit implementation can use a lower-cost model under an accepted task card.

## Replaced and active task sequence

1. `orch-kotlin-plan-audit-01`: audit this scope against SPEC, COVERAGE, architecture contracts, milestones, task cards and continuation files. It performs no application work.
2. `orch-kotlin-toolchain-01`: replace the stale Flutter/Dart readiness task after K0 acceptance and an explicit user execution release. It researches and proposes a Kotlin/Android lock; it must stop before committing an implementation lock until its evidence is independently reviewed. A source-backed candidate permits a later compatibility attempt; only a successful targeted compile/probe can validate a binding.
3. `orch-kotlin-m0-01`: define the narrow signed Android skeleton task only after the toolchain plan is accepted. It does not begin in this plan.

`orch-source-fixtures-01` remains a read-only evidence fallback and may be used only when a future accepted task explicitly selects it. It is not an instruction to download product data.

## Explicit non-goals for this planning checkpoint

- No Android source, Gradle files, Kotlin code, Flutter code, Windows code, data download, API request, signing, build, release, or synchronization work.
- No migration of existing requirements into an asserted Kotlin implementation.
- No version pin is current merely because this document names a technology.
- No Windows task begins before Android version 1 acceptance, and no sync task begins before the Windows app runs and receives explicit authorization.

## Superseded planning artifacts

The following are retained as historical research or obligation inventories and are ineligible as version-1 execution instructions: Flutter/Dart package contracts, pubspec ownership rules, the former `orch-toolchain-01`, Flutter/Windows clauses in former M0 cards, and any requirement wording that calls for a shared Flutter codebase or a Windows artifact during version 1. K0 must identify each active reference by file. K1 must publish its Kotlin replacement map before K2. Product requirements are retained; only their old technology and path instructions are superseded.

## Audit checklist

The K0 reviewer must verify: D-07 is resolved for version 1; all Android-v1 entry points name Kotlin; no v1 task requires Flutter/Dart or Windows; Windows is recorded as a separate v2 source tree; sync begins only after Windows runs; the source-fixture fallback does not imply an unapproved download; task cards do not contain stale commands; and every next action is planning-only. The reviewer records each remaining discrepancy by file and exact change, then stops.

