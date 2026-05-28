package net.yukh.currency.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Emerald = Color(0xFF059669)
private val EmeraldLight = Color(0xFF34D399)

private val LightColors = lightColorScheme(
    primary = Emerald,
    secondary = EmeraldLight,
)

private val DarkColors = darkColorScheme(
    primary = EmeraldLight,
    secondary = Emerald,
)

@Composable
fun CurrencyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
