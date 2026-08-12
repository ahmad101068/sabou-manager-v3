package ir.sabou.inventory.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val code: String,
    val name: String,
    val type: String,
    val isSystem: Boolean,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["entryNo"], unique = true),
        Index(value = ["entryEpochDay"]),
        Index(value = ["sourceType", "sourceId"]),
    ],
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryNo: String,
    val entryEpochDay: Long,
    val description: String,
    val sourceType: String,
    val sourceId: Long,
    val status: String = "POSTED",
    val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "journal_lines",
    foreignKeys = [
        ForeignKey(
            entity = JournalEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["code"],
            childColumns = ["accountCode"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("entryId"), Index("accountCode")],
)
data class JournalLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val accountCode: String,
    val debitRial: Long,
    val creditRial: Long,
    val memo: String = "",
)

