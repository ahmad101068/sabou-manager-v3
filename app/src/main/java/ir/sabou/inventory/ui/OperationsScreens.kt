package ir.sabou.inventory.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ir.sabou.inventory.domain.operations.InventoryItemDraft
import ir.sabou.inventory.domain.operations.InventoryItemRecord
import ir.sabou.inventory.domain.operations.SupplierDraft
import ir.sabou.inventory.domain.operations.SupplierRecord
import ir.sabou.inventory.domain.purchase.PostedPurchase
import ir.sabou.inventory.domain.purchase.PurchaseDraft
import ir.sabou.inventory.domain.purchase.PurchaseLineDraft
import ir.sabou.inventory.domain.purchase.PurchasePaymentMethod

@Composable
fun SuppliersScreen(
    state: OperationsUiState,
    onSave: (Long?, SupplierDraft, () -> Unit) -> Unit,
    onDeactivate: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<SupplierRecord?>(null) }
    var deactivateTarget by remember { mutableStateOf<SupplierRecord?>(null) }

    OperationsScaffold(
        title = "تأمین‌کنندگان",
        actionLabel = "افزودن",
        onAction = {
            selected = null
            editorOpen = true
        },
        onBack = onBack,
        message = state.message,
    ) { padding ->
        if (state.suppliers.isEmpty()) {
            EmptyState("هنوز تأمین‌کننده‌ای ثبت نشده است.", padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.suppliers, key = { it.id }) { supplier ->
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text(supplier.name, fontWeight = FontWeight.Bold)
                            if (supplier.contactName.isNotEmpty()) Text("رابط: ${supplier.contactName}")
                            if (supplier.phone.isNotEmpty()) Text("تماس: ${supplier.phone}")
                            Text("مهلت تسویه: ${supplier.paymentTermsDays} روز")
                            Row {
                                TextButton(onClick = {
                                    selected = supplier
                                    editorOpen = true
                                }) { Text("ویرایش") }
                                TextButton(onClick = { deactivateTarget = supplier }) {
                                    Text("غیرفعال‌کردن")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        SupplierEditorDialog(
            existing = selected,
            busy = state.busy,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                onSave(selected?.id, draft) { editorOpen = false }
            },
        )
    }
    deactivateTarget?.let { target ->
        ConfirmDeactivateDialog(
            title = "غیرفعال‌کردن تأمین‌کننده",
            body = "«${target.name}» از فهرست انتخاب‌ها حذف می‌شود؛ سوابق قبلی باقی می‌مانند.",
            onDismiss = { deactivateTarget = null },
            onConfirm = {
                deactivateTarget = null
                onDeactivate(target.id)
            },
        )
    }
}

@Composable
fun InventoryScreen(
    state: OperationsUiState,
    onSave: (Long?, InventoryItemDraft, () -> Unit) -> Unit,
    onDeactivate: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InventoryItemRecord?>(null) }
    var deactivateTarget by remember { mutableStateOf<InventoryItemRecord?>(null) }

    OperationsScaffold(
        title = "انبار و کالاها",
        actionLabel = "کالای جدید",
        onAction = {
            selected = null
            editorOpen = true
        },
        onBack = onBack,
        message = state.message,
    ) { padding ->
        if (state.inventoryItems.isEmpty()) {
            EmptyState("هنوز کالایی ثبت نشده است.", padding)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.inventoryItems, key = { it.id }) { item ->
                    val isLow = state.lowStockItems.any { it.id == item.id }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLow) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(item.name, fontWeight = FontWeight.Bold)
                                if (isLow) Text("کمبود موجودی", color = MaterialTheme.colorScheme.error)
                            }
                            Text("${formatQuantity(item.stockMicros)} ${item.unit}")
                            Text("ارزش: ${formatMoney(item.inventoryValueRial)}")
                            Text("دسته: ${item.category}")
                            if (item.alertEnabled) {
                                Text("هشدار در ${formatQuantity(item.alertThresholdMicros)} ${item.unit}")
                            }
                            Row {
                                TextButton(onClick = {
                                    selected = item
                                    editorOpen = true
                                }) { Text("ویرایش") }
                                TextButton(onClick = { deactivateTarget = item }) {
                                    Text("غیرفعال‌کردن")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorOpen) {
        InventoryEditorDialog(
            existing = selected,
            suppliers = state.suppliers,
            busy = state.busy,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                onSave(selected?.id, draft) { editorOpen = false }
            },
        )
    }
    deactivateTarget?.let { target ->
        ConfirmDeactivateDialog(
            title = "غیرفعال‌کردن کالا",
            body = "«${target.name}» از فهرست کالاهای فعال حذف می‌شود؛ گردش انبار قبلی باقی می‌ماند.",
            onDismiss = { deactivateTarget = null },
            onConfirm = {
                deactivateTarget = null
                onDeactivate(target.id)
            },
        )
    }
}

@Composable
fun PurchasesScreen(
    state: OperationsUiState,
    onSearch: (String) -> Unit,
    onSelect: (Long?) -> Unit,
    onSettle: (ir.sabou.inventory.domain.purchase.PurchaseSettlementDraft, () -> Unit) -> Unit,
    onReverse: (ir.sabou.inventory.domain.purchase.PurchaseReversalDraft, () -> Unit) -> Unit,
    onAdd: () -> Unit,
    onBack: () -> Unit,
) {
    OperationsScaffold(
        title = "فاکتورهای خرید",
        actionLabel = "ثبت خرید",
        onAction = onAdd,
        onBack = onBack,
        message = state.message,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.purchaseSearch,
                onValueChange = onSearch,
                label = { Text("جست‌وجوی شماره فاکتور یا تأمین‌کننده") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (state.purchases.isEmpty()) {
                Text(
                    if (state.purchaseSearch.isBlank()) {
                        "هنوز فاکتور خریدی ثبت نشده است."
                    } else {
                        "فاکتوری با این عبارت پیدا نشد."
                    },
                    modifier = Modifier.padding(18.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.purchases, key = { it.id }) { purchase ->
                        Card(onClick = { onSelect(purchase.id) }) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "فاکتور ${purchase.invoiceNo}",
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        when (purchase.paymentStatus) {
                                            "PAID" -> "تسویه‌شده"
                                            "PARTIAL" -> "پرداخت ناقص"
                                            "REVERSED" -> "برگشت‌خورده"
                                            else -> "تسویه‌نشده"
                                        },
                                    )
                                }
                                Text(purchase.supplierName)
                                Text("خرید: ${epochDayToPersian(purchase.purchaseEpochDay).display()}")
                                Text("تسویه: ${epochDayToPersian(purchase.dueEpochDay).display()}")
                                Text(formatMoney(purchase.totalRial), fontWeight = FontWeight.SemiBold)
                                if (purchase.outstandingRial > 0 && purchase.paymentStatus != "REVERSED") {
                                    Text("مانده: ${formatMoney(purchase.outstandingRial)}")
                                }
                                Text(
                                    "برای مشاهده جزئیات لمس کنید",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.selectedPurchase?.let { details ->
        PurchaseDetailsDialog(
            details = details,
            busy = state.busy,
            onDismiss = { onSelect(null) },
            onSettle = onSettle,
            onReverse = onReverse,
        )
    }
}

private data class PurchaseLineForm(
    val rowId: Int,
    val itemId: Long? = null,
    val quantity: String = "",
    val unitCostRial: String = "",
)

@Composable
fun PurchaseEntryScreen(
    state: OperationsUiState,
    onPost: (PurchaseDraft, (PostedPurchase) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val todayEpochDay = remember { System.currentTimeMillis() / 86_400_000L }
    var invoiceNo by remember { mutableStateOf("") }
    var supplierId by remember { mutableStateOf<Long?>(null) }
    var purchaseEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var dueEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var paymentMethod by remember { mutableStateOf(PurchasePaymentMethod.PAYABLE) }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderEpochDay by remember { mutableLongStateOf(todayEpochDay) }
    var nextRowId by remember { mutableIntStateOf(2) }
    var lines by remember { mutableStateOf(listOf(PurchaseLineForm(rowId = 1))) }
    var localError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            ScreenHeader(
                title = "ثبت فاکتور خرید",
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
            if (state.suppliers.isEmpty() || state.inventoryItems.isEmpty()) {
                MessageCard(
                    "برای ثبت خرید، ابتدا حداقل یک تأمین‌کننده و یک کالا ثبت کنید.",
                    isError = true,
                )
            }
            OutlinedTextField(
                value = invoiceNo,
                onValueChange = { invoiceNo = it },
                label = { Text("شماره فاکتور") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SelectionField(
                label = "تأمین‌کننده",
                selectedText = state.suppliers.firstOrNull { it.id == supplierId }?.name,
                options = state.suppliers.map { it.id to it.name },
                onSelected = { id ->
                    supplierId = id
                    val terms = state.suppliers.first { it.id == id }.paymentTermsDays
                    dueEpochDay = purchaseEpochDay + terms.toLong()
                    reminderEpochDay = dueEpochDay
                },
            )
            PersianDateField(
                label = "تاریخ خرید",
                epochDay = purchaseEpochDay,
                onSelected = { selectedDate ->
                    val previousTerm = dueEpochDay - purchaseEpochDay
                    purchaseEpochDay = selectedDate
                    dueEpochDay = selectedDate + previousTerm.coerceAtLeast(0)
                    if (reminderEpochDay < selectedDate) reminderEpochDay = selectedDate
                },
            )
            PersianDateField(
                label = "تاریخ تسویه",
                epochDay = dueEpochDay,
                onSelected = { dueEpochDay = it },
            )
            PaymentMethodField(paymentMethod) { paymentMethod = it }
            if (paymentMethod == PurchasePaymentMethod.PAYABLE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                    )
                    Text("یادآوری تسویه")
                }
                if (reminderEnabled) {
                    PersianDateField(
                        label = "تاریخ یادآوری",
                        epochDay = reminderEpochDay,
                        onSelected = { reminderEpochDay = it },
                    )
                }
            }

            Text("اقلام فاکتور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            lines.forEachIndexed { index, line ->
                PurchaseLineEditor(
                    index = index,
                    line = line,
                    items = state.inventoryItems,
                    removable = lines.size > 1,
                    onChanged = { changed ->
                        lines = lines.map { if (it.rowId == line.rowId) changed else it }
                    },
                    onRemove = {
                        lines = lines.filterNot { it.rowId == line.rowId }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    lines = lines + PurchaseLineForm(rowId = nextRowId)
                    nextRowId++
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("افزودن ردیف")
            }
            Button(
                enabled = !state.busy && state.suppliers.isNotEmpty() && state.inventoryItems.isNotEmpty(),
                onClick = {
                    try {
                        localError = null
                        val draft = PurchaseDraft(
                            invoiceNo = invoiceNo.trim(),
                            supplierId = requireNotNull(supplierId) { "تأمین‌کننده را انتخاب کنید." },
                            purchaseEpochDay = purchaseEpochDay,
                            dueEpochDay = dueEpochDay,
                            paymentMethod = paymentMethod,
                            reminderEnabled = paymentMethod == PurchasePaymentMethod.PAYABLE && reminderEnabled,
                            reminderEpochDay = if (
                                paymentMethod == PurchasePaymentMethod.PAYABLE && reminderEnabled
                            ) {
                                reminderEpochDay
                            } else {
                                null
                            },
                            lines = lines.map { line ->
                                PurchaseLineDraft(
                                    itemId = requireNotNull(line.itemId) {
                                        "کالای ردیف فاکتور را انتخاب کنید."
                                    },
                                    quantity = parseQuantity(line.quantity),
                                    unitCost = parseMoneyRial(line.unitCostRial),
                                )
                            },
                        )
                        onPost(draft) { onBack() }
                    } catch (error: Exception) {
                        localError = error.message ?: "اطلاعات فاکتور کامل نیست."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "در حال ثبت…" else "ثبت نهایی خرید")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PurchaseLineEditor(
    index: Int,
    line: PurchaseLineForm,
    items: List<InventoryItemRecord>,
    removable: Boolean,
    onChanged: (PurchaseLineForm) -> Unit,
    onRemove: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ردیف ${index + 1}", fontWeight = FontWeight.Bold)
                if (removable) TextButton(onClick = onRemove) { Text("حذف ردیف") }
            }
            SelectionField(
                label = "کالا",
                selectedText = items.firstOrNull { it.id == line.itemId }?.name,
                options = items.map { it.id to "${it.name} (${it.unit})" },
                onSelected = { onChanged(line.copy(itemId = it)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = { onChanged(line.copy(quantity = it)) },
                    label = { Text("مقدار") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = line.unitCostRial,
                    onValueChange = { onChanged(line.copy(unitCostRial = it)) },
                    label = { Text("بهای واحد (ریال)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SupplierEditorDialog(
    existing: SupplierRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (SupplierDraft) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var contact by remember(existing?.id) { mutableStateOf(existing?.contactName.orEmpty()) }
    var phone by remember(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var address by remember(existing?.id) { mutableStateOf(existing?.address.orEmpty()) }
    var terms by remember(existing?.id) { mutableStateOf(existing?.paymentTermsDays?.toString() ?: "0") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "تأمین‌کننده جدید" else "ویرایش تأمین‌کننده") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(name, { name = it }, label = { Text("نام") })
                OutlinedTextField(contact, { contact = it }, label = { Text("نام رابط") })
                OutlinedTextField(
                    phone,
                    { phone = it },
                    label = { Text("شماره تماس") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                OutlinedTextField(address, { address = it }, label = { Text("نشانی") })
                OutlinedTextField(
                    terms,
                    { terms = it },
                    label = { Text("مهلت تسویه پیش‌فرض (روز)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(notes, { notes = it }, label = { Text("یادداشت") })
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        val parsedTerms = normalizeNumberInput(terms).toInt()
                        onSave(
                            SupplierDraft(
                                name = name,
                                contactName = contact,
                                phone = phone,
                                address = address,
                                paymentTermsDays = parsedTerms,
                                notes = notes,
                            ),
                        )
                    } catch (failure: Exception) {
                        error = failure.message ?: "مهلت تسویه معتبر نیست."
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun InventoryEditorDialog(
    existing: InventoryItemRecord?,
    suppliers: List<SupplierRecord>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (InventoryItemDraft) -> Unit,
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var unit by remember(existing?.id) { mutableStateOf(existing?.unit.orEmpty()) }
    var supplierId by remember(existing?.id) { mutableStateOf(existing?.supplierId) }
    var alertEnabled by remember(existing?.id) { mutableStateOf(existing?.alertEnabled ?: true) }
    var threshold by remember(existing?.id) {
        mutableStateOf(existing?.let { formatQuantity(it.alertThresholdMicros) } ?: "5")
    }
    var error by remember(existing?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "کالای جدید" else "ویرایش کالا") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(name, { name = it }, label = { Text("نام کالا") })
                OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") })
                OutlinedTextField(unit, { unit = it }, label = { Text("واحد؛ مثل کیلو، عدد یا بسته") })
                SelectionField(
                    label = "تأمین‌کننده پیش‌فرض (اختیاری)",
                    selectedText = suppliers.firstOrNull { it.id == supplierId }?.name,
                    options = listOf(0L to "بدون تأمین‌کننده") + suppliers.map { it.id to it.name },
                    onSelected = { supplierId = it.takeIf { selectedId -> selectedId != 0L } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = alertEnabled, onCheckedChange = { alertEnabled = it })
                    Text("هشدار کمبود موجودی")
                }
                if (alertEnabled) {
                    OutlinedTextField(
                        threshold,
                        { threshold = it },
                        label = { Text("حد هشدار") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    try {
                        onSave(
                            InventoryItemDraft(
                                name = name,
                                category = category,
                                unit = unit,
                                alertEnabled = alertEnabled,
                                alertThresholdMicros = if (alertEnabled) {
                                    parseQuantity(threshold).value
                                } else {
                                    0L
                                },
                                supplierId = supplierId,
                            ),
                        )
                    } catch (failure: Exception) {
                        error = failure.message ?: "اطلاعات کالا معتبر نیست."
                    }
                },
            ) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PaymentMethodField(
    selected: PurchasePaymentMethod,
    onSelected: (PurchasePaymentMethod) -> Unit,
) {
    val labels = listOf(
        PurchasePaymentMethod.PAYABLE to "نسیه",
        PurchasePaymentMethod.CASH to "نقدی",
        PurchasePaymentMethod.CARD to "کارتخوان",
        PurchasePaymentMethod.TRANSFER to "حواله",
    )
    SelectionField(
        label = "روش پرداخت",
        selectedText = labels.first { it.first == selected }.second,
        options = labels.mapIndexed { index, value -> index.toLong() to value.second },
        onSelected = { index -> onSelected(labels[index.toInt()].first) },
    )
}

@Composable
internal fun SelectionField(
    label: String,
    selectedText: String?,
    options: List<Pair<Long, String>>,
    onSelected: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(selectedText ?: "انتخاب کنید")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        onClick = {
                            expanded = false
                            onSelected(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun PersianDateField(
    label: String,
    epochDay: Long,
    onSelected: (Long) -> Unit,
) {
    var dialogOpen by remember { mutableStateOf(false) }
    val value = epochDayToPersian(epochDay)
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { dialogOpen = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(value.display())
        }
    }
    if (dialogOpen) {
        PersianDatePickerDialog(
            initial = value,
            onDismiss = { dialogOpen = false },
            onConfirm = {
                dialogOpen = false
                onSelected(it.toEpochDay())
            },
        )
    }
}

@Composable
private fun PersianDatePickerDialog(
    initial: PersianDate,
    onDismiss: () -> Unit,
    onConfirm: (PersianDate) -> Unit,
) {
    var year by remember { mutableIntStateOf(initial.year) }
    var month by remember { mutableIntStateOf(initial.month) }
    var day by remember { mutableIntStateOf(initial.day) }
    var yearExpanded by remember { mutableStateOf(false) }
    var monthExpanded by remember { mutableStateOf(false) }
    val monthNames = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )
    val maxDay = daysInPersianMonth(year, month)
    val effectiveDay = day.coerceAtMost(maxDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { yearExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(year.toString()) }
                        DropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false },
                        ) {
                            (1300..1500).forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.toString()) },
                                    onClick = {
                                        year = option
                                        yearExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { monthExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(monthNames[month - 1]) }
                        DropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false },
                        ) {
                            monthNames.forEachIndexed { index, name ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        month = index + 1
                                        monthExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items((1..maxDay).toList()) { option ->
                        if (option == effectiveDay) {
                            Button(onClick = { day = option }) { Text(option.toString()) }
                        } else {
                            TextButton(onClick = { day = option }) { Text(option.toString()) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(PersianDate(year, month, effectiveDay)) }) {
                Text("تأیید")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun OperationsScaffold(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    message: String?,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenHeader(
                title = title,
                actionLabel = actionLabel,
                onAction = onAction,
                onBack = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            message?.let { MessageCard(it) }
            content(androidx.compose.foundation.layout.PaddingValues(bottom = padding.calculateBottomPadding()))
        }
    }
}

@Composable
internal fun ScreenHeader(
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
    onBack: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("بازگشت") }
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            if (actionLabel != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun MessageCard(message: String, isError: Boolean = false) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Text(message, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun EmptyState(
    text: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text)
    }
}

@Composable
private fun ConfirmDeactivateDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { Button(onClick = onConfirm) { Text("تأیید") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
