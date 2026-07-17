package net.yukh.currency.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import net.yukh.currency.CurrencyApp
import net.yukh.currency.ui.theme.CurrencyTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // язык приложения: подменяем базовый контекст ДО onCreate, чтобы все
    // ресурсы Compose брались в выбранной локали (см. CurrencyApp.localized)
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CurrencyApp.localized(newBase))
    }

    // запрос «открыть сводку» из тапа по уведомлению
    private val openSummary = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Корректный edge-to-edge: системные бары прозрачны, а их иконки
        // (статус-бар и навигация) получают контраст под светлую тему —
        // иначе на Android 15 системная навигация выглядит «пустой белой».
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        openSummary.value = intent?.getBooleanExtra(EXTRA_OPEN_SUMMARY, false) == true
        setContent {
            CurrencyTheme {
                AppScreen(
                    openSummary = openSummary.value,
                    onSummaryConsumed = { openSummary.value = false },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_SUMMARY, false)) openSummary.value = true
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_OPEN_SUMMARY = "open_summary"
    }
}
