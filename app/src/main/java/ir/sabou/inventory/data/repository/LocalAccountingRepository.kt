package ir.sabou.inventory.data.repository

import androidx.room.withTransaction
import ir.sabou.inventory.core.MoneyRial
import ir.sabou.inventory.core.SignedLongMath
import ir.sabou.inventory.data.db.AccountEntity
import ir.sabou.inventory.data.db.AppDatabase
import ir.sabou.inventory.data.db.JournalEntryEntity
import ir.sabou.inventory.data.db.JournalLineEntity
import ir.sabou.inventory.domain.accounting.AccountBalanceRecord
import ir.sabou.inventory.domain.accounting.AccountDraft
import ir.sabou.inventory.domain.accounting.AccountType
import ir.sabou.inventory.domain.accounting.AccountingRepository
import ir.sabou.inventory.domain.accounting.BalancedJournalDraft
import ir.sabou.inventory.domain.accounting.JournalDetailLine
import ir.sabou.inventory.domain.accounting.JournalDetails
import ir.sabou.inventory.domain.accounting.JournalLineDraft
import ir.sabou.inventory.domain.accounting.JournalSummary
import ir.sabou.inventory.domain.accounting.LedgerRow
import ir.sabou.inventory.domain.accounting.ManualJournalDraft
import ir.sabou.inventory.domain.accounting.PostedJournal
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalAccountingRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountingRepository {
    private val dao
        get() = database.accountingDao()

    override val accounts: Flow<List<AccountBalanceRecord>> =
        dao.observeAccountBalances().map { rows ->
            rows.map { row ->
                AccountBalanceRecord(
                    code = row.code,
                    name = row.name,
                    type = AccountType.fromStored(row.type),
                    isSystem = row.isSystem,
                    debitTurnoverRial = row.debitTurnoverRial,
                    creditTurnoverRial = row.creditTurnoverRial,
                )
            }
        }

    override fun journals(query: String): Flow<List<JournalSummary>> =
        dao.observeJournals(query.trim()).map { rows ->
            rows.map { row ->
                JournalSummary(
                    id = row.id,
                    entryNo = row.entryNo,
                    entryEpochDay = row.entryEpochDay,
                    description = row.description,
                    sourceType = row.sourceType,
                    totalDebitRial = row.totalDebitRial,
                    totalCreditRial = row.totalCreditRial,
                    isReversed = row.isReversed,
                )
            }
        }

    override fun journalDetails(entryId: Long): Flow<JournalDetails?> =
        dao.observeJournalDetails(entryId).map { rows ->
            val first = rows.firstOrNull() ?: return@map null
            val lines = rows.map { row ->
                JournalDetailLine(
                    id = row.lineId,
                    accountCode = row.accountCode,
                    accountName = row.accountName,
                    debitRial = row.debitRial,
                    creditRial = row.creditRial,
                    memo = row.memo,
                )
            }
            JournalDetails(
                id = first.entryId,
                entryNo = first.entryNo,
                entryEpochDay = first.entryEpochDay,
                description = first.entryDescription,
                sourceType = first.sourceType,
                sourceId = first.sourceId,
                totalDebitRial = exactSum(lines.map { it.debitRial }),
                totalCreditRial = exactSum(lines.map { it.creditRial }),
                isReversed = first.isReversed,
                lines = lines,
            )
        }

    override fun ledger(accountCode: String): Flow<List<LedgerRow>> =
        dao.observeLedger(accountCode).map { rows ->
            var runningBalance = 0L
            rows.map { row ->
                runningBalance = SignedLongMath.add(
                    runningBalance,
                    SignedLongMath.subtract(row.debitRial, row.creditRial),
                )
                LedgerRow(
                    lineId = row.lineId,
                    entryId = row.entryId,
                    entryNo = row.entryNo,
                    entryEpochDay = row.entryEpochDay,
                    description = row.description,
                    debitRial = row.debitRial,
                    creditRial = row.creditRial,
                    balanceAfterRial = runningBalance,
                )
            }
        }

    override suspend fun createAccount(draft: AccountDraft) {
        val valid = draft.validated()
        database.withTransaction {
            val previous = dao.accountByCode(valid.code)
            if (previous == null) {
                dao.insertAccount(
                    AccountEntity(
                        code = valid.code,
                        name = valid.name,
                        type = valid.type.storedValue,
                        isSystem = false,
                    ),
                )
                return@withTransaction
            }

            require(!previous.isActive) { "حسابی با این کد وجود دارد." }
            require(!previous.isSystem) { "حساب سیستمی قابل جایگزینی نیست." }
            if (dao.accountUsageCount(valid.code) > 0) {
                require(previous.type == valid.type.storedValue) {
                    "نوع حساب دارای گردش قابل تغییر نیست."
                }
            }
            check(
                dao.updateAccount(
                    previous.copy(
                        name = valid.name,
                        type = valid.type.storedValue,
                        isActive = true,
                    ),
                ) == 1,
            ) { "فعال‌سازی حساب انجام نشد." }
        }
    }

    override suspend fun updateAccount(code: String, draft: AccountDraft) {
        val valid = draft.validated()
        require(valid.code == code) { "کد حساب پس از ثبت قابل تغییر نیست." }
        database.withTransaction {
            val current = dao.accountByCode(code)
                ?: error("حساب پیدا نشد.")
            require(current.isActive) { "حساب غیرفعال است." }
            require(!current.isSystem) { "حساب‌های سیستمی قابل ویرایش نیستند." }
            if (current.type != valid.type.storedValue) {
                require(dao.accountUsageCount(code) == 0L) {
                    "نوع حساب دارای گردش قابل تغییر نیست."
                }
            }
            check(
                dao.updateAccount(
                    current.copy(
                        name = valid.name,
                        type = valid.type.storedValue,
                    ),
                ) == 1,
            ) { "ویرایش حساب انجام نشد." }
        }
    }

    override suspend fun deactivateAccount(code: String) {
        database.withTransaction {
            val current = dao.accountByCode(code)
                ?: error("حساب پیدا نشد.")
            require(current.isActive) { "حساب قبلاً غیرفعال شده است." }
            require(!current.isSystem) { "حساب‌های سیستمی قابل غیرفعال‌کردن نیستند." }
            require(dao.accountBalanceRial(code) == 0L) {
                "حساب دارای مانده را نمی‌توان غیرفعال کرد."
            }
            check(dao.updateAccount(current.copy(isActive = false)) == 1) {
                "غیرفعال‌کردن حساب انجام نشد."
            }
        }
    }

    override suspend fun postManual(draft: ManualJournalDraft): PostedJournal {
        val valid = draft.validated()
        return database.withTransaction {
            valid.lines.forEach { line ->
                require(dao.activeAccountExists(line.accountCode)) {
                    "حساب ${line.accountCode} فعال نیست."
                }
            }

            val entryId = dao.insertEntry(
                JournalEntryEntity(
                    entryNo = temporaryEntryNo(),
                    entryEpochDay = valid.entryEpochDay,
                    description = valid.description,
                    sourceType = valid.sourceType,
                    sourceId = 0,
                    createdAtEpochMillis = clock(),
                ),
            )
            val entryNo = "س-$entryId"
            check(dao.finalizeEntryIdentity(entryId, entryNo, entryId) == 1) {
                "شماره‌گذاری سند انجام نشد."
            }
            dao.insertLines(valid.lines.toEntities(entryId))
            PostedJournal(entryId, entryNo)
        }
    }

    override suspend fun reverseManual(
        entryId: Long,
        reversalEpochDay: Long,
        reason: String,
    ): PostedJournal {
        val normalizedReason = reason.trim()
        require(normalizedReason.length in 3..200) {
            "دلیل برگشت باید بین ۳ تا ۲۰۰ نویسه باشد."
        }
        return database.withTransaction {
            val original = dao.entryById(entryId)
                ?: error("سند پیدا نشد.")
            require(original.status == "POSTED") { "سند ثبت‌شده نیست." }
            require(original.sourceType == "MANUAL") {
                "سند خودکار باید از ماژول مبدأ اصلاح شود."
            }
            require(reversalEpochDay >= original.entryEpochDay) {
                "تاریخ برگشت نمی‌تواند قبل از تاریخ سند باشد."
            }
            require(!dao.hasPostedReversal(entryId)) {
                "این سند قبلاً برگشت خورده است."
            }
            val originalLines = dao.linesByEntry(entryId)
            require(originalLines.size >= 2) { "آرتیکل‌های سند کامل نیستند." }
            val reversal = BalancedJournalDraft(
                description = "برگشت ${original.entryNo}: $normalizedReason",
                entryEpochDay = reversalEpochDay,
                sourceType = "REVERSAL",
                sourceId = original.id,
                lines = originalLines.map { line ->
                    JournalLineDraft(
                        accountCode = line.accountCode,
                        debit = MoneyRial.of(line.creditRial),
                        credit = MoneyRial.of(line.debitRial),
                        memo = normalizedReason,
                    )
                },
            )

            val reversalId = dao.insertEntry(
                JournalEntryEntity(
                    entryNo = temporaryEntryNo(),
                    entryEpochDay = reversal.entryEpochDay,
                    description = reversal.description,
                    sourceType = reversal.sourceType,
                    sourceId = reversal.sourceId,
                    createdAtEpochMillis = clock(),
                ),
            )
            val entryNo = "ب-$reversalId"
            check(
                dao.finalizeEntryIdentity(
                    entryId = reversalId,
                    entryNo = entryNo,
                    sourceId = original.id,
                ) == 1,
            ) { "شماره‌گذاری سند برگشت انجام نشد." }
            dao.insertLines(reversal.lines.toEntities(reversalId))
            PostedJournal(reversalId, entryNo)
        }
    }

    private fun temporaryEntryNo(): String = "TMP-${UUID.randomUUID()}"
}

private fun List<JournalLineDraft>.toEntities(entryId: Long): List<JournalLineEntity> =
    map { line ->
        JournalLineEntity(
            entryId = entryId,
            accountCode = line.accountCode,
            debitRial = line.debit.value,
            creditRial = line.credit.value,
            memo = line.memo,
        )
    }

private fun exactSum(values: Iterable<Long>): Long =
    values.fold(0L, SignedLongMath::add)
