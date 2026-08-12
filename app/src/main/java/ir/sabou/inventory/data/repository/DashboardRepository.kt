package ir.sabou.inventory.data.repository

import ir.sabou.inventory.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class DashboardSnapshot(
    val inventoryValueRial: Long = 0,
    val supplierPayablesRial: Long = 0,
    val cashBalanceRial: Long = 0,
    val bankBalanceRial: Long = 0,
)

class DashboardRepository(database: AppDatabase) {
    val snapshot: Flow<DashboardSnapshot> = combine(
        database.inventoryDao().observeInventoryValueRial(),
        database.purchaseDao().observePayablesRial(),
        database.accountingDao().observeBalanceRial("1101"),
        database.accountingDao().observeBalanceRial("1102"),
    ) { inventory, payables, cash, bank ->
        DashboardSnapshot(
            inventoryValueRial = inventory,
            supplierPayablesRial = payables,
            cashBalanceRial = cash,
            bankBalanceRial = bank,
        )
    }
}

