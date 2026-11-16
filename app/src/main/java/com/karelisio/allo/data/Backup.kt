package com.karelisio.allo.data

import android.content.Context
import android.net.Uri
import com.karelisio.allo.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Export / import JSON des réglages et des listes (aucune donnée d'appel ni de contact). */
@Serializable
data class BackupFile(
    val format: Int = 1,
    val app: String = "com.karelisio.allo",
    val settings: AppSettings,
    val blacklist: List<BlacklistEntry>,
    val whitelist: List<WhitelistEntry>,
    val prefixes: List<TelemarketingPrefix>,
    val replies: List<QuickReply>,
    val contactSettings: List<ContactSetting>,
)

object Backup {
    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun build(): BackupFile {
        val db = Graph.db
        return BackupFile(
            settings = Graph.settings.current(),
            blacklist = db.block().blacklistNow(),
            whitelist = db.block().whitelistNow(),
            prefixes = db.block().prefixesNow(),
            replies = db.misc().repliesNow(),
            contactSettings = db.misc().allContactSettings(),
        )
    }

    fun encode(b: BackupFile): String = json.encodeToString(BackupFile.serializer(), b)
    fun decode(s: String): BackupFile = json.decodeFromString(BackupFile.serializer(), s)

    suspend fun export(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = encode(build())
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
            true
        }.getOrDefault(false)
    }

    suspend fun import(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val b = decode(text)
            require(b.app == "com.karelisio.allo")
            restore(b)
            true
        }.getOrDefault(false)
    }

    suspend fun restore(b: BackupFile) {
        val db = Graph.db
        Graph.settings.update { b.settings }
        db.block().clearBlack(); b.blacklist.forEach { db.block().addBlack(it.copy(id = 0)) }
        db.block().clearWhite(); b.whitelist.forEach { db.block().addWhite(it.copy(id = 0)) }
        db.block().clearPrefixes(); db.block().putPrefixes(b.prefixes)
        db.misc().clearReplies(); db.misc().putReplies(b.replies.map { it.copy(id = 0) })
        db.misc().clearContactSettings(); b.contactSettings.forEach { db.misc().putContactSetting(it) }
    }
}
