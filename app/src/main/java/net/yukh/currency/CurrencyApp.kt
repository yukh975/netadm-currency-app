package net.yukh.currency

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.yukh.currency.data.RatesRepository
import net.yukh.currency.data.SettingsStore
import net.yukh.currency.work.DailyUpdateWorker
import okhttp3.OkHttpClient
import java.util.Locale

/** Простейший контейнер зависимостей (ручной DI) + язык приложения. */
class CurrencyApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var repository: RatesRepository
        private set
    lateinit var httpClient: OkHttpClient
        private set

    /** Системная локаль на старте процесса — чтобы вернуться к «как в системе». */
    private lateinit var systemLocale: Locale

    /** Язык приложения: "" = как в системе, иначе "ru"/"en".
     *  SharedPreferences (не DataStore) — значение нужно СИНХРОННО в
     *  attachBaseContext активити, до любых корутин. */
    var langPref: String
        get() = getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE).getString(KEY_LANG, "") ?: ""
        set(v) {
            getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE).edit().putString(KEY_LANG, v).apply()
            applyDefaultLocale()
        }

    /** Контекст с выбранным языком — для строк вне активити (ViewModel,
     *  воркер уведомлений, Converter). При «как в системе» — сам Application. */
    fun l10n(): Context = localized(this)

    private fun applyDefaultLocale() {
        // Locale.getDefault() используют Currencies (выбор ru/en названий)
        // и форматирование — держим его в согласии с выбором пользователя.
        val lang = langPref
        Locale.setDefault(if (lang.isEmpty()) systemLocale else Locale(lang))
    }

    override fun onCreate() {
        super.onCreate()
        systemLocale = Locale.getDefault()
        applyDefaultLocale()
        val client = OkHttpClient()
        httpClient = client
        settingsStore = SettingsStore(this)
        repository = RatesRepository(settingsStore, client)
        DailyUpdateWorker.createChannel(l10n())
        // планируем уведомление по сохранённому пользователем времени
        CoroutineScope(Dispatchers.Default).launch {
            val s = settingsStore.current()
            DailyUpdateWorker.ensureScheduled(this@CurrencyApp, s.notifyHour, s.notifyMinute)
        }
    }

    companion object {
        private const val LOCALE_PREFS = "locale"
        private const val KEY_LANG = "lang"

        /** Обернуть [base] в контекст с выбранным языком (или вернуть как есть,
         *  если выбран системный). Используется и в attachBaseContext активити. */
        fun localized(base: Context): Context {
            val lang = base.getSharedPreferences(LOCALE_PREFS, MODE_PRIVATE)
                .getString(KEY_LANG, "") ?: ""
            if (lang.isEmpty()) return base
            val cfg = Configuration(base.resources.configuration)
            cfg.setLocale(Locale(lang))
            return base.createConfigurationContext(cfg)
        }
    }
}
