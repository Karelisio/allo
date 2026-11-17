package com.karelisio.allo.util

import android.content.Context
import com.karelisio.allo.core.PhoneNumbers
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Journal de diagnostic local : les 80 derniers événements d'appel (sonnerie, décroché,
 * filtrage, audio…), pour comprendre un problème sans crash. Numéros masqués, rien n'est envoyé.
 */
object CallDiagnostics {
    private const val MAX = 80
    private val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
    private var ctx: Context? = null
    private val lock = Any()

    fun init(context: Context) { ctx = context.applicationContext }

    private fun prefs() = ctx?.getSharedPreferences("call_diagnostics", Context.MODE_PRIVATE)

    /** « 06 12 34 56 78 » → « …5678 ». */
    fun mask(number: String?): String {
        if (PhoneNumbers.isHidden(number)) return "masqué"
        val d = PhoneNumbers.clean(number).filter { it.isDigit() }
        return if (d.length <= 4) d else "…" + d.takeLast(4)
    }

    fun log(event: String) {
        val p = prefs() ?: return
        synchronized(lock) {
            val lines = all().toMutableList()
            lines.add(0, "${LocalDateTime.now().format(fmt)}  $event")
            p.edit().putString("lines", lines.take(MAX).joinToString("\n")).apply()
        }
    }

    fun all(): List<String> = prefs()?.getString("lines", "").orEmpty().split('\n').filter { it.isNotBlank() }

    fun clear() { prefs()?.edit()?.remove("lines")?.apply() }
}
