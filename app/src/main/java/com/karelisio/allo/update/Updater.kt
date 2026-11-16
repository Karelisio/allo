package com.karelisio.allo.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.karelisio.allo.BuildConfig
import com.karelisio.allo.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Error(val message: String) : UpdateState
}

/** Mise à jour in-app : GitHub Releases (releases/latest) → téléchargement → PackageInstaller. */
object Updater {
    private const val INTERVAL = 6 * 60 * 60 * 1000L
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    fun compare(a: String, b: String): Int {
        val pa = a.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        val pb = b.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun parse(json: String): Release? {
        val o = Json.parseToJsonElement(json).jsonObject
        val tag = o["tag_name"]?.jsonPrimitive?.content ?: return null
        val asset = o["assets"]?.jsonArray?.map { it.jsonObject }?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true } ?: return null
        return Release(
            version = tag.removePrefix("v"),
            notes = o["body"]?.jsonPrimitive?.content.orEmpty(),
            apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null,
            size = (asset as JsonObject)["size"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    /** Vérifie au plus toutes les 6 h (sauf [force]). */
    suspend fun check(ctx: Context, force: Boolean = false) {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return
        val now = System.currentTimeMillis()
        if (!force && now - Graph.settings.lastUpdateCheck() < INTERVAL) return
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL("https://api.github.com/repos/${BuildConfig.RELEASES_REPO}/releases/latest").openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.setRequestProperty("User-Agent", "Allo-Android")
                c.connectTimeout = 10_000; c.readTimeout = 10_000
                c.inputStream.use { it.readBytes().decodeToString() }.let(::parse)
            }
        }
        Graph.settings.setLastUpdateCheck(now)
        _state.value = result.fold(
            onSuccess = { r ->
                if (r != null && compare(r.version, BuildConfig.VERSION_NAME.removeSuffix("-debug")) > 0) {
                    if (!force && Graph.settings.skippedVersion() == r.version) UpdateState.UpToDate else UpdateState.Available(r)
                } else UpdateState.UpToDate
            },
            onFailure = { if (force) UpdateState.Error(it.message ?: "réseau indisponible") else UpdateState.Idle },
        )
    }

    suspend fun skip(r: Release) { Graph.settings.skipVersion(r.version); _state.value = UpdateState.Idle }
    fun dismiss() { if (_state.value is UpdateState.Available) _state.value = UpdateState.Idle }

    suspend fun downloadAndInstall(ctx: Context, r: Release) {
        _state.value = UpdateState.Downloading(r, 0f)
        val file = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, "allo-${r.version}.apk")
                var url = URL(r.apkUrl)
                var conn: HttpURLConnection
                var redirects = 0
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.setRequestProperty("User-Agent", "Allo-Android")
                    val code = conn.responseCode
                    if (code in 300..399 && redirects++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
                    break
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
                conn.inputStream.use { input ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var last = 0f
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            val p = if (total > 0) read.toFloat() / total else 0f
                            if (p - last > 0.01f) { last = p; _state.value = UpdateState.Downloading(r, p) }
                        }
                    }
                }
                f
            }.getOrNull()
        }
        if (file == null) { _state.value = UpdateState.Error("Téléchargement interrompu"); return }
        _state.value = UpdateState.Installing(r)
        withContext(Dispatchers.IO) { runCatching { install(ctx, file) }.onFailure { _state.value = UpdateState.Error(it.message ?: "Installation impossible") } }
    }

    private fun install(ctx: Context, file: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { s ->
            s.openWrite("allo.apk", 0, file.length()).use { out -> file.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pi.intentSender)
        }
    }

    internal fun onInstallFailed(msg: String) { _state.value = UpdateState.Error(msg) }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java) else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> Updater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation refusée")
        }
    }
}
