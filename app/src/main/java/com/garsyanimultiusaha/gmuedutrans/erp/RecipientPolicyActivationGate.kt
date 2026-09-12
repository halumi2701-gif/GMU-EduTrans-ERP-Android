package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fail-closed activation layer for Recipient Policy + Staff Inbox.
 *
 * The controls stay invisible until the dedicated backend endpoint proves that
 * the current role can execute its real read action successfully. This lets us
 * ship the Android wiring safely before the Supabase production deployment is
 * available, without exposing a half-configured notification surface.
 */
@Composable
fun GmuNativeAppWithRecipientPolicyGate(vm: MainViewModel = viewModel()) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithNotificationRulesPreferences(vm)

        val session = (vm.state as? AppState.LoggedIn)?.session
        var backendReady by remember(session?.userId) { mutableStateOf(false) }

        LaunchedEffect(session?.userId, vm.currentPage) {
            backendReady = false
            if (session != null && vm.currentPage == AppPage.DASHBOARD) {
                backendReady = RecipientPolicyBackendProbe.isReady(session)
            }
        }

        if (session != null && backendReady && vm.currentPage == AppPage.DASHBOARD) {
            val role = session.profile.role

            if (role == ErpRoles.OWNER || ErpRoles.isDirector(role)) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 184.dp, end = 14.dp)
                ) {
                    RecipientPolicyCenterPreview(session)
                }
            }

            if (
                ErpRoles.isManagerEduTrans(role) ||
                role == "Operation" ||
                role == "Finance" ||
                role == "TL"
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 132.dp, start = 14.dp)
                ) {
                    StaffNotificationInboxPreview(vm, session)
                }
            }
        }
    }
}

private object RecipientPolicyBackendProbe {
    suspend fun isReady(session: SessionState): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val role = session.profile.role
            val action = if (role == ErpRoles.OWNER || ErpRoles.isDirector(role)) {
                "recipient_policies"
            } else {
                "staff_inbox"
            }

            val conn = (URL(BuildConfig.SUPABASE_URL + "/functions/v1/gmu-recipient-policy-admin").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                outputStream.use {
                    it.write(JSONObject().put("action", action).toString().toByteArray(Charsets.UTF_8))
                }
            }

            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            if (stream != null) BufferedReader(InputStreamReader(stream)).use { it.readText() }
            conn.disconnect()
            status in 200..299
        }.getOrDefault(false)
    }
}
