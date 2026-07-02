package net.yukh.currency.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import net.yukh.currency.ui.MainActivity
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import net.yukh.currency.CurrencyApp
import net.yukh.currency.R
import net.yukh.currency.data.Converter
import okhttp3.Request
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Ежедневное обновление курсов и уведомление-сводка.
 *
 * Поведение (как у бота, но правильнее): уведомление уходит ТОЛЬКО когда курс
 * реально сменился с прошлого уведомления. Если на заданное время курс ещё не
 * обновился — перезапрашиваем каждые [RETRY_MINUTES] минут (до [MAX_RETRIES]
 * попыток) цепочкой one-time воркеров. В нерабочие дни РФ (выходные и офиц.
 * праздники, по isdayoff.ru) не шлём и не повторяем — нового курса ЦБ не будет.
 */
class DailyUpdateWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CurrencyApp
        val s = app.settingsStore.current()
        if (!s.notify || s.favorites.isEmpty()) return Result.success()

        val attempt = inputData.getInt(KEY_ATTEMPT, 0)

        // Нерабочий день РФ — нового курса ЦБ не будет: не шлём и не повторяем.
        if (!isWorkingDayMoscow(app)) return Result.success()

        return try {
            val needed = (s.favorites + s.base).toSet()
            val (table, _) = app.repository.refresh(s.source, needed)

            // «Изменился» = sourceDate отличается от того, о чём уже уведомляли
            // (сравниваем с сохранённой меткой, а не с кэшем — ручное обновление
            // курса в приложении не «съедает» уведомление).
            val marker = app.settingsStore.lastNotified(s.source)
            val isNew = table.sourceDate.isNotBlank() && table.sourceDate != marker

            if (isNew) {
                val rows = Converter.summary(table, s.base, s.favorites, s.smartUnits)
                // динамику в тексте уведомления показываем обычным текстом (без цвета)
                val text = rows.joinToString("\n") { r ->
                    r.text + (r.delta?.let { " ($it)" } ?: "")
                }
                // заголовок — на дату, НА которую действует курс (у ЦБ это завтра)
                val date = Converter.courseDate(table)
                val title = if (date != null) "Курсы валют на $date" else "Курсы валют"
                notify(applicationContext, title, text.ifBlank { "Нет данных" })
                app.settingsStore.setLastNotified(s.source, table.sourceDate)
                Result.success()
            } else {
                // курс ещё не сменился — повтор через RETRY_MINUTES (только рабочий день)
                if (attempt < MAX_RETRIES) scheduleRetry(applicationContext, attempt + 1)
                Result.success()
            }
        } catch (e: Exception) {
            // сеть упала — повторим позже (не считаем это «изменением»)
            if (attempt < MAX_RETRIES) scheduleRetry(applicationContext, attempt + 1)
            Result.success()
        }
    }

    private fun notify(ctx: Context, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // тап по уведомлению открывает приложение и показывает сводку
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SUMMARY, true)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    /** Рабочий ли сегодня день в РФ (по московскому календарю).
     *  isdayoff.ru: «0»=рабочий, «1»=нерабочий, «2»=сокращённый (тоже рабочий).
     *  При недоступности сервиса — откат на проверку субботы/воскресенья. */
    private fun isWorkingDayMoscow(app: CurrencyApp): Boolean {
        val tz = TimeZone.getTimeZone("Europe/Moscow")
        val cal = Calendar.getInstance(tz)
        val ymd = String.format(
            Locale.US, "%04d%02d%02d",
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
        )
        try {
            val req = Request.Builder().url("https://isdayoff.ru/$ymd?cc=ru").build()
            app.httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.trim()
                if (resp.isSuccessful && body != null && body.length == 1 && body[0].isDigit()) {
                    return body == "0" || body == "2"
                }
            }
        } catch (_: Exception) {
            // сеть/сервис недоступны — откат ниже
        }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
    }

    private fun scheduleRetry(ctx: Context, attempt: Int) {
        val request = OneTimeWorkRequestBuilder<DailyUpdateWorker>()
            .setInitialDelay(RETRY_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_ATTEMPT to attempt))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            RETRY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    companion object {
        const val CHANNEL_ID = "daily_rates"
        private const val WORK_NAME = "daily_update"
        private const val RETRY_WORK_NAME = "daily_update_retry"
        private const val KEY_ATTEMPT = "attempt"
        private const val NOTIFICATION_ID = 1001

        // Повтор каждые 30 мин, до 12 попыток (≈6 часов) — как у бота.
        private const val RETRY_MINUTES = 30L
        private const val MAX_RETRIES = 12

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ежедневная сводка курсов",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }

        /** Запланировать ежедневное уведомление на hour:minute (МСК).
         *  REPLACE — чтобы смена времени в настройках сразу применялась. */
        fun ensureScheduled(ctx: Context, hour: Int = 17, minute: Int = 0) {
            val request = PeriodicWorkRequestBuilder<DailyUpdateWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMs(hour, minute), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                request,
            )
        }

        private fun initialDelayMs(hour: Int, minute: Int): Long {
            val tz = TimeZone.getTimeZone("Europe/Moscow")
            val now = Calendar.getInstance(tz)
            val next = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (!next.after(now)) next.add(Calendar.DAY_OF_MONTH, 1)
            return next.timeInMillis - now.timeInMillis
        }
    }
}
