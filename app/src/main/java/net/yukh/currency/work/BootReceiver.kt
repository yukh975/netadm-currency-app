package net.yukh.currency.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.yukh.currency.CurrencyApp

/**
 * Переставить будильник проверки курса после перезагрузки устройства или
 * обновления приложения — иначе цепочка [AlarmScheduler] обрывается (все
 * будильники сбрасываются при ребуте) и уведомления замолкают до первого
 * ручного запуска. Ставим только если уведомления включены; немедленную
 * проверку не делаем — сразу после загрузки сеть часто ещё не поднялась,
 * а первый будильник придёт через полчаса.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as CurrencyApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (app.settingsStore.current().notify) AlarmScheduler.schedule(context)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
