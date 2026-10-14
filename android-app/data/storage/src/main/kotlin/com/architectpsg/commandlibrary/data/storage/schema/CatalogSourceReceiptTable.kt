package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Admission evidence outlives acquisition job/artifact retention. No cascading job FK. */
@Entity(tableName = "catalog_source_receipt",
    indices = [Index(value = ["metadata_job_id"], unique = true, name = "index_catalog_source_receipt_metadata_job")])
internal data class CatalogSourceReceiptEntity(
    @PrimaryKey @ColumnInfo(name = "receipt_id") val receiptId: String,
    @ColumnInfo(name = "metadata_job_id") val metadataJobId: String,
    @ColumnInfo(name = "payload_schema_version") val payloadSchemaVersion: Int,
    @ColumnInfo(name = "receipt_payload") val receiptPayload: String,
)
