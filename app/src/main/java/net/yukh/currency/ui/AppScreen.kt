package net.yukh.currency.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.yukh.currency.BuildConfig
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

    // открытие из уведомления: вкладка «Конвертация» + загрузка свежей сводки
    LaunchedEffect(openSummary) {
        if (openSummary) {
            tab = 0
            vm.showSummary()
            onSummaryConsumed()
        }
    }

    Scaffold(
        bottomBar = {
            val tabs = listOf(
                Icons.Filled.SwapVert to "Конвертация",
                Icons.Filled.Star to "Избранное",
                Icons.Filled.Settings to "Настройки",
                Icons.Filled.Info to "О программе",
            )
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
                else -> AboutScreen()
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
        Text("Исходная валюта", style = MaterialTheme.typography.titleMedium)
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
            label = { Text("Сумма (например 1000)") },
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
            Text("Конвертировать")
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
            Text("🔔 Сводка курсов")
        }
        if (vm.summaryLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.summaryError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (vm.summary.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Курсы избранных валют",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    vm.summary.forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodyMedium)
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
            label = { Text("Поиск: код или название валюты") },
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
        Text("Избранное", style = MaterialTheme.typography.titleMedium)

        settings.favorites.forEach { code ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(Currencies.label(code), modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.removeFavorite(code) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Удалить")
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

        Text("Источник курсов", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = settings.source == "cbr",
                onClick = { vm.setSource("cbr") },
                label = { Text("🇷🇺 ЦБ РФ") },
            )
            FilterChip(
                selected = settings.source == "google",
                onClick = { vm.setSource("google") },
                label = { Text("🌐 Google") },
            )
        }

        HorizontalDivider()
        Text("Моя валюта", style = MaterialTheme.typography.titleMedium)
        Text(
            "Постоянная домашняя валюта. Относительно неё считается сводка в " +
                "уведомлениях; в конверторе она подставляется как исходная по умолчанию " +
                "(там её можно временно переключить).",
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
        Text("Курсы", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { vm.refreshRates() },
            enabled = !vm.refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.refreshing) "Обновляю…" else "🔄 Обновить курсы сейчас")
        }
        Text(vm.lastUpdate, style = MaterialTheme.typography.bodySmall)
        vm.refreshError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Удобный формат курса", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Для дешёвых валют: «100 RSD = 73 RUB»",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = settings.smartUnits, onCheckedChange = { vm.toggleSmart(it) })
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ежедневное уведомление", style = MaterialTheme.typography.bodyLarge)
                Text("Сводка курсов избранных валют (МСК)", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.notify, onCheckedChange = { vm.toggleNotify(it) })
        }

        val context = LocalContext.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Время уведомления", style = MaterialTheme.typography.bodyLarge)
                Text("Когда присылать сводку", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(
                enabled = settings.notify,
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> vm.setNotifyTime(h, m) },
                        settings.notifyHour,
                        settings.notifyMinute,
                        true,
                    ).show()
                },
            ) {
                Text(String.format("%02d:%02d", settings.notifyHour, settings.notifyMinute))
            }
        }

        Spacer(Modifier.width(0.dp))
        Text(
            "Курсы кэшируются на 3 часа и обновляются ежедневно. Источники: " +
                "ЦБ РФ (cbr-xml-daily.ru) и Google (open.er-api.com).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private const val SITE_URL = "https://yukh.net"
private const val BOT_URL = "https://t.me/netadm_currency_bot"

@Composable
private fun AboutScreen() {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("💱 Конвертер валют", style = MaterialTheme.typography.headlineSmall)
        Text("Версия ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)

        Text(
            "Конвертер валют по курсам ЦБ РФ или Google. Выберите источник и " +
                "исходную валюту, добавьте нужные валюты в избранное и вводите сумму — " +
                "получите перевод во все избранные. Есть удобный формат для дешёвых " +
                "валют, кэш курсов и ежедневная сводка в выбранное время.",
            style = MaterialTheme.typography.bodyMedium,
        )

        HorizontalDivider()

        TextButton(onClick = { uri.openUri(SITE_URL) }) {
            Text("🌐 Сайт разработчика — yukh.net")
        }
        TextButton(onClick = { uri.openUri(BOT_URL) }) {
            Text("✈️ Telegram-бот с тем же функционалом")
        }
        Text(
            "Тот же конвертер работает Telegram-ботом: те же источники (ЦБ РФ / Google), " +
                "избранные валюты, основная валюта и ежедневная рассылка курсов.",
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

        Text("© 2026 Yuriy Khachaturian", style = MaterialTheme.typography.bodySmall)
        Text("Лицензия MIT", style = MaterialTheme.typography.bodySmall)
    }
}
