# cx-k3-origin-cooldown-storage-01

Tests-only source: `android-app/data/storage/src/androidTest/kotlin/com/architectpsg/commandlibrary/data/storage/OriginCooldownMigrationTest.kt`.

The two native cases seed the committed KSP schema-4 export with one synthetic row in each of its 17 entities, then open the file through the real `StorageDatabase` migration to schema 5. They check all legacy rows, column metadata, DDL, FTS and foreign keys; the cooldown table's four columns, nullability, primary key and lack of job FK; finite and indefinite rows after reopen and job deletion; retained tombstone revision and stale CAS rejection; and fresh versus migrated cooldown table shape. The 17-entity seed was checked with local SQLite and has zero FK violations. No app data or real provider content is used.

Verification: native execution `not_run`; root owns schema-5 production implementation/registration and hosted execution. A local SQLite replay seeded all 17 schema-4 entities with zero foreign-key violations, and a static check found no trailing whitespace in the new test file. No Gradle, commit, push or workflow change by this worker. Next: root integrates migration/entity/version/export, compiles this test, then runs it on the coherent native matrix and inspects actual XML results.
