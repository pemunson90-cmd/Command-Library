# Complete local search preparation capability

> Inline root execution; independent public-contract/source review before implementation and exact-source review before publication. D-17/D-28: no routine user permission pause; batch meaningful checks.

Goal: expose the proven internal preparation engine as a complete independent local capability. No query executor/aggregate CardSearch placeholder, implicit generation selection, automatic backfill or acquisition initialization.

## Contract

Create core:model `CatalogSearchPreparationContracts.kt`:

- `CatalogSearchPreparationReadiness`: NOT_PREPARED, PREPARING, READY, FAILED.
- `CatalogSearchPreparationPhase`: CARDS, PRINTINGS, VERIFY_CARDS, VERIFY_FACES, VERIFY_PRINTINGS, VERIFY_TEXT, VERIFY_LEGALITIES, VERIFY_TEXT_MIRRORS. These are progress stages, not SQL/cursor fields. The internal VERIFY handoff maps to VERIFY_CARDS with no completed verification claim.
- `CatalogSearchPreparationCounts(cards:Long, faces:Long, printings:Long, textRows:Long, legalityRows:Long)`: all nonnegative, default0. Counts are complete committed materialized facts, not total upstream coverage, quantities owned, or verification percentages.
- `CatalogSearchPreparationState(generation:GenerationId, projectionVersion:Int, readiness:CatalogSearchPreparationReadiness, phase:CatalogSearchPreparationPhase?, processed:CatalogSearchPreparationCounts, failure:DomainFailure?)`. Version positive; generation bounded128UTF8/noNUL before database use. NOT_PREPARED requires NULL phase, zero processed and no failure; all other states require a phase. FAILED alone requires failure; READY requires final VERIFY_TEXT_MIRRORS phase. Producer emits version1 and fixed bounded CORRUPT_DATA diagnostic only for known durable FAILED; transient errors stay typed non-success Outcomes.
- `CatalogSearchPreparation.status(generation:GenerationId,cancel:CancelToken):Outcome<CatalogSearchPreparationState>` and `.advance(...)` same result. status is read-only; advance performs one bounded atomic unit or reads a terminal state. Missing/removed generation is NOT_INSTALLED, unsupported/corrupt source remains typed non-success, never rebound. Explicit English Scryfall ACTIVE/RETIRED only. Known committed result can be returned despite postcommit cancellation after exact readback, as with existing durable import semantics. Unknown acknowledgement is never replayed in that operation.

No raw source payload, provider/ID cursor, rowid, count-vector encoding, invented timestamp or authority token enters this public model. No reset/rebuild/delete or retry that clears FAILED/source data. An absent build on an installed valid source is NOT_PREPARED, never a successful empty ready index.

## Implementation and files

- Add internal `LocalCatalogSearchPreparation.kt`: implements both port methods by delegating to one actual CatalogSearchPreparationStore, mapping exact real state. Validate generation bound before delegation. Return no fake state on failure; pass failure/unavailable/unknown through unchanged. Do not catch/relabel CancellationException.
- Add pure static `AndroidCatalogServices.localSearchPreparation(database:Database,clock:Clock):CatalogSearchPreparation` alongside localRulings. It owns only the local wrapper; caller owns database/clock. No Context/settings/transport/artifact filesystem, consent, recovery job or second import owner. No root ShellPorts binding until real consumer exists.
- Add core contract tests for invalid phase/status/failure/count/version/generation combinations.
- Add catalog wrapper tests using actual schema9 SQLite fixture: installed absent→real bounded progress→ready, durable failed state, explicit retired snapshot vs missing generation, unchanged typed errors/cancel/unknown acknowledgement, no index reset.
- Extend existing shared EnglishCatalogAccessCases with actual StorageDatabase/Room9 local-import + public factory status/advance→ready and second-factory durable readback. Assert no acquisition artifact directory/job/consent; same fixture runs host and compiles for device. This specifically proves real Room transaction rowid pairing and public factory behavior, not device execution.

## Checks and continuation

Batch core:model tests + focused/full catalog tests and affected native/app tests as appropriate; run the actual Room9 factory case and existing shared-root regression. No whole-project/native workflow for this additive local capability. Review exact public/source implementation and publish truthful checkpoint. Next typed local compiler/executor design, independent semantic truth fixtures and real app consumer; new-import staging integration remains separately reviewed.

Independent design/exact-public-source ACK. Implemented all contract/adapter/factory and test items. Actual50s check:85model+5catalog adapter+12actualRoom9root tests,102total/17XML0failure/error/skip; native app test compile passes. No query/DI/device claim. Evidence SEARCH-PREPARATION-CAPABILITY-LOCAL-20261015.md.
