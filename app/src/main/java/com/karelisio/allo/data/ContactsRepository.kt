package com.karelisio.allo.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.util.hasPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

data class PhoneEntry(val number: String, val label: String, val isPrimary: Boolean = false, val type: Int = CommonDataKinds.Phone.TYPE_MOBILE)

data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val thumbUri: String?,
    val starred: Boolean,
    val phones: List<PhoneEntry>,
) {
    val primaryNumber: String? get() = (phones.firstOrNull { it.isPrimary } ?: phones.firstOrNull())?.number
    val lookupUri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

data class EmailEntry(val address: String, val label: String)

data class ContactDetail(
    val id: Long,
    val lookupKey: String,
    val rawContactId: Long?,
    val name: String,
    val givenName: String,
    val familyName: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<PhoneEntry>,
    val emails: List<EmailEntry>,
    val note: String,
    val ringtone: String?,
)

/** Données saisies dans l'éditeur. */
data class ContactEdit(
    val contactId: Long? = null,
    val givenName: String = "",
    val familyName: String = "",
    val phones: List<PhoneEntry> = listOf(PhoneEntry("", "Mobile")),
    val emails: List<String> = emptyList(),
    val note: String = "",
    val photo: ByteArray? = null,
    /** Compte de destination d'un nouveau contact (null = téléphone). */
    val account: ContactAccount? = null,
)

data class ContactAccount(val type: String, val name: String) {
    val label: String get() = when {
        type == "com.google" -> "Google · $name"
        type.contains("exchange", true) -> "Exchange · $name"
        else -> name
    }
}

data class NumberLookup(val contactId: Long, val lookupKey: String, val name: String, val photoUri: String?, val ringtone: String?)

class ContactsRepository(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val canRead get() = context.hasPermission(android.Manifest.permission.READ_CONTACTS)
    private val canWrite get() = context.hasPermission(android.Manifest.permission.WRITE_CONTACTS)

    private fun changes(uri: Uri): Flow<Unit> = callbackFlow {
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        if (canRead) resolver.registerContentObserver(uri, true, obs)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(obs) }
    }.conflate()

    fun observeAll(): Flow<List<Contact>> = flow {
        changes(ContactsContract.Contacts.CONTENT_URI).collect { emit(all()) }
    }.flowOn(Dispatchers.IO)

    fun all(): List<Contact> {
        if (!canRead) return emptyList()
        val proj = arrayOf(
            CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.LOOKUP_KEY,
            CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY, CommonDataKinds.Phone.PHOTO_URI,
            CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI, CommonDataKinds.Phone.STARRED,
            CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL,
            CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
        val map = LinkedHashMap<Long, Contact>()
        runCatching {
            resolver.query(CommonDataKinds.Phone.CONTENT_URI, proj, null, null,
                "${CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(6) ?: continue
                    val phone = PhoneEntry(number, typeLabel(c.getInt(7), c.getString(8)), c.getInt(9) == 1, c.getInt(7))
                    val existing = map[id]
                    if (existing == null) {
                        map[id] = Contact(id, c.getString(1).orEmpty(), c.getString(2) ?: number, c.getString(3), c.getString(4), c.getInt(5) == 1, listOf(phone))
                    } else if (existing.phones.none { PhoneNumbers.same(it.number, number) }) {
                        map[id] = existing.copy(phones = existing.phones + phone)
                    }
                }
            }
        }
        return map.values.toList()
    }

    fun typeLabel(type: Int, custom: String?): String = when (type) {
        CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
        CommonDataKinds.Phone.TYPE_HOME -> "Domicile"
        CommonDataKinds.Phone.TYPE_WORK -> "Travail"
        CommonDataKinds.Phone.TYPE_WORK_MOBILE -> "Mobile pro"
        CommonDataKinds.Phone.TYPE_MAIN -> "Principal"
        CommonDataKinds.Phone.TYPE_FAX_HOME, CommonDataKinds.Phone.TYPE_FAX_WORK -> "Fax"
        CommonDataKinds.Phone.TYPE_CUSTOM -> custom ?: "Autre"
        else -> custom ?: "Autre"
    }

    fun lookup(number: String?): NumberLookup? {
        if (!canRead || number.isNullOrBlank() || PhoneNumbers.isHidden(number)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return runCatching {
            resolver.query(uri, arrayOf(
                ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY,
                ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.CUSTOM_RINGTONE,
            ), null, null, null)?.use { c ->
                if (c.moveToFirst()) NumberLookup(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3), c.getString(4)) else null
            }
        }.getOrNull()
    }

    fun idForLookupKey(lookupKey: String): Long? {
        if (!canRead) return null
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        return runCatching {
            resolver.query(uri, arrayOf(ContactsContract.Contacts._ID), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
        }.getOrNull()
    }

    fun observeDetail(lookupKey: String): Flow<ContactDetail?> = flow {
        changes(ContactsContract.Contacts.CONTENT_URI).collect { emit(detail(lookupKey)) }
    }.flowOn(Dispatchers.IO)

    fun detail(lookupKey: String): ContactDetail? {
        val id = idForLookupKey(lookupKey) ?: return null
        var name = ""
        var photo: String? = null
        var starred = false
        var ringtone: String? = null
        resolver.query(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id), arrayOf(
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED, ContactsContract.Contacts.CUSTOM_RINGTONE,
        ), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                name = c.getString(0).orEmpty(); photo = c.getString(1); starred = c.getInt(2) == 1; ringtone = c.getString(3)
            }
        }
        val phones = mutableListOf<PhoneEntry>()
        val emails = mutableListOf<EmailEntry>()
        var note = ""
        var given = ""
        var family = ""
        var rawId: Long? = null
        resolver.query(ContactsContract.Data.CONTENT_URI, arrayOf(
            ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1, ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3, ContactsContract.Data.IS_SUPER_PRIMARY, ContactsContract.Data.RAW_CONTACT_ID,
        ), "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                if (rawId == null) rawId = c.getLong(5)
                when (c.getString(0)) {
                    CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> {
                        val n = c.getString(1) ?: continue
                        if (phones.none { PhoneNumbers.same(it.number, n) }) {
                            val t = c.getInt(2)
                            phones += PhoneEntry(n, typeLabel(t, c.getString(3)), c.getInt(4) == 1, t)
                        }
                    }
                    CommonDataKinds.Email.CONTENT_ITEM_TYPE -> c.getString(1)?.let { emails += EmailEntry(it, if (c.getInt(2) == CommonDataKinds.Email.TYPE_WORK) "Travail" else "Personnel") }
                    CommonDataKinds.Note.CONTENT_ITEM_TYPE -> note = c.getString(1).orEmpty()
                    CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> { given = c.getString(2).orEmpty(); family = c.getString(3).orEmpty() }
                }
            }
        }
        return ContactDetail(id, lookupKey, rawId, name, given, family, photo, starred, phones, emails, note, ringtone)
    }

    suspend fun setStarred(contactId: Long, starred: Boolean) = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext
        val v = ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }
        runCatching { resolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), v, null, null) }
    }

    suspend fun setRingtone(contactId: Long, ringtone: String?) = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext
        val v = ContentValues().apply { put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtone) }
        runCatching { resolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), v, null, null) }
    }

    suspend fun delete(contactId: Long) = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext
        runCatching { resolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null) }
    }

    /** Crée ou met à jour un contact. Renvoie la clé de recherche (lookup key) du contact. */
    suspend fun save(edit: ContactEdit): String? = withContext(Dispatchers.IO) {
        if (!canWrite) return@withContext null
        val ops = ArrayList<ContentProviderOperation>()
        val existingRaw: Long? = edit.contactId?.let { rawContactFor(it) }

        fun dataInsert(): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
            return if (existingRaw != null) b.withValue(ContactsContract.Data.RAW_CONTACT_ID, existingRaw)
            else b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
        }

        if (existingRaw == null) {
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, edit.account?.type)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, edit.account?.name).build()
        } else {
            val mimes = buildList {
                add(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE); add(CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                add(CommonDataKinds.Email.CONTENT_ITEM_TYPE); add(CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                if (edit.photo != null) add(CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            }
            ops += ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                .withSelection("${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (${mimes.joinToString(",") { "?" }})",
                    arrayOf(existingRaw.toString()) + mimes).build()
        }
        ops += dataInsert()
            .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .withValue(CommonDataKinds.StructuredName.GIVEN_NAME, edit.givenName.trim())
            .withValue(CommonDataKinds.StructuredName.FAMILY_NAME, edit.familyName.trim()).build()
        edit.phones.filter { it.number.isNotBlank() }.forEach { p ->
            ops += dataInsert()
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Phone.NUMBER, p.number.trim())
                .withValue(CommonDataKinds.Phone.TYPE, p.type).build()
        }
        edit.emails.filter { it.isNotBlank() }.forEach { e ->
            ops += dataInsert()
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Email.ADDRESS, e.trim())
                .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_HOME).build()
        }
        if (edit.note.isNotBlank()) {
            ops += dataInsert()
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Note.NOTE, edit.note.trim()).build()
        }
        edit.photo?.let { bytes ->
            ops += dataInsert()
                .withValue(ContactsContract.Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                .withValue(CommonDataKinds.Photo.PHOTO, bytes).build()
        }
        val results = runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ops) }.getOrNull() ?: return@withContext null
        val rawId = existingRaw ?: results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return@withContext null
        lookupKeyForRaw(rawId)
    }

    /** Comptes où l'on peut écrire des contacts (adaptateurs de synchro « uploading »). */
    fun writableAccounts(): List<ContactAccount> = runCatching {
        val writableTypes = android.content.ContentResolver.getSyncAdapterTypes()
            .filter { it.authority == ContactsContract.AUTHORITY && it.supportsUploading() }
            .map { it.accountType }.toSet()
        val out = LinkedHashSet<ContactAccount>()
        resolver.query(ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.DELETED} = 0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val t = c.getString(0) ?: continue
                val n = c.getString(1) ?: continue
                if (t in writableTypes) out += ContactAccount(t, n)
            }
        }
        out.toList()
    }.getOrDefault(emptyList())

    private fun rawContactFor(contactId: Long): Long? =
        resolver.query(ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(contactId.toString()), null)?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun lookupKeyForRaw(rawId: Long): String? {
        val uri = ContactsContract.RawContacts.getContactLookupUri(resolver, ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId)) ?: return null
        return uri.pathSegments.getOrNull(2)
    }
}
