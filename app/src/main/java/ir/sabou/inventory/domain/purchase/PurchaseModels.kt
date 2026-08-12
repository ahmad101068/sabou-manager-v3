package ir.sabou.inventory.domain.purchase

import ir.sabou.inventory.core.MoneyRial
import ir.sabou.inventory.core.QuantityMicros
import kotlinx.coroutines.flow.Flow

enum class PurchasePaymentMethod(val accountCode: String?, val storedValue: String?) {
    PAYABLE(accountCode = "2101", storedValue = null),
    CASH(accountCode = "1101", storedValue = "نقدی"),
    CARD(accountCode = "1102", storedValue = "کارتخوان"),
    TRANSFER(accountCode = "1102", storedValue = "حواله"),
    ;

    companion object {
        fun fromStored(value: String?): PurchasePaymentMethod =
            entries.firstOrNull { it.storedValue == value } ?: PAYABLE
    }
}

data class PurchaseLineDraft(
    val itemId: Long,
    val quantity: QuantityMicros,
    val unitCost: MoneyRial,
)

data class PurchaseDraft(
    val invoiceNo: String,
    val supplierId: Long,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val paymentMethod: PurchasePaymentMethod,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
    val lines: List<PurchaseLineDraft>,
)

data class PostedPurchase(
    val purchaseId: Long,
    val journalEntryId: Long?,
    val total: MoneyRial,
)

enum class SettlementPaymentMethod(val title: String, val accountCode: String) {
    CASH("نقدی", "1101"),
    CARD("کارتخوان", "1102"),
    TRANSFER("حواله", "1102"),
}

data class PurchaseSettlementDraft(
    val purchaseId: Long,
    val settlementEpochDay: Long,
    val amount: MoneyRial,
    val paymentMethod: SettlementPaymentMethod,
    val referenceNo: String = "",
    val notes: String = "",
) {
    fun validated(): PurchaseSettlementDraft {
        require(purchaseId > 0) { "فاکتور خرید معتبر نیست." }
        require(amount > MoneyRial.ZERO) { "مبلغ تسویه باید بیشتر از صفر باشد." }
        require(referenceNo.trim().length <= 80) { "شماره پیگیری بیش از حد طولانی است." }
        require(notes.trim().length <= 300) { "توضیحات بیش از حد طولانی است." }
        return copy(referenceNo = referenceNo.trim(), notes = notes.trim())
    }
}

data class PostedPurchaseSettlement(
    val purchaseId: Long,
    val journalEntryId: Long,
    val journalEntryNo: String,
    val remaining: MoneyRial,
)

data class PurchaseReversalDraft(
    val purchaseId: Long,
    val reversalEpochDay: Long,
    val reason: String,
) {
    fun validated(): PurchaseReversalDraft {
        require(purchaseId > 0) { "فاکتور خرید معتبر نیست." }
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..200) {
            "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد."
        }
        return copy(reason = normalizedReason)
    }
}

data class PurchaseLineRecord(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)

data class PurchaseSettlementRecord(
    val journalEntryId: Long,
    val entryNo: String,
    val settlementEpochDay: Long,
    val amountRial: Long,
    val paymentMethod: String,
    val referenceNo: String,
    val notes: String,
)

data class PurchaseDetails(
    val id: Long,
    val invoiceNo: String,
    val supplierName: String,
    val purchaseEpochDay: Long,
    val dueEpochDay: Long,
    val totalRial: Long,
    val paidRial: Long,
    val paymentStatus: String,
    val paymentMethod: String?,
    val reminderEnabled: Boolean,
    val reminderEpochDay: Long?,
    val lines: List<PurchaseLineRecord>,
    val settlements: List<PurchaseSettlementRecord>,
) {
    val outstandingRial: Long get() = totalRial - paidRial
    val isReversed: Boolean get() = paymentStatus == "REVERSED"
    val canSettle: Boolean
        get() = !isReversed && paymentMethod == null && outstandingRial > 0
    val canReverse: Boolean
        get() = !isReversed && (paidRial == 0L || paymentMethod != null)
}

interface PurchaseRepository {
    suspend fun post(draft: PurchaseDraft): PostedPurchase
    fun details(purchaseId: Long): Flow<PurchaseDetails?>
    suspend fun settle(draft: PurchaseSettlementDraft): PostedPurchaseSettlement
    suspend fun reverse(draft: PurchaseReversalDraft)
}
