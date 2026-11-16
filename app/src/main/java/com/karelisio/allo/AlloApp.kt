package com.karelisio.allo

import android.app.Application
import com.karelisio.allo.notifications.Notifications
import kotlinx.coroutines.launch

class AlloApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannels(this)
        Graph.scope.launch { runCatching { Graph.block.seedDefaults() } }
    }
}
