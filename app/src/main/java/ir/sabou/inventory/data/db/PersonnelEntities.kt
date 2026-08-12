package ir.sabou.inventory.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "employees",
    indices = [Index(value = ["nationalId"], unique = true)],
)
data class EmployeeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val jobTitle: String,
    val phone: String = "",
    val nationalId: String? = null,
    val birthEpochDay: Long? = null,
    val bankCard: String? = null,
    val monthlySalaryRial: Long,
    val leaveBalanceMicros: Long,
    val status: String = "ACTIVE",
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "attendance",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["employeeId", "workEpochDay"], unique = true),
        Index("workEpochDay"),
    ],
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val workEpochDay: Long,
    val status: String,
    val checkInMinute: Int?,
    val checkOutMinute: Int?,
    val lateMinutes: Int,
    val overtimeMinutes: Int,
    val notes: String,
)

@Entity(
    tableName = "leaves",
    foreignKeys = [
        ForeignKey(
            entity = EmployeeEntity::class,
            parentColumns = ["id"],
            childColumns = ["employeeId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("employeeId"), Index("startEpochDay"), Index("endEpochDay")],
)
data class LeaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val employeeId: Long,
    val startEpochDay: Long,
    val endEpochDay: Long,
    val daysMicros: Long,
    val leaveType: String,
    val status: String,
    val notes: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

