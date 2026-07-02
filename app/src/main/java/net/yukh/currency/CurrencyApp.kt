package net.yukh.currency

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.yukh.currency.data.RatesRepository
import net.yukh.currency.data.SettingsStore
import net.yukh.currency.work.DailyUpdateWorker
import okhttp3.OkHttpClient

/** Простейший контейнер зависимостей (ручной DI). */
class CurrencyApp : Application() {

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var repository: RatesRepository
        private set
    lateinit var httpClient: OkHttpClient
        private set

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient()
        httpClient = client
        settingsStore = SettingsStore(this)
        repository = RatesRepository(settingsStore, client)
        DailyUpdateWorker.createChannel(this)
        // планируем уведомление по сохранённому пользователем времени
        CoroutineScope(Dispatchers.Default).launch {
            val s = settingsStore.current()
            DailyUpdateWorker.ensureScheduled(this@CurrencyApp, s.notifyHour, s.notifyMinute)
        }
    }
}
