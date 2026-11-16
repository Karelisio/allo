package com.karelisio.allo.ui.permissions

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.automirrored.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.util.hasPermission
import com.karelisio.allo.util.isHonorDevice
import com.karelisio.allo.util.safeStart

enum class PermKind { ROLE, RUNTIME, SETTINGS, MANUAL }

data class PermItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val kind: PermKind,
    val runtime: List<String> = emptyList(),
    val role: String? = null,
    val intents: (Context) -> List<Intent> = { emptyList() },
    /** null = non détectable (confirmation manuelle). */
    val check: (Context) -> Boolean? = { null },
    val required: Boolean = true,
)

object Permissions {
    private fun pkgUri(ctx: Context) = Uri.parse("package:${ctx.packageName}")
    private fun details(ctx: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri(ctx))

    fun runtime(id: String, title: String, desc: String, icon: ImageVector, vararg perms: String, required: Boolean = true) =
        PermItem(id, title, desc, icon, PermKind.RUNTIME, runtime = perms.toList(), check = { c -> perms.all { c.hasPermission(it) } }, required = required)

    @SuppressLint("BatteryLife", "InlinedApi")
    fun all(ctx: Context): List<PermItem> = buildList {
        add(PermItem("role_dialer", "Appli Téléphone par défaut",
            "Allô remplace l'appli Téléphone : écrans d'appel, journal et blocage passent par elle.",
            Icons.Rounded.PhoneAndroid, PermKind.ROLE, role = RoleManager.ROLE_DIALER, check = { Dialer.isDefaultDialer(it) }))
        add(runtime("call_phone", "Passer des appels", "Pour appeler directement depuis le clavier, le journal et les contacts.", Icons.Rounded.Call, Manifest.permission.CALL_PHONE))
        add(runtime("phone_state", "État du téléphone", "Pour connaître tes SIM, la messagerie vocale et l'état des appels.", Icons.Rounded.SimCard, Manifest.permission.READ_PHONE_STATE))
        add(runtime("phone_numbers", "Numéros des SIM", "Pour afficher le numéro de chaque SIM en double SIM.", Icons.Rounded.Numbers, Manifest.permission.READ_PHONE_NUMBERS))
        add(runtime("answer", "Répondre aux appels", "Pour décrocher et raccrocher depuis les notifications.", Icons.AutoMirrored.Rounded.PhoneCallback, Manifest.permission.ANSWER_PHONE_CALLS))
        add(runtime("call_log", "Journal d'appels", "Pour afficher, regrouper et nettoyer ton historique d'appels.", Icons.Rounded.History,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG))
        add(runtime("contacts", "Contacts", "Pour afficher les noms, les photos et modifier tes contacts.", Icons.Rounded.Contacts,
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
        add(runtime("sms", "Envoyer des SMS", "Pour les réponses rapides quand tu ne peux pas décrocher.", Icons.Rounded.Sms, Manifest.permission.SEND_SMS, required = false))
        if (Build.VERSION.SDK_INT >= 33) add(runtime("notifications", "Notifications", "Pour les appels entrants, en cours, manqués et les rappels.", Icons.Rounded.Notifications, Manifest.permission.POST_NOTIFICATIONS))
        if (Build.VERSION.SDK_INT >= 34) add(PermItem("full_screen", "Écran d'appel plein écran",
            "Pour afficher l'appel entrant par-dessus l'écran de verrouillage.", Icons.Rounded.Fullscreen, PermKind.SETTINGS,
            intents = { listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri(it))) },
            check = { it.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }))
        if (Build.VERSION.SDK_INT >= 31) add(PermItem("exact_alarm", "Rappels à l'heure exacte",
            "Pour « Rappelle-moi dans 10 min » pile à l'heure.", Icons.Rounded.Alarm, PermKind.SETTINGS,
            intents = { listOf(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri(it))) },
            check = { it.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }, required = false))
        if (Build.VERSION.SDK_INT >= 31) add(runtime("bluetooth", "Appareils Bluetooth", "Pour afficher le nom de ton casque ou de ta voiture pendant un appel.", Icons.Rounded.Bluetooth,
            Manifest.permission.BLUETOOTH_CONNECT, required = false))
        add(PermItem("role_screening", "Filtrage des appels",
            "Pour bloquer le démarchage avant même que le téléphone sonne.", Icons.Rounded.Shield, PermKind.ROLE,
            role = RoleManager.ROLE_CALL_SCREENING, check = { Dialer.isScreeningRoleHeld(it) || Dialer.isDefaultDialer(it) }, required = false))
        add(PermItem("battery", "Optimisation de la batterie",
            "Exclure Allô de l'optimisation pour ne jamais rater un appel.", Icons.Rounded.BatteryChargingFull, PermKind.SETTINGS,
            intents = { listOf(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri(it)), Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            check = { it.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(it.packageName) }))
        if (isHonorDevice) {
            add(PermItem("honor_launch", "Lancement d'applis (MagicOS)",
                "Dans « Lancement d'applis », passe Allô en manuel et active : démarrage auto, lancement secondaire et exécution en arrière-plan.",
                Icons.Rounded.RocketLaunch, PermKind.MANUAL,
                intents = { c -> listOf(
                    Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
                    Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.systemmanager.mainscreen.MainScreenActivity")),
                    details(c),
                ) }))
            add(PermItem("honor_lockscreen", "Afficher sur l'écran de verrouillage",
                "Facultatif. Si ta version de MagicOS la propose (Autorisations ou Autres autorisations d'Allô), active « Afficher sur l'écran de verrouillage ». Sinon, passe : l'écran plein écran d'Android suffit.",
                Icons.Rounded.Lock, PermKind.MANUAL, required = false,
                intents = { c -> listOf(
                    Intent("huawei.intent.action.NOTIFICATIONMANAGER").setPackage("com.hihonor.systemmanager"),
                    Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.permissionmanager.ui.MainActivity")),
                    details(c),
                ) }))
            add(PermItem("honor_popup", "Fenêtres pop-up en arrière-plan",
                "Facultatif. Si ta version de MagicOS la propose (Autorisations ou Autres autorisations d'Allô), active « Fenêtres pop-up en arrière-plan ». Sinon, passe.",
                Icons.Rounded.PictureInPicture, PermKind.MANUAL, required = false,
                intents = { c -> listOf(
                    Intent().setComponent(ComponentName("com.hihonor.systemmanager", "com.hihonor.permissionmanager.ui.MainActivity")),
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri(c)),
                    details(c),
                ) }))
        }
    }

    fun isGranted(ctx: Context, item: PermItem, manual: Set<String>): Boolean = item.check(ctx) ?: (item.id in manual)

    fun openFirstWorking(ctx: Context, intents: List<Intent>): Boolean = intents.any { ctx.safeStart(it) }
}

