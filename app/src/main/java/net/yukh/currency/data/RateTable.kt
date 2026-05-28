package net.yukh.currency.data

/**
 * Таблица курсов относительно опорной валюты (pivot).
 * rates[code] = сколько единиц pivot стоит 1 единица code.
 */
data class RateTable(
    val pivot: String,
    val rates: Map<String, Double>,
    val sourceDate: String,
    val fetchedAt: Long, // epoch millis
    val source: String,
) {
    fun has(vararg codes: String): Boolean = codes.all { it in rates }

    fun convert(amount: Double, from: String, to: String): Double {
        val rf = rates[from] ?: throw IllegalArgumentException("no rate for $from")
        val rt = rates[to] ?: throw IllegalArgumentException("no rate for $to")
        return amount * rf / rt
    }
}
