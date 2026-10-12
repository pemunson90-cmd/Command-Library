# cx-k3-storage-03: additive durable acquisition schema

Status: assigned; implementation/test execution unrun. Parent orch-kotlin-k3-offline-data-01; jobs-only infrastructure, not full K3 acceptance.

Read AGENTS/CONTINUE, storage ARCHITECT, KOTLIN_OWNERSHIP_MAP/KOTLIN_CONTRACTS, docs/verification/k3-downloads/ACQUISITION-DESIGN.md and ACQUISITION-INDEPENDENT-REVIEW.md, actual schema2/export/Migration1To2/native storage tests. Proposal tables/indices/FKs approved independently for implementation; DownloadPlan naming and SHA-256 correction do not change physical job schema.

Isolated worktree /workspace/scratch/Command-Library-storage-03, branch agent/cx-k3-storage-03. Write only AcquisitionTables.kt, Migration2To3.kt, storage src/androidTest/** and coordination/handoffs/cx-k3-storage-03.md. Root owns StorageSchemaDatabase.kt version/entity registry, StorageDatabase.kt migration registration, Gradle/config/CI/schema exports and other components. Request exact shared changes; do not edit them or generate a fake3.json.

Implement exact three acquisition tables from reviewed proposal (job,artifact,seen_id), strict identical Room entity/SQL shapes and additive2->3 Migration. Preserve all13old entities unchanged. Use explicit ColumnInfo names/style consistent existing schema; import androidx.room3.Migration correct suspend API fromMigration1To2. No constraints/defaults absentfromRoom definition.

Add meaningful native fixtures: seed real schema2 from committed2.json then upgrade via actual StorageDatabase.open after root registers3; verify legacy catalog/rules/attempt payloads and bytevalue/FK/index preservation, create/update/reopen paused jobcheckpoint and verifiedartifact, seen-ID duplicate/foreign-key constraints and cascadejobdeletion neverdeletecatalog/rules, rollback zero-rowCAS with precedingseen-IDinsert via explicit abort/latch (contract requirescallerabort). Keep migration1->2->3 coverage and updateold native latest-version assertions2->3 (don't alterold schema2DDL/referencefixtures). Use actual generatedschema only, no inventedidentityhash.

Compile tests if SDK available; local lackSDK means not_run, root hostednative willverify. Can use directprivatePythonSQLiteDDLshape checks labelled nonAndroid, not substitution forRoom. Commit+push implementation/tests+handoff exactresults beforefinish; root applies sharedregistry/version edits and runsactualKSP/native. No subagents; <=10linesreturn.
