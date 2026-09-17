package id.zenstars.rukunya.nativeapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.zenstars.rukunya.nativeapp.billing.Feature
import id.zenstars.rukunya.nativeapp.billing.Plan
import id.zenstars.rukunya.nativeapp.billing.PlanSpec
import id.zenstars.rukunya.nativeapp.billing.PlanStore
import id.zenstars.rukunya.nativeapp.billing.Plans
import id.zenstars.rukunya.nativeapp.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class RukunyaViewModel(application: Application) : AndroidViewModel(application) {
    private val db = RukunyaDatabase.get(application)
    private val dao = db.dao()
    private val planStore = PlanStore(application)

    val activePlan: StateFlow<Plan> = planStore.plan
    val households = dao.observeHouseholds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val residentCount = dao.observeResidentCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val householdCount = dao.observeHouseholdCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val contributions = dao.observeContributions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cash = dao.observeCash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val announcements = dao.observeAnnouncements().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val letters = dao.observeLetters().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val admins = dao.observeAdmins().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun currentPlanSpec(): PlanSpec = Plans.get(activePlan.value)
    fun has(feature: Feature): Boolean = currentPlanSpec().has(feature)

    fun currentPeriod(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    fun addHousehold(kk: String, head: String, address: String, rt: String, phone: String) {
        val cleanKk = kk.trim()
        val cleanHead = head.trim()
        if (cleanKk.isBlank() || cleanHead.isBlank()) {
            _events.tryEmit("Nomor KK dan nama kepala keluarga wajib diisi.")
            return
        }
        val limit = currentPlanSpec().maxHouseholds
        if (limit != null && householdCount.value >= limit) {
            _events.tryEmit("Paket FREE maksimal $limit KK. Upgrade ke Basic untuk KK tanpa batas.")
            return
        }
        viewModelScope.launch {
            runCatching {
                val id = dao.insertHousehold(HouseholdEntity(kkNumber = cleanKk, headName = cleanHead, address = address.trim(), rt = rt.trim(), phone = phone.trim()))
                dao.insertResident(ResidentEntity(householdId = id, name = cleanHead, relation = "Kepala Keluarga"))
            }.onSuccess { _events.emit("Data keluarga tersimpan.") }
                .onFailure { _events.emit("Nomor KK sudah digunakan atau data tidak valid.") }
        }
    }

    fun addResident(householdId: Long, name: String, nik: String, relation: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertResident(ResidentEntity(householdId = householdId, name = name.trim(), nik = nik.trim(), relation = relation.trim().ifBlank { "Anggota Keluarga" }))
            _events.emit("Anggota keluarga ditambahkan.")
        }
    }

    fun addCash(type: String, amountText: String, category: String, note: String) {
        val amount = amountText.filter(Char::isDigit).toLongOrNull() ?: 0L
        if (amount <= 0) {
            _events.tryEmit("Nominal kas harus lebih dari Rp0.")
            return
        }
        viewModelScope.launch {
            dao.insertCash(CashEntryEntity(type = type, amount = amount, category = category.trim().ifBlank { "Umum" }, note = note.trim()))
            _events.emit("Transaksi kas tersimpan.")
        }
    }

    fun addContribution(householdId: Long, amountText: String, category: String) {
        val amount = amountText.filter(Char::isDigit).toLongOrNull() ?: 0L
        if (householdId <= 0 || amount <= 0) {
            _events.tryEmit("Pilih KK dan masukkan nominal iuran.")
            return
        }
        val resolvedCategory = if (has(Feature.CUSTOM_IURAN)) category.trim().ifBlank { "Iuran Bulanan" } else "Iuran Bulanan"
        viewModelScope.launch {
            dao.insertContribution(ContributionEntity(householdId = householdId, period = currentPeriod(), category = resolvedCategory, amount = amount))
            dao.insertCash(CashEntryEntity(type = "IN", amount = amount, category = "Iuran", note = resolvedCategory))
            _events.emit("Iuran tercatat dan masuk ke kas.")
        }
    }

    fun addAnnouncement(title: String, body: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            dao.insertAnnouncement(AnnouncementEntity(title = title.trim(), body = body.trim()))
            _events.emit("Pengumuman diterbitkan.")
        }
    }

    fun addLetter(residentName: String, type: String, purpose: String) {
        if (residentName.isBlank()) {
            _events.tryEmit("Nama warga wajib diisi.")
            return
        }
        viewModelScope.launch {
            val number = "${letters.value.size + 1}/RUKUNYA/${SimpleDateFormat("MM/yyyy", Locale.US).format(Date())}"
            dao.insertLetter(LetterEntity(residentName = residentName.trim(), type = type.trim().ifBlank { "Surat Pengantar" }, purpose = purpose.trim(), number = number))
            _events.emit("Permohonan surat dibuat.")
        }
    }

    fun setLetterStatus(id: Long, status: String) {
        if (!has(Feature.LETTER_APPROVAL) && status == "Disetujui") {
            _events.tryEmit("Approval surat tersedia di paket Pro.")
            return
        }
        viewModelScope.launch { dao.updateLetterStatus(id, status) }
    }

    fun addAdmin(name: String, role: String, phone: String) {
        if (!has(Feature.MULTI_ADMIN)) {
            _events.tryEmit("Multi-admin tersedia mulai paket Plus.")
            return
        }
        if (admins.value.size >= currentPlanSpec().maxAdmins) {
            _events.tryEmit("Batas admin paket ${currentPlanSpec().title}: ${currentPlanSpec().maxAdmins} akun.")
            return
        }
        if (name.isBlank()) return
        viewModelScope.launch {
            dao.insertAdmin(AdminProfileEntity(name = name.trim(), role = role.trim().ifBlank { "Pengurus" }, phone = phone.trim()))
            _events.emit("Profil pengurus ditambahkan.")
        }
    }

    fun applyVerifiedPlan(plan: Plan) {
        planStore.applyVerifiedPlan(plan)
        _events.tryEmit("Paket ${Plans.get(plan).title} aktif.")
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schema", 1)
        root.put("app", "RUKUNYA Native v2")
        root.put("createdAt", System.currentTimeMillis())
        root.put("households", JSONArray().apply { dao.allHouseholds().forEach { put(JSONObject().apply {
            put("id", it.id); put("kkNumber", it.kkNumber); put("headName", it.headName); put("address", it.address); put("rt", it.rt); put("phone", it.phone); put("housingStatus", it.housingStatus); put("createdAt", it.createdAt)
        }) } })
        root.put("residents", JSONArray().apply { dao.allResidents().forEach { put(JSONObject().apply {
            put("id", it.id); put("householdId", it.householdId); put("name", it.name); put("nik", it.nik); put("relation", it.relation); put("gender", it.gender); put("birthDate", it.birthDate)
        }) } })
        root.put("contributions", JSONArray().apply { dao.allContributions().forEach { put(JSONObject().apply {
            put("id", it.id); put("householdId", it.householdId); put("period", it.period); put("category", it.category); put("amount", it.amount); put("paidAt", it.paidAt)
        }) } })
        root.put("cash", JSONArray().apply { dao.allCash().forEach { put(JSONObject().apply {
            put("id", it.id); put("type", it.type); put("amount", it.amount); put("category", it.category); put("note", it.note); put("date", it.date)
        }) } })
        root.put("announcements", JSONArray().apply { dao.allAnnouncements().forEach { put(JSONObject().apply {
            put("id", it.id); put("title", it.title); put("body", it.body); put("createdAt", it.createdAt)
        }) } })
        root.put("letters", JSONArray().apply { dao.allLetters().forEach { put(JSONObject().apply {
            put("id", it.id); put("residentName", it.residentName); put("type", it.type); put("purpose", it.purpose); put("number", it.number); put("status", it.status); put("createdAt", it.createdAt)
        }) } })
        root.put("admins", JSONArray().apply { dao.allAdmins().forEach { put(JSONObject().apply {
            put("id", it.id); put("name", it.name); put("role", it.role); put("phone", it.phone)
        }) } })
        root.toString(2)
    }

    suspend fun importBackupJson(raw: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(raw)
        require(root.optInt("schema", 0) == 1) { "Format backup tidak dikenali" }

        val householdsJson = root.optJSONArray("households") ?: JSONArray()
        val residentsJson = root.optJSONArray("residents") ?: JSONArray()
        val contribJson = root.optJSONArray("contributions") ?: JSONArray()
        val cashJson = root.optJSONArray("cash") ?: JSONArray()
        val announcementsJson = root.optJSONArray("announcements") ?: JSONArray()
        val lettersJson = root.optJSONArray("letters") ?: JSONArray()
        val adminsJson = root.optJSONArray("admins") ?: JSONArray()

        db.withTransaction {
            dao.clearContributions(); dao.clearResidents(); dao.clearHouseholds(); dao.clearCash(); dao.clearAnnouncements(); dao.clearLetters(); dao.clearAdmins()

            dao.insertHouseholds((0 until householdsJson.length()).map { i -> householdsJson.getJSONObject(i).let { o -> HouseholdEntity(
                id = o.getLong("id"), kkNumber = o.getString("kkNumber"), headName = o.getString("headName"), address = o.optString("address"), rt = o.optString("rt"), phone = o.optString("phone"), housingStatus = o.optString("housingStatus", "Tetap"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ) } })
            dao.insertResidents((0 until residentsJson.length()).map { i -> residentsJson.getJSONObject(i).let { o -> ResidentEntity(
                id = o.getLong("id"), householdId = o.getLong("householdId"), name = o.getString("name"), nik = o.optString("nik"), relation = o.optString("relation", "Anggota Keluarga"), gender = o.optString("gender"), birthDate = o.optString("birthDate")
            ) } })
            dao.insertContributions((0 until contribJson.length()).map { i -> contribJson.getJSONObject(i).let { o -> ContributionEntity(
                id = o.getLong("id"), householdId = o.getLong("householdId"), period = o.getString("period"), category = o.getString("category"), amount = o.getLong("amount"), paidAt = o.optLong("paidAt", System.currentTimeMillis())
            ) } })
            dao.insertCashItems((0 until cashJson.length()).map { i -> cashJson.getJSONObject(i).let { o -> CashEntryEntity(
                id = o.getLong("id"), type = o.getString("type"), amount = o.getLong("amount"), category = o.optString("category", "Umum"), note = o.optString("note"), date = o.optLong("date", System.currentTimeMillis())
            ) } })
            dao.insertAnnouncements((0 until announcementsJson.length()).map { i -> announcementsJson.getJSONObject(i).let { o -> AnnouncementEntity(
                id = o.getLong("id"), title = o.getString("title"), body = o.optString("body"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ) } })
            dao.insertLetters((0 until lettersJson.length()).map { i -> lettersJson.getJSONObject(i).let { o -> LetterEntity(
                id = o.getLong("id"), residentName = o.getString("residentName"), type = o.getString("type"), purpose = o.optString("purpose"), number = o.getString("number"), status = o.optString("status", "Diajukan"), createdAt = o.optLong("createdAt", System.currentTimeMillis())
            ) } })
            dao.insertAdmins((0 until adminsJson.length()).map { i -> adminsJson.getJSONObject(i).let { o -> AdminProfileEntity(
                id = o.getLong("id"), name = o.getString("name"), role = o.optString("role", "Pengurus"), phone = o.optString("phone")
            ) } })
        }
    }
}
