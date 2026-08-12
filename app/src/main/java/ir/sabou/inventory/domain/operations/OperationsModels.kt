package ir.sabou.inventory.domain.operations

import kotlinx.coroutines.flow.Flow

data class SupplierRecord(
    val id: Long,
    val name: String,
    val contactName: String,
    val phone: String,
    val address: String,
    val paymentTermsDays: Int,
    val notes: String,
)

data class SupplierDraft(
    val name: String,
    val contactName: String = "",
    val phone: String = "",
    val address: String = "",
    val paymentTermsDays: Int = 0,
    val notes: String = "",
) {
    fun validated(): SupplierDraft {
        val normalizedName = name.trim()
        require(normalizedName.length in 2..120) {
            "نام تأمین‌کننده باید بین ۲ تا ۱۲۰ نویسه باشد."
        }
        require(paymentTermsDays in 0..3650) {
            "مهلت پرداخت معتبر نیست."
        }
        require(phone.trim().length <= 30) {
            "شماره تماس بیش از حد طولانی است."
        }
        return copy(
            name = normalizedName,
            contactName = contactName.trim(),
            phone = phone.trim(),
            address = address.trim(),
            notes = notes.trim(),
        )
    }
}

data class InventoryItemRecord(
    val id: Long,
    val name: String,
    val category: String,
    val unit: String,
    val stockMicros: Long,
    val inventoryValueRial: Long,
    val alertEnabled: Boolean,
    val alertThresholdMicros: Long,
    val supplierId: Long?,
)

data class InventoryItemDraft(
    val name: String,
    val category: String,
    val unit: String,
    val alertEnabled: Boolean,
    val alertThresholdMicros: Long,
    val supplierId: Long?,
) {
    fun validated(): InventoryItemDraft {
        val normalizedName = name.trim()
        val normalizedCategory = category.trim()
        val normalizedUnit = unit.trim()
        require(normalizedName.length in 2..120) {
            "نام کالا باید بین ۲ تا ۱۲۰ نویسه باشد."
        }
        require(normalizedCategory.isNotEmpty()) {
            "دسته‌بندی کالا را وارد کنید."
        }
        require(normalizedUnit.isNotEmpty()) {
            "واحد شمارش کالا را وارد کنید."
        }
        require(alertThresholdMicros >= 0) {
            "حد هشدار موجودی نمی‌تواند منفی باشد."
        }
        return copy(
            name = normalizedName,
            category = normalizedCategory,
            unit = normalizedUnit,
        )
    }
}

data class PurchaseSummary(
    val id: Long,
    val invoiceNo: String,
    val supplierName: String,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val isPaid: Boolean,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
) {
    val outstandingRial: Long get() = totalRial - paidRial

    fun reminderIsDue(todayEpochDay: Long): Boolean =
        paymentStatus in setOf("UNPAID", "PARTIAL") &&
            outstandingRial > 0 &&
            reminderEnabled &&
            reminderEpochDay != null &&
            reminderEpochDay <= todayEpochDay
}

interface OperationsRepository {
    val suppliers: Flow<List<SupplierRecord>>
    val inventoryItems: Flow<List<InventoryItemRecord>>
    val lowStockItems: Flow<List<InventoryItemRecord>>

    fun purchases(query: String): Flow<List<PurchaseSummary>>

    suspend fun createSupplier(draft: SupplierDraft): Long
    suspend fun updateSupplier(id: Long, draft: SupplierDraft)
    suspend fun deactivateSupplier(id: Long)

    suspend fun createInventoryItem(draft: InventoryItemDraft): Long
    suspend fun updateInventoryItem(id: Long, draft: InventoryItemDraft)
    suspend fun deactivateInventoryItem(id: Long)
}
