package net.yukh.currency.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.yukh.currency.CurrencyApp

/**
 * Срабатывание будильника [AlarmScheduler]: перепланировать следующую проверку
 * и запустить текущую.
 *
 * Всё завязано на «уведомления включены»: если пользователь их выключил,
 * цепочка будильников обрывается (следующий не ставится) — так же надёжно, как
 * прямой [AlarmScheduler.cancel]. Гейт по настройке требует чтения DataStore
 * (асинхронно), поэтому берём [goAsync] на время короткой корутины (без сети —
 * сеть уже внутри воркера).
 */
class RateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as CurrencyApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (app.settingsStore.current().notify) {
                    AlarmScheduler.schedule(context)        // следующая проверка через 30 мин
                    DailyUpdateWorker.enqueueNow(context)   // текущая проверка (сеть — в воркере)
                }
            } catch (_: Exception) {
                // не роняем систему из ресивера; следующий будильник переставит schedule()
            } finally {
                pending.finish()
            }
        }
    }
}
