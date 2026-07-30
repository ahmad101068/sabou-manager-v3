package ir.sabou.inventory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.sabou.inventory.domain.operations.InventoryItemDraft
import ir.sabou.inventory.domain.operations.InventoryItemRecord
import ir.sabou.inventory.domain.operations.OperationsRepository
import ir.sabou.inventory.domain.operations.PurchaseSummary
import ir.sabou.inventory.domain.operations.SupplierDraft
import ir.sabou.inventory.domain.operations.SupplierRecord
import ir.sabou.inventory.domain.purchase.PostedPurchase
import ir.sabou.inventory.domain.purchase.PurchaseDetails
import ir.sabou.inventory.domain.purchase.PurchaseDraft
import ir.sabou.inventory.domain.purchase.PurchaseRepository
import ir.sabou.inventory.domain.purchase.PurchaseReversalDraft
import ir.sabou.inventory.domain.purchase.PurchaseSettlementDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OperationsUiState(
    val suppliers: List<SupplierRecord> = emptyList(),
    val inventoryItems: List<InventoryItemRecord> = emptyList(),
    val lowStockItems: List<InventoryItemRecord> = emptyList(),
    val purchases: List<PurchaseSummary> = emptyList(),
    val settlementAlerts: List<PurchaseSummary> = emptyList(),
    val selectedPurchase: PurchaseDetails? = null,
    val purchaseSearch: String = "",
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class OperationsViewModel(
    private val operationsRepository: OperationsRepository,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {
    private val purchaseSearch = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val selectedPurchaseId = MutableStateFlow<Long?>(null)
    private val todayEpochDay = System.currentTimeMillis() / 86_400_000L

    private val content = combine(
        operationsRepository.suppliers,
        operationsRepository.inventoryItems,
        operationsRepository.lowStockItems,
        purchaseSearch.flatMapLatest(operationsRepository::purchases),
        operationsRepository.purchases(""),
    ) { suppliers, items, lowStock, purchases, allPurchases ->
        OperationsUiState(
            suppliers = suppliers,
            inventoryItems = items,
            lowStockItems = lowStock,
            purchases = purchases,
            settlementAlerts = allPurchases.filter { it.reminderIsDue(todayEpochDay) },
            purchaseSearch = purchaseSearch.value,
        )
    }

    private val selectedPurchase = selectedPurchaseId.flatMapLatest { purchaseId ->
        if (purchaseId == null) flowOf(null) else purchaseRepository.details(purchaseId)
    }

    val state: StateFlow<OperationsUiState> = combine(
        content,
        busy,
        message,
        selectedPurchase,
    ) { current, isBusy, currentMessage, details ->
        current.copy(
            busy = isBusy,
            message = currentMessage,
            selectedPurchase = details,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = OperationsUiState(),
    )

    fun searchPurchases(value: String) {
        purchaseSearch.value = value
    }

    fun selectPurchase(purchaseId: Long?) {
        selectedPurchaseId.value = purchaseId
    }

    fun clearMessage() {
        message.value = null
    }

    fun saveSupplier(id: Long?, draft: SupplierDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (id == null) "تأمین‌کننده ثبت شد." else "تأمین‌کننده ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            if (id == null) operationsRepository.createSupplier(draft)
            else operationsRepository.updateSupplier(id, draft)
        }
    }

    fun deactivateSupplier(id: Long) {
        runAction("تأمین‌کننده غیرفعال شد.") {
            operationsRepository.deactivateSupplier(id)
        }
    }

    fun saveInventoryItem(id: Long?, draft: InventoryItemDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (id == null) "کالا ثبت شد." else "کالا ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            if (id == null) operationsRepository.createInventoryItem(draft)
            else operationsRepository.updateInventoryItem(id, draft)
        }
    }

    fun deactivateInventoryItem(id: Long) {
        runAction("کالا غیرفعال شد.") {
            operationsRepository.deactivateInventoryItem(id)
        }
    }

    fun postPurchase(draft: PurchaseDraft, onSuccess: (PostedPurchase) -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val result = purchaseRepository.post(draft)
                message.value = "فاکتور خرید ثبت شد و موجودی و حسابداری به‌روزرسانی شدند."
                onSuccess(result)
            } catch (error: Exception) {
                message.value = error.message ?: "ثبت فاکتور خرید انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }

    fun settlePurchase(draft: PurchaseSettlementDraft, onSuccess: () -> Unit = {}) {
        runAction("تسویه ثبت شد و مانده تأمین‌کننده و سند حسابداری به‌روزرسانی شدند.", onSuccess) {
            purchaseRepository.settle(draft)
        }
    }

    fun reversePurchase(draft: PurchaseReversalDraft, onSuccess: () -> Unit = {}) {
        runAction("فاکتور برگشت خورد و موجودی و حسابداری با سند معکوس اصلاح شدند.", onSuccess) {
            purchaseRepository.reverse(draft)
        }
    }

    private fun runAction(
        successMessage: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                block()
                message.value = successMessage
                onSuccess()
            } catch (error: Exception) {
                message.value = error.message ?: "عملیات انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(
            operationsRepository: OperationsRepository,
            purchaseRepository: PurchaseRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OperationsViewModel(operationsRepository, purchaseRepository) as T
        }
    }
}
