package ir.sabou.inventory.domain.accounting

import ir.sabou.inventory.core.MoneyRial

data class JournalLineDraft(
    val accountCode: String,
    val debit: MoneyRial = MoneyRial.ZERO,
    val credit: MoneyRial = MoneyRial.ZERO,
    val memo: String = "",
) {
    init {
        require(accountCode.matches(Regex("[1-9][0-9]{3}"))) { "کد حساب معتبر نیست." }
        require((debit > MoneyRial.ZERO) xor (credit > MoneyRial.ZERO)) {
            "هر آرتیکل باید فقط بدهکار یا فقط بستانکار باشد."
        }
        require(memo.length <= 500) { "شرح آرتیکل بیش از حد طولانی است." }
    }
}

data class BalancedJournalDraft(
    val description: String,
    val entryEpochDay: Long,
    val sourceType: String,
    val sourceId: Long,
    val lines: List<JournalLineDraft>,
) {
    init {
        require(description.isNotBlank() && description.length <= 300) { "شرح سند معتبر نیست." }
        require(sourceType.isNotBlank() && sourceType.length <= 40) { "نوع منبع سند معتبر نیست." }
        require(sourceId > 0) { "شناسه منبع سند معتبر نیست." }
        require(lines.size >= 2) { "سند باید حداقل دو آرتیکل داشته باشد." }
        val debit = MoneyRial.sum(lines.map { it.debit })
        val credit = MoneyRial.sum(lines.map { it.credit })
        require(debit == credit) { "جمع بدهکار و بستانکار سند برابر نیست." }
    }
}

data class ManualJournalDraft(
    val description: String,
    val entryEpochDay: Long,
    val lines: List<JournalLineDraft>,
) {
    fun validated(sourceId: Long = 1): BalancedJournalDraft = BalancedJournalDraft(
        description = description.trim(),
        entryEpochDay = entryEpochDay,
        sourceType = "MANUAL",
        sourceId = sourceId,
        lines = lines.map { line ->
            line.copy(
                accountCode = line.accountCode.trim(),
                memo = line.memo.trim(),
            )
        },
    )
}
