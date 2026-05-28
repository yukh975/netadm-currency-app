package net.yukh.currency.data

/** Справочник валют: название, флаг страны, страна. */
data class CurrencyInfo(val name: String, val flag: String, val country: String)

/**
 * Каталог валют для поиска и отображения. Совпадает с набором бота
 * (все валюты ЦБ РФ). Коды от источника регистрируются динамически.
 */
object Currencies {
    private val map: LinkedHashMap<String, CurrencyInfo> = linkedMapOf(
        "RUB" to CurrencyInfo("Российский рубль", "🇷🇺", "Россия"),
        "USD" to CurrencyInfo("Доллар США", "🇺🇸", "США"),
        "EUR" to CurrencyInfo("Евро", "🇪🇺", "Еврозона"),
        "GBP" to CurrencyInfo("Фунт стерлингов", "🇬🇧", "Великобритания"),
        "CHF" to CurrencyInfo("Швейцарский франк", "🇨🇭", "Швейцария"),
        "JPY" to CurrencyInfo("Японская иена", "🇯🇵", "Япония"),
        "CNY" to CurrencyInfo("Китайский юань", "🇨🇳", "Китай"),
        "KZT" to CurrencyInfo("Казахстанский тенге", "🇰🇿", "Казахстан"),
        "BYN" to CurrencyInfo("Белорусский рубль", "🇧🇾", "Беларусь"),
        "UAH" to CurrencyInfo("Украинская гривна", "🇺🇦", "Украина"),
        "TRY" to CurrencyInfo("Турецкая лира", "🇹🇷", "Турция"),
        "GEL" to CurrencyInfo("Грузинский лари", "🇬🇪", "Грузия"),
        "AMD" to CurrencyInfo("Армянский драм", "🇦🇲", "Армения"),
        "AZN" to CurrencyInfo("Азербайджанский манат", "🇦🇿", "Азербайджан"),
        "INR" to CurrencyInfo("Индийская рупия", "🇮🇳", "Индия"),
        "AED" to CurrencyInfo("Дирхам ОАЭ", "🇦🇪", "ОАЭ"),
        "THB" to CurrencyInfo("Тайский бат", "🇹🇭", "Таиланд"),
        "VND" to CurrencyInfo("Вьетнамский донг", "🇻🇳", "Вьетнам"),
        "KRW" to CurrencyInfo("Южнокорейская вона", "🇰🇷", "Южная Корея"),
        "HKD" to CurrencyInfo("Гонконгский доллар", "🇭🇰", "Гонконг"),
        "SGD" to CurrencyInfo("Сингапурский доллар", "🇸🇬", "Сингапур"),
        "CAD" to CurrencyInfo("Канадский доллар", "🇨🇦", "Канада"),
        "AUD" to CurrencyInfo("Австралийский доллар", "🇦🇺", "Австралия"),
        "NZD" to CurrencyInfo("Новозеландский доллар", "🇳🇿", "Новая Зеландия"),
        "BRL" to CurrencyInfo("Бразильский реал", "🇧🇷", "Бразилия"),
        "PLN" to CurrencyInfo("Польский злотый", "🇵🇱", "Польша"),
        "CZK" to CurrencyInfo("Чешская крона", "🇨🇿", "Чехия"),
        "SEK" to CurrencyInfo("Шведская крона", "🇸🇪", "Швеция"),
        "NOK" to CurrencyInfo("Норвежская крона", "🇳🇴", "Норвегия"),
        "DKK" to CurrencyInfo("Датская крона", "🇩🇰", "Дания"),
        "HUF" to CurrencyInfo("Венгерский форинт", "🇭🇺", "Венгрия"),
        "RON" to CurrencyInfo("Румынский лей", "🇷🇴", "Румыния"),
        "BGN" to CurrencyInfo("Болгарский лев", "🇧🇬", "Болгария"),
        "RSD" to CurrencyInfo("Сербский динар", "🇷🇸", "Сербия"),
        "ILS" to CurrencyInfo("Израильский шекель", "🇮🇱", "Израиль"),
        "EGP" to CurrencyInfo("Египетский фунт", "🇪🇬", "Египет"),
        "ZAR" to CurrencyInfo("Южноафриканский рэнд", "🇿🇦", "ЮАР"),
        "MXN" to CurrencyInfo("Мексиканское песо", "🇲🇽", "Мексика"),
        "IDR" to CurrencyInfo("Индонезийская рупия", "🇮🇩", "Индонезия"),
        "MYR" to CurrencyInfo("Малайзийский ринггит", "🇲🇾", "Малайзия"),
        "PHP" to CurrencyInfo("Филиппинское песо", "🇵🇭", "Филиппины"),
        "QAR" to CurrencyInfo("Катарский риал", "🇶🇦", "Катар"),
        "SAR" to CurrencyInfo("Саудовский риял", "🇸🇦", "Саудовская Аравия"),
        "KGS" to CurrencyInfo("Киргизский сом", "🇰🇬", "Киргизия"),
        "UZS" to CurrencyInfo("Узбекский сум", "🇺🇿", "Узбекистан"),
        "TJS" to CurrencyInfo("Таджикский сомони", "🇹🇯", "Таджикистан"),
        "TMT" to CurrencyInfo("Туркменский манат", "🇹🇲", "Туркменистан"),
        "MDL" to CurrencyInfo("Молдавский лей", "🇲🇩", "Молдова"),
        "BDT" to CurrencyInfo("Бангладешская така", "🇧🇩", "Бангладеш"),
        "BOB" to CurrencyInfo("Боливийское боливиано", "🇧🇴", "Боливия"),
        "CUP" to CurrencyInfo("Кубинское песо", "🇨🇺", "Куба"),
        "DZD" to CurrencyInfo("Алжирский динар", "🇩🇿", "Алжир"),
        "ETB" to CurrencyInfo("Эфиопский быр", "🇪🇹", "Эфиопия"),
        "IRR" to CurrencyInfo("Иранский риал", "🇮🇷", "Иран"),
        "MMK" to CurrencyInfo("Мьянманский кьят", "🇲🇲", "Мьянма"),
        "MNT" to CurrencyInfo("Монгольский тугрик", "🇲🇳", "Монголия"),
        "NGN" to CurrencyInfo("Нигерийская найра", "🇳🇬", "Нигерия"),
        "OMR" to CurrencyInfo("Оманский риал", "🇴🇲", "Оман"),
        "BHD" to CurrencyInfo("Бахрейнский динар", "🇧🇭", "Бахрейн"),
        "XDR" to CurrencyInfo("СДР (спец. права заимствования)", "🏦", "МВФ"),
    )

