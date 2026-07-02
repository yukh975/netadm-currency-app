package net.yukh.currency.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val source: String = "cbr",
    val base: String = "RUB",
    val smartUnits: Boolean = true,
    val notify: Boolean = false,
    val notifyHour: Int = 17,
    val notifyMinute: Int = 0,
    val favorites: List<String> = listOf("RUB"),
)

/** Хранилище настроек и избранного (DataStore Preferences) + кэш курсов. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val source = stringPreferencesKey("source")
        val base = stringPreferencesKey("base")
        val smart = booleanPreferencesKey("smart_units")
        val notify = booleanPreferencesKey("notify")
        val notifyHour = intPreferencesKey("notify_hour")
        val notifyMinute = intPreferencesKey("notify_minute")
        val favorites = stringPreferencesKey("favorites")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            source = p[Keys.source] ?: "cbr",
            base = p[Keys.base] ?: "RUB",
            smartUnits = p[Keys.smart] ?: true,
            notify = p[Keys.notify] ?: false,
            notifyHour = p[Keys.notifyHour] ?: 17,
            notifyMinute = p[Keys.notifyMinute] ?: 0,
            favorites = p[Keys.favorites]?.split(",")?.filter { it.isNotBlank() } ?: listOf("RUB"),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSource(v: String) = context.dataStore.edit { it[Keys.source] = v }
    suspend fun setBase(v: String) = context.dataStore.edit { it[Keys.base] = v }
    suspend fun setSmart(v: Boolean) = context.dataStore.edit { it[Keys.smart] = v }
    suspend fun setNotify(v: Boolean) = context.dataStore.edit { it[Keys.notify] = v }
    suspend fun setNotifyTime(hour: Int, minute: Int) = context.dataStore.edit {
        it[Keys.notifyHour] = hour
        it[Keys.notifyMinute] = minute
    }
    suspend fun setFavorites(v: List<String>) =
        context.dataStore.edit { it[Keys.favorites] = v.joinToString(",") }

    suspend fun loadCache(source: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("cache_$source")]

    suspend fun saveCache(source: String, json: String) =
        context.dataStore.edit { it[stringPreferencesKey("cache_$source")] = json }

    /** Метка курса, о котором в последний раз слали уведомление (по источнику).
     *  Храним sourceDate: уведомляем только когда курс реально сменился. */
    suspend fun lastNotified(source: String): String =
        context.dataStore.data.first()[stringPreferencesKey("last_notified_$source")] ?: ""

    suspend fun setLastNotified(source: String, marker: String) =
        context.dataStore.edit { it[stringPreferencesKey("last_notified_$source")] = marker }
}
