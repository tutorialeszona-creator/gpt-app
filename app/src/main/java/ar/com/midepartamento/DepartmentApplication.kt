package ar.com.midepartamento

import android.app.Application
import androidx.room.Room
import ar.com.midepartamento.data.FinanceDatabase
import ar.com.midepartamento.data.MIGRATION_1_2

/** One database per application process, owned by the actual application context. */
class DepartmentApplication : Application() {
    val database: FinanceDatabase by lazy {
        Room.databaseBuilder(this, FinanceDatabase::class.java, "mi-departamento.db").addMigrations(MIGRATION_1_2).build()
    }
}
