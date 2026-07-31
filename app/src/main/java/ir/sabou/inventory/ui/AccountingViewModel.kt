package ir.sabou.inventory.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.sabou.inventory.domain.accounting.AccountBalanceRecord
import ir.sabou.inventory.domain.accounting.AccountDraft
import ir.sabou.inventory.domain.accounting.AccountingRepository
import ir.sabou.inventory.domain.accounting.JournalDetails
import ir.sabou.inventory.domain.accounting.JournalSummary
import ir.sabou.inventory.domain.accounting.LedgerRow
import ir.sabou.inventory.domain.accounting.ManualJournalDraft
import ir.sabou.inventory.domain.accounting.PostedJournal
import ir.sabou.inventory.domain.accounting.ProfitLossSnapshot
import ir.sabou.inventory.domain.accounting.TrialBalanceSnapshot
import ir.sabou.inventory.domain.accounting.calculateProfitLoss
import ir.sabou.inventory.domain.accounting.calculateTrialBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountingUiState(
    val accounts: List<AccountBalanceRecord> = emptyList(),
    val journals: List<JournalSummary> = emptyList(),
    val journalSearch: String = "",
    val selectedJournal: JournalDetails? = null,
    val selectedLedgerCode: String? = null,
    val ledgerRows: List<LedgerRow> = emptyList(),
    val trialBalance: TrialBalanceSnapshot = calculateTrialBalance(emptyList()),
    val profitLoss: ProfitLossSnapshot = calculateProfitLoss(emptyList()),
    val busy: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AccountingViewModel(
    private val repository: AccountingRepository,
) : ViewModel() {
    private val journalSearch = MutableStateFlow("")
    private val selectedJournalId = MutableStateFlow<Long?>(null)
    private val selectedLedgerCode = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val content = combine(
        repository.accounts,
        journalSearch.flatMapLatest(repository::journals),
        selectedJournalId.flatMapLatest { entryId ->
            if (entryId == null) flowOf(null)
            else repository.journalDetails(entryId)
        },
        selectedLedgerCode.flatMapLatest { accountCode ->
            if (accountCode == null) flowOf(emptyList())
            else repository.ledger(accountCode)
        },
    ) { accounts, journals, journalDetails, ledgerRows ->
        AccountingUiState(
            accounts = accounts,
            journals = journals,
            journalSearch = journalSearch.value,
            selectedJournal = journalDetails,
            selectedLedgerCode = selectedLedgerCode.value,
            ledgerRows = ledgerRows,
            trialBalance = calculateTrialBalance(accounts),
            profitLoss = calculateProfitLoss(accounts),
        )
    }

    val state: StateFlow<AccountingUiState> = combine(
        content,
        busy,
        message,
    ) { current, isBusy, currentMessage ->
        current.copy(busy = isBusy, message = currentMessage)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountingUiState(),
    )

    fun searchJournals(value: String) {
        journalSearch.value = value
    }

    fun selectJournal(entryId: Long?) {
        selectedJournalId.value = entryId
    }

    fun selectLedger(accountCode: String?) {
        selectedLedgerCode.value = accountCode
    }

    fun clearMessage() {
        message.value = null
    }

    fun saveAccount(code: String?, draft: AccountDraft, onSuccess: () -> Unit) {
        runAction(
            successMessage = if (code == null) "حساب جدید ثبت شد." else "حساب ویرایش شد.",
            onSuccess = onSuccess,
        ) {
            if (code == null) repository.createAccount(draft)
            else repository.updateAccount(code, draft)
        }
    }

    fun deactivateAccount(code: String) {
        runAction("حساب غیرفعال شد.") {
            repository.deactivateAccount(code)
        }
    }

    fun postManual(draft: ManualJournalDraft, onSuccess: (PostedJournal) -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            message.value = null
            try {
                val posted = repository.postManual(draft)
                message.value = "سند ${posted.entryNo} با موفقیت ثبت شد."
                onSuccess(posted)
            } catch (error: Exception) {
                message.value = error.message ?: "ثبت سند انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }

    fun reverseManual(entryId: Long, epochDay: Long, reason: String) {
        runAction(
            successMessage = "سند برگشت ثبت شد و اثر مالی سند قبلی خنثی شد.",
            onSuccess = { selectedJournalId.value = null },
        ) {
            repository.reverseManual(entryId, epochDay, reason)
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
                message.value = error.message ?: "عملیات حسابداری انجام نشد."
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(repository: AccountingRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AccountingViewModel(repository) as T
            }
    }
}
