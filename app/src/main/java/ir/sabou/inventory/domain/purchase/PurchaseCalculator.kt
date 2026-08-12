package ir.sabou.inventory.domain.purchase

import ir.sabou.inventory.core.MoneyRial

data class PreparedPurchaseLine(
    val itemId: Long,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val total: MoneyRial,
)

data class PreparedPurchase(
    val draft: PurchaseDraft,
    val lines: List<PreparedPurchaseLine>,
    val total: MoneyRial,
)

object PurchaseCalculator {
    fun prepare(draft: PurchaseDraft): PreparedPurchase {
        validate(draft)
        val lines = draft.lines.map { line ->
            PreparedPurchaseLine(
                itemId = line.itemId,
                quantityMicros = line.quantity.value,
                unitCostRial = line.unitCost.value,
                total = line.unitCost.times(line.quantity),
            )
        }
        return PreparedPurchase(
            draft = draft,
            lines = lines,
            total = MoneyRial.sum(lines.map { it.total }),
        )
    }

    private fun validate(draft: PurchaseDraft) {
        require(draft.invoiceNo.isNotBlank() && draft.invoiceNo.length <= 80) {
            "شماره فاکتور خرید معتبر نیست."
        }
        require(draft.supplierId > 0) { "تأمین‌کننده الزامی است." }
        require(draft.dueEpochDay >= draft.purchaseEpochDay) {
            "تاریخ تسویه نمی‌تواند قبل از تاریخ خرید باشد."
        }
        require(draft.lines.isNotEmpty() && draft.lines.size <= 500) {
            "فاکتور باید بین ۱ تا ۵۰۰ ردیف داشته باشد."
        }
        require(draft.lines.map { it.itemId }.distinct().size == draft.lines.size) {
            "یک کالا در فاکتور خرید تکرار شده است."
        }
        require(draft.lines.all { it.quantity.value > 0 }) {
            "مقدار هر ردیف خرید باید بیشتر از صفر باشد."
        }
        if (draft.reminderEnabled && draft.paymentMethod == PurchasePaymentMethod.PAYABLE) {
            requireNotNull(draft.reminderEpochDay) { "تاریخ یادآوری الزامی است." }
            require(draft.reminderEpochDay >= draft.purchaseEpochDay) {
                "تاریخ یادآوری نمی‌تواند قبل از تاریخ خرید باشد."
            }
        }
    }
}
