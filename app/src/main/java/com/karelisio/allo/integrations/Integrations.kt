package com.karelisio.allo.integrations

import android.content.Context
import android.content.Intent
import com.karelisio.allo.data.ContactDetail
import com.karelisio.allo.util.isPackageInstalled
import com.karelisio.allo.util.safeStart
import com.karelisio.allo.util.toast

/** Écho (com.karelisio.echo) : bouton « Pense à toi » sur la fiche des contacts choisis. */
object Echo {
    const val PACKAGE = "com.karelisio.echo"
    const val ACTION_THINKING_OF_YOU = "com.karelisio.echo.action.THINKING_OF_YOU"

    fun isInstalled(ctx: Context) = ctx.isPackageInstalled(PACKAGE)

    fun thinkingOfYou(ctx: Context, d: ContactDetail) {
        val number = (d.phones.firstOrNull { it.isPrimary } ?: d.phones.firstOrNull())?.number
        fun Intent.withExtras() = apply {
            putExtra("contact_name", d.name)
            putExtra("contact_lookup_key", d.lookupKey)
            putExtra("phone_number", number)
            putExtra("source", "com.karelisio.allo")
        }
        // Intent explicite vers Écho ; à défaut, son activité principale avec les mêmes extras.
        val explicit = Intent(ACTION_THINKING_OF_YOU).setPackage(PACKAGE).withExtras()
        if (ctx.packageManager.resolveActivity(explicit, 0) != null && ctx.safeStart(explicit)) return
        val launch = ctx.packageManager.getLaunchIntentForPackage(PACKAGE)?.withExtras()
        if (launch == null || !ctx.safeStart(launch)) ctx.toast("Écho n'est pas installé")
    }
}

/**
 * Bulle (com.karelisio.bulle) : Allô publie des notifications CallStyle standards
 * (CATEGORY_CALL, actions Répondre / Refuser / Raccrocher) que Bulle sait reconnaître.
 */
object Bulle {
    const val PACKAGE = "com.karelisio.bulle"
    fun isInstalled(ctx: Context) = ctx.isPackageInstalled(PACKAGE)
}
