package ir.sabou.inventory.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {
    @Query("SELECT * FROM suppliers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE id = :id AND isActive = 1")
    suspend fun activeById(id: Long): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<SupplierEntity>>

    @Insert
    suspend fun insert(entity: SupplierEntity): Long

    @Update
    suspend fun update(entity: SupplierEntity): Int

    @Query(
        """
        UPDATE suppliers
        SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND isActive = 1
        """,
    )
    suspend fun deactivate(id: Long, updatedAtEpochMillis: Long): Int
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory_items WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): InventoryItemEntity?

    @Query("SELECT * FROM inventory_items WHERE id = :id AND isActive = 1")
    suspend fun activeById(id: Long): InventoryItemEntity?

    @Query(
        """
        UPDATE inventory_items
        SET stockMicros = :stockMicros,
            inventoryValueRial = :inventoryValueRial,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :itemId AND isActive = 1
        """,
    )
    suspend fun updateValuation(
        itemId: Long,
        stockMicros: Long,
        inventoryValueRial: Long,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name")
    fun observeActive(): Flow<List<InventoryItemEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(inventoryValueRial), 0)
        FROM inventory_items
        WHERE isActive = 1
        """,
    )
    fun observeInventoryValueRial(): Flow<Long>

    @Insert
    suspend fun insert(entity: InventoryItemEntity): Long

    @Update
    suspend fun update(entity: InventoryItemEntity): Int

    @Query(
        """
        UPDATE inventory_items
        SET isActive = 0, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND isActive = 1
        """,
    )
    suspend fun deactivate(id: Long, updatedAtEpochMillis: Long): Int

    @Query(
        """
        UPDATE inventory_items
        SET supplierId = NULL, updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE supplierId = :supplierId
        """,
    )
    suspend fun clearSupplierReference(supplierId: Long, updatedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE isActive = 1
          AND alertEnabled = 1
          AND stockMicros <= alertThresholdMicros
        ORDER BY stockMicros ASC, name ASC
        """,
    )
    fun observeLowStock(): Flow<List<InventoryItemEntity>>
}

@Dao
interface PurchaseDao {
    @Query("SELECT EXISTS(SELECT 1 FROM purchases WHERE invoiceNo = :invoiceNo)")
    suspend fun invoiceExists(invoiceNo: String): Boolean

    @Insert
    suspend fun insert(entity: PurchaseEntity): Long

    @Insert
    suspend fun insertLines(lines: List<PurchaseLineEntity>)

    @Query("SELECT * FROM purchases WHERE id = :purchaseId LIMIT 1")
    suspend fun byId(purchaseId: Long): PurchaseEntity?

    @Query("SELECT * FROM purchase_lines WHERE purchaseId = :purchaseId ORDER BY id")
    suspend fun linesByPurchase(purchaseId: Long): List<PurchaseLineEntity>

    @Query(
        """
        SELECT
            p.id AS purchaseId,
            p.invoiceNo AS invoiceNo,
            s.name AS supplierName,
            p.purchaseEpochDay AS purchaseEpochDay,
            p.dueEpochDay AS dueEpochDay,
            p.totalRial AS totalRial,
            p.paidRial AS paidRial,
            p.paymentStatus AS paymentStatus,
            p.paymentMethod AS paymentMethod,
            p.reminderEnabled AS reminderEnabled,
            p.reminderEpochDay AS reminderEpochDay
        FROM purchases p
        INNER JOIN suppliers s ON s.id = p.supplierId
        WHERE p.id = :purchaseId
        LIMIT 1
        """,
    )
    fun observeHeader(purchaseId: Long): Flow<PurchaseHeaderRow?>

    @Query(
        """
        SELECT
            pl.itemId AS itemId,
            pl.itemNameSnapshot AS itemName,
            i.unit AS unit,
            pl.quantityMicros AS quantityMicros,
            pl.unitCostRial AS unitCostRial,
            pl.lineTotalRial AS lineTotalRial
        FROM purchase_lines pl
        INNER JOIN inventory_items i ON i.id = pl.itemId
        WHERE pl.purchaseId = :purchaseId
        ORDER BY pl.id
        """,
    )
    fun observeDetailLines(purchaseId: Long): Flow<List<PurchaseLineDetailRow>>

