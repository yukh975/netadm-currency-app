package net.yukh.currency.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Планирование проверок курса через [AlarmManager].
 *
 * Почему не WorkManager: `PeriodicWorkRequest` — отложенная работа, её душат
 * Doze (батчинг в окна обслуживания) и агрессивное энергосбережение OEM.
 * Симптом (0.6.3 и раньше): в фоне тишина по несколько дней, а при открытии
 * приложения уведомление прилетает сразу. `setAndAllowWhileIdle` просыпается
 * даже в Doze (инexactно, но раз в ~9–15 мин на приложение — для проверки раз
 * в полчаса с запасом). Точный будильник (`setExactAndAllowWhileIdle`) не берём
 * намеренно: на Android 12+ он требует разрешения SCHEDULE_EXACT_ALARM, а раз в
 * день курс не нужен «секунда в секунду».
 *
 * Будильник разовый и **перепланирует сам себя** в [RateAlarmReceiver] после
 * каждого срабатывания; при перезагрузке его переставляет [BootReceiver].
 * Force-stop приложения средствами OEM это, как и любой клиентский механизм,
 * пережить не может — для этого пользователю нужно снять приложение с
 * оптимизации батареи (кнопка в настройках).
 */
object AlarmScheduler {

    private const val REQUEST_CODE = 2001
    private const val CHECK_INTERVAL_MS = 30L * 60L * 1000L  // 30 минут

    private fun pendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, RateAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Поставить следующую проверку через [CHECK_INTERVAL_MS]. Вызывается при
     *  включении уведомлений, при старте приложения, после каждого срабатывания
     *  и после перезагрузки. Идемпотентно: FLAG_UPDATE_CURRENT переиспользует
     *  тот же PendingIntent, так что дубликатов будильников не копится. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + CHECK_INTERVAL_MS
        // RTC_WAKEUP — разбудить устройство; AllowWhileIdle — сработать в Doze.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(ctx))
    }

    /** Снять будильник (уведомления выключены в настройках). */
    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(ctx))
    }
}
