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
import net.yukh.currency.BuildConfig
import net.yukh.currency.CurrencyApp
import net.yukh.currency.R
import net.yukh.currency.data.ApkInstaller
import net.yukh.currency.data.AppSettings
import net.yukh.currency.data.ConversionRow
import net.yukh.currency.data.Converter
import net.yukh.currency.data.Currencies
import net.yukh.currency.data.UpdateChecker
import net.yukh.currency.work.DailyUpdateWorker

/** Исходы проверки обновления (показываются модалкой поверх любой вкладки). */
sealed interface UpdateDialog {
    data class Available(val update: UpdateChecker.Update) : UpdateDialog
    data class Message(val text: String) : UpdateDialog
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appCtx = app as CurrencyApp
    private val repo = appCtx.repository
    private val store = appCtx.settingsStore

    // строки — через локализованный контекст (учитывает выбранный язык приложения)
    private val loc get() = appCtx.l10n()
    private fun str(id: Int, vararg args: Any): String = loc.getString(id, *args)

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
    var lastUpdate by mutableStateOf("")
        private set
    var refreshing by mutableStateOf(false)
        private set
    var refreshError by mutableStateOf<String?>(null)
        private set
    var summary by mutableStateOf<List<Converter.SummaryRow>>(emptyList())
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

    init {
        lastUpdate = str(R.string.updated_never)
    }

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
            summaryError = str(R.string.add_favorites_first)
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
            summary = Converter.summary(loc, table, src, s.favorites, s.smartUnits)
            summaryFreshness = Converter.freshness(loc, table)
        } catch (e: Exception) {
            summaryError = str(R.string.source_unavailable)
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
        lastUpdate = if (table != null) {
            str(R.string.updated_fmt, Converter.lastUpdated(loc, table))
        } else {
            str(R.string.updated_never)
        }
    }

    /** Принудительно обновить курсы текущего источника. */
    fun refreshRates() = viewModelScope.launch {
        val s = store.current()
        val needed = (s.favorites + s.base).toSet()
        refreshing = true
        refreshError = null
        try {
            val table = repo.getTable(s.source, needed, force = true)
            lastUpdate = str(R.string.updated_fmt, Converter.lastUpdated(loc, table))
        } catch (e: Exception) {
            refreshError = str(R.string.refresh_failed)
        } finally {
            refreshing = false
        }
    }

    fun convert() {
        error = null
        val parsed = parseInput(input)
        if (parsed == null) {
            error = str(R.string.enter_amount)
            return
        }
        val (amount, code, bad) = parsed
        if (bad != null) {
            error = str(R.string.unknown_currency_fmt, bad)
            return
        }
        if (amount == null) {
            error = str(R.string.amount_not_understood)
            return
        }
        viewModelScope.launch {
            val s = store.current()
            val from = code ?: (pickedSource?.takeIf { it in s.favorites } ?: s.base)
            val needed = (s.favorites + s.base + from).toSet()
            loading = true
            try {
                val table = repo.getTable(s.source, needed)
                rows = Converter.rows(loc, table, from, s.base, s.favorites, amount, s.smartUnits)
                freshness = Converter.freshness(loc, table)
                // Некуда конвертировать (в избранном только исходная валюта) —
                // молча пустой экран сбивает с толку при первом запуске, поэтому
                // подсказываем добавить валюты в избранное.
                if (rows.isEmpty()) error = str(R.string.no_targets_hint)
            } catch (e: Exception) {
                error = str(R.string.source_unavailable)
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

    /** Вкл/выкл уведомление о новом курсе (запускает/останавливает наблюдение). */
    fun toggleNotify(v: Boolean) = viewModelScope.launch {
        store.setNotify(v)
        if (v) DailyUpdateWorker.ensureScheduled(appCtx) else DailyUpdateWorker.cancel(appCtx)
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

    // --- Обновление приложения (только flavor direct, UPDATE_ENABLED) ---

    var updateDialog by mutableStateOf<UpdateDialog?>(null)
        private set
    var updateStatus by mutableStateOf<String?>(null)   // прогресс скачивания/установки
        private set
    var updateBusy by mutableStateOf(false)
        private set

    /** Ручная проверка (кнопки в «Настройках» и «О программе»): результат
     *  всегда модалкой — обновление, «последняя версия» или ошибка. */
    fun checkUpdate() {
        if (!BuildConfig.UPDATE_ENABLED) return
        updateBusy = true
        updateStatus = str(R.string.update_checking)
        viewModelScope.launch {
            updateDialog = try {
                val u = UpdateChecker.check(appCtx.httpClient, BuildConfig.VERSION_NAME)
                if (u != null) {
                    UpdateDialog.Available(u)
                } else {
                    UpdateDialog.Message(str(R.string.update_latest_fmt, BuildConfig.VERSION_NAME))
                }
            } catch (e: Exception) {
                UpdateDialog.Message(
                    str(R.string.update_check_failed_fmt, e.message ?: str(R.string.network_error)),
                )
            }
            updateStatus = null
            updateBusy = false
        }
    }

    /** Автопроверка при запуске: не чаще раза в [AUTO_CHECK_INTERVAL_MS];
     *  молчит, если обновления нет/ошибка сети; версию, отложенную кнопкой
     *  «Позже», повторно не предлагает. */
    fun autoCheckUpdate() {
        if (!BuildConfig.UPDATE_ENABLED) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (now - store.lastUpdateCheck() < AUTO_CHECK_INTERVAL_MS) return@launch
            store.setLastUpdateCheck(now)
            val u = try {
                UpdateChecker.check(appCtx.httpClient, BuildConfig.VERSION_NAME)
            } catch (e: Exception) {
                null  // авто-режим: сетевые ошибки молча игнорируем
            } ?: return@launch
            if (u.versionName == store.skippedUpdateVersion()) return@launch
            if (updateDialog == null) updateDialog = UpdateDialog.Available(u)
        }
    }

    /** Закрыть модалку; [skipVersion] — версия, нажатая «Позже» (автопроверка
     *  её больше не предлагает; ручная проверка предложит снова). */
    fun dismissUpdateDialog(skipVersion: String? = null) {
        updateDialog = null
        if (skipVersion != null) {
            viewModelScope.launch { store.setSkippedUpdateVersion(skipVersion) }
        }
    }

    /** Скачать APK и запустить системный установщик. */
    fun installUpdate(u: UpdateChecker.Update) {
        updateDialog = null
        if (!ApkInstaller.canInstall(appCtx)) {
            // сначала попросим разрешение ставить APK из этого источника
            ApkInstaller.requestInstallPermission(appCtx)
            updateStatus = str(R.string.update_allow_install)
            return
        }
        updateBusy = true
        updateStatus = str(R.string.update_downloading)
        viewModelScope.launch {
            try {
                val file = ApkInstaller.download(appCtx, appCtx.httpClient, u.apkUrl)
                updateStatus = str(R.string.update_installing)
                ApkInstaller.install(appCtx, file)
            } catch (e: Exception) {
                updateStatus = str(
                    R.string.update_download_error_fmt,
                    e.message ?: str(R.string.unknown),
                )
            } finally {
                updateBusy = false
            }
        }
    }

    private companion object {
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L  // 6 часов
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
