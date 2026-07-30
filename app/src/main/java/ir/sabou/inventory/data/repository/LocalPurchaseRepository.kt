package ir.sabou.inventory.data.repository

import androidx.room.withTransaction
import ir.sabou.inventory.core.MoneyRial
import ir.sabou.inventory.core.QuantityMicros
import ir.sabou.inventory.data.db.AppDatabase
import ir.sabou.inventory.data.db.JournalEntryEntity
import ir.sabou.inventory.data.db.JournalLineEntity
import ir.sabou.inventory.data.db.PurchaseEntity
import ir.sabou.inventory.data.db.PurchaseLineEntity
import ir.sabou.inventory.data.db.StockMovementEntity
import ir.sabou.inventory.domain.accounting.BalancedJournalDraft
import ir.sabou.inventory.domain.accounting.JournalLineDraft
import ir.sabou.inventory.domain.purchase.PostedPurchase
import ir.sabou.inventory.domain.purchase.PostedPurchaseSettlement
import ir.sabou.inventory.domain.purchase.PurchaseCalculator
import ir.sabou.inventory.domain.purchase.PurchaseDetails
import ir.sabou.inventory.domain.purchase.PurchaseDraft
import ir.sabou.inventory.domain.purchase.PurchaseLineRecord
import ir.sabou.inventory.domain.purchase.PurchasePaymentMethod
import ir.sabou.inventory.domain.purchase.PurchaseRepository
import ir.sabou.inventory.domain.purchase.PurchaseReversalDraft
import ir.sabou.inventory.domain.purchase.PurchaseSettlementDraft
import ir.sabou.inventory.domain.purchase.PurchaseSettlementRecord
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class LocalPurchaseRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : PurchaseRepository {
    override suspend fun post(draft: PurchaseDraft): PostedPurchase {
        val prepared = PurchaseCalculator.prepare(draft)
        val preparedLines = prepared.lines
        val total = prepared.total
        val now = clock()

        return database.withTransaction {
            val supplier = database.supplierDao().activeById(draft.supplierId)
                ?: error("تأمین‌کننده فعال پیدا نشد.")
            require(!database.purchaseDao().invoiceExists(draft.invoiceNo)) {
                "شماره فاکتور خرید تکراری است."
            }

            val paid = draft.paymentMethod != PurchasePaymentMethod.PAYABLE
            val purchaseId = database.purchaseDao().insert(
                PurchaseEntity(
                    invoiceNo = draft.invoiceNo,
                    supplierId = supplier.id,
                    purchaseEpochDay = draft.purchaseEpochDay,
                    dueEpochDay = draft.dueEpochDay,
                    totalRial = total.value,
                    paidRial = if (paid) total.value else 0,
                    paymentStatus = if (paid) "PAID" else "UNPAID",
                    paymentMethod = draft.paymentMethod.storedValue,
                    reminderEnabled = !paid && draft.reminderEnabled,
                    reminderEpochDay = if (!paid && draft.reminderEnabled) draft.reminderEpochDay else null,
                    createdAtEpochMillis = now,
                ),
            )

            val purchaseLines = preparedLines.map { line ->
                val item = database.inventoryDao().activeById(line.itemId)
                    ?: error("یکی از کالاهای خرید پیدا نشد.")
                val nextStock = (
                    QuantityMicros.of(item.stockMicros) + QuantityMicros.of(line.quantityMicros)
                ).value
                val nextValue = MoneyRial.of(item.inventoryValueRial) + line.total
                check(
                    database.inventoryDao().updateValuation(
                        itemId = item.id,
                        stockMicros = nextStock,
                        inventoryValueRial = nextValue.value,
                        updatedAtEpochMillis = now,
                    ) == 1,
                ) { "به‌روزرسانی موجودی انجام نشد." }
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        itemId = item.id,
                        movementType = "PURCHASE",
                        quantityDeltaMicros = line.quantityMicros,
                        valueDeltaRial = line.total.value,
                        referenceType = "PURCHASE",
                        referenceId = purchaseId,
                        movementEpochDay = draft.purchaseEpochDay,
                        notes = draft.invoiceNo,
                        createdAtEpochMillis = now,
                    ),
                )
                PurchaseLineEntity(
                    purchaseId = purchaseId,
                    itemId = item.id,
                    itemNameSnapshot = item.name,
                    quantityMicros = line.quantityMicros,
                    unitCostRial = line.unitCostRial,
                    lineTotalRial = line.total.value,
                )
            }
            database.purchaseDao().insertLines(purchaseLines)

            val journalId = if (total > MoneyRial.ZERO) {
                val journal = BalancedJournalDraft(
                    description = "خرید مواد اولیه از ${supplier.name}",
                    entryEpochDay = draft.purchaseEpochDay,
                    sourceType = "PURCHASE",
                    sourceId = purchaseId,
                    lines = listOf(
                        JournalLineDraft("1301", debit = total),
                        JournalLineDraft(draft.paymentMethod.accountCode!!, credit = total),
                    ),
                )
                journal.lines.forEach { line ->
                    check(database.accountingDao().activeAccountExists(line.accountCode)) {
                        "حساب ${line.accountCode} فعال نیست."
                    }
                }
                database.accountingDao().insertEntry(
                    JournalEntryEntity(
                        entryNo = "خ-$purchaseId",
                        entryEpochDay = journal.entryEpochDay,
                        description = journal.description,
                        sourceType = journal.sourceType,
                        sourceId = journal.sourceId,
                        createdAtEpochMillis = now,
                    ),
                ).also { entryId ->
                    database.accountingDao().insertLines(
                        journal.lines.map { line ->
                            JournalLineEntity(
                                entryId = entryId,
                                accountCode = line.accountCode,
                                debitRial = line.debit.value,
                                creditRial = line.credit.value,
                                memo = line.memo,
                            )
                        },
                    )
                }
            } else {
                null
            }
            PostedPurchase(purchaseId, journalId, total)
        }
    }

    override fun details(purchaseId: Long): Flow<PurchaseDetails?> =
        combine(
            database.purchaseDao().observeHeader(purchaseId),
            database.purchaseDao().observeDetailLines(purchaseId),
            database.accountingDao().observePurchaseSettlements(purchaseId),
        ) { header, lines, settlements ->
            header?.let {
                PurchaseDetails(
                    id = it.purchaseId,
                    invoiceNo = it.invoiceNo,
                    supplierName = it.supplierName,
                    purchaseEpochDay = it.purchaseEpochDay,
                    dueEpochDay = it.dueEpochDay,
                    totalRial = it.totalRial,
                    paidRial = it.paidRial,
                    paymentStatus = it.paymentStatus,
                    paymentMethod = it.paymentMethod,
                    reminderEnabled = it.reminderEnabled,
                    reminderEpochDay = it.reminderEpochDay,
                    lines = lines.map { line ->
                        PurchaseLineRecord(
                            itemId = line.itemId,
                            itemName = line.itemName,
                            unit = line.unit,
                            quantityMicros = line.quantityMicros,
                            unitCostRial = line.unitCostRial,
                            lineTotalRial = line.lineTotalRial,
                        )
                    },
                    settlements = settlements.map { settlement ->
                        PurchaseSettlementRecord(
                            journalEntryId = settlement.journalEntryId,
                            entryNo = settlement.entryNo,
                            settlementEpochDay = settlement.settlementEpochDay,
                            amountRial = settlement.amountRial,
                            paymentMethod = settlement.paymentMethod,
                            referenceNo = settlement.referenceNo,
                            notes = settlement.notes,
                        )
                    },
                )
            }
        }

    override suspend fun settle(draft: PurchaseSettlementDraft): PostedPurchaseSettlement {
        val valid = draft.validated()
        return database.withTransaction {
            val purchase = database.purchaseDao().byId(valid.purchaseId)
                ?: error("فاکتور خرید پیدا نشد.")
            require(purchase.paymentMethod == null) {
                "این فاکتور هنگام خرید پرداخت شده است."
            }
            require(purchase.paymentStatus in setOf("UNPAID", "PARTIAL")) {
                "این فاکتور قابل تسویه نیست."
            }
            require(valid.settlementEpochDay >= purchase.purchaseEpochDay) {
                "تاریخ تسویه نمی‌تواند قبل از تاریخ خرید باشد."
            }
            val remaining = MoneyRial.of(purchase.totalRial) - MoneyRial.of(purchase.paidRial)
            require(valid.amount <= remaining) {
                "مبلغ تسویه از مانده فاکتور بیشتر است."
            }
            require(database.accountingDao().activeAccountExists("2101")) {
                "حساب پرداختنی فعال نیست."
            }
            require(database.accountingDao().activeAccountExists(valid.paymentMethod.accountCode)) {
                "حساب روش پرداخت فعال نیست."
            }

            val journalId = database.accountingDao().insertEntry(
                JournalEntryEntity(
                    entryNo = temporaryEntryNo(),
                    entryEpochDay = valid.settlementEpochDay,
                    description = "تسویه فاکتور ${purchase.invoiceNo} با ${valid.paymentMethod.title}",
                    sourceType = "PURCHASE_SETTLEMENT",
                    sourceId = purchase.id,
                    createdAtEpochMillis = clock(),
                ),
            )
            val entryNo = "ت-$journalId"
            check(
                database.accountingDao().finalizeEntryIdentity(
                    entryId = journalId,
                    entryNo = entryNo,
                    sourceId = purchase.id,
                ) == 1,
            ) { "شماره‌گذاری سند تسویه انجام نشد." }
            database.accountingDao().insertLines(
                listOf(
                    JournalLineEntity(
                        entryId = journalId,
                        accountCode = "2101",
                        debitRial = valid.amount.value,
                        creditRial = 0,
                        memo = valid.notes,
                    ),
                    JournalLineEntity(
                        entryId = journalId,
                        accountCode = valid.paymentMethod.accountCode,
                        debitRial = 0,
                        creditRial = valid.amount.value,
                        memo = valid.referenceNo,
                    ),
                ),
            )

            val newPaid = MoneyRial.of(purchase.paidRial) + valid.amount
            val newRemaining = MoneyRial.of(purchase.totalRial) - newPaid
            val paidInFull = newRemaining == MoneyRial.ZERO
            check(
                database.purchaseDao().updateSettlementState(
                    purchaseId = purchase.id,
                    expectedPaidRial = purchase.paidRial,
                    newPaidRial = newPaid.value,
                    paymentStatus = if (paidInFull) "PAID" else "PARTIAL",
                    reminderEnabled = !paidInFull && purchase.reminderEnabled,
                    reminderEpochDay = if (paidInFull) null else purchase.reminderEpochDay,
                ) == 1,
            ) { "مانده فاکتور هم‌زمان تغییر کرده است؛ دوباره تلاش کنید." }

            PostedPurchaseSettlement(
                purchaseId = purchase.id,
                journalEntryId = journalId,
                journalEntryNo = entryNo,
                remaining = newRemaining,
            )
        }
    }

    override suspend fun reverse(draft: PurchaseReversalDraft) {
        val valid = draft.validated()
        database.withTransaction {
            val purchase = database.purchaseDao().byId(valid.purchaseId)
                ?: error("فاکتور خرید پیدا نشد.")
            require(purchase.paymentStatus != "REVERSED") {
                "این فاکتور قبلاً برگشت خورده است."
            }
            require(valid.reversalEpochDay >= purchase.purchaseEpochDay) {
                "تاریخ برگشت نمی‌تواند قبل از تاریخ خرید باشد."
            }
            require(purchase.paidRial == 0L || purchase.paymentMethod != null) {
                "فاکتور نسیه‌ای که تسویه دارد ابتدا باید از مسیر اصلاح تسویه بررسی شود."
            }
            require(!database.accountingDao().hasPurchaseReversal(purchase.id)) {
                "برای این فاکتور قبلاً سند برگشت ثبت شده است."
            }
            val originalJournalLines = if (purchase.totalRial > 0) {
                val originalEntry = database.accountingDao().entryBySource("PURCHASE", purchase.id)
                    ?: error("سند حسابداری خرید پیدا نشد.")
                database.accountingDao().linesByEntry(originalEntry.id).also { lines ->
                    require(lines.size >= 2) { "آرتیکل‌های سند خرید کامل نیستند." }
                }
            } else {
                emptyList()
            }
            val purchaseLines = database.purchaseDao().linesByPurchase(purchase.id)
            require(purchaseLines.isNotEmpty()) { "ردیف‌های فاکتور خرید پیدا نشدند." }
            val now = clock()

            purchaseLines.forEach { line ->
                val item = database.inventoryDao().activeById(line.itemId)
                    ?: error("کالای «${line.itemNameSnapshot}» فعال نیست.")
                val nextStock = (
                    QuantityMicros.of(item.stockMicros) - QuantityMicros.of(line.quantityMicros)
                ).value
                val nextValue = (
                    MoneyRial.of(item.inventoryValueRial) - MoneyRial.of(line.lineTotalRial)
                ).value
                check(
                    database.inventoryDao().updateValuation(
                        itemId = item.id,
                        stockMicros = nextStock,
                        inventoryValueRial = nextValue,
                        updatedAtEpochMillis = now,
                    ) == 1,
                ) { "برگشت موجودی انجام نشد." }
                database.stockMovementDao().insert(
                    StockMovementEntity(
                        itemId = item.id,
                        movementType = "PURCHASE_REVERSAL",
                        quantityDeltaMicros = -line.quantityMicros,
                        valueDeltaRial = -line.lineTotalRial,
                        referenceType = "PURCHASE",
                        referenceId = purchase.id,
                        movementEpochDay = valid.reversalEpochDay,
                        notes = valid.reason,
                        createdAtEpochMillis = now,
                    ),
                )
            }

            if (originalJournalLines.isNotEmpty()) {
                val reversalId = database.accountingDao().insertEntry(
                    JournalEntryEntity(
                        entryNo = temporaryEntryNo(),
                        entryEpochDay = valid.reversalEpochDay,
                        description = "برگشت فاکتور ${purchase.invoiceNo}: ${valid.reason}",
                        sourceType = "PURCHASE_REVERSAL",
                        sourceId = purchase.id,
                        createdAtEpochMillis = now,
                    ),
                )
                val entryNo = "بخ-$reversalId"
                check(
                    database.accountingDao().finalizeEntryIdentity(
                        entryId = reversalId,
                        entryNo = entryNo,
                        sourceId = purchase.id,
                    ) == 1,
                ) { "شماره‌گذاری سند برگشت خرید انجام نشد." }
                database.accountingDao().insertLines(
                    originalJournalLines.map { line ->
                        JournalLineEntity(
                            entryId = reversalId,
                            accountCode = line.accountCode,
                            debitRial = line.creditRial,
                            creditRial = line.debitRial,
                            memo = valid.reason,
                        )
                    },
                )
            }
            check(database.purchaseDao().markReversed(purchase.id) == 1) {
                "وضعیت فاکتور تغییر نکرد."
            }
        }
    }

    private fun temporaryEntryNo(): String = "TMP-${UUID.randomUUID()}"
}
