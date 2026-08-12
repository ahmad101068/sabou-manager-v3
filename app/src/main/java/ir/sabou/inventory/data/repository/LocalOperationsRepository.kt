package ir.sabou.inventory.data.repository

import androidx.room.withTransaction
import ir.sabou.inventory.data.db.AppDatabase
import ir.sabou.inventory.data.db.InventoryItemEntity
import ir.sabou.inventory.data.db.SupplierEntity
import ir.sabou.inventory.domain.operations.InventoryItemDraft
import ir.sabou.inventory.domain.operations.InventoryItemRecord
import ir.sabou.inventory.domain.operations.OperationsRepository
import ir.sabou.inventory.domain.operations.PurchaseSummary
import ir.sabou.inventory.domain.operations.SupplierDraft
import ir.sabou.inventory.domain.operations.SupplierRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalOperationsRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : OperationsRepository {
    override val suppliers: Flow<List<SupplierRecord>> =
        database.supplierDao().observeActive().map { rows -> rows.map(SupplierEntity::toRecord) }

    override val inventoryItems: Flow<List<InventoryItemRecord>> =
        database.inventoryDao().observeActive().map { rows -> rows.map(InventoryItemEntity::toRecord) }

    override val lowStockItems: Flow<List<InventoryItemRecord>> =
        database.inventoryDao().observeLowStock().map { rows -> rows.map(InventoryItemEntity::toRecord) }

    override fun purchases(query: String): Flow<List<PurchaseSummary>> =
        database.purchaseDao().observeSearch(query.trim()).map { rows ->
            rows.map { row ->
                PurchaseSummary(
                    id = row.purchaseId,
                    invoiceNo = row.invoiceNo,
                    supplierName = row.supplierName,
                    purchaseEpochDay = row.purchaseEpochDay,
                    dueEpochDay = row.dueEpochDay,
                    totalRial = row.totalRial,
                    paidRial = row.paidRial,
                    isPaid = row.paymentStatus == "PAID",
                    paymentStatus = row.paymentStatus,
                    paymentMethod = row.paymentMethod,
                    reminderEnabled = row.reminderEnabled,
                    reminderEpochDay = row.reminderEpochDay,
                )
            }
        }

    override suspend fun createSupplier(draft: SupplierDraft): Long {
        val valid = draft.validated()
        val now = clock()
        return database.withTransaction {
            val previous = database.supplierDao().byName(valid.name)
            if (previous != null) {
                require(!previous.isActive) { "تأمین‌کننده‌ای با این نام وجود دارد." }
                check(
                    database.supplierDao().update(
                        previous.copy(
                            contactName = valid.contactName,
                            phone = valid.phone,
                            address = valid.address,
                            paymentTermsDays = valid.paymentTermsDays,
                            notes = valid.notes,
                            isActive = true,
                            updatedAtEpochMillis = now,
                        ),
                    ) == 1,
                ) { "فعال‌سازی دوباره تأمین‌کننده انجام نشد." }
                return@withTransaction previous.id
            }
            database.supplierDao().insert(
                SupplierEntity(
                    name = valid.name,
                    contactName = valid.contactName,
                    phone = valid.phone,
                    address = valid.address,
                    paymentTermsDays = valid.paymentTermsDays,
                    notes = valid.notes,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    override suspend fun updateSupplier(id: Long, draft: SupplierDraft) {
        val current = database.supplierDao().activeById(id)
            ?: error("تأمین‌کننده پیدا نشد.")
        val valid = draft.validated()
        check(
            database.supplierDao().update(
                current.copy(
                    name = valid.name,
                    contactName = valid.contactName,
                    phone = valid.phone,
                    address = valid.address,
                    paymentTermsDays = valid.paymentTermsDays,
                    notes = valid.notes,
                    updatedAtEpochMillis = clock(),
                ),
            ) == 1,
        ) { "ویرایش تأمین‌کننده انجام نشد." }
    }

    override suspend fun deactivateSupplier(id: Long) {
        database.withTransaction {
            val now = clock()
            check(database.supplierDao().deactivate(id, now) == 1) {
                "حذف تأمین‌کننده انجام نشد."
            }
            database.inventoryDao().clearSupplierReference(id, now)
        }
    }

    override suspend fun createInventoryItem(draft: InventoryItemDraft): Long {
        val valid = draft.validated()
        ensureSupplierIsActive(valid.supplierId)
        val now = clock()
        return database.withTransaction {
            val previous = database.inventoryDao().byName(valid.name)
            if (previous != null) {
                require(!previous.isActive) { "کالایی با این نام وجود دارد." }
                check(
                    database.inventoryDao().update(
                        previous.copy(
                            category = valid.category,
                            unit = valid.unit,
                            alertEnabled = valid.alertEnabled,
                            alertThresholdMicros = valid.alertThresholdMicros,
                            supplierId = valid.supplierId,
                            isActive = true,
                            updatedAtEpochMillis = now,
                        ),
                    ) == 1,
                ) { "فعال‌سازی دوباره کالا انجام نشد." }
                return@withTransaction previous.id
            }
            database.inventoryDao().insert(
                InventoryItemEntity(
                    name = valid.name,
                    category = valid.category,
                    unit = valid.unit,
                    alertEnabled = valid.alertEnabled,
                    alertThresholdMicros = valid.alertThresholdMicros,
                    supplierId = valid.supplierId,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
        }
    }

    override suspend fun updateInventoryItem(id: Long, draft: InventoryItemDraft) {
        val current = database.inventoryDao().activeById(id)
            ?: error("کالا پیدا نشد.")
        val valid = draft.validated()
        ensureSupplierIsActive(valid.supplierId)
        check(
            database.inventoryDao().update(
                current.copy(
                    name = valid.name,
                    category = valid.category,
                    unit = valid.unit,
                    alertEnabled = valid.alertEnabled,
                    alertThresholdMicros = valid.alertThresholdMicros,
                    supplierId = valid.supplierId,
                    updatedAtEpochMillis = clock(),
                ),
            ) == 1,
        ) { "ویرایش کالا انجام نشد." }
    }

    override suspend fun deactivateInventoryItem(id: Long) {
        check(database.inventoryDao().deactivate(id, clock()) == 1) {
            "حذف کالا انجام نشد."
        }
    }

    private suspend fun ensureSupplierIsActive(supplierId: Long?) {
        if (supplierId != null) {
            require(database.supplierDao().activeById(supplierId) != null) {
                "تأمین‌کننده انتخاب‌شده فعال نیست."
            }
        }
    }
}

private fun SupplierEntity.toRecord() = SupplierRecord(
    id = id,
    name = name,
    contactName = contactName,
    phone = phone,
    address = address,
    paymentTermsDays = paymentTermsDays,
    notes = notes,
)

private fun InventoryItemEntity.toRecord() = InventoryItemRecord(
    id = id,
    name = name,
    category = category,
    unit = unit,
    stockMicros = stockMicros,
    inventoryValueRial = inventoryValueRial,
    alertEnabled = alertEnabled,
    alertThresholdMicros = alertThresholdMicros,
    supplierId = supplierId,
)
