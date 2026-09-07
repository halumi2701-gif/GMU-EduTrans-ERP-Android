package com.pamoyanan.one.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pamoyanan.one.data.ApiClient
import com.pamoyanan.one.data.SecureSession
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class AuthStage { BOOT, LOGIN, INITIAL_PASSWORD, MAIN }

data class UserMe(
    val username: String,
    val role: String,
    val rtScope: String?,
    val rwScope: String?,
    val residentId: String?,
    val mustChangePassword: Boolean
)

data class UiNotice(val message: String, val error: Boolean = false)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val session = SecureSession(application)
    private val api = ApiClient(application, session)

    var authStage = androidx.compose.runtime.mutableStateOf(AuthStage.BOOT)
        private set
    var me = androidx.compose.runtime.mutableStateOf<UserMe?>(null)
        private set
    var route = androidx.compose.runtime.mutableStateOf("home")
        private set
    var loading = androidx.compose.runtime.mutableStateOf(false)
        private set
    var notice = androidx.compose.runtime.mutableStateOf<UiNotice?>(null)
        private set

    var home = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var digitalId = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var staffDigitalId = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var staffDigitalResidentId = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var staffDigitalResidentName = androidx.compose.runtime.mutableStateOf("")
        private set
    var staffDigitalId = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var staffDigitalResidentId = androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var letters = androidx.compose.runtime.mutableStateOf<JSONArray?>(null)
        private set
    var templates = androidx.compose.runtime.mutableStateOf<JSONArray?>(null)
        private set
    var complaints = androidx.compose.runtime.mutableStateOf<JSONArray?>(null)
        private set
    var residents = androidx.compose.runtime.mutableStateOf<JSONArray?>(null)
        private set
    var inbox = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var command = androidx.compose.runtime.mutableStateOf<JSONObject?>(null)
        private set
    var genericData = androidx.compose.runtime.mutableStateOf<Any?>(null)
        private set
    var genericTitle = androidx.compose.runtime.mutableStateOf("")
        private set
    var searchResults = androidx.compose.runtime.mutableStateOf<Any?>(null)
        private set

    init {
        bootstrap()
    }

    private fun parseMe(obj: JSONObject) = UserMe(
        username = obj.optString("username"),
        role = obj.optString("role"),
        rtScope = obj.optString("rt_scope").takeIf { it.isNotBlank() && it != "null" },
        rwScope = obj.optString("rw_scope").takeIf { it.isNotBlank() && it != "null" },
        residentId = obj.optString("resident_id").takeIf { it.isNotBlank() && it != "null" },
        mustChangePassword = obj.optBoolean("must_change_password")
    )

    fun clearNotice() { notice.value = null }

    private fun fail(e: Throwable) {
        notice.value = UiNotice(e.message ?: "Terjadi kendala.", true)
    }

    fun bootstrap() {
        viewModelScope.launch {
            if (!session.hasSession()) {
                authStage.value = AuthStage.LOGIN
                return@launch
            }
            loading.value = true
            try {
                val user = parseMe(api.me())
                me.value = user
                if (user.mustChangePassword) authStage.value = AuthStage.INITIAL_PASSWORD
                else {
                    authStage.value = AuthStage.MAIN
                    loadHome()
                }
            } catch (_: Exception) {
                session.clear()
                authStage.value = AuthStage.LOGIN
            } finally {
                loading.value = false
            }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            notice.value = UiNotice("NIK/username dan password wajib diisi.", true)
            return
        }
        viewModelScope.launch {
            loading.value = true
            try {
                val token = api.login(username.trim(), password)
                session.accessToken = token.optString("access_token")
                session.refreshToken = token.optString("refresh_token")
                session.username = username.trim()
                if (token.optBoolean("must_change_password")) {
                    authStage.value = AuthStage.INITIAL_PASSWORD
                } else {
                    val user = parseMe(api.me())
                    me.value = user
                    authStage.value = AuthStage.MAIN
                    loadHome()
                }
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading.value = false
            }
        }
    }

    fun setInitialPassword(password: String, confirm: String) {
        if (password != confirm) {
            notice.value = UiNotice("Konfirmasi password tidak sama.", true)
            return
        }
        viewModelScope.launch {
            loading.value = true
            try {
                api.setInitialPassword(password)
                session.clear()
                me.value = null
                authStage.value = AuthStage.LOGIN
                notice.value = UiNotice("Password pribadi tersimpan. Silakan login kembali.")
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            loading.value = true
            try { api.logout() } catch (_: Exception) {}
            session.clear()
            me.value = null
            home.value = null
            authStage.value = AuthStage.LOGIN
            loading.value = false
        }
    }

    fun navigate(target: String) {
        route.value = target
        when (target) {
            "home" -> loadHome()
            "digital" -> loadDigitalId()
            "digitalStaff" -> loadResidents()
            "letters" -> loadLetters()
            "complaints" -> loadComplaints()
            "residents" -> loadResidents()
            "inbox" -> loadInbox()
            "command" -> loadCommand()
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            try { home.value = api.home() } catch (e: Exception) { fail(e) }
        }
    }

    fun loadDigitalId() {
        viewModelScope.launch {
            loading.value = true
            try { digitalId.value = api.digitalId() } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun openResidentDigitalId(residentId: String) {
        staffDigitalResidentId.value = residentId
        viewModelScope.launch {
            loading.value = true
            try {
                staffDigitalId.value = api.residentDigitalId(residentId)
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun closeResidentDigitalId() {
        staffDigitalId.value = null
        staffDigitalResidentId.value = null
    }

    fun rotateResidentDigitalId() {
        val residentId = staffDigitalResidentId.value ?: return
        viewModelScope.launch {
            loading.value = true
            try {
                staffDigitalId.value = api.rotateResidentQr(residentId)
                notice.value = UiNotice("QR Digital ID warga berhasil diperbarui.")
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun revokeResidentDigitalId() {
        val residentId = staffDigitalResidentId.value ?: return
        viewModelScope.launch {
            loading.value = true
            try {
                api.revokeResidentDigitalId(residentId)
                notice.value = UiNotice("Digital ID warga berhasil dicabut.")
                closeResidentDigitalId()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun rotateQr() {
        viewModelScope.launch {
            loading.value = true
            try {
                digitalId.value = api.rotateMyQr()
                notice.value = UiNotice("QR Digital ID berhasil diperbarui.")
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun openResidentDigitalId(residentId: String, residentName: String) {
        staffDigitalResidentId.value = residentId
        staffDigitalResidentName.value = residentName
        viewModelScope.launch {
            loading.value = true
            try {
                staffDigitalId.value = api.residentDigitalId(residentId)
            } catch (e: Exception) {
                staffDigitalId.value = null
                fail(e)
            } finally {
                loading.value = false
            }
        }
    }

    fun rotateResidentDigitalId() {
        val id = staffDigitalResidentId.value ?: return
        viewModelScope.launch {
            loading.value = true
            try {
                staffDigitalId.value = api.rotateResidentDigitalId(id)
                notice.value = UiNotice("QR Digital ID warga berhasil diperbarui.")
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun revokeResidentDigitalId() {
        val id = staffDigitalResidentId.value ?: return
        viewModelScope.launch {
            loading.value = true
            try {
                api.revokeResidentDigitalId(id)
                staffDigitalId.value = null
                notice.value = UiNotice("QR Digital ID warga berhasil dicabut.")
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun clearResidentDigitalId() {
        staffDigitalId.value = null
        staffDigitalResidentId.value = null
        staffDigitalResidentName.value = ""
    }

    fun loadLetters() {
        viewModelScope.launch {
            loading.value = true
            try {
                letters.value = api.letters()
                templates.value = api.templates()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun createLetter(template: String, purpose: String, fields: JSONObject, requirements: JSONArray, priority: String, urgentReason: String?) {
        viewModelScope.launch {
            loading.value = true
            try {
                api.createLetter(template, purpose, fields, requirements, priority, urgentReason)
                notice.value = UiNotice("Pengajuan surat berhasil dikirim.")
                loadLetters()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun letterTimeline(id: String, callback: (JSONObject?) -> Unit) {
        viewModelScope.launch {
            try { callback(api.letterTimeline(id)) } catch (e: Exception) { fail(e); callback(null) }
        }
    }

    fun letterAction(id: String, action: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                api.letterAction(id, action)
                notice.value = UiNotice("Status surat berhasil diperbarui.")
                loadLetters()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun openLetterPdf(id: String) {
        viewModelScope.launch {
            loading.value = true
            try {
                if (me.value?.role == "RW" || me.value?.role == "ADMIN") {
                    api.letterAction(id, "regenerate-pdf")
                }
                val file: File = api.downloadLetterPdf(id)
                val context = getApplication<Application>()
                val uri = FileProvider.getUriForFile(context, "com.pamoyanan.one.files", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading.value = false
            }
        }
    }

    fun loadComplaints() {
        viewModelScope.launch {
            loading.value = true
            try { complaints.value = api.complaints() } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun createComplaint(category: String, location: String, body: String, priority: String, rt: String?) {
        viewModelScope.launch {
            loading.value = true
            try {
                api.createComplaint(category, location, body, priority, rt)
                notice.value = UiNotice("Laporan berhasil dikirim.")
                loadComplaints()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun complaintAction(id: String, action: String, note: String?) {
        viewModelScope.launch {
            loading.value = true
            try {
                api.complaintAction(id, action, note)
                notice.value = UiNotice("Tindak lanjut laporan berhasil disimpan.")
                loadComplaints()
            } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun loadResidents(query: String = "") {
        viewModelScope.launch {
            loading.value = true
            try { residents.value = api.residents(query) } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun loadInbox() {
        viewModelScope.launch {
            loading.value = true
            try { inbox.value = api.inbox() } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            try {
                api.markAllNotificationsRead()
                loadInbox()
            } catch (e: Exception) { fail(e) }
        }
    }

    fun loadCommand() {
        viewModelScope.launch {
            loading.value = true
            try { command.value = api.modernDashboard() } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun loadGeneric(title: String, path: String) {
        genericTitle.value = title
        route.value = "generic"
        viewModelScope.launch {
            loading.value = true
            try { genericData.value = api.generic(path) } catch (e: Exception) { fail(e) }
            finally { loading.value = false }
        }
    }

    fun search(query: String) {
        if (query.length < 2) {
            searchResults.value = null
            return
        }
        viewModelScope.launch {
            try {
                searchResults.value = if (me.value?.role == "WARGA") null else api.globalSearch(query)
            } catch (e: Exception) { fail(e) }
        }
    }
}
