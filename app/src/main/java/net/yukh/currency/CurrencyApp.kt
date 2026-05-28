package net.yukh.currency

import android.app.Application
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

    override fun onCreate() {
        super.onCreate()
        val client = OkHttpClient()
        settingsStore = SettingsStore(this)
        repository = RatesRepository(settingsStore, client)
        DailyUpdateWorker.createChannel(this)
        DailyUpdateWorker.ensureScheduled(this)
    }
}
