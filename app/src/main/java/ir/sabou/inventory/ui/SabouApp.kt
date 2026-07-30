package ir.sabou.inventory.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.sabou.inventory.data.AppContainer
import ir.sabou.inventory.data.repository.DashboardSnapshot

enum class AppScreen {
    DASHBOARD,
    PURCHASES,
    NEW_PURCHASE,
    SUPPLIERS,
    INVENTORY,
    ACCOUNTING,
    NEW_JOURNAL,
}

private data class ModuleCard(val title: String, val screen: AppScreen?)

private val modules = listOf(
    ModuleCard("خرید و فاکتورها", AppScreen.PURCHASES),
    ModuleCard("تأمین‌کنندگان", AppScreen.SUPPLIERS),
    ModuleCard("انبار و هشدار موجودی", AppScreen.INVENTORY),
    ModuleCard("رسپی و بهای تمام‌شده", null),
    ModuleCard("فروش و مشتریان", null),
    ModuleCard("حسابداری", AppScreen.ACCOUNTING),
    ModuleCard("پرسنل و حقوق", null),
    ModuleCard("دارایی‌های رستوران", null),
    ModuleCard("گزارش و چاپ", null),
    ModuleCard("کاربران و دسترسی", null),
    ModuleCard("تنظیمات و پشتیبان", null),
)

@Composable
fun SabouApp(container: AppContainer) {
    val dashboard: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(container.dashboardRepository),
    )
    val operations: OperationsViewModel = viewModel(
        factory = OperationsViewModel.factory(
            container.operationsRepository,
            container.purchaseRepository,
        ),
    )
    val accounting: AccountingViewModel = viewModel(
        factory = AccountingViewModel.factory(container.accountingRepository),
    )
    val dashboardState by dashboard.state.collectAsState()
    val operationsState by operations.state.collectAsState()
    val accountingState by accounting.state.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.DASHBOARD) }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl) {
        when (screen) {
            AppScreen.DASHBOARD -> DashboardScreen(
                state = dashboardState,
                operationsState = operationsState,
                onOpen = { target ->
                    if (target != null) screen = target
                    else operations.clearMessage()
                },
            )

            AppScreen.PURCHASES -> PurchasesScreen(
                state = operationsState,
                onSearch = operations::searchPurchases,
                onSelect = operations::selectPurchase,
                onSettle = operations::settlePurchase,
                onReverse = operations::reversePurchase,
                onAdd = { screen = AppScreen.NEW_PURCHASE },
                onBack = {
                    operations.selectPurchase(null)
                    screen = AppScreen.DASHBOARD
                },
            )

            AppScreen.NEW_PURCHASE -> PurchaseEntryScreen(
                state = operationsState,
                onPost = operations::postPurchase,
                onBack = { screen = AppScreen.PURCHASES },
            )

            AppScreen.SUPPLIERS -> SuppliersScreen(
                state = operationsState,
                onSave = operations::saveSupplier,
                onDeactivate = operations::deactivateSupplier,
                onBack = { screen = AppScreen.DASHBOARD },
            )

            AppScreen.INVENTORY -> InventoryScreen(
                state = operationsState,
                onSave = operations::saveInventoryItem,
                onDeactivate = operations::deactivateInventoryItem,
                onBack = { screen = AppScreen.DASHBOARD },
            )

            AppScreen.ACCOUNTING -> AccountingScreen(
                state = accountingState,
                onSearch = accounting::searchJournals,
                onSelectJournal = accounting::selectJournal,
                onSelectLedger = accounting::selectLedger,
                onSaveAccount = accounting::saveAccount,
                onDeactivateAccount = accounting::deactivateAccount,
                onReverse = accounting::reverseManual,
                onAddJournal = { screen = AppScreen.NEW_JOURNAL },
                onBack = { screen = AppScreen.DASHBOARD },
            )

            AppScreen.NEW_JOURNAL -> ManualJournalEntryScreen(
                state = accountingState,
                onPost = accounting::postManual,
                onBack = { screen = AppScreen.ACCOUNTING },
            )
        }
    }
}

@Composable
private fun DashboardScreen(
    state: DashboardSnapshot,
    operationsState: OperationsUiState,
    onOpen: (AppScreen?) -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "مدیریت رستوران",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "نسخه ۳ · آفلاین، امن و حسابداری‌محور",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            )
            if (operationsState.lowStockItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Card(
                    onClick = { onOpen(AppScreen.INVENTORY) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        text = "${operationsState.lowStockItems.size} کالا به حد هشدار موجودی رسیده است.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (operationsState.settlementAlerts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Card(
                    onClick = { onOpen(AppScreen.PURCHASES) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Text(
                        text = "${operationsState.settlementAlerts.size} یادآور تسویه تأمین‌کننده سررسید شده است.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "ارزش انبار",
                    value = formatMoney(state.inventoryValueRial),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "بدهی تأمین‌کننده",
                    value = formatMoney(state.supplierPayablesRial),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MetricCard(
                    title = "صندوق",
                    value = formatMoney(state.cashBalanceRial),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "بانک",
                    value = formatMoney(state.bankBalanceRial),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text("ماژول‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(modules) { module ->
                    Card(
                        onClick = { onOpen(module.screen) },
                        enabled = module.screen != null,
                        colors = CardDefaults.cardColors(
                            containerColor = if (module.screen == null) {
                                MaterialTheme.colorScheme.surfaceVariant
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 18.dp)) {
                            Text(module.title, fontWeight = FontWeight.SemiBold)
                            if (module.screen == null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "در حال بازسازی",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
