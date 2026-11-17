package com.karelisio.allo.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.karelisio.allo.Graph
import com.karelisio.allo.MainActivity
import com.karelisio.allo.R
import com.karelisio.allo.core.BlockDecision
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.telecom.AudioUi
import com.karelisio.allo.telecom.CallInfo
import com.karelisio.allo.telecom.CallPhase
import com.karelisio.allo.ui.call.InCallActivity
import com.karelisio.allo.ui.CallTrampolineActivity

object Notifications {
    const val CH_INCOMING = "incoming_calls"
    const val CH_ONGOING = "ongoing_calls"
    const val CH_MISSED = "missed_calls"
    const val CH_BLOCKED = "blocked_calls"
    const val CH_REMINDER = "call_reminders"
    const val CH_UPDATE = "updates"

    const val CALL_ID = 1001
    const val BLOCKED_ID = 1002
    const val MISSED_SUMMARY_ID = 1003
    private const val GROUP_MISSED = "allo.missed"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_INCOMING, "Appels entrants", NotificationManager.IMPORTANCE_HIGH).apply {
                // La sonnerie est jouée par Telecom : le canal reste silencieux.
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_ONGOING, "Appel en cours", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel(CH_MISSED, "Appels manqués", NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_BLOCKED, "Appels bloqués", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
            NotificationChannel(CH_REMINDER, "Rappels d'appel", NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CH_UPDATE, "Mises à jour", NotificationManager.IMPORTANCE_LOW),
        ))
    }

    private fun flags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    fun broadcast(context: Context, action: String, number: String? = null, code: Int = action.hashCode()): PendingIntent =
        PendingIntent.getBroadcast(context, code + (number?.hashCode() ?: 0),
            Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(CallActionReceiver.EXTRA_NUMBER, number), flags())

    private fun inCallPi(context: Context, action: String? = null, code: Int = 0): PendingIntent =
        PendingIntent.getActivity(context, code,
            Intent(context, InCallActivity::class.java).setAction(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags())

    fun callPi(context: Context, number: String, code: Int = number.hashCode()): PendingIntent =
        PendingIntent.getActivity(context, code,
            Intent(context, CallTrampolineActivity::class.java).setAction(Intent.ACTION_CALL).setData(Uri.fromParts("tel", number, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags())

    fun smsPi(context: Context, number: String): PendingIntent =
        PendingIntent.getActivity(context, number.hashCode() + 7,
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), flags())

    private fun mainPi(context: Context, tab: String): PendingIntent =
        PendingIntent.getActivity(context, tab.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), flags())

    fun loadBitmap(context: Context, uri: String?, size: Int = 256): Bitmap? = runCatching {
        if (uri == null) return null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= size && opts.outHeight / (sample * 2) >= size) sample *= 2
        context.contentResolver.openInputStream(Uri.parse(uri))?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    private fun person(context: Context, c: CallInfo): Person {
        val b = Person.Builder().setName(c.displayName).setImportant(true)
        loadBitmap(context, c.photoUri)?.let { b.setIcon(IconCompat.createWithBitmap(it)) }
        if (c.number.isNotEmpty()) b.setUri("tel:${c.number}")
        return b.build()
    }

    /** Notification CallStyle standard (entrant ou en cours) : reconnue par Bulle et par le système. */
    fun buildCall(context: Context, c: CallInfo, audio: AudioUi, liveCount: Int): Notification {
        val p = person(context, c)
        val incoming = c.phase == CallPhase.RINGING
        val b = NotificationCompat.Builder(context, if (incoming) CH_INCOMING else CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(!incoming)
            .setPriority(if (incoming) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(inCallPi(context))
            .addPerson(p)
        if (incoming) {
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(p,
                broadcast(context, CallActionReceiver.ACTION_DECLINE),
                inCallPi(context, InCallActivity.ACTION_ANSWER, 1)))
            b.setFullScreenIntent(inCallPi(context, null, 2), true)
            val sub = listOfNotNull(c.label, c.simLabel).joinToString(" · ")
            b.setContentText(sub.ifEmpty { if (c.name != null) PhoneNumbers.format(c.number) else "Appel entrant" })
        } else {
            b.setStyle(NotificationCompat.CallStyle.forOngoingCall(p, broadcast(context, CallActionReceiver.ACTION_HANGUP)))
            b.addAction(NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_stat_speaker),
                if (audio.speaker) "Haut-parleur ✓" else "Haut-parleur",
                broadcast(context, CallActionReceiver.ACTION_SPEAKER)).build())
            b.addAction(NotificationCompat.Action.Builder(
                IconCompat.createWithResource(context, R.drawable.ic_stat_mute),
                if (audio.muted) "Muet ✓" else "Muet",
                broadcast(context, CallActionReceiver.ACTION_MUTE)).build())
            when (c.phase) {
                CallPhase.ACTIVE -> if (c.connectTimeMillis > 0) b.setWhen(c.connectTimeMillis).setUsesChronometer(true).setShowWhen(true)
                CallPhase.HOLDING -> b.setContentText("En attente")
                else -> b.setContentText("Appel en cours…")
            }
            if (liveCount > 1) b.setSubText("$liveCount appels")
        }
        return b.build()
    }

    const val PREVIEW_ID = 1004

    /** Appel entrant de test (Fiabilité) : même chemin que les vrais appels (CallStyle + plein écran). */
    fun notifyPreviewIncoming(context: Context) {
        val open = PendingIntent.getActivity(context, 40,
            Intent(context, InCallActivity::class.java).putExtra(InCallActivity.EXTRA_PREVIEW, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), flags())
        val p = Person.Builder().setName("Test Allô").setImportant(true).build()
        val n = NotificationCompat.Builder(context, CH_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(p, broadcast(context, CallActionReceiver.ACTION_PREVIEW_DISMISS), open))
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setContentText("Appel de test")
            .setTimeoutAfter(30_000)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(PREVIEW_ID, n) }
    }

    fun notifyCall(context: Context, n: Notification) = runCatching {
        NotificationManagerCompat.from(context).notify(CALL_ID, n)
    }

    fun cancelCall(context: Context) = NotificationManagerCompat.from(context).cancel(CALL_ID)

    /** Reconstruit les notifications d'appels manqués à partir du journal. */
    fun refreshMissed(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val missed = Graph.callLog.newMissed()
        val active = context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.notification.group == GROUP_MISSED }
        if (missed.isEmpty()) { active.forEach { nm.cancel(it.id) }; return }
        val byNumber = missed.groupBy { it.key }
        val keepIds = mutableSetOf<Int>()
        byNumber.values.forEach { calls ->
            val first = calls.first()
            val name = Graph.contacts.lookup(first.number)
            val title = name?.name ?: if (PhoneNumbers.isHidden(first.number)) "Numéro masqué" else PhoneNumbers.format(first.number)
            val id = 2000 + (first.key.hashCode() and 0xffff)
            keepIds += id
            val b = NotificationCompat.Builder(context, CH_MISSED)
                .setSmallIcon(R.drawable.ic_stat_missed)
                .setContentTitle(title)
                .setContentText(if (calls.size > 1) "${calls.size} appels manqués" else "Appel manqué")
                .setWhen(first.date).setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setGroup(GROUP_MISSED)
                .setAutoCancel(true)
                .setContentIntent(mainPi(context, MainActivity.TAB_LOG))
                .setDeleteIntent(broadcast(context, CallActionReceiver.ACTION_MISSED_SEEN))
            loadBitmap(context, name?.photoUri)?.let { b.setLargeIcon(it) }
            if (!PhoneNumbers.isHidden(first.number)) {
                b.addAction(R.drawable.ic_stat_call, "Rappeler", callPi(context, first.number))
                b.addAction(R.drawable.ic_stat_sms, "SMS", smsPi(context, first.number))
                if (name == null) b.addAction(R.drawable.ic_stat_block, "Bloquer", broadcast(context, CallActionReceiver.ACTION_BLOCK, first.number))
            }
            runCatching { nm.notify(id, b.build()) }
        }
        val summary = NotificationCompat.Builder(context, CH_MISSED)
            .setSmallIcon(R.drawable.ic_stat_missed)
            .setContentTitle("${missed.size} appels manqués")
            .setGroup(GROUP_MISSED).setGroupSummary(true).setAutoCancel(true)
            .setContentIntent(mainPi(context, MainActivity.TAB_LOG))
            .build()
        keepIds += MISSED_SUMMARY_ID
        runCatching { nm.notify(MISSED_SUMMARY_ID, summary) }
        active.filter { it.id !in keepIds }.forEach { nm.cancel(it.id) }
    }

    fun cancelMissed(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.activeNotifications.filter { it.notification.group == GROUP_MISSED }.forEach { nm.cancel(it.id) }
    }

    fun notifyBlocked(context: Context, number: String?, d: BlockDecision) {
        val what = if (number.isNullOrBlank()) "Numéro masqué" else PhoneNumbers.format(number)
        val b = NotificationCompat.Builder(context, CH_BLOCKED)
            .setSmallIcon(R.drawable.ic_stat_block)
            .setContentTitle("Appel bloqué")
            .setContentText("$what · ${d.label ?: ""}".trim(' ', '·'))
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(mainPi(context, MainActivity.TAB_BLOCKED))
        if (!number.isNullOrBlank()) b.addAction(R.drawable.ic_stat_call, "Autoriser", broadcast(context, CallActionReceiver.ACTION_ALLOW, number))
        runCatching { NotificationManagerCompat.from(context).notify(BLOCKED_ID, b.build()) }
    }

    fun notifyReminder(context: Context, number: String, name: String?) {
        val id = 3000 + (number.hashCode() and 0xffff)
        val b = NotificationCompat.Builder(context, CH_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("Rappeler ${name ?: PhoneNumbers.format(number)}")
            .setContentText("Tu avais demandé à rappeler ce numéro.")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(callPi(context, number, id))
            .addAction(R.drawable.ic_stat_call, "Appeler", callPi(context, number, id + 1))
            .addAction(R.drawable.ic_stat_sms, "SMS", smsPi(context, number))
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }
}
