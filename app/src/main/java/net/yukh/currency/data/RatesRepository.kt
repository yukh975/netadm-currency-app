package net.yukh.currency.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject

/** Кэширование курсов (TTL 3 ч, переживает рестарт), конвертация. */
class RatesRepository(
    private val settings: SettingsStore,
    client: OkHttpClient,
) {
    private val sources: Map<String, RateSource> = mapOf(
        "cbr" to CbrSource(client),
        "google" to GoogleSource(client),
    )
    private val ttlMs = 3 * 60 * 60 * 1000L
    private val cache = HashMap<String, RateTable>()
    private val mutex = Mutex()

    private fun isFresh(t: RateTable?, needed: Set<String>): Boolean {
        if (t == null) return false
        if (System.currentTimeMillis() - t.fetchedAt > ttlMs) return false
        return t.has(*needed.toTypedArray())
    }

    suspend fun getTable(source: String, needed: Set<String>, force: Boolean = false): RateTable {
        val src = sources[source] ?: sources.getValue("cbr")
        val id = src.id
        if (cache[id] == null) {
            settings.loadCache(id)?.let { runCatching { cache[id] = deserialize(it) } }
        }
        val cached = cache[id]
        if (!force && isFresh(cached, needed)) return cached!!

        return mutex.withLock {
            val c2 = cache[id]
            if (!force && isFresh(c2, needed)) return@withLock c2!!
            val fresh = try {
                src.fetch(needed)
            } catch (e: Exception) {
                if (c2 != null) return@withLock c2 else throw e
            }
            val merged = if (c2 != null && id == "google") {
                fresh.copy(rates = c2.rates + fresh.rates)
            } else fresh
            cache[id] = merged
            settings.saveCache(id, serialize(merged))
            merged
        }
    }

    /** Текущая закэшированная таблица (без обращения в сеть), или null. */
    suspend fun cachedTable(source: String): RateTable? {
        val id = sources[source]?.id ?: "cbr"
        if (cache[id] == null) {
            settings.loadCache(id)?.let { runCatching { cache[id] = deserialize(it) } }
        }
        return cache[id]
    }

    suspend fun refresh(source: String, needed: Set<String>): Pair<RateTable, Boolean> {
        val prev = cache[sources[source]?.id ?: "cbr"]
        val table = getTable(source, needed, force = true)
        val changed = prev == null || prev.rates != table.rates || prev.sourceDate != table.sourceDate
        return table to changed
    }

    private fun serialize(t: RateTable): String {
        val o = JSONObject()
        o.put("pivot", t.pivot)
        o.put("sourceDate", t.sourceDate)
        o.put("fetchedAt", t.fetchedAt)
        o.put("source", t.source)
        o.put("rates", JSONObject(t.rates.mapValues { it.value as Any }))
        return o.toString()
    }

    private fun deserialize(s: String): RateTable {
        val o = JSONObject(s)
        val r = o.getJSONObject("rates")
        val rates = HashMap<String, Double>()
        val keys = r.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            rates[k] = r.getDouble(k)
        }
        return RateTable(
            pivot = o.optString("pivot", "RUB"),
            rates = rates,
            sourceDate = o.optString("sourceDate", ""),
            fetchedAt = o.optLong("fetchedAt", 0L),
            source = o.optString("source", "cbr"),
        )
    }
}
