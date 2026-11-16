package com.karelisio.allo.telecom

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import com.karelisio.allo.Graph
import com.karelisio.allo.core.BlockDecision
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.NumberLookup
import com.karelisio.allo.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class CallPhase { RINGING, DIALING, CONNECTING, ACTIVE, HOLDING, DISCONNECTING, DISCONNECTED }

data class CallInfo(
    val id: String,
    val phase: CallPhase,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val lookupKey: String?,
    val simLabel: String?,
    val connectTimeMillis: Long,
    val isConference: Boolean,
    val conferenceSize: Int,
    val canHold: Boolean,
    val canMerge: Boolean,
    val canSwap: Boolean,
    val isIncoming: Boolean,
    val label: String?,
    val silenced: Boolean,
) {
    val displayName: String get() = name ?: if (PhoneNumbers.isHidden(number)) "Numéro masqué" else PhoneNumbers.format(number)
    val isLive: Boolean get() = phase != CallPhase.DISCONNECTED && phase != CallPhase.DISCONNECTING
}

enum class AudioRoute { EARPIECE, SPEAKER, WIRED, BLUETOOTH, CAR }

data class AudioOutput(val route: AudioRoute, val name: String, val address: String? = null)

data class AudioUi(
    val muted: Boolean = false,
    val current: AudioOutput = AudioOutput(AudioRoute.EARPIECE, "Écouteur"),
    val outputs: List<AudioOutput> = listOf(AudioOutput(AudioRoute.EARPIECE, "Écouteur"), AudioOutput(AudioRoute.SPEAKER, "Haut-parleur")),
) {
    val speaker: Boolean get() = current.route == AudioRoute.SPEAKER
}

/** État global des appels, alimenté par [AlloInCallService]. */
object CallManager {
    private val calls = mutableListOf<Call>()
    private val lookups = ConcurrentHashMap<String, NumberLookupResult>()
    private val callbacks = HashMap<Call, Call.Callback>()

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val state: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    /** Dernier appel terminé (pour l'animation de fin). */
    val lastEnded = MutableStateFlow<CallInfo?>(null)

    @Volatile var service: AlloInCallService? = null
    private var rawAudio: CallAudioState? = null

    private data class NumberLookupResult(val lookup: NumberLookup?)

