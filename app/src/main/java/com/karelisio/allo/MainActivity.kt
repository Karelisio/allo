package com.karelisio.allo

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.karelisio.allo.ui.AlloRoot
import com.karelisio.allo.ui.theme.AlloTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()
    /** Intention à traiter (numéro à composer, onglet à ouvrir). */
    val pendingRoute = mutableStateOf<LaunchRequest?>(null)
    private var ready = false
    private val onboardingDone = mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener { provider ->
            val icon: View? = runCatching { provider.iconView }.getOrNull()
            if (icon == null) { provider.remove(); return@setOnExitAnimationListener }
            val sx = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 0.6f, 9f)
            val sy = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 0.6f, 9f)
            val a = ObjectAnimator.ofFloat(provider.view, View.ALPHA, 1f, 0f)
            listOf(sx, sy).forEach { it.interpolator = AnticipateInterpolator(); it.duration = 520 }
            a.startDelay = 260; a.duration = 300
            a.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = provider.remove()
            })
            sx.start(); sy.start(); a.start()
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            onboardingDone.value = Graph.settings.onboardingDone.first()
            ready = true
        }
        handleIntent(intent)
        setContent {
            AlloTheme {
                val done by onboardingDone
                val isDefault by vm.isDefaultDialer.collectAsState()
                if (done != null) {
                    AlloRoot(
                        vm = vm,
                        onboardingDone = done == true,
                        onOnboardingFinished = { lifecycleScope.launch { Graph.settings.setOnboardingDone(); onboardingDone.value = true; vm.recheck() } },
                        launch = pendingRoute.value,
                        onLaunchConsumed = { pendingRoute.value = null },
                        isDefaultDialer = isDefault,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.recheck()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val req = when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, "android.intent.action.CALL_BUTTON" -> {
                if (intent.type == "vnd.android.cursor.dir/calls") LaunchRequest.Tab(TAB_LOG)
                else LaunchRequest.Dial(intent.data?.schemeSpecificPart?.takeIf { intent.data?.scheme == "tel" } ?: "")
            }
            ACTION_ADD_CALL -> LaunchRequest.Dial("")
            else -> intent.getStringExtra(EXTRA_TAB)?.let { LaunchRequest.Tab(it) }
        }
        if (req != null) pendingRoute.value = req
    }

    sealed interface LaunchRequest {
        data class Dial(val number: String) : LaunchRequest
        data class Tab(val tab: String) : LaunchRequest
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_LOG = "log"
        const val TAB_FAVORITES = "favorites"
        const val TAB_CONTACTS = "contacts"
        const val TAB_BLOCKED = "blocked"
        const val ACTION_ADD_CALL = "com.karelisio.allo.ADD_CALL"
    }
}
