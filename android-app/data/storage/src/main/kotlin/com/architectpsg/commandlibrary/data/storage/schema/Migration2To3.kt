package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection

/** Adds acquisition infrastructure only. Room owns the transaction, validation and identity. */
internal object Migration2To3 : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        for (sql in statements) connection.prepare(sql).use { it.step() }
    }

    private val statements = listOf(
        """CREATE TABLE IF NOT EXISTS acquisition_job ( job_id TEXT NOT NULL, payload_version INTEGER NOT NULL, plan_payload TEXT NOT NULL, plan_sha256 TEXT NOT NULL, state TEXT NOT NULL, phase TEXT NOT NULL, revision INTEGER NOT NULL, attempt INTEGER NOT NULL, owner_token TEXT, created_seconds INTEGER NOT NULL, created_nano INTEGER NOT NULL, updated_seconds INTEGER NOT NULL, updated_nano INTEGER NOT NULL, started_seconds INTEGER, started_nano INTEGER, consent_id TEXT, partial_key TEXT NOT NULL, durable_offset INTEGER NOT NULL, transferred_bytes INTEGER NOT NULL, record_count INTEGER NOT NULL, page_count INTEGER NOT NULL, expected_records INTEGER, next_page_uri TEXT, first_page_sha256 TEXT, final_uri TEXT, strong_etag TEXT, last_modified TEXT, expected_representation_bytes INTEGER, response_encoding TEXT, failure_payload TEXT, PRIMARY KEY(job_id))""",
        """CREATE INDEX IF NOT EXISTS index_acquisition_job_state_created ON acquisition_job(state,created_seconds,created_nano,job_id)""",
        """CREATE INDEX IF NOT EXISTS index_acquisition_job_created ON acquisition_job(created_seconds,created_nano,job_id)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS index_acquisition_job_partial_key ON acquisition_job(partial_key)""",
        """CREATE TABLE IF NOT EXISTS acquisition_artifact ( job_id TEXT NOT NULL, artifact_key TEXT NOT NULL, byte_count INTEGER NOT NULL, sha256 TEXT NOT NULL, framing TEXT NOT NULL, compression TEXT NOT NULL, verified_payload TEXT NOT NULL, payload_version INTEGER NOT NULL, completed_seconds INTEGER NOT NULL, completed_nano INTEGER NOT NULL, PRIMARY KEY(job_id), FOREIGN KEY(job_id) REFERENCES acquisition_job(job_id) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        """CREATE UNIQUE INDEX IF NOT EXISTS index_acquisition_artifact_key ON acquisition_artifact(artifact_key)""",
        """CREATE TABLE IF NOT EXISTS acquisition_seen_id ( job_id TEXT NOT NULL, raw_id TEXT NOT NULL, page_number INTEGER NOT NULL, PRIMARY KEY(job_id,raw_id), FOREIGN KEY(job_id) REFERENCES acquisition_job(job_id) ON UPDATE NO ACTION ON DELETE CASCADE )""",
        """CREATE INDEX IF NOT EXISTS index_acquisition_seen_id_page ON acquisition_seen_id(job_id,page_number)""",
    )
}