/** Lance la bonne demande pour un élément, et rappelle [onDone] au retour. */
class PermissionRequester(
    private val launchRuntime: (Array<String>) -> Unit,
    private val launchActivity: (Intent) -> Unit,
    private val ctx: Context,
) {
    var current by mutableStateOf<PermItem?>(null)

    fun request(item: PermItem) {
        current = item
        when (item.kind) {
            PermKind.RUNTIME -> launchRuntime(item.runtime.toTypedArray())
            PermKind.ROLE -> {
                val i = item.role?.let { Dialer.roleRequestIntent(ctx, it) }
                if (i != null) launchActivity(i) else Permissions.openFirstWorking(ctx, listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)))
            }
            PermKind.SETTINGS, PermKind.MANUAL -> {
                val intents = item.intents(ctx)
                val ok = intents.firstOrNull { runCatching { ctx.packageManager.resolveActivity(it, 0) != null }.getOrDefault(false) }
                if (ok != null) runCatching { launchActivity(ok) }.onFailure { Permissions.openFirstWorking(ctx, intents) }
                else Permissions.openFirstWorking(ctx, intents)
            }
        }
    }
}

@Composable
fun rememberPermissionRequester(onDone: (PermItem?) -> Unit): PermissionRequester {
    val ctx = LocalContext.current
    var holder: PermissionRequester? = null
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onDone(holder?.current) }
    val activity = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onDone(holder?.current) }
    val r = remember { PermissionRequester({ runtime.launch(it) }, { activity.launch(it) }, ctx) }
    holder = r
    return r
}
