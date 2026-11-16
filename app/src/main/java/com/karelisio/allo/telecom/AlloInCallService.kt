package com.karelisio.allo.telecom

import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.ServiceCompat
import com.karelisio.allo.Graph
import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.notifications.Notifications
import com.karelisio.allo.ui.call.InCallActivity
import kotlinx.coroutines.launch

/** Service d'appel : reçoit les appels de Telecom et pilote l'interface et la notification. */
class AlloInCallService : InCallService() {
    private var foreground = false
    private var proximity: ProximityController? = null
    private var flip: FlipDetector? = null

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
        proximity = ProximityController(this)
    }

    override fun onDestroy() {
        proximity?.release()
        flip?.stop()
        if (CallManager.service === this) CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(call)
        val ringing = CallManager.phaseOf(call) == CallPhase.RINGING
        if (ringing) {
            onIncoming(call)
        } else {
            // Appel sortant : on affiche directement l'écran d'appel.
            launchUi()
        }
    }

    private fun onIncoming(call: Call) {
        Graph.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            val number = call.details?.handle?.schemeSpecificPart
            // Filet de sécurité : la liste noire s'applique même si le filtrage n'a pas été appelé.
            if (ScreeningResults.get(number) == null) {
                val d = runCatching { Graph.block.decide(number) }.getOrNull()
                if (d != null) {
                    ScreeningResults.put(number, d)
                    if (d.action == BlockAction.BLOCK) {
                        call.reject(false, null)
                        Graph.block.log(number.orEmpty(), d.category, d.action)
                        return@launch
                    }
                    if (d.action == BlockAction.SILENCE) silenceRinger()
                    CallManager.publish()
                }
            }
            val s = Graph.settings.current()
            val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
            val useFullScreen = locked || !(s.headsUpWhenUnlocked || s.bulleHandlesIncoming)
            if (useFullScreen && !locked) launchUi()
            if (s.flipToSilence) {
                flip = FlipDetector(this@AlloInCallService) { silenceRinger() }.also { it.start() }
            }
        }
    }

    fun silenceRinger() {
        runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.silenceRinger() }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioState(audioState)
    }

    fun launchUi() {
        startActivity(Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }

    /** Appelé par [CallManager] à chaque changement : notification, proximité, capteurs. */
    fun onCallsChanged(calls: List<CallInfo>) {
        val live = calls.filter { it.isLive }
        if (live.isEmpty()) {
            if (foreground) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foreground = false
            }
            Notifications.cancelCall(this)
            proximity?.update(false)
            flip?.stop(); flip = null
            return
        }
        if (live.none { it.phase == CallPhase.RINGING }) { flip?.stop(); flip = null }
        val main = live.firstOrNull { it.phase == CallPhase.RINGING } ?: live.firstOrNull { it.phase == CallPhase.ACTIVE } ?: live.first()
        val notif = Notifications.buildCall(this, main, CallManager.audio.value, live.size)
        if (!foreground) {
            val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0
            foreground = runCatching {
                ServiceCompat.startForeground(this, Notifications.CALL_ID, notif, type); true
            }.getOrDefault(false)
            if (!foreground) Notifications.notifyCall(this, notif)
        } else {
            Notifications.notifyCall(this, notif)
        }
        val audio = CallManager.audio.value
        val nearEar = main.phase != CallPhase.RINGING && audio.current.route == AudioRoute.EARPIECE
        proximity?.update(nearEar)
    }
}
