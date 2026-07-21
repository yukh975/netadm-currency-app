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
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.yukh.currency.CurrencyApp
import net.yukh.currency.R
import net.yukh.currency.data.Converter
import okhttp3.Request
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Наблюдение за курсом и уведомление-сводка.
 *
 * Модель (с 0.6.3, решение пользователя): фиксированного времени сводки НЕТ.
 * Проверка раз в полчаса (в окне [QUIET_UNTIL_HOUR]–[QUIET_FROM_HOUR] по МСК):
 * как только опубликован новый курс (sourceDate сменился с прошлого уведомления
 * — персистентный маркер), сразу шлём сводку. «Пустых» уведомлений не бывает;
 * ретраи не нужны — следующая проверка сама придёт через полчаса.
 * У ЦБ в нерабочие дни РФ (isdayoff.ru) курс не выходит — сеть не дёргаем.
 *
 * Планирование (с 0.6.4) — через [AlarmScheduler] (AlarmManager
 * `setAndAllowWhileIdle`), а не WorkManager `PeriodicWorkRequest`: периодическую
 * работу душили Doze и энергосбережение OEM (в фоне тишина по несколько дней,
 * уведомление прилетало только при открытии приложения). Сам воркер остаётся
 * одноразовым — его ставит в очередь [enqueueNow] из будильника/при запуске,
 * а сеть и корутины он тянет надёжно (в отличие от короткого ресивера).
 */
class DailyUpdateWorker(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CurrencyApp
        val s = app.settingsStore.current()
        if (!s.notify || s.favorites.isEmpty()) return Result.success()

        // ночью не проверяем и не будим пользователя (окно 08:00–22:59 МСК);
        // «ночное» обновление рыночного курса доедет с первой утренней проверкой
        val hourMsk = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"))
            .get(Calendar.HOUR_OF_DAY)
        if (hourMsk < QUIET_UNTIL_HOUR || hourMsk >= QUIET_FROM_HOUR) return Result.success()

        // Нерабочий день РФ — нового курса ЦБ не будет: не дёргаем источник.
        if (s.source == "cbr" && !isWorkingDayMoscow(app)) return Result.success()

        return try {
            val needed = (s.favorites + s.base).toSet()
            val (table, _) = app.repository.refresh(s.source, needed)

            // «Новый курс» = sourceDate отличается от того, о чём уже уведомляли
            // (персистентный маркер, а не кэш — ручное обновление курса в
            // приложении не «съедает» уведомление).
            val marker = app.settingsStore.lastNotified(s.source)
            val isNew = table.sourceDate.isNotBlank() && table.sourceDate != marker

            if (isNew) {
                // строки уведомления — в выбранном языке приложения
                val loc = app.l10n()
                val rows = Converter.summary(loc, table, s.base, s.favorites, s.smartUnits)
                // динамику в тексте уведомления показываем обычным текстом (без цвета)
                val text = rows.joinToString("\n") { r ->
                    r.text + (r.delta?.let { " ($it)" } ?: "")
                }
                // заголовок — на дату, НА которую действует курс (у ЦБ это завтра)
                val date = Converter.courseDate(table)
                val title = if (date != null) {
                    loc.getString(R.string.notif_title_fmt, date)
                } else {
                    loc.getString(R.string.notif_title)
                }
                if (text.isNotBlank()) {
                    notify(applicationContext, title, text)
                    app.settingsStore.setLastNotified(s.source, table.sourceDate)
                }
            }
            Result.success()
        } catch (e: Exception) {
            // сеть/источник недоступны — просто дождёмся следующей проверки
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

    companion object {
        const val CHANNEL_ID = "daily_rates"
        private const val WORK_NAME = "daily_update"
        private const val LEGACY_RETRY_WORK_NAME = "daily_update_retry"
        private const val NOTIFICATION_ID = 1001

        private const val QUIET_UNTIL_HOUR = 8   // МСК: до 08:00 не проверяем
        private const val QUIET_FROM_HOUR = 23   // МСК: с 23:00 не проверяем

        fun createChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.notif_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }

        /** Включить наблюдение за курсом: поставить будильник (AlarmManager) и
         *  сразу выполнить догоняющую проверку — чтобы при открытии приложения
         *  или включении уведомлений пропущенная в фоне сводка пришла тут же.
         *  Вызывается из onCreate приложения и при включении тумблера. */
        fun ensureScheduled(ctx: Context) {
            // миграция со старых WorkManager-моделей (периодическая + ретрай-цепочка)
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(ctx).cancelUniqueWork(LEGACY_RETRY_WORK_NAME)
            AlarmScheduler.schedule(ctx)
            enqueueNow(ctx)
        }

        /** Разовая проверка курса прямо сейчас (сеть — в воркере, поэтому с
         *  сетевым ограничением). KEEP — если проверка уже в очереди (например
         *  висит без сети), не плодим дубликаты: она отработает, когда сеть
         *  вернётся. Вызывается будильником [RateAlarmReceiver] и из
         *  [ensureScheduled]. */
        fun enqueueNow(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<DailyUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Остановить наблюдение (уведомления выключены в настройках). */
        fun cancel(ctx: Context) {
            AlarmScheduler.cancel(ctx)
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(ctx).cancelUniqueWork(LEGACY_RETRY_WORK_NAME)
        }
    }
}
