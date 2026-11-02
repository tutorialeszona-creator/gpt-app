package ar.com.midepartamento.data

import android.content.Context
import ar.com.midepartamento.DepartmentApplication
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey val id: String,
    val name: String, val cents: Long, val estimated: Boolean,
    val frequency: Int = 1, val startMonth: String, val active: Boolean = true
)
@Entity(tableName = "bills", indices = [Index(value = ["recurringId", "month"], unique = true)])
data class Bill(
    @PrimaryKey val id: String, val recurringId: String? = null,
    val month: String, val name: String, val planned: Long, val actual: Long? = null,
    val estimated: Boolean = false, val dueDate: String? = null,
    val paidDate: String? = null, val notes: String = "", val attachment: String? = null
) { @get:Ignore val amount: Long get() = actual ?: planned }
@Entity(tableName = "incomes")
data class Income(@PrimaryKey val id: String, val month: String, val name: String, val cents: Long, val currency: String)
@Entity(tableName = "expenses")
data class Expense(@PrimaryKey val id: String, val date: String, val category: String, val description: String, val cents: Long,
    @ColumnInfo(defaultValue = "''") val merchant: String = "",
    @ColumnInfo(defaultValue = "'[]'") val items: List<ar.com.midepartamento.domain.PurchaseItem> = emptyList(),
    @ColumnInfo(defaultValue = "NULL") val receipt: String? = null
)
@Entity(tableName = "months")
data class GeneratedMonth(@PrimaryKey val month: String)
@Entity(tableName = "settings")
data class Settings(@PrimaryKey val id: Int = 1, val rateCents: Long = 0, val salaryCents: Long = 100_000)
@Entity(tableName = "payment_events")
data class PaymentEvent(@PrimaryKey val id: String, val billId: String, val timestamp: String, val action: String, val cents: Long, val paidDate: String?)

@Dao
interface FinanceDao {
    @Query("SELECT * FROM recurring ORDER BY name") fun recurring(): Flow<List<Recurring>>
    @Query("SELECT * FROM bills ORDER BY month DESC, name") fun bills(): Flow<List<Bill>>
    @Query("SELECT * FROM incomes ORDER BY month DESC, name") fun incomes(): Flow<List<Income>>
    @Query("SELECT * FROM expenses ORDER BY date DESC") fun expenses(): Flow<List<Expense>>
    @Query("SELECT * FROM settings WHERE id=1") fun settings(): Flow<Settings?>
    @Query("SELECT * FROM payment_events ORDER BY timestamp DESC") fun events(): Flow<List<PaymentEvent>>
    @Query("SELECT * FROM recurring") suspend fun allRecurring(): List<Recurring>
    @Query("SELECT * FROM months") suspend fun allMonths(): List<GeneratedMonth>
    @Query("SELECT * FROM bills") suspend fun allBills(): List<Bill>
    @Query("SELECT * FROM incomes") suspend fun allIncomes(): List<Income>
    @Query("SELECT * FROM expenses") suspend fun allExpenses(): List<Expense>
    @Query("SELECT * FROM payment_events") suspend fun allEvents(): List<PaymentEvent>
    @Query("SELECT * FROM settings WHERE id=1") suspend fun getSettings(): Settings?
    @Query("SELECT COUNT(*) FROM months WHERE month=:month") suspend fun monthExists(month: String): Int
    @Insert suspend fun month(value: GeneratedMonth)
    @Upsert suspend fun recurring(value: Recurring)
    @Upsert suspend fun bill(value: Bill)
    @Upsert suspend fun income(value: Income)
    @Upsert suspend fun expense(value: Expense)
    @Upsert suspend fun settings(value: Settings)
    @Insert suspend fun event(value: PaymentEvent)
    @Query("DELETE FROM bills WHERE id=:id") suspend fun deleteBill(id: String)
    @Query("DELETE FROM incomes WHERE id=:id") suspend fun deleteIncome(id: String)
    @Query("DELETE FROM expenses WHERE id=:id") suspend fun deleteExpense(id: String)
    @Query("DELETE FROM payment_events WHERE billId=:id") suspend fun deleteEvents(id: String)
}
@TypeConverters(PurchaseConverters::class)
@Database(entities = [Recurring::class, Bill::class, Income::class, Expense::class, GeneratedMonth::class, Settings::class, PaymentEvent::class], version = 2, exportSchema = true)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun dao(): FinanceDao
    companion object {
        fun get(context: Context): FinanceDatabase =
            (context.applicationContext as DepartmentApplication).database
    }
}
