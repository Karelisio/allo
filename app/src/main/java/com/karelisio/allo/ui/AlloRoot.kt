package com.karelisio.allo.ui

import android.net.Uri
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.AnimatedContentScope
import com.karelisio.allo.MainActivity
import com.karelisio.allo.MainViewModel
import com.karelisio.allo.ui.components.CallerHost
import com.karelisio.allo.ui.components.LocalCaller
import com.karelisio.allo.ui.components.LocalNavAnimatedScope
import com.karelisio.allo.ui.components.LocalSharedTransitionScope
import com.karelisio.allo.ui.components.rememberCaller
import com.karelisio.allo.ui.contacts.ContactDetailScreen
import com.karelisio.allo.ui.contacts.ContactEditScreen
import com.karelisio.allo.ui.dialer.DialerScreen
import com.karelisio.allo.ui.home.HomeScreen
import com.karelisio.allo.ui.log.NumberDetailScreen
import com.karelisio.allo.ui.onboarding.OnboardingScreen
import com.karelisio.allo.ui.settings.AboutScreen
import com.karelisio.allo.ui.settings.BlockingScreen
import com.karelisio.allo.ui.settings.EchoSettingsScreen
import com.karelisio.allo.ui.settings.PermissionsScreen
import com.karelisio.allo.ui.settings.QuickRepliesScreen
import com.karelisio.allo.ui.settings.ReliabilityScreen
import com.karelisio.allo.ui.settings.SettingsScreen
import com.karelisio.allo.update.UpdateHost

object Routes {
    const val HOME = "home"
    fun dialer(number: String = "") = "dialer?number=${Uri.encode(number)}"
    fun contact(lookupKey: String, sk: String? = null) = "contact/${Uri.encode(lookupKey)}?sk=${Uri.encode(sk ?: lookupKey)}"
    fun number(number: String, sk: String? = null) = "number/${Uri.encode(number.ifEmpty { "-" })}?sk=${Uri.encode(sk ?: number)}"
    fun edit(lookupKey: String? = null, number: String? = null) = "edit?lookupKey=${Uri.encode(lookupKey ?: "")}&number=${Uri.encode(number ?: "")}"
    const val SETTINGS = "settings"
    const val BLOCKING = "settings/blocking"
    const val REPLIES = "settings/replies"
    const val PERMISSIONS = "settings/permissions"
    const val RELIABILITY = "settings/reliability"
    const val ECHO = "settings/echo"
    const val ABOUT = "settings/about"
}

@Composable
fun AlloRoot(
    vm: MainViewModel,
    onboardingDone: Boolean,
    onOnboardingFinished: () -> Unit,
    launch: MainActivity.LaunchRequest?,
    onLaunchConsumed: () -> Unit,
    isDefaultDialer: Boolean,
) {
    com.karelisio.allo.ui.components.CrashReportDialog()
    if (!onboardingDone) {
        OnboardingScreen(onFinished = onOnboardingFinished, onPermissionsChanged = { vm.recheck() })
        return
    }
    val nav = rememberNavController()
    val caller = rememberCaller()
    LaunchedEffect(launch) {
        when (launch) {
            is MainActivity.LaunchRequest.Dial -> { nav.navigate(Routes.dialer(launch.number)) { launchSingleTop = true }; onLaunchConsumed() }
            is MainActivity.LaunchRequest.Tab -> {
                if (launch.tab == MainActivity.TAB_BLOCKED) nav.navigate(Routes.BLOCKING) { launchSingleTop = true }
                else { nav.popBackStack(Routes.HOME, false); vm.requestedTab.value = launch.tab }
                onLaunchConsumed()
            }
            null -> {}
        }
    }
    CompositionLocalProvider(LocalCaller provides caller) {
        SharedTransitionLayout(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    enterTransition = { slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 6 } + fadeIn() },
                    exitTransition = { fadeOut() + scaleOut(targetScale = 0.96f) },
                    popEnterTransition = { fadeIn() + scaleIn(initialScale = 0.96f) },
                    popExitTransition = { slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it / 6 } + fadeOut() },
                ) {
                    screen(Routes.HOME) { HomeScreen(vm, nav, isDefaultDialer) }
                    screen("dialer?number={number}", listOf(navArgument("number") { type = NavType.StringType; defaultValue = "" })) {
                        DialerScreen(vm, nav, it.arguments?.getString("number").orEmpty())
                    }
                    screen("contact/{lookupKey}?sk={sk}", listOf(navArgument("lookupKey") { type = NavType.StringType }, navArgument("sk") { type = NavType.StringType; defaultValue = "" })) {
                        ContactDetailScreen(vm, nav, it.arguments?.getString("lookupKey").orEmpty(), it.arguments?.getString("sk"))
                    }
                    screen("number/{number}?sk={sk}", listOf(navArgument("number") { type = NavType.StringType }, navArgument("sk") { type = NavType.StringType; defaultValue = "" })) {
                        NumberDetailScreen(vm, nav, it.arguments?.getString("number").orEmpty().let { n -> if (n == "-") "" else n }, it.arguments?.getString("sk"))
                    }
                    screen("edit?lookupKey={lookupKey}&number={number}", listOf(
                        navArgument("lookupKey") { type = NavType.StringType; defaultValue = "" },
                        navArgument("number") { type = NavType.StringType; defaultValue = "" },
                    )) {
                        ContactEditScreen(nav, it.arguments?.getString("lookupKey").orEmpty().ifEmpty { null }, it.arguments?.getString("number").orEmpty())
                    }
                    screen(Routes.SETTINGS) { SettingsScreen(vm, nav) }
                    screen(Routes.BLOCKING) { BlockingScreen(vm, nav) }
                    screen(Routes.REPLIES) { QuickRepliesScreen(nav) }
                    screen(Routes.PERMISSIONS) { PermissionsScreen(vm, nav) }
                    screen(Routes.RELIABILITY) { ReliabilityScreen(vm, nav) }
                    screen(Routes.ECHO) { EchoSettingsScreen(vm, nav) }
                    screen(Routes.ABOUT) { AboutScreen(nav) }
                }
            }
        }
        CallerHost(caller)
        UpdateHost()
    }
}

private fun NavGraphBuilder.screen(
    route: String,
    args: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable(route, arguments = args) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

fun NavHostController.back() { if (!popBackStack()) navigate(Routes.HOME) }
