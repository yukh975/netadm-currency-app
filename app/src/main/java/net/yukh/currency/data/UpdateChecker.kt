package net.yukh.currency.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.yukh.currency.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * Проверка обновлений из публичного релиза GitLab (только для flavor `direct`).
 *
 * Тянет последний Release проекта, берёт тег версии и ссылку на `.apk`-ассет,
 * сравнивает SemVer с текущей версией сборки. Ничего не делает, если апдейтер
 * выключен ([BuildConfig.UPDATE_ENABLED] == false — например в Play-сборке).
 */
object UpdateChecker {

    data class Update(val versionName: String, val apkUrl: String, val notes: String)

    /** Вернуть [Update], если доступна более новая версия, иначе null. */
    suspend fun check(client: OkHttpClient, currentVersion: String): Update? =
        withContext(Dispatchers.IO) {
            if (!BuildConfig.UPDATE_ENABLED || BuildConfig.UPDATE_RELEASES_URL.isBlank()) {
                return@withContext null
            }
            val req = Request.Builder().url(BuildConfig.UPDATE_RELEASES_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                val arr = JSONArray(body)
                if (arr.length() == 0) return@withContext null
                val rel = arr.getJSONObject(0)
                val remote = rel.optString("tag_name").trimStart('v', 'V').trim()
                if (remote.isBlank() || compareSemVer(remote, currentVersion) <= 0) {
                    return@withContext null
                }
                val links = rel.optJSONObject("assets")?.optJSONArray("links")
                var apk = ""
                if (links != null) {
                    for (i in 0 until links.length()) {
                        val url = links.getJSONObject(i).optString("url")
                        if (url.endsWith(".apk", ignoreCase = true)) { apk = url; break }
                    }
                }
                if (apk.isBlank()) return@withContext null
                // Список изменений берём ТОЛЬКО из CHANGELOG.md (публичный raw).
                // Описание релиза не используем — там служебный текст. Если раздел
                // не найден, оставляем пусто (в UI покажется «недоступен»).
                val notes = fetchChangelog(client, remote) ?: ""
                Update(remote, apk, notes)
            }
        }

    /** Достать раздел CHANGELOG.md для версии [version] и слегка очистить markdown. */
    private fun fetchChangelog(client: OkHttpClient, version: String): String? {
        if (BuildConfig.UPDATE_CHANGELOG_URL.isBlank()) return null
        return try {
            val req = Request.Builder().url(BuildConfig.UPDATE_CHANGELOG_URL).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val md = resp.body?.string() ?: return null
                extractSection(md, version)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Вернуть текст раздела «## [version] …» до следующего «## », с лёгкой
     *  очисткой markdown (для показа в диалоге). null, если раздела нет. */
    private fun extractSection(md: String, version: String): String? {
        val lines = md.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("## [$version]") }
        if (start < 0) return null
        val body = StringBuilder()
        for (i in (start + 1) until lines.size) {
            val raw = lines[i]
            if (raw.trimStart().startsWith("## ")) break
            body.append(raw).append('\n')
        }
        val cleaned = body.toString()
            .replace("**", "")
            .replace("`", "")
            .lines()
            .joinToString("\n") { line ->
                val t = line.trim()
                when {
                    t.startsWith("### ") -> t.removePrefix("### ")
                    t.startsWith("- ") -> "• " + t.removePrefix("- ")
                    else -> line.trimEnd()
                }
            }
            .trim()
        return cleaned.ifBlank { null }
    }

    /** Сравнить версии вида «1.2.3» покомпонентно: >0 если a новее b, 0 равны, <0 старее. */
    fun compareSemVer(a: String, b: String): Int {
        val pa = a.split('.')
        val pb = b.split('.')
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrNull(i)?.toIntOrNull() ?: 0
            val y = pb.getOrNull(i)?.toIntOrNull() ?: 0
            if (x != y) return x - y
        }
        return 0
    }
}