    @Query(
        """
        UPDATE purchases
        SET paidRial = :newPaidRial,
            paymentStatus = :paymentStatus,
            reminderEnabled = :reminderEnabled,
            reminderEpochDay = :reminderEpochDay
        WHERE id = :purchaseId
          AND paidRial = :expectedPaidRial
          AND paymentStatus IN ('UNPAID', 'PARTIAL')
        """,
    )
    suspend fun updateSettlementState(
        purchaseId: Long,
        expectedPaidRial: Long,
        newPaidRial: Long,
        paymentStatus: String,
        reminderEnabled: Boolean,
        reminderEpochDay: Long?,
    ): Int

    @Query(
        """
        UPDATE purchases
        SET paymentStatus = 'REVERSED',
            reminderEnabled = 0,
            reminderEpochDay = NULL
        WHERE id = :purchaseId
          AND paymentStatus != 'REVERSED'
        """,
    )
    suspend fun markReversed(purchaseId: Long): Int

    @Query("SELECT * FROM purchases ORDER BY purchaseEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<PurchaseEntity>>

    @Query(
        """
        SELECT
            p.id AS purchaseId,
            p.invoiceNo AS invoiceNo,
            s.name AS supplierName,
            p.purchaseEpochDay AS purchaseEpochDay,
            p.dueEpochDay AS dueEpochDay,
            p.totalRial AS totalRial,
            p.paidRial AS paidRial,
            p.paymentStatus AS paymentStatus,
            p.paymentMethod AS paymentMethod,
            p.reminderEnabled AS reminderEnabled,
            p.reminderEpochDay AS reminderEpochDay
        FROM purchases p
        INNER JOIN suppliers s ON s.id = p.supplierId
        WHERE :query = ''
           OR p.invoiceNo LIKE '%' || :query || '%'
           OR s.name LIKE '%' || :query || '%'
        ORDER BY p.purchaseEpochDay DESC, p.id DESC
        """,
    )
    fun observeSearch(query: String): Flow<List<PurchaseListRow>>

    @Query(
        """
        SELECT COALESCE(SUM(totalRial - paidRial), 0)
        FROM purchases
        WHERE paymentStatus IN ('UNPAID', 'PARTIAL')
        """,
    )
    fun observePayablesRial(): Flow<Long>
}

data class PurchaseListRow(
    val purchaseId: Long,
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
)

data class PurchaseHeaderRow(
    val purchaseId: Long,
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
)

data class PurchaseLineDetailRow(
    val itemId: Long,
    val itemName: String,
    val unit: String,
    val quantityMicros: Long,
    val unitCostRial: Long,
    val lineTotalRial: Long,
)

@Dao
interface StockMovementDao {
    @Insert
    suspend fun insert(entity: StockMovementEntity): Long
}

@Dao
interface AccountingDao {
    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1")
    suspend fun accountByCode(code: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(entity: AccountEntity)

    @Update
    suspend fun updateAccount(entity: AccountEntity): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM journal_lines
        WHERE accountCode = :accountCode
        """,
    )
    suspend fun accountUsageCount(accountCode: String): Long

    @Query(
        """
        SELECT COALESCE(SUM(jl.debitRial - jl.creditRial), 0)
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode
          AND je.status = 'POSTED'
        """,
    )
    suspend fun accountBalanceRial(accountCode: String): Long

    @Query(
        """
        SELECT
            a.code AS code,
            a.name AS name,
            a.type AS type,
            a.isSystem AS isSystem,
            COALESCE(SUM(
                CASE WHEN je.status = 'POSTED' THEN jl.debitRial ELSE 0 END
            ), 0) AS debitTurnoverRial,
            COALESCE(SUM(
                CASE WHEN je.status = 'POSTED' THEN jl.creditRial ELSE 0 END
            ), 0) AS creditTurnoverRial
        FROM accounts a
        LEFT JOIN journal_lines jl ON jl.accountCode = a.code
        LEFT JOIN journal_entries je ON je.id = jl.entryId
        WHERE a.isActive = 1
        GROUP BY a.code, a.name, a.type, a.isSystem
        ORDER BY a.code
        """,
    )
    fun observeAccountBalances(): Flow<List<AccountBalanceRow>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entity: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<JournalLineEntity>)

    @Query(
        """
        UPDATE journal_entries
        SET entryNo = :entryNo, sourceId = :sourceId
        WHERE id = :entryId
        """,
    )
    suspend fun finalizeEntryIdentity(
        entryId: Long,
        entryNo: String,
        sourceId: Long,
    ): Int

    @Query("SELECT * FROM journal_entries WHERE id = :entryId LIMIT 1")
    suspend fun entryById(entryId: Long): JournalEntryEntity?

    @Query(
        """
        SELECT *
        FROM journal_lines
        WHERE entryId = :entryId
        ORDER BY id
        """,
    )
    suspend fun linesByEntry(entryId: Long): List<JournalLineEntity>

    @Query(
        """
        SELECT * FROM journal_entries
        WHERE sourceType = :sourceType
          AND sourceId = :sourceId
          AND status = 'POSTED'
        ORDER BY id
        LIMIT 1
        """,
    )
    suspend fun entryBySource(sourceType: String, sourceId: Long): JournalEntryEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM journal_entries
            WHERE sourceType = 'PURCHASE_REVERSAL'
              AND sourceId = :purchaseId
              AND status = 'POSTED'
        )
        """,
    )
    suspend fun hasPurchaseReversal(purchaseId: Long): Boolean

