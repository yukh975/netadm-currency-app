package net.yukh.currency.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/** Строка результата конвертации для UI. */
data class ConversionRow(
    val code: String,
    val label: String,
    val isBase: Boolean,
    val result: String,   // «100.00 RSD = 0.99 USD»
    val reverse: String,  // «1 USD = 100.84 RSD»
)

object Converter {

    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val av = abs(v)
        val digits = if (av >= 1.0) 2 else if (av < 0.01) 6 else 4
        return String.format(Locale.US, "%,.${digits}f", v).replace(",", " ")
    }

    /** Обратный курс: «1 code = N ref», с масштабом для дешёвых валют. */
    private fun rateLine(code: String, ref: String, perRef: Double, smart: Boolean): String {
        var v = perRef
        if (smart && v > 0.0 && v < 1.0) {
            var factor = 1L
            while (v * factor < 10.0 && factor < 1_000_000_000L) factor *= 10
            return "$factor $code = ${format(v * factor)} $ref"
        }
        return "1 $code = ${format(v)} $ref"
    }

    fun rows(
        table: RateTable,
        src: String,
        base: String,
        favorites: List<String>,
        amount: Double,
        smart: Boolean,
    ): List<ConversionRow> {
        if (src !in table.rates) return emptyList()
        val targets = ArrayList<String>()
        if (src != base) targets.add(base)
        for (code in favorites.sorted()) if (code != src && code !in targets) targets.add(code)

        return targets.map { code ->
            val baseMark = code == base && code != src
            val label = Currencies.label(code) + if (baseMark) "  (исходная)" else ""
            if (code !in table.rates) {
                ConversionRow(code, label, baseMark, "нет данных", "")
            } else {
                val result = table.convert(amount, src, code)
                val reverse = rateLine(code, src, table.convert(1.0, code, src), smart)
                ConversionRow(code, label, baseMark, "${format(amount)} $src = ${format(result)} $code", reverse)
            }
        }
    }

    fun lastUpdated(table: RateTable): String {
        val tz = TimeZone.getTimeZone("Europe/Moscow")
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).apply { timeZone = tz }
        return fmt.format(Date(table.fetchedAt)) + " МСК"
    }

    private val sourceLabels = mapOf("cbr" to "🇷🇺 ЦБ РФ", "google" to "🌐 Google")

    fun freshness(table: RateTable): String {
        val tz = TimeZone.getTimeZone("Europe/Moscow")
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).apply { timeZone = tz }
        val updated = fmt.format(Date(table.fetchedAt))
        val label = sourceLabels[table.source] ?: table.source
        val courseDate = parseCourseDate(table.sourceDate, tz)
        return buildString {
            append("📊 Источник: ").append(label).append('\n')
            if (courseDate != null) append("📅 Курс на дату: ").append(courseDate).append('\n')
            append("🕒 Обновлено: ").append(updated).append(" МСК")
        }
    }

    private fun parseCourseDate(raw: String, tz: TimeZone): String? {
        if (raw.isBlank()) return null
        val out = SimpleDateFormat("dd.MM.yyyy", Locale.US).apply { timeZone = tz }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",      // ЦБ РФ
            "EEE, dd MMM yyyy HH:mm:ss Z",    // open.er-api (RFC 1123)
        )
        for (p in patterns) {
            try {
                val parser = SimpleDateFormat(p, Locale.US)
                return out.format(parser.parse(raw)!!)
            } catch (_: Exception) {
            }
        }
        return null
    }
}
