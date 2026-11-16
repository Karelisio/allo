package com.karelisio.allo

import android.content.Context
import com.karelisio.allo.data.AlloDatabase
import com.karelisio.allo.data.BlockRepository
import com.karelisio.allo.data.CallLogRepository
import com.karelisio.allo.data.ContactsRepository
import com.karelisio.allo.data.SettingsStore
import com.karelisio.allo.data.SimRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Localisateur de dépendances minimaliste (pas de DI externe). */
object Graph {
    lateinit var app: Context
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db by lazy { AlloDatabase.get(app) }
    val settings by lazy { SettingsStore(app) }
    val sims by lazy { SimRepository(app) }
    val contacts by lazy { ContactsRepository(app) }
    val callLog by lazy { CallLogRepository(app, sims) }
    val block by lazy { BlockRepository(db, settings, contacts) }
    val voicemail by lazy { com.karelisio.allo.data.VoicemailRepository(app) }

    fun init(context: Context) { app = context.applicationContext }
}
