package net.yukh.currency.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.yukh.currency.CurrencyApp
import net.yukh.currency.data.AppSettings
import net.yukh.currency.data.ConversionRow
import net.yukh.currency.data.Converter
import net.yukh.currency.data.Currencies
import net.yukh.currency.work.DailyUpdateWorker

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as CurrencyApp
    private val repo = appCtx.repository
    private val store = appCtx.settingsStore

    val settings: StateFlow<AppSettings> =
        store.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    var input by mutableStateOf("")
        private set
    var rows by mutableStateOf<List<ConversionRow>>(emptyList())
        private set
    var freshness by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var searchResults by mutableStateOf<List<String>>(emptyList())
        private set
    var lastUpdate by mutableStateOf("Обновлено: —")
        private set
    var refreshing by mutableStateOf(false)
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set
    var summary by mutableStateOf<List<String>>(emptyList())
        private set
    var summaryFreshness by mutableStateOf("")
        private set
    var summaryLoading by mutableStateOf(false)
        private set
    var summaryError by mutableStateOf<String?>(null)
        private set
    // Исходная валюта конвертора: выбирается временно и НЕ сохраняется в настройки.
    // null = использовать «мою валюту» (base) из настроек.
    var pickedSource by mutableStateOf<String?>(null)
        private set

    fun onInputChange(v: String) { input = v }

    /** Выбрать исходную валюту в конверторе (временно, без изменения настроек). */
    fun pickSource(code: String) { pickedSource = code }

    /** Сводка по запросу (кнопка): относительно выбранной исходной валюты
     *  конвертора (по умолчанию — «моя валюта»). Смена исходной меняет и сводку. */
    fun showSummary() = viewModelScope.launch { loadSummary(pickedSource) }

    /** Сводка строго относительно «моей валюты» — для открытия из уведомления
     *  (само уведомление-сводка тоже считается от «моей валюты»). */
    fun showSummaryForBase() = viewModelScope.launch { loadSummary(null) }

    private suspend fun loadSummary(picked: String?) {
        val s = store.current()
        // транзиентный выбор валиден, только пока он в избранном
        val src = picked?.takeIf { it in s.favorites } ?: s.base
        if (s.favorites.all { it == src }) {
            summaryError = "Добавьте валюты в избранное"
            summary = emptyList()
            summaryFreshness = ""
            return
        }
        val needed = (s.favorites + s.base + src).toSet()
        summaryLoading = true
        summaryError = null
        try {
            // принудительно обновляем из источника — сводка всегда свежая
            val table = repo.getTable(s.source, needed, force = true)
            summary = Converter.summary(table, src, s.favorites, s.smartUnits)
            summaryFreshness = Converter.freshness(table)
        } catch (e: Exception) {
            summaryError = "Источник недоступен. Попробуйте позже."
            summary = emptyList()
            summaryFreshness = ""
        } finally {
            summaryLoading = false
        }
    }

    /** Показать время последнего обновления для текущего источника (без сети). */
    fun refreshLastUpdate() = viewModelScope.launch {
        val s = store.current()
        val table = repo.cachedTable(s.source)
        lastUpdate = if (table != null) "Обновлено: " + Converter.lastUpdated(table) else "Обновлено: —"
    }

    /** Принудительно обновить курсы текущего источника. */
    fun refreshRates() = viewModelScope.launch {
        val s = store.current()
        val needed = (s.favorites + s.base).toSet()
        refreshing = true
        refreshError = null
        try {
            val table = repo.getTable(s.source, needed, force = true)
            lastUpdate = "Обновлено: " + Converter.lastUpdated(table)
        } catch (e: Exception) {
            refreshError = "Не удалось обновить курсы. Проверьте соединение."
        } finally {
            refreshing = false
        }
    }

    fun convert() {
        error = null
        val parsed = parseInput(input)
        if (parsed == null) {
            error = "Введите сумму, например 1000 или USD 100"
            return
        }
        val (amount, code, bad) = parsed
        if (bad != null) {
            error = "Неизвестная валюта: $bad"
            return
        }
        if (amount == null) {
            error = "Не понял сумму"
            return
        }
        viewModelScope.launch {
            val s = store.current()
            val from = code ?: (pickedSource?.takeIf { it in s.favorites } ?: s.base)
            val needed = (s.favorites + s.base + from).toSet()
            loading = true
            try {
                val table = repo.getTable(s.source, needed)
                rows = Converter.rows(table, from, s.base, s.favorites, amount, s.smartUnits)
                freshness = Converter.freshness(table)
            } catch (e: Exception) {
                error = "Источник недоступен. Попробуйте позже."
                rows = emptyList()
                freshness = ""
            } finally {
                loading = false
            }
        }
    }

    /** Задать «мою валюту» (постоянная домашняя валюта; меняется только здесь). */
    fun setBase(code: String) = viewModelScope.launch {
        store.setBase(code)
        val s = store.current()
        if (code !in s.favorites) store.setFavorites(s.favorites + code)
        pickedSource = null  // конвертер снова отталкивается от «моей валюты»
    }

    fun setSource(code: String) = viewModelScope.launch { store.setSource(code) }

    fun toggleSmart(v: Boolean) = viewModelScope.launch { store.setSmart(v) }

    fun toggleNotify(v: Boolean) = viewModelScope.launch { store.setNotify(v) }

    /** Сохранить время ежедневной сводки и сразу перепланировать уведомление. */
    fun setNotifyTime(hour: Int, minute: Int) = viewModelScope.launch {
        store.setNotifyTime(hour, minute)
        DailyUpdateWorker.ensureScheduled(appCtx, hour, minute)
    }

    fun addFavorite(code: String) = viewModelScope.launch {
        val s = store.current()
        if (code !in s.favorites) store.setFavorites(s.favorites + code)
    }

    fun removeFavorite(code: String) = viewModelScope.launch {
        val s = store.current()
        store.setFavorites(s.favorites - code)
        // не оставляем «висящий» выбор исходной валюты на удалённой
        if (code == pickedSource) pickedSource = null
    }

    fun search(query: String) {
        searchResults = Currencies.search(query)
    }

    /** (amount, code|null, badToken|null); null если в строке нет числа. */
    private fun parseInput(text: String): Triple<Double?, String?, String?>? {
        val s = text.trim()
        val m = Regex("[0-9][0-9 .,]*[0-9]|[0-9]").find(s) ?: return null
        val amount = m.value.replace(" ", "").replace(",", ".").toDoubleOrNull() ?: return null
        val rest = (s.substring(0, m.range.first) + " " + s.substring(m.range.last + 1)).trim()
        if (rest.isEmpty()) return Triple(amount, null, null)
        val token = rest.replace(Regex("[^0-9A-Za-zА-Яа-яёЁ ]"), "").trim()
        if (token.isEmpty()) return Triple(amount, null, null)
        val up = token.uppercase()
        val code = if (Currencies.isKnown(up)) up else Currencies.search(token, 1).firstOrNull()
        return if (code == null) Triple(amount, null, rest) else Triple(amount, code, null)
    }
}
