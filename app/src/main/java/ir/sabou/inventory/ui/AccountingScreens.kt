package ir.sabou.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.sabou.inventory.core.MoneyRial
import ir.sabou.inventory.core.SignedLongMath
import ir.sabou.inventory.domain.accounting.AccountBalanceRecord
import ir.sabou.inventory.domain.accounting.AccountDraft
import ir.sabou.inventory.domain.accounting.AccountType
import ir.sabou.inventory.domain.accounting.JournalDetails
import ir.sabou.inventory.domain.accounting.JournalLineDraft
import ir.sabou.inventory.domain.accounting.JournalSummary
import ir.sabou.inventory.domain.accounting.LedgerRow
import ir.sabou.inventory.domain.accounting.ManualJournalDraft
import ir.sabou.inventory.domain.accounting.PostedJournal

private enum class AccountingTab {
    DOCUMENTS,
    ACCOUNTS,
    REPORTS,
}

@Composable
fun AccountingScreen(
    state: AccountingUiState,
    onSearch: (String) -> Unit,
    onSelectJournal: (Long?) -> Unit,
    onSelectLedger: (String?) -> Unit,
    onSaveAccount: (String?, AccountDraft, () -> Unit) -> Unit,
    onDeactivateAccount: (String) -> Unit,
    onReverse: (Long, Long, String) -> Unit,
    onAddJournal: () -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableStateOf(AccountingTab.DOCUMENTS) }
    var accountEditor by remember { mutableStateOf<AccountBalanceRecord?>(null) }
    var newAccountEditorOpen by remember { mutableStateOf(false) }
    var deactivateTarget by remember { mutableStateOf<AccountBalanceRecord?>(null) }
    var reversalTarget by remember { mutableStateOf<JournalDetails?>(null) }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "حسابداری",
                actionLabel = if (tab == AccountingTab.DOCUMENTS) "سند جدید" else null,
                onAction = onAddJournal,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            state.message?.let { MessageCard(it) }
            AccountingTabs(
                selected = tab,
                onSelected = { tab = it },
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f)) {
                when (tab) {
                    AccountingTab.DOCUMENTS -> JournalList(
                        journals = state.journals,
                        search = state.journalSearch,
                        onSearch = onSearch,
                        onSelect = onSelectJournal,
                    )

                    AccountingTab.ACCOUNTS -> AccountList(
                        accounts = state.accounts,
                        onNew = { newAccountEditorOpen = true },
                        onEdit = { accountEditor = it },
                        onDeactivate = { deactivateTarget = it },
                        onLedger = onSelectLedger,
                    )

                    AccountingTab.REPORTS -> AccountingReports(state)
                }
            }
        }
    }

    state.selectedJournal?.let { details ->
        JournalDetailsDialog(
            details = details,
            onDismiss = { onSelectJournal(null) },
            onReverse = {
                reversalTarget = details
                onSelectJournal(null)
            },
        )
    }
    state.selectedLedgerCode?.let { code ->
        LedgerDialog(
            account = state.accounts.firstOrNull { it.code == code },
            rows = state.ledgerRows,
            onDismiss = { onSelectLedger(null) },
        )
    }
    if (newAccountEditorOpen || accountEditor != null) {
        AccountEditorDialog(
            existing = accountEditor,
            busy = state.busy,
            onDismiss = {
                newAccountEditorOpen = false
                accountEditor = null
            },
            onSave = { draft ->
                onSaveAccount(accountEditor?.code, draft) {
                    newAccountEditorOpen = false
                    accountEditor = null
                }
            },
        )
    }
    deactivateTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deactivateTarget = null },
            title = { Text("غیرفعال‌کردن حساب") },
            text = {
                Text(
                    "حساب «${account.name}» فقط در صورتی غیرفعال می‌شود که مانده آن صفر باشد. " +
                        "گردش‌های قبلی حذف نمی‌شوند.",
                )
            },
            confirmButton = {
                Button(
                    enabled = !state.busy,
                    onClick = {
                        deactivateTarget = null
                        onDeactivateAccount(account.code)
                    },
                ) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { deactivateTarget = null }) { Text("انصراف") }
            },
        )
    }
    reversalTarget?.let { details ->
        ReversalDialog(
            details = details,
            busy = state.busy,
            onDismiss = { reversalTarget = null },
            onConfirm = { epochDay, reason ->
                reversalTarget = null
                onSelectJournal(null)
                onReverse(details.id, epochDay, reason)
            },
        )
    }
}

