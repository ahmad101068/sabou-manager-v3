package ir.sabou.inventory.data

import android.content.Context
import ir.sabou.inventory.data.db.AppDatabase
import ir.sabou.inventory.data.repository.DashboardRepository
import ir.sabou.inventory.data.repository.LocalAccountingRepository
import ir.sabou.inventory.data.repository.LocalOperationsRepository
import ir.sabou.inventory.data.repository.LocalPurchaseRepository
import ir.sabou.inventory.data.security.DatabaseKeyProvider
import ir.sabou.inventory.domain.accounting.AccountingRepository
import ir.sabou.inventory.domain.operations.OperationsRepository
import ir.sabou.inventory.domain.purchase.PurchaseRepository

class AppContainer(context: Context) {
    private val database: AppDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppDatabase.create(context, DatabaseKeyProvider(context))
    }

    val purchaseRepository: PurchaseRepository by lazy {
        LocalPurchaseRepository(database)
    }

    val dashboardRepository: DashboardRepository by lazy {
        DashboardRepository(database)
    }

    val operationsRepository: OperationsRepository by lazy {
        LocalOperationsRepository(database)
    }

    val accountingRepository: AccountingRepository by lazy {
        LocalAccountingRepository(database)
    }
}
