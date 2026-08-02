package net.yukh.currency.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Скачивание APK обновления и запуск системного установщика (flavor `direct`). */
object ApkInstaller {

    /**
     * Скачать APK во внешний files-каталог приложения; вернуть файл.
     *
     * [onProgress] `(доля 0..1 или null, скачано_байт, всего_байт_или_-1)` —
     * зовётся из фонового потока по мере чтения (раз в ~128 КБ, чтобы не
     * заваливать UI). Доля `null`, если сервер не прислал `Content-Length` —
     * тогда показываем мегабайты без процентов.
     *
     * Собственный клиент с увеличенным read-timeout: APK ~11 МБ, дефолтные
     * 10 с на чтение на медленном канале рвут загрузку.
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: ((Float?, Long, Long) -> Unit)? = null,
    ): File =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("пустой ответ")
                val total = body.contentLength()   // -1, если сервер не прислал длину
                val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
                val file = File(dir, "currency-converter-update.apk")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastReport = 0L
                        onProgress?.invoke(if (total > 0) 0f else null, 0L, total)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (done - lastReport >= 128 * 1024) {
                                lastReport = done
                                onProgress?.invoke(
                                    if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null,
                                    done, total,
                                )
                            }
                        }
                        onProgress?.invoke(if (total > 0) 1f else null, done, total)
                    }
                }
                file
            }
        }

    /** Есть ли право ставить APK (Android 8+ требует «Установка неизвестных приложений»). */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** Открыть системный экран выдачи права на установку из этого источника. */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /** Запустить установку скачанного APK через FileProvider. */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
