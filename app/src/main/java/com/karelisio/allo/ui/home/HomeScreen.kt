package com.karelisio.allo.ui.home

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.karelisio.allo.Graph
import com.karelisio.allo.MainActivity
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.core.CallKind
import com.karelisio.allo.notifications.Notifications
import com.karelisio.allo.telecom.CallManager
import com.karelisio.allo.telecom.Dialer
import com.karelisio.allo.ui.Routes
import com.karelisio.allo.ui.call.InCallActivity
import com.karelisio.allo.ui.call.ReturnToCallBanner
import com.karelisio.allo.ui.components.AlloShapes
import com.karelisio.allo.ui.components.sharedBoundsKey
import com.karelisio.allo.ui.contacts.ContactsTab
import com.karelisio.allo.ui.contacts.FavoritesTab
import com.karelisio.allo.ui.log.CallLogTab
import kotlinx.coroutines.launch

enum class HomeTab(val key: String, val label: String) { FAVORITES(MainActivity.TAB_FAVORITES, "Favoris"), LOG(MainActivity.TAB_LOG, "Récents"), CONTACTS(MainActivity.TAB_CONTACTS, "Contacts") }

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavHostController, isDefaultDialer: Boolean) {
    var tab by rememberSaveable { mutableStateOfTab(HomeTab.LOG) }
    val requested by vm.requestedTab.collectAsState()
    LaunchedEffect(requested) {
        requested?.let { r -> HomeTab.entries.firstOrNull { it.key == r }?.let { tab = it }; vm.requestedTab.value = null }
    }
    val log by vm.callLog.collectAsState()
    val missedCount = log.count { it.isNew && it.kind == CallKind.MISSED }
    val ctx = LocalContext.current
    LaunchedEffect(tab) {
        if (tab == HomeTab.LOG) {
            Notifications.cancelMissed(ctx)
            Graph.callLog.markMissedRead()
        }
    }
    val calls by CallManager.state.collectAsState()
    val live = calls.firstOrNull { it.isLive }
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        AnimatedContent(tab, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "title") {
                            Text(when (it) { HomeTab.FAVORITES -> "Favoris"; HomeTab.LOG -> "Récents"; HomeTab.CONTACTS -> "Contacts" },
                                style = MaterialTheme.typography.headlineMedium)
                        }
                    },
                    actions = {
                        IconButton(onClick = { nav.navigate(Routes.STATS) }) { Icon(Icons.Rounded.Insights, "Statistiques") }
                        IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Rounded.Settings, "Réglages") }
                    },
                    scrollBehavior = scroll,
                )
                AnimatedVisibility(!isDefaultDialer, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    RoleLostBanner(onFixed = { vm.recheck() })
                }
                if (live != null) {
                    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                        ReturnToCallBanner(live, onClick = {
                            ctx.startActivity(Intent(ctx, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        })
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            when (t) {
                                HomeTab.FAVORITES -> Icon(Icons.Rounded.Star, null)
                                HomeTab.LOG -> BadgedBox(badge = { if (missedCount > 0) Badge { Text("$missedCount") } }) { Icon(Icons.Rounded.History, null) }
                                HomeTab.CONTACTS -> Icon(Icons.Rounded.People, null)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = { nav.navigate(Routes.dialer()) },
                shape = AlloShapes.cookie,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.sharedBoundsKey("dialer-fab"),
            ) { Icon(Icons.Rounded.Dialpad, "Clavier") }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AnimatedContent(tab, transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f)) { w -> dir * w / 5 } + fadeIn()) togetherWith
                    (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f)) { w -> -dir * w / 5 } + fadeOut())
            }, label = "tab") { t ->
                when (t) {
                    HomeTab.FAVORITES -> FavoritesTab(vm, nav)
                    HomeTab.LOG -> CallLogTab(vm, nav)
                    HomeTab.CONTACTS -> ContactsTab(vm, nav)
                }
            }
        }
    }
}

private fun mutableStateOfTab(t: HomeTab) = androidx.compose.runtime.mutableStateOf(t)

/** Le rôle d'appli Téléphone a été retiré : on le signale et on propose de le reprendre. */
@Composable
fun RoleLostBanner(onFixed: () -> Unit) {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onFixed() }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.PhoneDisabled, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text("Allô n'est pas l'appli Téléphone par défaut : les appels passent par une autre appli.",
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { Dialer.roleRequestIntent(ctx)?.let { launcher.launch(it) } }) { Text("Reprendre") }
        }
    }
}
