# Complete local search preparation capability —2026-10-15

Public contract, adapter and pure local factory implemented with independent design/exact-source ACK. Parenta6ba6f8 on agent/cx-thread-recovery-20261013. No query executor, ShellPorts/DI binding, acquisition initialization or finished APK claim.

Core:model CatalogSearchPreparation exposes real read-only status and one bounded advance for an explicit installed English Scryfall generation. NOT_PREPARED means no build, not empty READY. Typed immutable phase/count/state models expose committed materialized work without storage cursors/rowids, count-vector encoding, guessed timestamps, totals or context quantities. Failure exists iff FAILED; READY requires final verification phase. Version positive, counts nonnegative, generation128UTF8/noNUL. Invalid input returns INVALID_INPUT before database access. Missing generation never substitutes active.

LocalCatalogSearchPreparation delegates both methods to the same proven durable engine, maps exact state and preserves typed failure/unavailable/unknown/cancellation. AndroidCatalogServices.localSearchPreparation(database,clock) creates only that local wrapper; database/clock remain caller-owned and all instances share the existing catalog import owner. No transport, settings, artifact directory, consent or recovery engine is constructed. No reset/source deletion API or fake CardSearch aggregate.

Actual check SUCCESS50s/146tasks21executed125up-to-date:

- core:model:test:14XML85tests0failure/error/skip (81prior+4new contract invariant methods).
- Focused catalog adapter:1XML5tests0failure/error/skip; actualschema9 fixture proves absent/progress/ready/durablefailed, invalid-before-DB, retired/missing generation, cancellation/unknown acknowledgement.
- Existing app root/source plus new factory case:2XML12tests0failure/error/skip. Actual StorageDatabase/Room9 local import→public factory→READY→second-factory durable status readback proves the same transaction normal-text/FTS rowid behavior, with zero acquisition jobs, no consent and no acquisition artifact directory.
- Native app Kotlin test compilation passed, including the same shared factory case. Device execution not run.

Total102tests/17XML, no failures/errors/skips; retained `/tmp/search-preparation-capability-xml` by module, log `/tmp/search-preparation-capability-r1.log`. This packet adds5catalog adapter cases; it does not claim a rerun of the prior full269catalog boundary. That independent reviewed source engine/lint/native+app pass and11prior root checks are recorded in CATALOG-SEARCH-READINESS-LOCAL-20261015.md. No schema/migration/importer activation behavior changed.

Next typed local compiler/executor semantic design and actual shell consumer. New-import staging integration, full source/FullLean/owned-deck-price contexts and M2–M9 remain incomplete. Frozen9c1573b/native38657175303 failure remains preserved; no rerun/native acceptance/full corpus/performance/physical/release evidence is added here.
