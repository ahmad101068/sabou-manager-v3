package ir.sabou.inventory.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import ir.sabou.inventory.data.security.DatabaseKeyProvider
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AccountEntity::class,
        JournalEntryEntity::class,
        JournalLineEntity::class,
        SupplierEntity::class,
        InventoryItemEntity::class,
        PurchaseEntity::class,
        PurchaseLineEntity::class,
        StockMovementEntity::class,
        EmployeeEntity::class,
        AttendanceEntity::class,
        LeaveEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun supplierDao(): SupplierDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun accountingDao(): AccountingDao
    abstract fun personnelDao(): PersonnelDao

    companion object {
        fun create(context: Context, keyProvider: DatabaseKeyProvider): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(keyProvider.getOrCreatePassphrase(), null, true)
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sabou_manager_v3.db",
            )
                .openHelperFactory(factory)
                .addCallback(AccountSeedCallback)
                .build()
        }
    }
}

private object AccountSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedMissingAccounts(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seedMissingAccounts(db)
    }

    private fun seedMissingAccounts(db: SupportSQLiteDatabase) {
        val accounts = listOf(
            arrayOf("1101", "صندوق", "ASSET"),
            arrayOf("1102", "بانک و کارت‌خوان", "ASSET"),
            arrayOf("1103", "تنخواه‌گردان", "ASSET"),
            arrayOf("1201", "حساب‌های دریافتنی", "ASSET"),
            arrayOf("1202", "اسناد دریافتنی", "ASSET"),
            arrayOf("1301", "موجودی مواد اولیه", "ASSET"),
            arrayOf("1302", "موجودی ملزومات و بسته‌بندی", "ASSET"),
            arrayOf("1401", "مساعده پرسنل", "ASSET"),
            arrayOf("1501", "دارایی‌های ثابت", "ASSET"),
            arrayOf("2101", "حساب‌های پرداختنی", "LIABILITY"),
            arrayOf("2102", "حقوق پرداختنی", "LIABILITY"),
            arrayOf("2103", "مالیات و عوارض پرداختنی", "LIABILITY"),
            arrayOf("2104", "بیمه پرداختنی", "LIABILITY"),
            arrayOf("3101", "سرمایه", "EQUITY"),
            arrayOf("4101", "فروش غذا و نوشیدنی", "REVENUE"),
            arrayOf("4102", "سایر درآمدها", "REVENUE"),
            arrayOf("4103", "درآمد ارسال و خدمات", "REVENUE"),
            arrayOf("5101", "بهای تمام‌شده فروش", "EXPENSE"),
            arrayOf("5102", "ملزومات و بسته‌بندی مصرف‌شده", "EXPENSE"),
            arrayOf("6101", "حقوق و دستمزد", "EXPENSE"),
            arrayOf("6102", "اجاره", "EXPENSE"),
            arrayOf("6103", "آب، برق و گاز", "EXPENSE"),
            arrayOf("6104", "ضایعات مواد اولیه", "EXPENSE"),
            arrayOf("6105", "سایر هزینه‌های جاری", "EXPENSE"),
            arrayOf("6106", "بیمه سهم کارفرما", "EXPENSE"),
            arrayOf("6107", "تعمیر و نگهداری", "EXPENSE"),
            arrayOf("6108", "تبلیغات", "EXPENSE"),
            arrayOf("6109", "هزینه ارسال", "EXPENSE"),
            arrayOf("6110", "استهلاک", "EXPENSE"),
        )
        accounts.forEach { account ->
            db.execSQL(
                """
                INSERT OR IGNORE INTO accounts(code, name, type, isSystem, isActive)
                VALUES (?, ?, ?, 1, 1)
                """.trimIndent(),
                account,
            )
        }
    }
}
