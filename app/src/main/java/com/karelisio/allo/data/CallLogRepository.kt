package com.karelisio.allo.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import com.karelisio.allo.core.CallEntry
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context, private val sims: SimRepository) {
    private val resolver get() = context.contentResolver

    fun observe(limit: Int = 3000): Flow<List<CallEntry>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        if (context.hasPermission(android.Manifest.permission.READ_CALL_LOG)) {
            resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, observer)
        }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().let { trigger ->
        kotlinx.coroutines.flow.flow { trigger.collect { emit(query(limit)) } }
    }.flowOn(Dispatchers.IO)

    @SuppressLint("MissingPermission")
    fun query(limit: Int = 3000, number: String? = null): List<CallEntry> {
        if (!context.hasPermission(android.Manifest.permission.READ_CALL_LOG)) return emptyList()
        val proj = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
            CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.CACHED_PHOTO_URI, CallLog.Calls.CACHED_LOOKUP_URI, CallLog.Calls.NEW,
            CallLog.Calls.NUMBER_PRESENTATION,
        )
        val slots = sims.slotByAccountId()
        val out = ArrayList<CallEntry>()
        val uri = CallLog.Calls.CONTENT_URI.buildUpon()
            .appendQueryParameter(CallLog.Calls.LIMIT_PARAM_KEY, limit.toString()).build()
        runCatching {
            resolver.query(uri, proj, null, null, "${CallLog.Calls.DATE} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val presentation = c.getInt(10)
                    val num = if (presentation == CallLog.Calls.PRESENTATION_ALLOWED) c.getString(1).orEmpty() else ""
                    if (number != null && !PhoneNumbers.same(num, number)) continue
                    out += CallEntry(
                        id = c.getLong(0),
                        number = num,
                        name = c.getString(2)?.takeIf { it.isNotBlank() },
                        kind = kindOf(c.getInt(3)),
                        date = c.getLong(4),
                        durationSec = c.getLong(5),
                        simSlot = c.getString(6)?.let { slots[it] },
                        photoUri = c.getString(7),
                        lookupUri = c.getString(8),
                        isNew = c.getInt(9) == 1,
                    )
                }
            }
        }
        return out
    }

    fun kindOf(type: Int): CallKind = when (type) {
        CallLog.Calls.INCOMING_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE -> CallKind.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
        CallLog.Calls.VOICEMAIL_TYPE -> CallKind.VOICEMAIL
        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
        else -> CallKind.INCOMING
    }

    suspend fun delete(ids: Collection<Long>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty() || !context.hasPermission(android.Manifest.permission.WRITE_CALL_LOG)) return@withContext
        runCatching {
            ids.chunked(200).forEach { chunk ->
                resolver.delete(CallLog.Calls.CONTENT_URI, "${CallLog.Calls._ID} IN (${chunk.joinToString(",")})", null)
            }
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        if (context.hasPermission(android.Manifest.permission.WRITE_CALL_LOG)) runCatching { resolver.delete(CallLog.Calls.CONTENT_URI, null, null) }
    }

    /** Marque les appels manqués comme lus (retire la pastille « nouveau »). */
    suspend fun markMissedRead() = withContext(Dispatchers.IO) {
        if (!context.hasPermission(android.Manifest.permission.WRITE_CALL_LOG)) return@withContext
        runCatching {
            val v = ContentValues().apply { put(CallLog.Calls.NEW, 0); put(CallLog.Calls.IS_READ, 1) }
            resolver.update(CallLog.Calls.CONTENT_URI, v, "${CallLog.Calls.NEW} = 1 AND ${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}", null)
        }
    }

    fun newMissed(): List<CallEntry> = query(200).filter { it.isNew && it.kind == CallKind.MISSED }
}
