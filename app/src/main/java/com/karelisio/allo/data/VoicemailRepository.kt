package com.karelisio.allo.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.VoicemailContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Voicemail(
    val id: Long,
    val number: String,
    val date: Long,
    val durationSec: Long,
    val hasContent: Boolean,
    val isRead: Boolean,
    val transcription: String?,
) {
    val uri: Uri get() = ContentUris.withAppendedId(VoicemailContract.Voicemails.CONTENT_URI, id)
}

/**
 * Messagerie vocale visuelle : messages déposés dans VoicemailContract par le client
 * de messagerie visuelle du système (si l'opérateur le prend en charge).
 */
class VoicemailRepository(private val context: Context) {
    suspend fun all(): List<Voicemail> = withContext(Dispatchers.IO) {
        runCatching {
            val out = mutableListOf<Voicemail>()
            context.contentResolver.query(
                VoicemailContract.Voicemails.CONTENT_URI,
                arrayOf(
                    VoicemailContract.Voicemails._ID, VoicemailContract.Voicemails.NUMBER, VoicemailContract.Voicemails.DATE,
                    VoicemailContract.Voicemails.DURATION, VoicemailContract.Voicemails.HAS_CONTENT,
                    VoicemailContract.Voicemails.IS_READ, VoicemailContract.Voicemails.TRANSCRIPTION,
                ),
                "${VoicemailContract.Voicemails.DELETED} = 0", null, "${VoicemailContract.Voicemails.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext()) out += Voicemail(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2), c.getLong(3),
                    c.getInt(4) == 1, c.getInt(5) == 1, c.getString(6)?.takeIf { it.isNotBlank() })
            }
            out
        }.getOrDefault(emptyList())
    }

    suspend fun markRead(v: Voicemail) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.update(v.uri, ContentValues().apply { put(VoicemailContract.Voicemails.IS_READ, 1) }, null, null) }
    }

    suspend fun delete(v: Voicemail) = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.update(v.uri, ContentValues().apply { put(VoicemailContract.Voicemails.DELETED, 1) }, null, null) }
    }
}
