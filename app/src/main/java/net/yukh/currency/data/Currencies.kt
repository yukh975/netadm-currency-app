package net.yukh.currency.data

import java.util.Locale

/** Справочник валют: название, флаг страны, страна (уже в языке интерфейса). */
data class CurrencyInfo(val name: String, val flag: String, val country: String)

/** Двуязычная запись каталога (ru + en); наружу отдаётся CurrencyInfo по локали. */
private data class Entry(
    val nameRu: String,
    val nameEn: String,
    val flag: String,
    val countryRu: String,
    val countryEn: String,
)

/**
 * Каталог валют для поиска и отображения. Совпадает с набором бота
 * (все валюты ЦБ РФ). Коды от источника регистрируются динамически.
 * Названия/страны хранятся на двух языках: интерфейс берёт по системной
 * локали, поиск матчится по обоим языкам сразу.
 */
object Currencies {
    private fun e(nameRu: String, nameEn: String, flag: String, countryRu: String, countryEn: String) =
        Entry(nameRu, nameEn, flag, countryRu, countryEn)

    private val map: LinkedHashMap<String, Entry> = linkedMapOf(
        "RUB" to e("Российский рубль", "Russian ruble", "🇷🇺", "Россия", "Russia"),
        "USD" to e("Доллар США", "US dollar", "🇺🇸", "США", "United States"),
        "EUR" to e("Евро", "Euro", "🇪🇺", "Еврозона", "Eurozone"),
        "GBP" to e("Фунт стерлингов", "Pound sterling", "🇬🇧", "Великобритания", "United Kingdom"),
        "CHF" to e("Швейцарский франк", "Swiss franc", "🇨🇭", "Швейцария", "Switzerland"),
        "JPY" to e("Японская иена", "Japanese yen", "🇯🇵", "Япония", "Japan"),
        "CNY" to e("Китайский юань", "Chinese yuan", "🇨🇳", "Китай", "China"),
        "KZT" to e("Казахстанский тенге", "Kazakhstani tenge", "🇰🇿", "Казахстан", "Kazakhstan"),
        "BYN" to e("Белорусский рубль", "Belarusian ruble", "🇧🇾", "Беларусь", "Belarus"),
        "UAH" to e("Украинская гривна", "Ukrainian hryvnia", "🇺🇦", "Украина", "Ukraine"),
        "TRY" to e("Турецкая лира", "Turkish lira", "🇹🇷", "Турция", "Turkey"),
        "GEL" to e("Грузинский лари", "Georgian lari", "🇬🇪", "Грузия", "Georgia"),
        "AMD" to e("Армянский драм", "Armenian dram", "🇦🇲", "Армения", "Armenia"),
        "AZN" to e("Азербайджанский манат", "Azerbaijani manat", "🇦🇿", "Азербайджан", "Azerbaijan"),
        "INR" to e("Индийская рупия", "Indian rupee", "🇮🇳", "Индия", "India"),
        "AED" to e("Дирхам ОАЭ", "UAE dirham", "🇦🇪", "ОАЭ", "United Arab Emirates"),
        "THB" to e("Тайский бат", "Thai baht", "🇹🇭", "Таиланд", "Thailand"),
        "VND" to e("Вьетнамский донг", "Vietnamese dong", "🇻🇳", "Вьетнам", "Vietnam"),
        "KRW" to e("Южнокорейская вона", "South Korean won", "🇰🇷", "Южная Корея", "South Korea"),
        "HKD" to e("Гонконгский доллар", "Hong Kong dollar", "🇭🇰", "Гонконг", "Hong Kong"),
        "SGD" to e("Сингапурский доллар", "Singapore dollar", "🇸🇬", "Сингапур", "Singapore"),
        "CAD" to e("Канадский доллар", "Canadian dollar", "🇨🇦", "Канада", "Canada"),
        "AUD" to e("Австралийский доллар", "Australian dollar", "🇦🇺", "Австралия", "Australia"),
        "NZD" to e("Новозеландский доллар", "New Zealand dollar", "🇳🇿", "Новая Зеландия", "New Zealand"),
        "BRL" to e("Бразильский реал", "Brazilian real", "🇧🇷", "Бразилия", "Brazil"),
        "PLN" to e("Польский злотый", "Polish zloty", "🇵🇱", "Польша", "Poland"),
        "CZK" to e("Чешская крона", "Czech koruna", "🇨🇿", "Чехия", "Czechia"),
        "SEK" to e("Шведская крона", "Swedish krona", "🇸🇪", "Швеция", "Sweden"),
        "NOK" to e("Норвежская крона", "Norwegian krone", "🇳🇴", "Норвегия", "Norway"),
        "DKK" to e("Датская крона", "Danish krone", "🇩🇰", "Дания", "Denmark"),
        "HUF" to e("Венгерский форинт", "Hungarian forint", "🇭🇺", "Венгрия", "Hungary"),
        "RON" to e("Румынский лей", "Romanian leu", "🇷🇴", "Румыния", "Romania"),
        "BGN" to e("Болгарский лев", "Bulgarian lev", "🇧🇬", "Болгария", "Bulgaria"),
        "RSD" to e("Сербский динар", "Serbian dinar", "🇷🇸", "Сербия", "Serbia"),
        "ILS" to e("Израильский шекель", "Israeli new shekel", "🇮🇱", "Израиль", "Israel"),
        "EGP" to e("Египетский фунт", "Egyptian pound", "🇪🇬", "Египет", "Egypt"),
        "ZAR" to e("Южноафриканский рэнд", "South African rand", "🇿🇦", "ЮАР", "South Africa"),
        "MXN" to e("Мексиканское песо", "Mexican peso", "🇲🇽", "Мексика", "Mexico"),
        "IDR" to e("Индонезийская рупия", "Indonesian rupiah", "🇮🇩", "Индонезия", "Indonesia"),
        "MYR" to e("Малайзийский ринггит", "Malaysian ringgit", "🇲🇾", "Малайзия", "Malaysia"),
        "PHP" to e("Филиппинское песо", "Philippine peso", "🇵🇭", "Филиппины", "Philippines"),
        "QAR" to e("Катарский риал", "Qatari riyal", "🇶🇦", "Катар", "Qatar"),
        "SAR" to e("Саудовский риял", "Saudi riyal", "🇸🇦", "Саудовская Аравия", "Saudi Arabia"),
        "KGS" to e("Киргизский сом", "Kyrgyzstani som", "🇰🇬", "Киргизия", "Kyrgyzstan"),
        "UZS" to e("Узбекский сум", "Uzbekistani sum", "🇺🇿", "Узбекистан", "Uzbekistan"),
        "TJS" to e("Таджикский сомони", "Tajikistani somoni", "🇹🇯", "Таджикистан", "Tajikistan"),
        "TMT" to e("Туркменский манат", "Turkmenistani manat", "🇹🇲", "Туркменистан", "Turkmenistan"),
        "MDL" to e("Молдавский лей", "Moldovan leu", "🇲🇩", "Молдова", "Moldova"),
        "BDT" to e("Бангладешская така", "Bangladeshi taka", "🇧🇩", "Бангладеш", "Bangladesh"),
        "BOB" to e("Боливийское боливиано", "Bolivian boliviano", "🇧🇴", "Боливия", "Bolivia"),
        "CUP" to e("Кубинское песо", "Cuban peso", "🇨🇺", "Куба", "Cuba"),
        "DZD" to e("Алжирский динар", "Algerian dinar", "🇩🇿", "Алжир", "Algeria"),
        "ETB" to e("Эфиопский быр", "Ethiopian birr", "🇪🇹", "Эфиопия", "Ethiopia"),
        "IRR" to e("Иранский риал", "Iranian rial", "🇮🇷", "Иран", "Iran"),
        "MMK" to e("Мьянманский кьят", "Myanmar kyat", "🇲🇲", "Мьянма", "Myanmar"),
        "MNT" to e("Монгольский тугрик", "Mongolian tugrik", "🇲🇳", "Монголия", "Mongolia"),
        "NGN" to e("Нигерийская найра", "Nigerian naira", "🇳🇬", "Нигерия", "Nigeria"),
        "OMR" to e("Оманский риал", "Omani rial", "🇴🇲", "Оман", "Oman"),
        "BHD" to e("Бахрейнский динар", "Bahraini dinar", "🇧🇭", "Бахрейн", "Bahrain"),
        "XDR" to e("СДР (спец. права заимствования)", "SDR (special drawing rights)", "🏦", "МВФ", "IMF"),
    )

