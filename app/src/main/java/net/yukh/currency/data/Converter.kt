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

    /** Фиксированный формат: всегда 2 знака после запятой. */
    private fun format2(v: Double): String =
        String.format(Locale.US, "%,.2f", v).replace(",", " ")

    /** Строка сводки для UI/уведомления: текст «🇪🇺 1 EUR = 90.50 RUB» + динамика
     *  курса (изменение к предыдущему курсу в основной валюте). */
    data class SummaryRow(
        val text: String,
        val delta: String? = null,     // «+0.50» / «−0.07» / «0.00»; null — данных нет
        val deltaUp: Boolean? = null,  // true=рост (зелёный), false=падение (красный), null=без изм.
    )

    /** Сводка: курс каждой избранной валюты к основной — по строке на валюту,
     *  с динамикой (если у источника есть предыдущие курсы, как у ЦБ РФ).
     *  Основная валюта пропускается. */
    fun summary(
        table: RateTable,
        base: String,
        favorites: List<String>,
        smart: Boolean,
    ): List<SummaryRow> {
        val out = ArrayList<SummaryRow>()
        for (code in favorites.sorted()) {
            if (code == base) continue
            val flag = Currencies.info(code).flag
            if (code !in table.rates) {
                out.add(SummaryRow("$flag $code: нет данных"))
                continue
            }
            val perBase = table.convert(1.0, code, base)
            var factor = 1L
            if (smart && perBase > 0.0 && perBase < 1.0) {
                while (perBase * factor < 10.0 && factor < 1_000_000_000L) factor *= 10
            }
            val text = "$flag $factor $code = ${format2(perBase * factor)} $base"
            val perBasePrev = table.convertPrev(1.0, code, base)
            if (perBasePrev == null) {
                out.add(SummaryRow(text))
                continue
            }
            val d = (perBase - perBasePrev) * factor
            val ds = format2(abs(d))
            if (ds == "0.00") {
                out.add(SummaryRow(text, "0.00", null))
            } else {
                val up = d > 0
                out.add(SummaryRow(text, (if (up) "+" else "−") + ds, up))
            }
        }
        return out
    }

    /** Дата, НА которую действует курс (для заголовка уведомления): у ЦБ это
     *  обычно следующий день, у рыночного — сегодня. null, если не распарсилось. */
    fun courseDate(table: RateTable): String? =
        parseCourseDate(table.sourceDate, TimeZone.getTimeZone("Europe/Moscow"))

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
        return buildString {
            append("📊 Источник: ").append(label).append('\n')
            // ЦБ — официальный курс на дату (без времени); рыночный — спот-курс,
            // обновляется в течение суток, поэтому показываем момент обновления
            // с временем по Москве.
            if (table.source == "google") {
                val moment = parseCourseDateTime(table.sourceDate, tz)
                if (moment != null) append("🕒 Курс обновлён: ").append(moment).append(" МСК").append('\n')
            } else {
                val courseDate = parseCourseDate(table.sourceDate, tz)
                if (courseDate != null) append("📅 Курс на дату: ").append(courseDate).append('\n')
            }
            append("🕒 Обновлено: ").append(updated).append(" МСК")
        }
    }

    private val courseDatePatterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",      // ЦБ РФ (ISO с таймзоной)
        "EEE, dd MMM yyyy HH:mm:ss Z",    // open.er-api (RFC 1123)
    )

    private fun parseCourseDate(raw: String, tz: TimeZone): String? =
        formatCourseDate(raw, "dd.MM.yyyy", tz)

    /** Момент курса с временем по Москве (для рыночного источника): «dd.MM HH:mm». */
    private fun parseCourseDateTime(raw: String, tz: TimeZone): String? =
        formatCourseDate(raw, "dd.MM HH:mm", tz)

    private fun formatCourseDate(raw: String, outPattern: String, tz: TimeZone): String? {
        if (raw.isBlank()) return null
        val out = SimpleDateFormat(outPattern, Locale.US).apply { timeZone = tz }
        for (p in courseDatePatterns) {
            try {
                val parser = SimpleDateFormat(p, Locale.US)
                return out.format(parser.parse(raw)!!)
            } catch (_: Exception) {
            }
        }
        return null
    }
}
