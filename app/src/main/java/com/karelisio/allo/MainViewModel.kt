package com.karelisio.allo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.allo.core.CallEntry
import com.karelisio.allo.core.PhoneNumbers
import com.karelisio.allo.data.AppSettings
import com.karelisio.allo.data.Contact
import com.karelisio.allo.data.Sim
import com.karelisio.allo.telecom.Dialer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Index numéro → contact, pour afficher les noms et photos à jour dans le journal. */
class ContactIndex(contacts: List<Contact>) {
    private val byKey = HashMap<String, Contact>()
    init { contacts.forEach { c -> c.phones.forEach { p -> byKey.putIfAbsent(PhoneNumbers.key(p.number), c) } } }
    fun find(number: String?): Contact? = if (number.isNullOrBlank()) null else byKey[PhoneNumbers.key(number)]
    val isEmpty get() = byKey.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    /** Incrémenté quand les autorisations changent, pour relancer les requêtes. */
    val refresh = MutableStateFlow(0)

    val contacts: StateFlow<List<Contact>> = refresh.flatMapLatest { Graph.contacts.observeAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val index: StateFlow<ContactIndex> = contacts.map { ContactIndex(it) }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactIndex(emptyList()))

    val callLog: StateFlow<List<CallEntry>> = refresh.flatMapLatest { Graph.callLog.observe() }
        .combine(index) { entries, idx ->
            if (idx.isEmpty) entries.map { it.copy(lookupUri = null) } else entries.map { e ->
                val c = idx.find(e.number)
                if (c != null) e.copy(name = c.name, photoUri = c.thumbUri ?: c.photoUri, lookupUri = c.lookupKey) else e.copy(lookupUri = null)
            }
        }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = Graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val sims: StateFlow<List<Sim>> = refresh.map { Graph.sims.sims() }.flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val blockedThisMonth: StateFlow<Int> = Graph.block.blockedThisMonth()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val isDefaultDialer = MutableStateFlow(Dialer.isDefaultDialer(app))

    /** Onglet demandé depuis une notification ou un intent. */
    val requestedTab = MutableStateFlow<String?>(null)

    fun recheck() {
        isDefaultDialer.value = Dialer.isDefaultDialer(getApplication())
        refresh.value++
    }
}