    fun add(call: Call) {
        if (calls.contains(call)) return
        calls += call
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
            override fun onChildrenChanged(call: Call, children: MutableList<Call>) = publish()
            override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>) = publish()
            override fun onParentChanged(call: Call, parent: Call?) = publish()
        }
        callbacks[call] = cb
        call.registerCallback(cb)
        resolveContact(call)
        publish()
    }

    fun remove(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        info(call)?.let { lastEnded.value = it.copy(phase = CallPhase.DISCONNECTED) }
        calls -= call
        publish()
    }

    fun callById(id: String): Call? = calls.firstOrNull { idOf(it) == id }

    private fun idOf(call: Call) = System.identityHashCode(call).toString()

    private fun numberOf(call: Call): String {
        val d = call.details ?: return ""
        if (d.handlePresentation != android.telecom.TelecomManager.PRESENTATION_ALLOWED) return ""
        return d.handle?.schemeSpecificPart.orEmpty()
    }

    private fun resolveContact(call: Call) {
        val n = numberOf(call)
        if (n.isEmpty() || lookups.containsKey(PhoneNumbers.key(n))) return
        Graph.scope.launch(Dispatchers.IO) {
            lookups[PhoneNumbers.key(n)] = NumberLookupResult(Graph.contacts.lookup(n))
            publish()
        }
    }

    @Suppress("DEPRECATION")
    fun phaseOf(call: Call): CallPhase {
        val s = if (Build.VERSION.SDK_INT >= 31) call.details.state else call.state
        return when (s) {
            Call.STATE_RINGING, Call.STATE_NEW, Call.STATE_SIMULATED_RINGING -> CallPhase.RINGING
            Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallPhase.DIALING
            Call.STATE_CONNECTING, Call.STATE_SELECT_PHONE_ACCOUNT, Call.STATE_AUDIO_PROCESSING -> CallPhase.CONNECTING
            Call.STATE_ACTIVE -> CallPhase.ACTIVE
            Call.STATE_HOLDING -> CallPhase.HOLDING
            Call.STATE_DISCONNECTING -> CallPhase.DISCONNECTING
            else -> CallPhase.DISCONNECTED
        }
    }

    private fun info(call: Call): CallInfo? {
        val d = call.details ?: return null
        val number = numberOf(call)
        val lk = lookups[PhoneNumbers.key(number)]?.lookup
        val sims = Graph.sims.sims()
        val sim = if (sims.size > 1) sims.firstOrNull { it.accountId == d.accountHandle?.id } else null
        val screening = ScreeningResults.get(number)
        val isIncoming = if (Build.VERSION.SDK_INT >= 29) d.callDirection == Call.Details.DIRECTION_INCOMING else phaseOf(call) == CallPhase.RINGING
        return CallInfo(
            id = idOf(call),
            phase = phaseOf(call),
            number = number,
            name = lk?.name ?: (if (Build.VERSION.SDK_INT >= 30) d.contactDisplayName else null) ?: d.callerDisplayName?.takeIf { it.isNotBlank() },
            photoUri = lk?.photoUri,
            lookupKey = lk?.lookupKey,
            simLabel = sim?.let { "${it.shortLabel} · ${it.label}" },
            connectTimeMillis = d.connectTimeMillis,
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
            conferenceSize = call.children.size,
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMerge = d.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || call.conferenceableCalls.isNotEmpty(),
            canSwap = d.can(Call.Details.CAPABILITY_SWAP_CONFERENCE),
            isIncoming = isIncoming,
            label = screening?.label?.takeIf { lk == null },
            silenced = screening?.action == com.karelisio.allo.core.BlockAction.SILENCE,
        )
    }

    /** Appels de premier niveau (les participants d'une conférence sont regroupés). */
    fun publish() {
        val top = calls.filter { it.parent == null }
        _calls.value = top.mapNotNull { info(it) }
        service?.onCallsChanged(_calls.value)
    }

    fun primary(): Call? {
        val top = calls.filter { it.parent == null }
        return top.firstOrNull { phaseOf(it) == CallPhase.RINGING }
            ?: top.firstOrNull { phaseOf(it) == CallPhase.DIALING || phaseOf(it) == CallPhase.CONNECTING }
            ?: top.firstOrNull { phaseOf(it) == CallPhase.ACTIVE }
            ?: top.firstOrNull()
    }

    fun ringing(): Call? = calls.firstOrNull { it.parent == null && phaseOf(it) == CallPhase.RINGING }
    fun active(): Call? = calls.firstOrNull { it.parent == null && phaseOf(it) == CallPhase.ACTIVE }
    fun held(): Call? = calls.firstOrNull { it.parent == null && phaseOf(it) == CallPhase.HOLDING }

    // ---- Actions -------------------------------------------------------------------------

    fun answer(id: String? = null) { (id?.let { callById(it) } ?: ringing())?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun reject(id: String? = null) { (id?.let { callById(it) } ?: ringing())?.reject(false, null) }
    fun hangup(id: String? = null) {
        val c = id?.let { callById(it) } ?: primary() ?: return
        if (phaseOf(c) == CallPhase.RINGING) c.reject(false, null) else c.disconnect()
    }
    fun hangupAll() { calls.toList().forEach { if (phaseOf(it) == CallPhase.RINGING) it.reject(false, null) else it.disconnect() } }

    fun toggleHold(id: String? = null) {
        val c = id?.let { callById(it) } ?: primary() ?: return
        when (phaseOf(c)) {
            CallPhase.HOLDING -> c.unhold()
            CallPhase.ACTIVE -> c.hold()
            else -> {}
        }
    }

    /** Bascule entre l'appel actif et l'appel en attente. */
    fun swap() {
        val active = active()
        if (active != null && active.details.can(Call.Details.CAPABILITY_SWAP_CONFERENCE)) { active.swapConference(); return }
        held()?.unhold()
    }

    /** Fusionne en conférence. */
    fun merge() {
        val active = active() ?: return
        if (active.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE)) { active.mergeConference(); return }
        val other = active.conferenceableCalls.firstOrNull() ?: held() ?: return
        active.conference(other)
    }

    fun playDtmf(c: Char) {
        val call = active() ?: primary() ?: return
        call.playDtmfTone(c)
        Graph.scope.launch { kotlinx.coroutines.delay(150); call.stopDtmfTone() }
    }

    fun setMuted(muted: Boolean) { service?.setMuted(muted) }
    fun toggleMute() = setMuted(!_audio.value.muted)
    fun toggleSpeaker() {
        if (_audio.value.speaker) {
            val back = _audio.value.outputs.firstOrNull { it.route == AudioRoute.BLUETOOTH || it.route == AudioRoute.CAR || it.route == AudioRoute.WIRED }
                ?: AudioOutput(AudioRoute.EARPIECE, "Écouteur")
            selectOutput(back)
        } else selectOutput(AudioOutput(AudioRoute.SPEAKER, "Haut-parleur"))
    }

    fun selectOutput(o: AudioOutput) {
        val s = service ?: return
        when (o.route) {
            AudioRoute.SPEAKER -> s.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            AudioRoute.EARPIECE -> s.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
            AudioRoute.WIRED -> s.setAudioRoute(CallAudioState.ROUTE_WIRED_HEADSET)
            AudioRoute.BLUETOOTH, AudioRoute.CAR -> {
                val dev = rawAudio?.supportedBluetoothDevices?.firstOrNull { it.address == o.address }
                if (dev != null) s.requestBluetoothAudio(dev) else s.setAudioRoute(CallAudioState.ROUTE_BLUETOOTH)
            }
        }
    }

    @Suppress("MissingPermission")
    fun onAudioState(state: CallAudioState?) {
        rawAudio = state ?: return
        val ctx = Graph.app
        val canBt = Build.VERSION.SDK_INT < 31 || ctx.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
        fun btOutput(d: BluetoothDevice?): AudioOutput {
            val name = if (canBt) runCatching { d?.name }.getOrNull() else null
            val isCar = canBt && runCatching {
                val cls = d?.bluetoothClass?.deviceClass
                cls == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO || cls == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE
            }.getOrDefault(false)
            return AudioOutput(if (isCar) AudioRoute.CAR else AudioRoute.BLUETOOTH, name ?: if (isCar) "Voiture" else "Bluetooth", d?.address)
        }
        val outputs = mutableListOf<AudioOutput>()
        val mask = state.supportedRouteMask
        if (mask and CallAudioState.ROUTE_EARPIECE != 0) outputs += AudioOutput(AudioRoute.EARPIECE, "Écouteur")
        if (mask and CallAudioState.ROUTE_WIRED_HEADSET != 0) outputs += AudioOutput(AudioRoute.WIRED, "Casque filaire")
        if (mask and CallAudioState.ROUTE_SPEAKER != 0) outputs += AudioOutput(AudioRoute.SPEAKER, "Haut-parleur")
        if (mask and CallAudioState.ROUTE_BLUETOOTH != 0) {
            val devs = state.supportedBluetoothDevices
            if (devs.isNullOrEmpty()) outputs += AudioOutput(AudioRoute.BLUETOOTH, "Bluetooth") else devs.forEach { outputs += btOutput(it) }
        }
        val current = when (state.route) {
            CallAudioState.ROUTE_SPEAKER -> AudioOutput(AudioRoute.SPEAKER, "Haut-parleur")
            CallAudioState.ROUTE_WIRED_HEADSET -> AudioOutput(AudioRoute.WIRED, "Casque filaire")
            CallAudioState.ROUTE_BLUETOOTH -> btOutput(state.activeBluetoothDevice)
            else -> AudioOutput(AudioRoute.EARPIECE, "Écouteur")
        }
        _audio.value = AudioUi(state.isMuted, current, outputs)
        service?.onCallsChanged(_calls.value)
    }
}

/** Résultats du filtrage, partagés entre [AlloCallScreeningService] et l'écran d'appel. */
object ScreeningResults {
    private val map = ConcurrentHashMap<String, Pair<Long, BlockDecision>>()
    fun put(number: String?, d: BlockDecision) { map[PhoneNumbers.key(number ?: "")] = System.currentTimeMillis() to d }
    fun get(number: String?): BlockDecision? {
        val e = map[PhoneNumbers.key(number ?: "")] ?: return null
        return if (System.currentTimeMillis() - e.first < 5 * 60_000) e.second else null
    }
}
