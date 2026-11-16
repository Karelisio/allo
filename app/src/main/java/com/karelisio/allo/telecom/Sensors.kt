package com.karelisio.allo.telecom

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

/** Éteint l'écran quand le téléphone est à l'oreille (PROXIMITY_SCREEN_OFF_WAKE_LOCK). */
class ProximityController(context: Context) {
    private val lock: PowerManager.WakeLock? = context.getSystemService(PowerManager::class.java)?.let { pm ->
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK))
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "allo:proximity").apply { setReferenceCounted(false) }
        else null
    }

    fun update(enabled: Boolean) {
        val l = lock ?: return
        runCatching {
            if (enabled && !l.isHeld) l.acquire(3 * 60 * 60 * 1000L)
            else if (!enabled && l.isHeld) l.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    fun release() = update(false)
}

/** Détecte un téléphone retourné face contre la table (coupe la sonnerie). */
class FlipDetector(context: Context, private val onFlip: () -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var fired = false
    private var wasFaceUp = false

    fun start() {
        val acc = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() = sm?.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values[2]
        if (z > 3f) wasFaceUp = true
        if (!fired && z < -8.5f && (wasFaceUp || true)) {
            fired = true
            onFlip()
            stop()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
