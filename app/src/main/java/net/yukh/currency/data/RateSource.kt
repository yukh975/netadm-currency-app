package net.yukh.currency.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

interface RateSource {
    val id: String
    suspend fun fetch(codes: Set<String>): RateTable
}

/** ЦБ РФ — официальный JSON, опорная валюта RUB. */
class CbrSource(private val client: OkHttpClient) : RateSource {
    override val id = "cbr"

    override suspend fun fetch(codes: Set<String>): RateTable = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://www.cbr-xml-daily.ru/daily_json.js").build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("ЦБ РФ: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("ЦБ РФ: пустой ответ")
        }
        val json = JSONObject(body)
        val valute = json.getJSONObject("Valute")
        val rates = HashMap<String, Double>()
        val prev = HashMap<String, Double>()   // предыдущие курсы (для динамики)
        val names = HashMap<String, String>()
        rates["RUB"] = 1.0
        prev["RUB"] = 1.0
        val keys = valute.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val o = valute.getJSONObject(code)
            val nominalRaw = o.optDouble("Nominal", 1.0)
            val nominal = if (nominalRaw == 0.0) 1.0 else nominalRaw
            val value = o.optDouble("Value", 0.0)
            if (value > 0.0) {
                rates[code] = value / nominal
                names[code] = o.optString("Name", code)
                val previous = o.optDouble("Previous", 0.0)
                if (previous > 0.0) prev[code] = previous / nominal
            }
        }
        Currencies.register(names)
        // Date у ЦБ — дата, НА которую действует курс (обычно следующий день)
        RateTable("RUB", rates, json.optString("Date", ""), System.currentTimeMillis(), id, prevRates = prev)
    }
}

/**
 * Рыночный (межбанковский, mid-market) курс через **ExchangeRate-API**
 * (`open.er-api.com`), опорная валюта USD. К Google сервис отношения не имеет —
 * это тот же тип курса, который показывает конвертер Google (в UI подпись
 * «Рыночный курс», см. `source_google`).
 *
 * Идентификатор источника остаётся `google` намеренно: он сохранён в настройках
 * пользователей (DataStore) и в кэше курсов — переименование сбросило бы выбор.
 */
class MarketSource(private val client: OkHttpClient) : RateSource {
    override val id = "google"

    override suspend fun fetch(codes: Set<String>): RateTable = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://open.er-api.com/v6/latest/USD").build()
        val body = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("open.er-api: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("open.er-api: пустой ответ")
        }
        val json = JSONObject(body)
        if (json.optString("result") != "success") throw IOException("open.er-api: ошибка источника")
        val r = json.getJSONObject("rates")
        val rates = HashMap<String, Double>()
        rates["USD"] = 1.0
        val keys = r.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            val perUsd = r.optDouble(code, 0.0)
            if (perUsd > 0.0) rates[code] = 1.0 / perUsd
        }
        RateTable("USD", rates, json.optString("time_last_update_utc", ""), System.currentTimeMillis(), id)
    }
}
