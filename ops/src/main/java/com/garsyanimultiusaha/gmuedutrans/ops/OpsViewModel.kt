package com.garsyanimultiusaha.gmuedutrans.ops

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.launch

class OpsViewModel(application: Application) : AndroidViewModel(application) {
    private val api = OpsApi()
    private val masterKey = MasterKey.Builder(application)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        application,
        "gmu_ops_secure_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var session by mutableStateOf<OpsSession?>(null)
        private set
    var orders by mutableStateOf<List<OpsOrder>>(emptyList())
        private set
    var evidence by mutableStateOf(OpsEvidence())
        private set
    var orderId by mutableStateOf<String?>(null)
        private set

    val selected: OpsOrder?
        get() = orders.firstOrNull { it.id == orderId } ?: orders.firstOrNull()

    init {
        viewModelScope.launch {
            val saved = prefs.getString("refresh", null)
            if (saved.isNullOrBlank()) {
                loading = false
                return@launch
            }
            try {
                val active = api.refresh(saved)
                session = active
                persist(active)
                loadData(active)
            } catch (_: Exception) {
                prefs.edit().clear().apply()
                session = null
            }
            loading = false
        }
    }

    private fun persist(active: OpsSession) {
        prefs.edit().putString("refresh", active.refreshToken).apply()
    }

    fun clearError() { error = null }

    fun login(email: String, password: String) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                val active = api.signIn(email, password)
                session = active
                persist(active)
                loadData(active)
            } catch (e: Exception) {
                error = e.message ?: "Gagal masuk. Periksa akun ERP."
            }
            busy = false
        }
    }

    private suspend fun loadData(active: OpsSession) {
        val incomingOrders = api.loadOrders(active)
        orders = incomingOrders
        if (orderId !in incomingOrders.map { it.id }) orderId = incomingOrders.firstOrNull()?.id
        evidence = api.loadEvidence(active)
    }

    fun reload() {
        val active = session ?: return
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                loadData(active)
            } catch (e: Exception) {
                error = e.message ?: "Sinkronisasi dengan ERP gagal."
            }
            busy = false
        }
    }

    fun selectOrder(id: String) { orderId = id }

    fun setPresent(person: OpsPerson, present: Boolean) {
        val active = session ?: return
        if (busy) return
        busy = true
        error = null
        val current = evidence.attendance.firstOrNull { it.manifestId == person.id }
        viewModelScope.launch {
            try {
                api.setAttendance(active, person.bookingId, person.id, current?.id, present)
                evidence = api.loadEvidence(active)
            } catch (e: Exception) {
                error = e.message ?: "Absensi belum berhasil tersimpan di ERP."
            }
            busy = false
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        session = null
        orders = emptyList()
        evidence = OpsEvidence()
        orderId = null
        error = null
    }
}
