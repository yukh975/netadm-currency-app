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
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.yukh.currency.CurrencyApp
import net.yukh.currency.R
import net.yukh.currency.data.Converter
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** Ежедневное обновление курсов и уведомление-сводка (~17:00 МСК). */
class DailyUpdateWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CurrencyApp
        val s = app.settingsStore.current()
        if (!s.notify || s.favorites.isEmpty()) return Result.success()
        return try {
            val needed = (s.favorites + s.base).toSet()
            val (table, _) = app.repository.refresh(s.source, needed)
            val lines = Converter.summary(table, s.base, s.favorites, s.smartUnits)
            val text = lines.joinToString("\n")
            notify(applicationContext, "Курсы на сегодня", text.ifBlank { "Нет данных" })
            Result.success()
        } catch (e: Exception) {
            Result.retry()
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

    companion object {
        const val CHANNEL_ID = "daily_rates"
        private const val WORK_NAME = "daily_update"
        private const val NOTIFICATION_ID = 1001

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
