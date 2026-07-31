package ir.sabou.inventory.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suppliers",
    indices = [Index(value = ["name"], unique = true)],
)
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val address: String = "",
    val paymentTermsDays: Int = 0,
    val notes: String = "",
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["name"], unique = true),
        Index("supplierId"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val unit: String,
    val stockMicros: Long = 0,
    val inventoryValueRial: Long = 0,
    val alertEnabled: Boolean = true,
    val alertThresholdMicros: Long = 0,
    val supplierId: Long? = null,
    val isActive: Boolean = true,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["invoiceNo"], unique = true),
        Index("supplierId"),
        Index("purchaseEpochDay"),
        Index("dueEpochDay"),
    ],
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceNo: String,
    val supplierId: Long,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "purchase_lines",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("purchaseId"), Index("itemId")],
)
data class PurchaseLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val itemId: Long,
    val itemNameSnapshot: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)

@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = InventoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("itemId"),
        Index("movementEpochDay"),
        Index(value = ["referenceType", "referenceId"]),
    ],
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val movementType: String,
    val quantityDeltaMicros: Long,
    val valueDeltaRial: Long,
    val referenceType: String,
    val referenceId: Long,
    val movementEpochDay: Long,
    val notes: String,
    val createdAtEpochMillis: Long,
)
