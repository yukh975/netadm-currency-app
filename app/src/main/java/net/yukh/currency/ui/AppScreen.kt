package net.yukh.currency.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.yukh.currency.data.AppSettings
import net.yukh.currency.data.Currencies

@Composable
fun AppScreen(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.SwapVert, contentDescription = null) },
                    label = { Text("Конвертация") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    label = { Text("Избранное") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Настройки") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> ConvertScreen(vm, settings)
                1 -> FavoritesScreen(vm, settings)
                else -> SettingsScreen(vm, settings)
            }
        }
    }
}

@Composable
private fun ConvertScreen(vm: MainViewModel, settings: AppSettings) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Основная валюта", style = MaterialTheme.typography.titleMedium)
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

        OutlinedTextField(
            value = vm.input,
            onValueChange = vm::onInputChange,
            label = { Text("Сумма: 1000 или USD 100") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardActions = KeyboardActions(onDone = { vm.convert() }),
        )
        Button(onClick = { vm.convert() }, modifier = Modifier.fillMaxWidth()) {
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
                Text("Сводка курсов около 17:00 МСК", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = settings.notify, onCheckedChange = { vm.toggleNotify(it) })
        }

        Spacer(Modifier.width(0.dp))
        Text(
            "Курсы кэшируются на 3 часа и обновляются ежедневно. Источники: " +
                "ЦБ РФ (cbr-xml-daily.ru) и Google (open.er-api.com).",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
