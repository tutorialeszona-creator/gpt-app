package ar.com.midepartamento

import android.app.Application
import androidx.room.Room
import ar.com.midepartamento.data.FinanceDatabase

/** One database per application process, owned by the actual application context. */
class DepartmentApplication : Application() {
    val database: FinanceDatabase by lazy {
        Room.databaseBuilder(this, FinanceDatabase::class.java, "mi-departamento.db").build()
    }
}
