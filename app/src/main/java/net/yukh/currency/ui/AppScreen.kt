package net.yukh.currency.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import java.util.Locale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.yukh.currency.BuildConfig
import net.yukh.currency.CurrencyApp
import net.yukh.currency.R
import net.yukh.currency.data.AppSettings
import net.yukh.currency.data.Currencies

@Composable
fun AppScreen(
    openSummary: Boolean = false,
    onSummaryConsumed: () -> Unit = {},
    vm: MainViewModel = viewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    // открытие из уведомления: вкладка «Конвертация» + сводка от «моей валюты»
    // (как и текст уведомления), независимо от выбора в конверторе
    LaunchedEffect(openSummary) {
        if (openSummary) {
            tab = 0
            vm.showSummaryForBase()
            onSummaryConsumed()
        }
    }

    if (BuildConfig.UPDATE_ENABLED) {
        // автопроверка обновления при запуске (тихая, с троттлингом в VM);
        // модалка рендерится здесь — поверх любой вкладки
        LaunchedEffect(Unit) { vm.autoCheckUpdate() }
        UpdateDialogHost(vm)
    }

    Scaffold(
        bottomBar = {
            val tabs = listOf(
                Icons.Filled.SwapVert to stringResource(R.string.tab_convert),
                Icons.Filled.Star to stringResource(R.string.tab_favorites),
                Icons.Filled.Settings to stringResource(R.string.tab_settings),
                Icons.Filled.Info to stringResource(R.string.tab_about),
            )
            Surface(tonalElevation = 3.dp) {
                // отступ под системную навигацию Android 15 (edge-to-edge при
                // targetSdk 35), чтобы иконки не уезжали под системную панель;
                // фон Surface при этом заполняет область до нижнего края экрана
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                    ) {
                        tabs.forEachIndexed { i, item ->
                            IconButton(onClick = { tab = i }) {
                                Icon(
                                    item.first,
                                    contentDescription = item.second,
                                    tint = if (tab == i) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        tabs[tab].second,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ConvertScreen(vm, settings)
                1 -> FavoritesScreen(vm, settings)
                2 -> SettingsScreen(vm, settings)
                else -> AboutScreen(vm)
            }
        }
    }
}

@Composable
private fun ConvertScreen(vm: MainViewModel, settings: AppSettings) {
    val focusManager = LocalFocusManager.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.source_currency), style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            settings.favorites.forEach { code ->
                FilterChip(
                    // временный выбор: «моя валюта» по умолчанию, без записи в настройки
                    selected = code == (vm.pickedSource ?: settings.base),
                    onClick = { vm.pickSource(code) },
                    label = { Text("${Currencies.info(code).flag} $code") },
                )
            }
        }

        OutlinedTextField(
            value = vm.input,
            onValueChange = vm::onInputChange,
            // статичная подсказка внутри рамки (не «плавающий» label)
            placeholder = { Text(stringResource(R.string.amount_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                vm.convert()
            }),
        )
        Button(
            onClick = {
                focusManager.clearFocus()
                vm.convert()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.convert_button))
        }

        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.rows.forEach { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (row.isBase) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(row.result, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    if (row.reverse.isNotEmpty()) {
                        Text(row.reverse, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        if (vm.freshness.isNotEmpty()) {
            Text(vm.freshness, style = MaterialTheme.typography.bodySmall)
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Button(onClick = { vm.showSummary() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.summary_button))
        }
        if (vm.summaryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.summaryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.summary.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.summary_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    vm.summary.forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.text, style = MaterialTheme.typography.bodyMedium)
                            if (row.delta != null) {
                                Spacer(Modifier.width(6.dp))
                                // в тёмной теме тёмные оттенки не читаются
                                // на фоне карточки — берём светлые
                                val dark = isSystemInDarkTheme()
                                val color = when (row.deltaUp) {
                                    true -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    false -> if (dark) Color(0xFFEF9A9A) else Color(0xFFD32F2F)
                                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                Text(
                                    "(${row.delta})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = color,
                                )
                            }
                        }
                    }
                    if (vm.summaryFreshness.isNotEmpty()) {
                        Text(vm.summaryFreshness, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesScreen(vm: MainViewModel, settings: AppSettings) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            // статичная подсказка внутри рамки (не «плавающий» label),
            // короче — чтобы помещалась в одну строку
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        vm.searchResults.forEach { code ->
            val info = Currencies.info(code)
            val inFav = code in settings.favorites
            val left = if (info.country.isNotEmpty()) "${info.flag} ${info.country} — ${info.name}"
            else "${info.flag} ${info.name}"
            Card(onClick = { vm.addFavorite(code) }, modifier = Modifier.fillMaxWidth()) {
                Text(
                    left + if (inFav) "  ✅" else "",
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.favorites_title), style = MaterialTheme.typography.titleMedium)

        settings.favorites.forEach { code ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(Currencies.label(code), modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.removeFavorite(code) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(vm: MainViewModel, settings: AppSettings) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // подтянуть время последнего обновления при входе/смене источника
        LaunchedEffect(settings.source) { vm.refreshLastUpdate() }

        Text(stringResource(R.string.rates_source), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.source == "cbr",
                onClick = { vm.setSource("cbr") },
                label = { Text(stringResource(R.string.source_cbr)) },
            )
            FilterChip(
                selected = settings.source == "google",
                onClick = { vm.setSource("google") },
                label = { Text(stringResource(R.string.source_google)) },
            )
        }
        Text(
            stringResource(R.string.rates_source_hint),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        Text(stringResource(R.string.my_currency), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.my_currency_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            settings.favorites.forEach { code ->
                FilterChip(
                    selected = code == settings.base,
                    onClick = { vm.setBase(code) },
                    label = { Text("${Currencies.info(code).flag} $code") },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.rates_section), style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { vm.refreshRates() },
            enabled = !vm.refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (vm.refreshing) stringResource(R.string.refreshing)
                else stringResource(R.string.refresh_now),
            )
        }
        Text(vm.lastUpdate, style = MaterialTheme.typography.bodySmall)
        vm.refreshError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.smart_format), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.smart_format_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.smartUnits, onCheckedChange = { vm.toggleSmart(it) })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.daily_notification), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.daily_notification_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.notify, onCheckedChange = { vm.toggleNotify(it) })
        }

        val context = LocalContext.current

        // Фоновая работа уведомлений. На многих прошивках (Xiaomi, Samsung,
        // Huawei…) система «усыпляет»/останавливает приложение в фоне, и сводка
        // приходит только при открытии. Единственное клиентское средство —
        // попросить пользователя снять приложение с оптимизации батареи.
        if (settings.notify) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
                Text(
                    stringResource(R.string.battery_ok),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    stringResource(R.string.battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    // экран «О приложении» → пункт «Батарея» есть на всех
                    // прошивках; прямой запрос-диалог требует разрешения и
                    // косо оценивается магазинами, поэтому ведём сюда
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }) {
                    Text(stringResource(R.string.battery_button))
                }
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.lang_section),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            // язык приложения: "" = как в системе (дефолт), иначе ru/en.
            // Выпадающий список (чипы не влезали по ширине). Смена переписывает
            // pref и пересоздаёт активити — ресурсы подхватываются в
            // attachBaseContext (см. CurrencyApp.localized)
            val app = context.applicationContext as CurrencyApp
            val options = listOf(
                "" to stringResource(R.string.lang_system),
                "ru" to stringResource(R.string.lang_ru),
                "en" to stringResource(R.string.lang_en),
            )
            var langMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { langMenu = true }) {
                    Text(options.firstOrNull { it.first == app.langPref }?.second ?: options[0].second)
                }
                DropdownMenu(expanded = langMenu, onDismissRequest = { langMenu = false }) {
                    options.forEach { (code, title) ->
                        DropdownMenuItem(
                            text = { Text(title) },
                            onClick = {
                                langMenu = false
                                if (app.langPref != code) {
                                    app.langPref = code
                                    (context as? Activity)?.recreate()
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(0.dp))
        Text(
            stringResource(R.string.cache_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private const val SITE_URL = "https://yukh.net"
private const val XUI_URL = "https://f-droid.org/packages/net.yukh.xui"
private const val XUI_MANUAL_URL = "https://github.com/yukh975/3X-UI-Manual"
private const val NETADM_URL = "https://netadm.pro"
private const val BOT_URL = "https://t.me/netadm_currency_bot"

@Composable
private fun AboutScreen(vm: MainViewModel) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("💱 " + stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(
            stringResource(R.string.version_fmt, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        TextButton(onClick = { uri.openUri(SITE_URL) }) {
            Text(stringResource(R.string.site_link))
        }
        TextButton(onClick = { uri.openUri(BOT_URL) }) {
            Text(stringResource(R.string.bot_link))
        }
        Text(
            stringResource(R.string.bot_note),
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()
        if (BuildConfig.UPDATE_ENABLED) {
            UpdateSection(vm)
        } else {
            // Сборка для каталога (fdroid/play) сама себя не обновляет — поясняем откуда обновления
            Text(
                stringResource(R.string.update_via_fdroid),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        HorizontalDivider()

        // Кросс-ссылки на другие наши проекты. Тон строго нейтральный (название +
        // факт), без промо-лексики: у F-Droid есть анти-фича «Promotes other apps».
        Text(stringResource(R.string.our_projects), style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column {
                ProjectRow(
                    icon = Icons.Outlined.Router,
                    title = stringResource(R.string.proj_xui),
                    subtitle = stringResource(R.string.proj_xui_sub),
                    onClick = { uri.openUri(XUI_URL) },
                )
                HorizontalDivider()
                ProjectRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.proj_xui_manual),
                    subtitle = stringResource(R.string.proj_xui_manual_sub),
                    onClick = { uri.openUri(XUI_MANUAL_URL) },
                )
                HorizontalDivider()
                ProjectRow(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.proj_netadm),
                    subtitle = stringResource(R.string.proj_netadm_sub),
                    onClick = { uri.openUri(NETADM_URL) },
                )
            }
        }

        HorizontalDivider()

        Text(stringResource(R.string.copyright), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.license_mit), style = MaterialTheme.typography.bodySmall)
    }
}

/** Строка проекта в блоке «Наши проекты»: иконка · название + пояснение ·
 *  значок внешней ссылки. Открывает URL по тапу. */
@Composable
private fun ProjectRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Блок «Обновление» на вкладке «О программе» (только для flavor `direct`):
 *  кнопка ручной проверки. Состояние и логика — в [MainViewModel]; модалки
 *  рендерит [UpdateDialogHost] на уровне [AppScreen] (поверх любой вкладки). */
@Composable
private fun UpdateSection(vm: MainViewModel) {
    Text(stringResource(R.string.update_section), style = MaterialTheme.typography.titleSmall)

    Button(enabled = !vm.updateBusy, onClick = { vm.checkUpdate() }) {
        Text(stringResource(R.string.update_check))
    }

    vm.updateStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

/** Модалки обновления: «доступна версия» (Установить/Позже) или сообщение
 *  («последняя версия»/ошибка). Показываются и при автопроверке на старте. */
@Composable
private fun UpdateDialogHost(vm: MainViewModel) {
    when (val d = vm.updateDialog) {
        is UpdateDialog.Available -> {
            val u = d.update
            AlertDialog(
                onDismissRequest = { vm.dismissUpdateDialog() },
                title = { Text(stringResource(R.string.update_available_fmt, u.versionName)) },
                text = {
                    Column(
                        Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(R.string.update_installed_fmt, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            u.notes.ifBlank { stringResource(R.string.update_no_changelog) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        vm.updateStatus?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        // во время скачивания — прогресс прямо в модалке
                        if (vm.updateBusy) {
                            DownloadProgress(vm.updateProgress, vm.updateGotBytes, vm.updateTotalBytes)
                        }
                    }
                },
                confirmButton = {
                    // кнопки заблокированы, пока идёт загрузка
                    TextButton(enabled = !vm.updateBusy, onClick = { vm.installUpdate(u) }) {
                        Text(stringResource(R.string.update_install))
                    }
                },
                dismissButton = {
                    // «Позже» = не предлагать эту версию автоматически
                    // (кнопка ручной проверки предложит снова)
                    TextButton(
                        enabled = !vm.updateBusy,
                        onClick = { vm.dismissUpdateDialog(skipVersion = u.versionName) },
                    ) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }
        is UpdateDialog.Message -> AlertDialog(
            onDismissRequest = { vm.dismissUpdateDialog() },
            title = { Text(stringResource(R.string.update_section)) },
            text = { Text(d.text) },
            confirmButton = {
                TextButton(onClick = { vm.dismissUpdateDialog() }) { Text(stringResource(R.string.ok)) }
            },
        )
        null -> {}
    }
}

/** Полоса скачивания APK: проценты и мегабайты, если сервер сообщил размер;
 *  иначе бесконечная полоса и просто «скачано N МБ». */
@Composable
private fun DownloadProgress(progress: Float?, got: Long, total: Long) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.update_progress_fmt, (progress * 100).toInt(), mb(got), mb(total)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.update_progress_indeterminate_fmt, mb(got)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun mb(bytes: Long): String =
    if (bytes <= 0) "0" else String.format(Locale.US, "%.1f", bytes / 1024.0 / 1024.0)