@Composable
private fun AccountingTabs(
    selected: AccountingTab,
    onSelected: (AccountingTab) -> Unit,
) {
    val tabs = listOf(
        AccountingTab.DOCUMENTS to "اسناد",
        AccountingTab.ACCOUNTS to "حساب‌ها",
        AccountingTab.REPORTS to "گزارش‌ها",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEach { (tab, title) ->
            if (selected == tab) {
                Button(
                    onClick = { onSelected(tab) },
                    modifier = Modifier.weight(1f),
                ) { Text(title) }
            } else {
                OutlinedButton(
                    onClick = { onSelected(tab) },
                    modifier = Modifier.weight(1f),
                ) { Text(title) }
            }
        }
    }
}

@Composable
private fun JournalList(
    journals: List<JournalSummary>,
    search: String,
    onSearch: (String) -> Unit,
    onSelect: (Long) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            label = { Text("جست‌وجوی شماره، شرح یا نام حساب") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        if (journals.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (search.isBlank()) {
                        "هنوز سند حسابداری ثبت نشده است."
                    } else {
                        "سندی با این عبارت پیدا نشد."
                    },
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(journals, key = { it.id }) { journal ->
                    Card(onClick = { onSelect(journal.id) }) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(journal.entryNo, fontWeight = FontWeight.Bold)
                                Text(epochDayToPersian(journal.entryEpochDay).display())
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(journal.description)
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(sourceTitle(journal.sourceType))
                                Text(
                                    formatMoney(journal.totalDebitRial),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (journal.isReversed) {
                                Text(
                                    "برگشت خورده",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
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
private fun AccountList(
    accounts: List<AccountBalanceRecord>,
    onNew: () -> Unit,
    onEdit: (AccountBalanceRecord) -> Unit,
    onDeactivate: (AccountBalanceRecord) -> Unit,
    onLedger: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("افزودن حساب تفصیلی")
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(accounts, key = { it.code }) { account ->
                Card {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column {
                                Text(
                                    "${account.code} · ${account.name}",
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(account.type.title)
                            }
                            if (account.isSystem) {
                                Text(
                                    "سیستمی",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("گردش بدهکار: ${formatMoney(account.debitTurnoverRial)}")
                        Text("گردش بستانکار: ${formatMoney(account.creditTurnoverRial)}")
                        Text(accountBalanceText(account), fontWeight = FontWeight.SemiBold)
                        Row {
                            TextButton(onClick = { onLedger(account.code) }) {
                                Text("دفتر حساب")
                            }
                            if (!account.isSystem) {
                                TextButton(onClick = { onEdit(account) }) { Text("ویرایش") }
                                TextButton(onClick = { onDeactivate(account) }) {
                                    Text("غیرفعال‌کردن")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountingReports(state: AccountingUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "سود و زیان",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportMetric(
                    title = "درآمد",
                    value = formatMoney(state.profitLoss.revenueRial),
                    modifier = Modifier.weight(1f),
                )
                ReportMetric(
                    title = "هزینه",
                    value = formatMoney(state.profitLoss.expenseRial),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            ReportMetric(
                title = if (state.profitLoss.netProfitRial >= 0) "سود خالص" else "زیان خالص",
                value = formatMoney(state.profitLoss.netProfitRial),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(Modifier.height(6.dp))
            Text(
                "تراز آزمایشی",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (state.trialBalance.isBalanced) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        if (state.trialBalance.isBalanced) "تراز است" else "عدم تراز شناسایی شد",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "جمع گردش بدهکار: " +
                            formatMoney(state.trialBalance.totalDebitTurnoverRial),
                    )
                    Text(
                        "جمع گردش بستانکار: " +
                            formatMoney(state.trialBalance.totalCreditTurnoverRial),
                    )
                    Text(
                        "جمع مانده بدهکار: " +
                            formatMoney(state.trialBalance.totalDebitBalanceRial),
                    )
                    Text(
                        "جمع مانده بستانکار: " +
                            formatMoney(state.trialBalance.totalCreditBalanceRial),
                    )
                }
            }
        }
        items(state.trialBalance.accounts, key = { it.code }) { account ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    Text("${account.code} · ${account.name}", fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("بدهکار: ${formatMoney(account.debitBalanceRial)}")
                        Text("بستانکار: ${formatMoney(account.creditBalanceRial)}")
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun ReportMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title)
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun JournalDetailsDialog(
    details: JournalDetails,
    onDismiss: () -> Unit,
    onReverse: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سند ${details.entryNo}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(details.description, fontWeight = FontWeight.Bold)
                Text("تاریخ: ${epochDayToPersian(details.entryEpochDay).display()}")
                Text("نوع: ${sourceTitle(details.sourceType)}")
                if (details.isReversed) {
                    Text(
                        "این سند با یک سند برگشت خنثی شده است.",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                details.lines.forEach { line ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(
                                "${line.accountCode} · ${line.accountName}",
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "بدهکار: ${
                                    if (line.debitRial == 0L) "—" else formatMoney(line.debitRial)
                                }",
                            )
                            Text(
                                "بستانکار: ${
                                    if (line.creditRial == 0L) "—" else formatMoney(line.creditRial)
                                }",
                            )
                            if (line.memo.isNotBlank()) Text("شرح: ${line.memo}")
                        }
                    }
                }
                Text("جمع بدهکار: ${formatMoney(details.totalDebitRial)}")
                Text("جمع بستانکار: ${formatMoney(details.totalCreditRial)}")
                if (details.sourceType != "MANUAL" && details.sourceType != "REVERSAL") {
                    Text(
                        "برای اصلاح این سند باید فاکتور یا عملیات مبدأ اصلاح شود.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (details.canReverse) {
                    TextButton(onClick = onReverse) { Text("ثبت برگشت") }
                }
                Button(onClick = onDismiss) { Text("بستن") }
            }
        },
    )
}

@Composable
private fun LedgerDialog(
    account: AccountBalanceRecord?,
    rows: List<LedgerRow>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("دفتر ${account?.name ?: "حساب"}") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (rows.isEmpty()) {
                    Text("برای این حساب هنوز گردشی ثبت نشده است.")
                } else {
                    rows.forEach { row ->
                        Card {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(row.entryNo, fontWeight = FontWeight.Bold)
                                    Text(epochDayToPersian(row.entryEpochDay).display())
                                }
                                Text(row.description)
                                Text(
                                    "بدهکار: ${
                                        if (row.debitRial == 0L) "—" else formatMoney(row.debitRial)
                                    }",
                                )
                                Text(
                                    "بستانکار: ${
                                        if (row.creditRial == 0L) "—" else formatMoney(row.creditRial)
                                    }",
                                )
                                Text(
                                    signedBalanceText(row.balanceAfterRial),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun AccountEditorDialog(
    existing: AccountBalanceRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (AccountDraft) -> Unit,
) {
    var code by remember(existing?.code) { mutableStateOf(existing?.code.orEmpty()) }
    var name by remember(existing?.code) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing?.code) { mutableStateOf(existing?.type ?: AccountType.EXPENSE) }
    var error by remember(existing?.code) { mutableStateOf<String?>(null) }
    val types = AccountType.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "حساب جدید" else "ویرایش حساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    enabled = existing == null,
                    label = { Text("کد چهاررقمی حساب") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("نام حساب") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectionField(
                    label = "نوع حساب",
                    selectedText = type.title,
                    options = types.mapIndexed { index, value ->
                        index.toLong() to value.title
                    },
                    onSelected = { type = types[it.toInt()] },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val draft = AccountDraft(
                            code = normalizeNumberInput(code),
                            name = name,
                            type = type,
                        ).validated()
                        onSave(draft)
                    } catch (failure: Exception) {
                        error = failure.message ?: "اطلاعات حساب معتبر نیست."
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun ReversalDialog(
    details: JournalDetails,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit,
) {
    val today = remember { System.currentTimeMillis() / 86_400_000L }
    var epochDay by remember(details.id) {
        mutableLongStateOf(maxOf(today, details.entryEpochDay))
    }
    var reason by remember(details.id) { mutableStateOf("") }
    var error by remember(details.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("برگشت سند ${details.entryNo}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سند اصلی حذف نمی‌شود و یک سند معکوس برای حفظ سابقه ثبت خواهد شد.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                PersianDateField(
                    label = "تاریخ برگشت",
                    epochDay = epochDay,
                    onSelected = { epochDay = it },
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("دلیل برگشت") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    if (reason.trim().length < 3) {
                        error = "دلیل برگشت را کامل وارد کنید."
                    } else {
                        onConfirm(epochDay, reason)
                    }
                },
            ) { Text("ثبت سند برگشت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

private data class JournalLineForm(
    val rowId: Int,
    val accountCode: String? = null,
    val debitRial: String = "",
    val creditRial: String = "",
    val memo: String = "",
)

@Composable
fun ManualJournalEntryScreen(
    state: AccountingUiState,
    onPost: (ManualJournalDraft, (PostedJournal) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { System.currentTimeMillis() / 86_400_000L }
    var description by remember { mutableStateOf("") }
    var epochDay by remember { mutableLongStateOf(today) }
    var nextRowId by remember { mutableIntStateOf(3) }
    var lines by remember {
        mutableStateOf(
            listOf(
                JournalLineForm(rowId = 1),
                JournalLineForm(rowId = 2),
            ),
        )
    }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "ثبت سند حسابداری",
                actionLabel = null,
                onAction = {},
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.message?.let { MessageCard(it) }
            localError?.let { MessageCard(it, isError = true) }
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("شرح سند") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            PersianDateField(
                label = "تاریخ سند",
                epochDay = epochDay,
                onSelected = { epochDay = it },
            )
            Text(
                "آرتیکل‌های سند",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            lines.forEachIndexed { index, line ->
                JournalLineEditor(
                    index = index,
                    line = line,
                    accounts = state.accounts,
                    removable = lines.size > 2,
                    onChanged = { changed ->
                        lines = lines.map { current ->
                            if (current.rowId == line.rowId) changed else current
                        }
                    },
                    onRemove = {
                        lines = lines.filterNot { it.rowId == line.rowId }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    lines = lines + JournalLineForm(rowId = nextRowId)
                    nextRowId++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("افزودن آرتیکل")
            }
            val debitPreview = enteredTotal(lines.map { it.debitRial })
            val creditPreview = enteredTotal(lines.map { it.creditRial })
            if (debitPreview != null && creditPreview != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (debitPreview == creditPreview) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("جمع بدهکار: ${formatMoney(debitPreview)}")
                        Text("جمع بستانکار: ${formatMoney(creditPreview)}")
                        Text(
                            if (debitPreview == creditPreview) "سند تراز است" else "سند تراز نیست",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Button(
                enabled = !state.busy && state.accounts.isNotEmpty(),
                onClick = {
                    try {
                        localError = null
                        val draft = ManualJournalDraft(
                            description = description,
                            entryEpochDay = epochDay,
                            lines = lines.map { line ->
                                JournalLineDraft(
                                    accountCode = line.accountCode
                                        ?: error("حساب همه آرتیکل‌ها را انتخاب کنید."),
                                    debit = parseOptionalMoney(line.debitRial),
                                    credit = parseOptionalMoney(line.creditRial),
                                    memo = line.memo,
                                )
                            },
                        )
                        draft.validated()
                        onPost(draft) { onBack() }
                    } catch (failure: Exception) {
                        localError = failure.message ?: "اطلاعات سند معتبر نیست."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "در حال ثبت…" else "ثبت نهایی سند")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun JournalLineEditor(
    index: Int,
    line: JournalLineForm,
    accounts: List<AccountBalanceRecord>,
    removable: Boolean,
    onChanged: (JournalLineForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("آرتیکل ${index + 1}", fontWeight = FontWeight.Bold)
                if (removable) {
                    TextButton(onClick = onRemove) { Text("حذف ردیف") }
                }
            }
            SelectionField(
                label = "حساب",
                selectedText = accounts.firstOrNull { it.code == line.accountCode }?.let {
                    "${it.code} · ${it.name}"
                },
                options = accounts.map { account ->
                    account.code.toLong() to "${account.code} · ${account.name}"
                },
                onSelected = { selected ->
                    onChanged(line.copy(accountCode = selected.toString()))
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.debitRial,
                    onValueChange = { onChanged(line.copy(debitRial = it)) },
                    label = { Text("بدهکار (ریال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.creditRial,
                    onValueChange = { onChanged(line.copy(creditRial = it)) },
                    label = { Text("بستانکار (ریال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = line.memo,
                onValueChange = { onChanged(line.copy(memo = it)) },
                label = { Text("شرح آرتیکل (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun parseOptionalMoney(value: String): MoneyRial =
    if (value.isBlank()) MoneyRial.ZERO else parseMoneyRial(value)

private fun enteredTotal(values: List<String>): Long? = try {
    values.fold(0L) { total, value ->
        SignedLongMath.add(total, parseOptionalMoney(value).value)
    }
} catch (_: Exception) {
    null
}

private fun sourceTitle(sourceType: String): String = when (sourceType) {
    "PURCHASE" -> "خرید"
    "MANUAL" -> "سند دستی"
    "REVERSAL" -> "سند برگشت"
    "SALE" -> "فروش"
    "PAYROLL" -> "حقوق"
    else -> sourceType
}

private fun accountBalanceText(account: AccountBalanceRecord): String = when {
    account.debitBalanceRial > 0 -> "مانده بدهکار: ${formatMoney(account.debitBalanceRial)}"
    account.creditBalanceRial > 0 -> "مانده بستانکار: ${formatMoney(account.creditBalanceRial)}"
    else -> "مانده: صفر"
}

private fun signedBalanceText(balanceRial: Long): String = when {
    balanceRial > 0 -> "مانده پس از سند: بدهکار ${formatMoney(balanceRial)}"
    balanceRial < 0 ->
        "مانده پس از سند: بستانکار ${formatMoney(balanceRial).removePrefix("−")}"
    else -> "مانده پس از سند: صفر"
}
