package com.karelisio.allo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.core.BlockConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.store by preferencesDataStore("allo_settings")

/** Préférences de l'appli (DataStore). Les clés techniques (MAJ, onboarding) sont à part. */
@Serializable
data class AppSettings(
    val keyVibration: Boolean = true,
    val dtmfTones: Boolean = false,
    val voicemailFallback: String = "888",
    /** Identifiant du PhoneAccount par défaut, « » = demander à chaque appel. */
    val defaultAccountId: String = "",
    val callbackDelayMin: Int = 10,
    val headsUpWhenUnlocked: Boolean = true,
    val flipToSilence: Boolean = false,
    val bulleHandlesIncoming: Boolean = false,
    val blockedNotification: Boolean = true,
    val missedNotification: Boolean = true,
    val blockConfig: BlockConfig = BlockConfig(),
    val echoContacts: Set<String> = emptySet(),
    val manualChecks: Set<String> = emptySet(),
)

class SettingsStore(private val context: Context) {
    private object K {
        val vib = booleanPreferencesKey("key_vibration")
        val dtmf = booleanPreferencesKey("dtmf_tones")
        val vm = stringPreferencesKey("voicemail_fallback")
        val acc = stringPreferencesKey("default_account")
        val delay = intPreferencesKey("callback_delay")
        val headsUp = booleanPreferencesKey("heads_up_unlocked")
        val flip = booleanPreferencesKey("flip_silence")
        val bulle = booleanPreferencesKey("bulle_incoming")
        val blockedNotif = booleanPreferencesKey("blocked_notif")
        val missedNotif = booleanPreferencesKey("missed_notif")
        val bTele = stringPreferencesKey("block_tele")
        val bHidden = stringPreferencesKey("block_hidden")
        val bUnknown = stringPreferencesKey("block_unknown")
        val bBlack = stringPreferencesKey("block_black")
        val echo = stringSetPreferencesKey("echo_contacts")
        val manual = stringSetPreferencesKey("manual_checks")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skipVersion = stringPreferencesKey("skip_version")
        val roleWarnDismissed = booleanPreferencesKey("role_warn_dismissed")
    }

    private fun Preferences.action(k: Preferences.Key<String>, def: BlockAction) =
        this[k]?.let { runCatching { BlockAction.valueOf(it) }.getOrNull() } ?: def

    val settings: Flow<AppSettings> = context.store.data.map { fromPrefs(it) }

    private fun fromPrefs(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            keyVibration = p[K.vib] ?: d.keyVibration,
            dtmfTones = p[K.dtmf] ?: d.dtmfTones,
            voicemailFallback = p[K.vm] ?: d.voicemailFallback,
            defaultAccountId = p[K.acc] ?: d.defaultAccountId,
            callbackDelayMin = p[K.delay] ?: d.callbackDelayMin,
            headsUpWhenUnlocked = p[K.headsUp] ?: d.headsUpWhenUnlocked,
            flipToSilence = p[K.flip] ?: d.flipToSilence,
            bulleHandlesIncoming = p[K.bulle] ?: d.bulleHandlesIncoming,
            blockedNotification = p[K.blockedNotif] ?: d.blockedNotification,
            missedNotification = p[K.missedNotif] ?: d.missedNotification,
            blockConfig = BlockConfig(
                telemarketing = p.action(K.bTele, d.blockConfig.telemarketing),
                hidden = p.action(K.bHidden, d.blockConfig.hidden),
                unknown = p.action(K.bUnknown, d.blockConfig.unknown),
                blacklist = p.action(K.bBlack, d.blockConfig.blacklist),
            ),
            echoContacts = p[K.echo] ?: emptySet(),
            manualChecks = p[K.manual] ?: emptySet(),
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.store.edit { p ->
            val s = transform(fromPrefs(p))
            p[K.vib] = s.keyVibration
            p[K.dtmf] = s.dtmfTones
            p[K.vm] = s.voicemailFallback
            p[K.acc] = s.defaultAccountId
            p[K.delay] = s.callbackDelayMin
            p[K.headsUp] = s.headsUpWhenUnlocked
            p[K.flip] = s.flipToSilence
            p[K.bulle] = s.bulleHandlesIncoming
            p[K.blockedNotif] = s.blockedNotification
            p[K.missedNotif] = s.missedNotification
            p[K.bTele] = s.blockConfig.telemarketing.name
            p[K.bHidden] = s.blockConfig.hidden.name
            p[K.bUnknown] = s.blockConfig.unknown.name
            p[K.bBlack] = s.blockConfig.blacklist.name
            p[K.echo] = s.echoContacts
            p[K.manual] = s.manualChecks
        }
    }

    val onboardingDone: Flow<Boolean> = context.store.data.map { it[K.onboarding] ?: false }
    suspend fun setOnboardingDone() = context.store.edit { it[K.onboarding] = true }

    suspend fun lastUpdateCheck(): Long = context.store.data.first()[K.lastUpdateCheck] ?: 0L
    suspend fun setLastUpdateCheck(t: Long) = context.store.edit { it[K.lastUpdateCheck] = t }
    suspend fun skippedVersion(): String? = context.store.data.first()[K.skipVersion]
    suspend fun skipVersion(v: String) = context.store.edit { it[K.skipVersion] = v }

    val roleWarnDismissed: Flow<Boolean> = context.store.data.map { it[K.roleWarnDismissed] ?: false }
    suspend fun setRoleWarnDismissed(v: Boolean) = context.store.edit { it[K.roleWarnDismissed] = v }
}