    @Query(
        """
        SELECT
            je.id AS journalEntryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS settlementEpochDay,
            debitLine.debitRial AS amountRial,
            CASE creditLine.accountCode
                WHEN '1101' THEN 'نقدی'
                ELSE CASE
                    WHEN je.description LIKE '%حواله%' THEN 'حواله'
                    ELSE 'کارتخوان'
                END
            END AS paymentMethod,
            creditLine.memo AS referenceNo,
            debitLine.memo AS notes
        FROM journal_entries je
        INNER JOIN journal_lines debitLine
            ON debitLine.entryId = je.id
           AND debitLine.accountCode = '2101'
           AND debitLine.debitRial > 0
        INNER JOIN journal_lines creditLine
            ON creditLine.entryId = je.id
           AND creditLine.creditRial > 0
        WHERE je.sourceType = 'PURCHASE_SETTLEMENT'
          AND je.sourceId = :purchaseId
          AND je.status = 'POSTED'
        ORDER BY je.entryEpochDay DESC, je.id DESC
        """,
    )
    fun observePurchaseSettlements(purchaseId: Long): Flow<List<PurchaseSettlementRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM accounts WHERE code = :code AND isActive = 1)")
    suspend fun activeAccountExists(code: String): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM journal_entries
            WHERE sourceType = 'REVERSAL'
              AND sourceId = :entryId
              AND status = 'POSTED'
        )
        """,
    )
    suspend fun hasPostedReversal(entryId: Long): Boolean

    @Query(
        """
        SELECT
            je.id AS id,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS description,
            je.sourceType AS sourceType,
            COALESCE(SUM(jl.debitRial), 0) AS totalDebitRial,
            COALESCE(SUM(jl.creditRial), 0) AS totalCreditRial,
            EXISTS(
                SELECT 1
                FROM journal_entries reversal
                WHERE reversal.sourceType = 'REVERSAL'
                  AND reversal.sourceId = je.id
                  AND reversal.status = 'POSTED'
            ) AS isReversed
        FROM journal_entries je
        LEFT JOIN journal_lines jl ON jl.entryId = je.id
        WHERE :query = ''
           OR je.entryNo LIKE '%' || :query || '%'
           OR je.description LIKE '%' || :query || '%'
           OR EXISTS(
                SELECT 1
                FROM journal_lines searchedLine
                INNER JOIN accounts searchedAccount
                    ON searchedAccount.code = searchedLine.accountCode
                WHERE searchedLine.entryId = je.id
                  AND (
                    searchedLine.accountCode LIKE '%' || :query || '%'
                    OR searchedAccount.name LIKE '%' || :query || '%'
                  )
           )
        GROUP BY
            je.id,
            je.entryNo,
            je.entryEpochDay,
            je.description,
            je.sourceType
        ORDER BY je.entryEpochDay DESC, je.id DESC
        """,
    )
    fun observeJournals(query: String): Flow<List<JournalListRow>>

    @Query(
        """
        SELECT
            je.id AS entryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS entryDescription,
            je.sourceType AS sourceType,
            je.sourceId AS sourceId,
            jl.id AS lineId,
            jl.accountCode AS accountCode,
            a.name AS accountName,
            jl.debitRial AS debitRial,
            jl.creditRial AS creditRial,
            jl.memo AS memo,
            EXISTS(
                SELECT 1
                FROM journal_entries reversal
                WHERE reversal.sourceType = 'REVERSAL'
                  AND reversal.sourceId = je.id
                  AND reversal.status = 'POSTED'
            ) AS isReversed
        FROM journal_entries je
        INNER JOIN journal_lines jl ON jl.entryId = je.id
        INNER JOIN accounts a ON a.code = jl.accountCode
        WHERE je.id = :entryId
        ORDER BY jl.id
        """,
    )
    fun observeJournalDetails(entryId: Long): Flow<List<JournalDetailRow>>

    @Query(
        """
        SELECT
            jl.id AS lineId,
            je.id AS entryId,
            je.entryNo AS entryNo,
            je.entryEpochDay AS entryEpochDay,
            je.description AS description,
            jl.debitRial AS debitRial,
            jl.creditRial AS creditRial
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode
          AND je.status = 'POSTED'
        ORDER BY je.entryEpochDay, je.id, jl.id
        """,
    )
    fun observeLedger(accountCode: String): Flow<List<AccountLedgerRow>>

    @Query(
        """
        SELECT COALESCE(SUM(jl.debitRial - jl.creditRial), 0)
        FROM journal_lines jl
        INNER JOIN journal_entries je ON je.id = jl.entryId
        WHERE jl.accountCode = :accountCode AND je.status = 'POSTED'
        """,
    )
    fun observeBalanceRial(accountCode: String): Flow<Long>
}

data class AccountBalanceRow(
    val code: String,
    val name: String,
    val type: String,
    val isSystem: Boolean,
    val debitTurnoverRial: Long,
    val creditTurnoverRial: Long,
)

data class JournalListRow(
    val id: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val totalDebitRial: Long,
    val totalCreditRial: Long,
    val isReversed: Boolean,
)

data class JournalDetailRow(
    val entryId: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val entryDescription: String,
    val sourceType: String,
    val sourceId: Long,
    val lineId: Long,
    val accountCode: String,
    val accountName: String,
    val debitRial: Long,
    val creditRial: Long,
    val memo: String,
    val isReversed: Boolean,
)

data class AccountLedgerRow(
    val lineId: Long,
    val entryId: Long,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val debitRial: Long,
    val creditRial: Long,
)

data class PurchaseSettlementRow(
    val journalEntryId: Long,
    val entryNo: String,
    val settlementEpochDay: Long,
    val amountRial: Long,
    val paymentMethod: String,
    val referenceNo: String,
    val notes: String,
)

@Dao
interface PersonnelDao {
    @Insert
    suspend fun insertEmployee(entity: EmployeeEntity): Long

    @Query("SELECT * FROM employees WHERE status = 'ACTIVE' ORDER BY name")
    fun observeActiveEmployees(): Flow<List<EmployeeEntity>>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM attendance
            WHERE employeeId = :employeeId
              AND workEpochDay BETWEEN :startEpochDay AND :endEpochDay
              AND status != 'LEAVE'
        )
        """,
    )
    suspend fun hasAttendanceConflict(
        employeeId: Long,
        startEpochDay: Long,
        endEpochDay: Long,
    ): Boolean

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM leaves
            WHERE employeeId = :employeeId
              AND status = 'APPROVED'
              AND startEpochDay <= :workEpochDay
              AND endEpochDay >= :workEpochDay
        )
        """,
    )
    suspend fun hasApprovedLeave(employeeId: Long, workEpochDay: Long): Boolean
}
