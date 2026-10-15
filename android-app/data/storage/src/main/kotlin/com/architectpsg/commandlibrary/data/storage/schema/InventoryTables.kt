package com.architectpsg.commandlibrary.data.storage.schema

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** Root-owned incremental inventory journal. Application validation enforces typed row invariants. */
@Entity(tableName = "inventory_operation", foreignKeys = [
    ForeignKey(entity = InventoryGroupEntity::class, parentColumns = ["sequence"], childColumns = ["base_group_sequence"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = InventoryGroupEntity::class, parentColumns = ["sequence"], childColumns = ["undo_target_sequence"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["operation_id"], name = "index_inventory_operation_operation_id", unique = true),
    Index(value = ["base_group_sequence"], name = "index_inventory_operation_base_group_sequence"),
    Index(value = ["undo_target_sequence"], name = "index_inventory_operation_undo_target_sequence"),
])
internal data class InventoryOperationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "operation_id") val operation_id: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "request_version") val request_version: Int,
    @ColumnInfo(name = "request_sha256") val request_sha256: String,
    @ColumnInfo(name = "expected_revision") val expected_revision: Long,
    @ColumnInfo(name = "result_revision") val result_revision: Long,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "content_sha256") val content_sha256: String,
    @ColumnInfo(name = "change_count") val change_count: Int,
    @ColumnInfo(name = "merge_count") val merge_count: Int,
    @ColumnInfo(name = "reservation_count") val reservation_count: Int,
    @ColumnInfo(name = "changes_sha256") val changes_sha256: String,
    @ColumnInfo(name = "descriptor_length") val descriptor_length: Int,
    @ColumnInfo(name = "descriptor_version") val descriptor_version: Int,
    @ColumnInfo(name = "descriptor_sha256") val descriptor_sha256: String,
    @ColumnInfo(name = "descriptor") val descriptor: ByteArray,
    @ColumnInfo(name = "base_group_sequence") val base_group_sequence: Long?,
    @ColumnInfo(name = "undo_target_sequence") val undo_target_sequence: Long?,
)

@Entity(tableName = "inventory_group", primaryKeys = ["sequence"], foreignKeys = [
    ForeignKey(entity = InventoryOperationEntity::class, parentColumns = ["sequence"], childColumns = ["sequence"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = InventoryGroupEntity::class, parentColumns = ["sequence"], childColumns = ["previous_group_sequence"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["previous_group_sequence"], name = "index_inventory_group_previous_group_sequence"),
])
internal data class InventoryGroupEntity(
    @ColumnInfo(name = "sequence") val sequence: Long,
    @ColumnInfo(name = "previous_group_sequence") val previous_group_sequence: Long?,
    @ColumnInfo(name = "before_sha256") val before_sha256: String,
    @ColumnInfo(name = "after_sha256") val after_sha256: String,
)

@Entity(tableName = "inventory_state", primaryKeys = ["singleton"], foreignKeys = [
    ForeignKey(entity = InventoryOperationEntity::class, parentColumns = ["sequence"], childColumns = ["latest_operation_sequence"], onDelete = ForeignKey.NO_ACTION),
    ForeignKey(entity = InventoryGroupEntity::class, parentColumns = ["sequence"], childColumns = ["current_group_sequence"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["latest_operation_sequence"], name = "index_inventory_state_latest_operation_sequence"),
    Index(value = ["current_group_sequence"], name = "index_inventory_state_current_group_sequence"),
])
internal data class InventoryStateEntity(
    @ColumnInfo(name = "singleton") val singleton: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "modified_seconds") val modified_seconds: Long,
    @ColumnInfo(name = "modified_nanos") val modified_nanos: Int,
    @ColumnInfo(name = "latest_operation_sequence") val latest_operation_sequence: Long,
    @ColumnInfo(name = "current_group_sequence") val current_group_sequence: Long?,
    @ColumnInfo(name = "content_sha256") val content_sha256: String,
)

@Entity(tableName = "inventory_lot", primaryKeys = ["lot_id"], foreignKeys = [
    ForeignKey(entity = InventoryOperationEntity::class, parentColumns = ["sequence"], childColumns = ["last_operation_sequence"], onDelete = ForeignKey.NO_ACTION),
], indices = [
    Index(value = ["last_operation_sequence"], name = "index_inventory_lot_last_operation_sequence"),
    Index(value = ["active", "lot_id"], name = "index_inventory_lot_active_lot_id"),
])
internal data class InventoryLotEntity(
    @ColumnInfo(name = "lot_id") val lot_id: String,
    @ColumnInfo(name = "active") val active: Int,
    @ColumnInfo(name = "revision") val revision: Long,
    @ColumnInfo(name = "quantity") val quantity: Int,
    @ColumnInfo(name = "payload_length") val payload_length: Int,
    @ColumnInfo(name = "payload_version") val payload_version: Int,
    @ColumnInfo(name = "payload_sha256") val payload_sha256: String,
    @ColumnInfo(name = "payload") val payload: ByteArray,
    @ColumnInfo(name = "last_operation_sequence") val last_operation_sequence: Long,
)

@Entity(tableName = "inventory_change", primaryKeys = ["operation_sequence", "lot_id"], foreignKeys = [
    ForeignKey(entity = InventoryOperationEntity::class, parentColumns = ["sequence"], childColumns = ["operation_sequence"], onDelete = ForeignKey.NO_ACTION),
])
internal data class InventoryChangeEntity(
    @ColumnInfo(name = "operation_sequence") val operation_sequence: Long,
    @ColumnInfo(name = "lot_id") val lot_id: String,
    @ColumnInfo(name = "before_present") val before_present: Int,
    @ColumnInfo(name = "before_active") val before_active: Int?,
    @ColumnInfo(name = "before_revision") val before_revision: Long?,
    @ColumnInfo(name = "before_quantity") val before_quantity: Int?,
    @ColumnInfo(name = "before_payload_length") val before_payload_length: Int?,
    @ColumnInfo(name = "before_payload_version") val before_payload_version: Int?,
    @ColumnInfo(name = "before_payload_sha256") val before_payload_sha256: String?,
    @ColumnInfo(name = "before_payload") val before_payload: ByteArray?,
    @ColumnInfo(name = "after_present") val after_present: Int,
    @ColumnInfo(name = "after_active") val after_active: Int?,
    @ColumnInfo(name = "after_revision") val after_revision: Long?,
    @ColumnInfo(name = "after_quantity") val after_quantity: Int?,
    @ColumnInfo(name = "after_payload_length") val after_payload_length: Int?,
    @ColumnInfo(name = "after_payload_version") val after_payload_version: Int?,
    @ColumnInfo(name = "after_payload_sha256") val after_payload_sha256: String?,
    @ColumnInfo(name = "after_payload") val after_payload: ByteArray?,
)
