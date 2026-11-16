package com.karelisio.allo.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat

fun Context.hasPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

fun Context.copyToClipboard(label: String, text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < 33) toast("Copié")
}

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.safeStart(intent: Intent): Boolean = runCatching {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
}.getOrDefault(false)

fun Context.openSms(number: String, body: String? = null) {
    val i = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number)))
    if (body != null) i.putExtra("sms_body", body)
    safeStart(i)
}

fun Context.openAppDetails() = safeStart(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
)

fun Context.isPackageInstalled(pkg: String): Boolean = runCatching {
    packageManager.getPackageInfo(pkg, 0); true
}.getOrDefault(false)

val isHonorDevice: Boolean
    get() = Build.MANUFACTURER.equals("HONOR", true) || Build.BRAND.equals("HONOR", true) ||
        Build.MANUFACTURER.equals("HUAWEI", true)