    /** Коды, добавленные источником (имя — как отдал источник, без перевода). */
    private val dynamic = HashMap<String, String>()

    val popular = listOf("RUB", "USD", "EUR", "GBP", "CNY", "KZT", "TRY", "GEL", "AMD")

    private fun isRu(): Boolean = Locale.getDefault().language == "ru"

    fun info(code: String): CurrencyInfo {
        map[code]?.let { en ->
            return if (isRu()) CurrencyInfo(en.nameRu, en.flag, en.countryRu)
            else CurrencyInfo(en.nameEn, en.flag, en.countryEn)
        }
        dynamic[code]?.let { return CurrencyInfo(it, "🏳️", "") }
        return CurrencyInfo(code, "🏳️", "")
    }

    fun isKnown(code: String): Boolean = code in map || code in dynamic

    fun label(code: String): String {
        val i = info(code)
        return "${i.flag} $code — ${i.name}"
    }

    /** Дополнить каталог кодами от источника (не перетирая описанные вручную). */
    fun register(names: Map<String, String>) {
        for ((code, name) in names) {
            if (code !in map && code !in dynamic) dynamic[code] = name.ifBlank { code }
        }
    }

    /** Поиск по коду, названию или стране — на обоих языках сразу. */
    fun search(query: String, limit: Int = 12): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val exact = ArrayList<String>()
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        fun classify(code: String, fields: List<String>) {
            val lc = code.lowercase()
            when {
                lc == q -> exact.add(code)
                lc.startsWith(q) || fields.any { it.startsWith(q) } -> starts.add(code)
                q in lc || fields.any { q in it } -> contains.add(code)
            }
        }
        for ((code, i) in map) {
            classify(
                code,
                listOf(i.nameRu.lowercase(), i.nameEn.lowercase(), i.countryRu.lowercase(), i.countryEn.lowercase()),
            )
        }
        for ((code, name) in dynamic) classify(code, listOf(name.lowercase()))
        return (exact + starts + contains).take(limit)
    }
}