    val popular = listOf("RUB", "USD", "EUR", "GBP", "CNY", "KZT", "TRY", "GEL", "AMD")

    fun info(code: String): CurrencyInfo = map[code] ?: CurrencyInfo(code, "🏳️", "")

    fun isKnown(code: String): Boolean = map.containsKey(code)

    fun label(code: String): String {
        val i = info(code)
        return "${i.flag} $code — ${i.name}"
    }

    /** Дополнить каталог кодами от источника (не перетирая описанные вручную). */
    fun register(names: Map<String, String>) {
        for ((code, name) in names) {
            if (code !in map) map[code] = CurrencyInfo(name.ifBlank { code }, "🏳️", "")
        }
    }

    fun search(query: String, limit: Int = 12): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val exact = ArrayList<String>()
        val starts = ArrayList<String>()
        val contains = ArrayList<String>()
        for ((code, i) in map) {
            val lc = code.lowercase()
            val ln = i.name.lowercase()
            val lcountry = i.country.lowercase()
            when {
                lc == q -> exact.add(code)
                lc.startsWith(q) || ln.startsWith(q) || lcountry.startsWith(q) -> starts.add(code)
                q in lc || q in ln || q in lcountry -> contains.add(code)
            }
        }
        return (exact + starts + contains).take(limit)
    }
}
