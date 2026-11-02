package ar.com.midepartamento

import android.os.Bundle
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ar.com.midepartamento.ui.DepartmentApp

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("es-AR")
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DepartmentApp(viewModel()) }
    }
}
