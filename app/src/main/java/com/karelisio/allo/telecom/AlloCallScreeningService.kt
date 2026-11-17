package com.karelisio.allo.telecom

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.karelisio.allo.Graph
import com.karelisio.allo.core.BlockAction
import com.karelisio.allo.core.BlockDecision
import com.karelisio.allo.notifications.Notifications
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Filtrage des appels entrants : préfixes de démarchage, masqués, inconnus, liste noire. */
class AlloCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        if (Build.VERSION.SDK_INT >= 29 && details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = if (details.handlePresentation == TelecomManager.PRESENTATION_ALLOWED) details.handle?.schemeSpecificPart else null
        Graph.scope.launch {
            val decision = withTimeoutOrNull(3_500) { runCatching { Graph.block.decide(number) }.getOrNull() } ?: BlockDecision.ALLOW
            ScreeningResults.put(number, decision)
            com.karelisio.allo.util.CallDiagnostics.log("Filtrage (${com.karelisio.allo.util.CallDiagnostics.mask(number)}) : ${decision.action}${decision.category?.let { " · $it" } ?: ""}")
            val response = CallResponse.Builder()
            when (decision.action) {
                BlockAction.BLOCK -> response.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(false)
                BlockAction.SILENCE -> if (Build.VERSION.SDK_INT >= 29) response.setSilenceCall(true)
                BlockAction.ALLOW -> {}
            }
            runCatching { respondToCall(details, response.build()) }
            if (decision.action != BlockAction.ALLOW) {
                Graph.block.log(number.orEmpty(), decision.category, decision.action)
            }
            if (decision.action == BlockAction.BLOCK && Graph.settings.current().blockedNotification) {
                Notifications.notifyBlocked(this@AlloCallScreeningService, number, decision)
            }
        }
    }
}
