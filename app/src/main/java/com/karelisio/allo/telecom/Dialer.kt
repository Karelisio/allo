package com.karelisio.allo.telecom

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.karelisio.allo.Graph
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.Sim
import com.karelisio.allo.util.hasPermission
import com.karelisio.allo.util.safeStart
import com.karelisio.allo.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Dialer {
    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) return rm.isRoleHeld(RoleManager.ROLE_DIALER)
        }
        return context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage == context.packageName
    }

    fun isScreeningRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val rm = context.getSystemService(RoleManager::class.java) ?: return false
        return rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun roleRequestIntent(context: Context, role: String = RoleManager.ROLE_DIALER): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        return if (rm.isRoleAvailable(role)) rm.createRequestRoleIntent(role) else null
    }

    /**
     * Choix de la SIM : SIM préférée du contact, puis SIM par défaut des réglages.
     * Renvoie null s'il faut demander à l'utilisateur (plusieurs SIM, aucune préférence).
     */
    suspend fun resolveAccount(context: Context, lookupKey: String?, number: String?): AccountChoice {
        val sims = Graph.sims.sims()
        if (sims.size <= 1) return AccountChoice.Resolved(sims.firstOrNull()?.handle)
        val key = lookupKey ?: number?.let { Graph.contacts.lookup(it)?.lookupKey }
        key?.let { k ->
            Graph.db.misc().contactSetting(k)?.preferredAccountId?.let { id ->
                sims.firstOrNull { it.accountId == id }?.let { return AccountChoice.Resolved(it.handle) }
            }
        }
        val def = Graph.settings.current().defaultAccountId
        sims.firstOrNull { it.accountId == def }?.let { return AccountChoice.Resolved(it.handle) }
        return AccountChoice.Ask(sims)
    }

    sealed interface AccountChoice {
        data class Resolved(val handle: PhoneAccountHandle?) : AccountChoice
        data class Ask(val sims: List<Sim>) : AccountChoice
    }

    /** Passe l'appel via Telecom. Les codes spéciaux sont transmis au système. */
    @SuppressLint("MissingPermission")
    fun place(context: Context, rawNumber: String, handle: PhoneAccountHandle? = null) {
        val number = rawNumber.trim()
        if (number.isEmpty()) return
        PhoneNumbers.secretCode(number)?.let { code ->
            if (Build.VERSION.SDK_INT >= 26 && runCatching {
                    context.getSystemService(TelephonyManager::class.java).sendDialerSpecialCode(code); true
                }.getOrDefault(false)) return
        }
        if (!context.hasPermission(android.Manifest.permission.CALL_PHONE)) {
            context.toast("Autorisation d'appel manquante")
            return
        }
        val telecom = context.getSystemService(TelecomManager::class.java)
        val extras = Bundle()
        if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle)
        val uri = Uri.fromParts("tel", number, null)
        runCatching { telecom.placeCall(uri, extras) }.onFailure {
            context.safeStart(Intent(Intent.ACTION_CALL, uri))
        }
    }

    /**
     * Messagerie vocale : le numéro renseigné dans les réglages est prioritaire
     * (la SIM fournit parfois le numéro « depuis l'étranger »). Sinon, numéro fourni par la SIM.
     */
    fun voicemail(context: Context, handle: PhoneAccountHandle?) {
        Graph.scope.launch(Dispatchers.Main) {
            val custom = Graph.settings.current().voicemailNumber.trim()
            if (custom.isNotEmpty()) { place(context, custom, handle); return@launch }
            val vm = Graph.sims.voicemailNumber(handle)
            if (vm == null) { context.toast("Aucun numéro de messagerie : renseigne-le dans Réglages → Appels"); return@launch }
            val telecom = context.getSystemService(TelecomManager::class.java)
            val extras = Bundle().apply { if (handle != null) putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle) }
            @SuppressLint("MissingPermission")
            val ok = runCatching { telecom.placeCall(Uri.fromParts("voicemail", "", null), extras); true }.getOrDefault(false)
            if (!ok) place(context, vm, handle)
        }
    }
}
