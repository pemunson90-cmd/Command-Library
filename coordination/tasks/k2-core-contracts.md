# K2 core contracts

Status: completed and accepted. Worker commits8cf8449 +9681aec were tested on compile revision2ac4a302 with25 JVM tests (13 model/12 common), and independent Astra review closed all four bounded findings. Root integrated exact unchanged core source; component lock released. Android/runtime behavior is not proved by this result.

Owner: scoped Sol-high implementation worker. Branch: agent/k2-core-contracts. Base: 63be52d (full verified SHA recorded in core lock). Exclusive source/test paths: android-app/core/model/src/** and android-app/core/common/src/**. Gradle/CI/coordination files remain orchestrator-owned.

Read KOTLIN_CONTRACTS, DATA_MODEL, INTERFACES and all twenty component public sketches. Define real pure Kotlin public ports and immutable typed DTOs for the complete inventory. Do not implement future services or use Any/raw maps/empty DTOs/TODO/error placeholders. Preserve provenance, source-qualified identities, exact decimal/native units, revisions, bounded pages, unavailable/unknown/cancellation and English consent semantics. Declare the full aggregate CatalogRepository; implement no partial aggregate. Independently reviewed local lookup/import/status interfaces will be added at the root design gate.

One numbered step: write complete real contract declarations plus meaningful pure-domain tests. Write early, report exact unresolved semantics instead of inventing source facts. kotlin.coroutines/Flow may be used; no Android/SQLite/HTTP platform types. Ports may be declared without implementations. Root handles hosted compilation; do not claim local compilation without a compiler. No source/provider terms research; user handles regular-chat packets. No recursive agents, push or shared-file edit.

Root independently reviews declarations, compiles all ports, integrates only accepted source and updates notes. A draft is not accepted code or completed K2.
