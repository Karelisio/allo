package com.karelisio.allo.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.TelecomManager
import androidx.core.app.NotificationManagerCompat
import com.karelisio.allo.Graph
import com.karelisio.allo.telecom.CallManager
import com.karelisio.allo.util.toast
import kotlinx.coroutines.launch

/** Actions des notifications (raccrocher, haut-parleur, muet, bloquer, autoriser…). */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_NUMBER)
        when (intent.action) {
            ACTION_DECLINE -> CallManager.reject()
            ACTION_HANGUP -> CallManager.hangup()
            ACTION_SPEAKER -> CallManager.toggleSpeaker()
            ACTION_MUTE -> CallManager.toggleMute()
            ACTION_MISSED_SEEN -> Graph.scope.launch { Graph.callLog.markMissedRead() }
            ACTION_BLOCK -> if (number != null) {
                val pending = goAsync()
                Graph.scope.launch {
                    Graph.block.block(number)
                    Notifications.refreshMissed(context)
                    pending.finish()
                }
                context.toast("Numéro bloqué")
            }
            ACTION_ALLOW -> if (number != null) {
                val pending = goAsync()
                Graph.scope.launch {
                    Graph.block.allow(number)
                    NotificationManagerCompat.from(context).cancel(Notifications.BLOCKED_ID)
                    pending.finish()
                }
                context.toast("Numéro autorisé")
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val ACTION_DECLINE = "com.karelisio.allo.DECLINE"
        const val ACTION_HANGUP = "com.karelisio.allo.HANGUP"
        const val ACTION_SPEAKER = "com.karelisio.allo.SPEAKER"
        const val ACTION_MUTE = "com.karelisio.allo.MUTE"
        const val ACTION_BLOCK = "com.karelisio.allo.BLOCK"
        const val ACTION_ALLOW = "com.karelisio.allo.ALLOW"
        const val ACTION_MISSED_SEEN = "com.karelisio.allo.MISSED_SEEN"
    }
}

/** Telecom délègue l'affichage des appels manqués à l'appli Téléphone par défaut. */
class MissedCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelecomManager.ACTION_SHOW_MISSED_CALLS_NOTIFICATION) return
        val pending = goAsync()
        Graph.scope.launch {
            kotlinx.coroutines.delay(600) // le journal peut être écrit juste après la diffusion
            if (Graph.settings.current().missedNotification) Notifications.refreshMissed(context)
            else Notifications.cancelMissed(context)
            pending.finish()
        }
    }
}

/** « Rappelle-moi dans 10 min ». */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val number = intent.getStringExtra(EXTRA_NUMBER) ?: return
        forget(context, number)
        Notifications.notifyReminder(context, number, intent.getStringExtra(EXTRA_NAME))
    }

    companion object {
        const val EXTRA_NUMBER = "number"
        const val EXTRA_NAME = "name"

        private fun prefs(c: Context) = c.getSharedPreferences("reminders", Context.MODE_PRIVATE)

        /** Rappels en attente : « horodatage|numéro|nom ». */
        fun pending(c: Context): List<Triple<Long, String, String?>> = prefs(c).getStringSet("items", emptySet()).orEmpty().mapNotNull {
            val p = it.split('|', limit = 3)
            val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            Triple(t, p.getOrNull(1) ?: return@mapNotNull null, p.getOrNull(2)?.ifEmpty { null })
        }

        private fun save(c: Context, items: List<Triple<Long, String, String?>>) =
            prefs(c).edit().putStringSet("items", items.map { "${it.first}|${it.second}|${it.third.orEmpty()}" }.toSet()).apply()

        fun forget(c: Context, number: String) = save(c, pending(c).filter { it.second != number })

        /** Après un redémarrage, les alarmes sont perdues : on les reprogramme (ou on notifie si l'heure est passée). */
        fun reschedule(c: Context) {
            val now = System.currentTimeMillis()
            pending(c).forEach { (at, number, name) ->
                if (at <= now) { forget(c, number); Notifications.notifyReminder(c, number, name) }
                else setAlarm(c, number, name, at)
            }
        }

        fun schedule(context: Context, number: String, name: String?, delayMin: Int) {
            val at = System.currentTimeMillis() + delayMin * 60_000L
            save(context, pending(context).filter { it.second != number } + Triple(at, number, name))
            setAlarm(context, number, name, at)
        }

        private fun setAlarm(context: Context, number: String, name: String?, at: Long) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(context, number.hashCode(),
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_NUMBER, number).putExtra(EXTRA_NAME, name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            runCatching {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }
}

/** Reprogramme les rappels après un redémarrage. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            ReminderReceiver.reschedule(context)
        }
    }
}
