
# Kotlin ownership and replacement map

Status: K1 candidate. It preserves existing product obligations while replacing historical Dart/package ownership. It is not application source and requires independent K1 review before K2.

## Root and shared ownership

Future Android version-1 source root: `android-app/`. The future Gradle settings, version catalog, root build configuration, Android application manifest, CI workflow, database migration registry, DI assembly and navigation assembly are orchestrator-owned shared paths. A component worker may change only its named module and tests; cross-module contracts require the orchestrator and reviewer.

## Component modules

| Historical component | Kotlin module | Direct Kotlin dependencies | Coverage retained |
|---|---|---|---|
| core | `:core:model` and `:core:common` | none | N01, N03 |
| network | `:data:network` | core | N02 |
| vault | `:data:vault` | core | H07 |
| storage | `:data:storage` | core | N01, A06, A08, I02 |
| catalog | `:feature:catalog` | core, network, storage | A01, A02, A03, A05, A06 |
| rules | `:feature:rules` | core, network, storage | A04 |
| search | `:feature:search` | core, catalog, rules, storage | A07, A08 |
| decks | `:feature:decks` | core, storage | B01, B02, B03, B04, B05, B07, B09, B10, B12 |
| collection | `:feature:collection` | core, storage | C01, B10 |
| pricing | `:feature:pricing` | core, catalog, collection, network, storage | C02, C03, C04, B11 |
| validation | `:feature:validation` | core, catalog, rules | B06 |
| brackets | `:feature:brackets` | core, catalog, rules, validation, storage, network | E01, E02 |
| recommendations | `:feature:recommendations` | core, search, pricing, validation, brackets | D01, D02 |
| transfer | `:feature:transfer` | core, catalog, decks, collection, storage | B08, B13, G14, I01, I02 |
| life | `:feature:life` | core, rules, storage | G01-G13 |
| inference | `:feature:inference` | core, network, storage | H03 |
| ai | `:feature:ai` | core, search, validation, pricing, recommendations, vault, network, inference, storage | H01, H02, H04, H05, H06, H08, H09 |
| settings | `:feature:settings` | core, storage, vault | K01, K02 |
| links | `:feature:links` | core, catalog | F01 |
| shell | `:app` | core plus all feature modules through public UI contracts | J01-J04, N01, N03 |

No module may import another module's implementation package. `:core:model` holds immutable IDs, outcomes, cancellation and clock abstractions; `:core:common` holds no Android UI or persistence implementation. `:app` owns Compose navigation and adaptive presentation only. Module naming does not authorize implementation; K2's card will select a small accepted subset.

## Technology replacements still requiring proof

| Historical assumption | Kotlin replacement direction | K1/K2 proof required |
|---|---|---|
| Dart public exports | Kotlin public interfaces/data classes in `:core:model` or module API packages | Compile every public contract before consumers |
| pubspec workspace | Gradle multi-module settings/version catalog | Hosted Gradle configuration/compile probe |
| Flutter presentation | Jetpack Compose in `:app` and feature UI contracts | Compile plus Android accessibility/adaptive checks in delivered sections |
| Drift/sqlite3 packages | Room/SQLite candidate, with a separately selected FTS5 binding/adapter | Android packaged FTS create/insert/MATCH probe; no assumption from Room alone |
| Flutter secure storage | Android Keystore-backed vault adapter | Device/API-level behavior and backup/exclusion tests before H07 acceptance |

Windows Flutter ownership is intentionally absent: it belongs to a later separate version-2 source tree. Sync ownership is intentionally absent until the Windows application runs and receives later authorization.

