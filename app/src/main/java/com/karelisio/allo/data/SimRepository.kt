package com.karelisio.allo.data

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.karelisio.allo.util.hasPermission

data class Sim(
    val accountId: String,
    val handle: PhoneAccountHandle,
    /** 0 ou 1. */
    val slot: Int,
    val label: String,
    val color: Int,
    val carrier: String?,
) {
    val shortLabel: String get() = "SIM ${slot + 1}"
}

class SimRepository(private val context: Context) {
    private val telecom get() = context.getSystemService(TelecomManager::class.java)

    @SuppressLint("MissingPermission")
    fun sims(): List<Sim> {
        if (!context.hasPermission(android.Manifest.permission.READ_PHONE_STATE)) return emptyList()
        return runCatching {
            val subs = context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
            telecom.callCapablePhoneAccounts.mapIndexed { index, h ->
                val acc = telecom.getPhoneAccount(h)
                val sub = subs.firstOrNull { it.subscriptionId.toString() == h.id || it.iccId == h.id }
                Sim(
                    accountId = h.id,
                    handle = h,
                    slot = sub?.simSlotIndex ?: index,
                    label = acc?.label?.toString() ?: sub?.displayName?.toString() ?: "SIM ${index + 1}",
                    color = acc?.highlightColor?.takeIf { it != 0 } ?: sub?.iconTint ?: 0,
                    carrier = sub?.carrierName?.toString(),
                )
            }.sortedBy { it.slot }
        }.getOrDefault(emptyList())
    }

    fun slotByAccountId(): Map<String, Int> {
        val s = sims()
        return if (s.size < 2) emptyMap() else s.associate { it.accountId to it.slot }
    }

    fun byId(id: String?): Sim? = id?.let { i -> sims().firstOrNull { it.accountId == i } }

    @SuppressLint("MissingPermission")
    fun voicemailNumber(handle: PhoneAccountHandle?): String? = runCatching {
        if (!context.hasPermission(android.Manifest.permission.READ_PHONE_STATE)) return null
        val h = handle ?: telecom.getDefaultOutgoingPhoneAccount("tel")
        val fromTelecom = h?.let { telecom.getVoiceMailNumber(it) }
        fromTelecom?.takeIf { it.isNotBlank() } ?: run {
            val tm = context.getSystemService(TelephonyManager::class.java)
            (h?.let { tm.createForPhoneAccountHandle(it) } ?: tm)?.voiceMailNumber
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}
