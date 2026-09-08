package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import android.net.Uri
import android.os.Bundle\nimport android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val PILOT_AREA = "ID-JB-CJR-PILOT"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GawoneNotificationChannels.ensure(this)
        Stage4GDeepLinkRouter.accept(intent?.data)
        setContent { GawoneMitraStage4B() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Stage4GDeepLinkRouter.accept(intent.data)
    }
}

private val GawoneGreen = Color(0xFF0A6B47)
private val GawoneSoft = Color(0xFFE8F4EE)
private val GawoneBg = Color(0xFFF7FAF8)

private enum class Screen { PHONE, OTP, PROFILE, SERVICE, VEHICLE, KYC, STATUS }

internal data class Session(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val userId: String,
    val phone: String
)

private data class DocStatus(
    val id: String,
    val type: String,
    val status: String,
    val expiresAt: String?,
    val isExpired: Boolean,
    val canResubmit: Boolean,
    val rejectionReason: String?
)

private data class VehicleState(
    val id: String,
    val type: String,
    val plate: String,
    val verificationStatus: String
)

private data class Dashboard(
    val accountStatus: String,
    val onboardingStatus: String,
    val availabilityStatus: String
)

private data class KycPlan(
    val serviceCode: String?,
    val serviceName: String?,
    val requiresVehicle: Boolean,
    val allowedVehicleTypes: List<String>,
    val requiredDocuments: List<String>,
    val documents: List<DocStatus>,
    val vehicles: List<VehicleState>
)

private class ApiException(message: String) : Exception(message)

private class GawoneApi(private val context: Context) {
    private val secureSession = SecureSessionStore(context)
    private val baseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    fun readSession(): Session? = secureSession.read()

    private fun saveSession(s: Session) = secureSession.save(s)

    fun clearSession() = secureSession.clear()

    suspend fun requestOtp(phone: String) {
        request(
            path = "/auth/v1/otp",
            method = "POST",
            body = JSONObject().put("phone", phone).toString()
        )
    }

    suspend fun verifyOtp(phone: String, otp: String): Session {
        val raw = request(
            path = "/auth/v1/verify",
            method = "POST",
            body = JSONObject()
                .put("type", "sms")
                .put("phone", phone)
                .put("token", otp)
                .toString()
        )
        val json = JSONObject(raw)
        val user = json.getJSONObject("user")
        val expiresAt = json.optLong("expires_at").takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000L + json.optLong("expires_in", 3600L))
        val s = Session(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = expiresAt,
            userId = user.getString("id"),
            phone = user.optString("phone", phone)
        )
        saveSession(s)
        return s
    }

    suspend fun signOut() {
        runCatching {
            val s = validSession()
            request("/auth/v1/logout", "POST", token = s.accessToken)
        }
        clearSession()
    }

    suspend fun patchDisplayName(name: String) {
        val s = validSession()
        request(
            path = "/rest/v1/profiles?id=eq.${s.userId}",
            method = "PATCH",
            body = JSONObject().put("full_name", name).toString(),
            token = s.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    suspend fun registerPartner() {
        rpc("register_as_partner", JSONObject().put("p_partner_type", "INDIVIDUAL_PARTNER"))
    }

    suspend fun updatePartnerProfile(
        address: String,
        city: String,
        emergencyName: String,
        emergencyPhone: String,
        bio: String
    ) {
        rpc(
            "update_my_partner_profile",
            JSONObject()
                .put("p_address_text", address)
                .put("p_city_name", city)
                .put("p_birth_date", JSONObject.NULL)
                .put("p_emergency_contact_name", emergencyName)
                .put("p_emergency_contact_phone", emergencyPhone)
                .put("p_bio", bio)
        )
    }

    suspend fun selectService(code: String) {
        rpc("select_partner_service", JSONObject().put("p_service_code", code))
        rpc("set_partner_work_area", JSONObject().put("p_area_code", PILOT_AREA))
    }

    suspend fun registerVehicle(
        type: String,
        plate: String,
        brand: String,
        model: String,
        year: Int?,
        color: String
    ): String {
        val raw = rpc(
            "register_partner_vehicle",
            JSONObject()
                .put("p_vehicle_type", type)
                .put("p_ownership", "SELF_OWNED")
                .put("p_license_plate", plate)
                .put("p_brand", brand)
                .put("p_model", model)
                .put("p_production_year", year ?: JSONObject.NULL)
                .put("p_color", color)
        )
        return JSONObject(raw).getString("vehicleId")
    }

    suspend fun dashboard(): Dashboard? {
        val raw = rpc("get_my_partner_dashboard", JSONObject())
        if (raw.isBlank() || raw.trim() == "null") return null
        val j = JSONObject(raw)
        return Dashboard(
            accountStatus = j.optString("accountStatus"),
            onboardingStatus = j.optString("onboardingStatus"),
            availabilityStatus = j.optString("availabilityStatus")
        )
    }

    suspend fun kycPlan(): KycPlan {
        val raw = rpc("get_my_partner_kyc_plan", JSONObject())
        if (raw.isBlank() || raw.trim() == "null") {
            return KycPlan(null, null, false, emptyList(), emptyList(), emptyList(), emptyList())
        }
        val j = JSONObject(raw)
        val services = j.optJSONArray("services") ?: JSONArray()
        val service = if (services.length() > 0) services.getJSONObject(0) else null

        val docs = mutableListOf<DocStatus>()
        val docArray = j.optJSONArray("documents") ?: JSONArray()
        for (i in 0 until docArray.length()) {
            val d = docArray.getJSONObject(i)
            docs += DocStatus(
                id = d.optString("documentId"),
                type = d.optString("type"),
                status = d.optString("status"),
                expiresAt = d.optString("expiresAt").takeIf { it.isNotBlank() && it != "null" },
                isExpired = d.optBoolean("isExpired"),
                canResubmit = d.optBoolean("canResubmit"),
                rejectionReason = d.optString("rejectionReason").takeIf { it.isNotBlank() && it != "null" }
            )
        }

        val vehicles = mutableListOf<VehicleState>()
        val vehicleArray = j.optJSONArray("vehicles") ?: JSONArray()
        for (i in 0 until vehicleArray.length()) {
            val v = vehicleArray.getJSONObject(i)
            vehicles += VehicleState(
                id = v.optString("vehicleId"),
                type = v.optString("type"),
                plate = v.optString("plate"),
                verificationStatus = v.optString("verificationStatus")
            )
        }

        return KycPlan(
            serviceCode = service?.optString("serviceCode"),
            serviceName = service?.optString("serviceName"),
            requiresVehicle = service?.optBoolean("requiresVehicle") ?: false,
            allowedVehicleTypes = service?.optJSONArray("allowedVehicleTypes").toStringList(),
            requiredDocuments = service?.optJSONArray("requiredDocuments").toStringList(),
            documents = docs,
            vehicles = vehicles
        )
    }

    suspend fun uploadDocument(
        uri: Uri,
        documentType: String,
        expiresDate: String?,
        vehicleId: String?
    ) {
        val s = validSession()
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        require(mime in setOf("image/jpeg", "image/png")) { "Gunakan file JPG atau PNG." }

        val bytes = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ApiException("File tidak dapat dibaca.")
        }
        require(bytes.size <= 10 * 1024 * 1024) { "Ukuran file maksimum 10 MB." }

        val ext = if (mime == "image/png") "png" else "jpg"
        val path = "${s.userId}/$documentType/${UUID.randomUUID()}.$ext"

        requestBytes(
            path = "/storage/v1/object/partner-kyc-private/$path",
            bytes = bytes,
            mime = mime,
            token = s.accessToken
        )

        val expiresAt = expiresDate?.trim()?.takeIf { it.isNotBlank() }?.let {
            require(Regex("""\d{4}-\d{2}-\d{2}""").matches(it)) {
                "Tanggal berlaku harus format YYYY-MM-DD."
            }
            "${it}T23:59:59+07:00"
        }

        try {
            rpc(
                "register_partner_document",
                JSONObject()
                    .put("p_document_type", documentType)
                    .put("p_storage_path", path)
                    .put("p_mime_type", mime)
                    .put("p_expires_at", expiresAt ?: JSONObject.NULL)
                    .put("p_service_code", JSONObject.NULL)
                    .put("p_vehicle_id", vehicleId ?: JSONObject.NULL)
            )
        } catch (error: Throwable) {
            runCatching {
                request(
                    path = "/storage/v1/object/partner-kyc-private/$path",
                    method = "DELETE",
                    token = s.accessToken
                )
            }
            throw error
        }
    }

    private suspend fun rpc(name: String, body: JSONObject): String {
        val s = validSession()
        return request(
            path = "/rest/v1/rpc/$name",
            method = "POST",
            body = body.toString(),
            token = s.accessToken
        )
    }

    private suspend fun validSession(): Session {
        val current = readSession() ?: throw ApiException("Silakan login kembali.")
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAt - now > 60L) return current

        val raw = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", current.refreshToken).toString()
        )
        val j = JSONObject(raw)
        val user = j.getJSONObject("user")
        val refreshed = Session(
            accessToken = j.getString("access_token"),
            refreshToken = j.getString("refresh_token"),
            expiresAt = j.optLong("expires_at").takeIf { it > 0 }
                ?: (now + j.optLong("expires_in", 3600L)),
            userId = user.getString("id"),
            phone = user.optString("phone", current.phone)
        )
        saveSession(refreshed)
        return refreshed
    }

    private suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        token: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 25_000
            useCaches = false
            doInput = true
            setRequestProperty("apikey", key)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            extraHeaders.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            if (body != null) {
                c.doOutput = true
                c.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(raw, code))
            raw
        } finally {
            c.disconnect()
        }
    }

    private suspend fun requestBytes(
        path: String,
        bytes: ByteArray,
        mime: String,
        token: String
    ) = withContext(Dispatchers.IO) {
        val c = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            readTimeout = 45_000
            doInput = true
            doOutput = true
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", mime)
            setRequestProperty("x-upsert", "false")
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            c.outputStream.use { it.write(bytes) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiException(errorMessage(raw, code))
        } finally {
            c.disconnect()
        }
    }

    private fun errorMessage(raw: String, code: Int): String {
        return runCatching {
            val j = JSONObject(raw)
            j.optString("message")
                .ifBlank { j.optString("msg") }
                .ifBlank { j.optString("error_description") }
                .ifBlank { j.optString("error") }
        }.getOrDefault("").ifBlank { "Permintaan gagal (HTTP $code)." }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) add(optString(i))
        }
    }
}

@Composable
private fun GawoneMitraStage4B() {
    val context = LocalContext.current
    val api = remember { GawoneApi(context.applicationContext) }
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(Screen.PHONE) }
    var phone by rememberSaveable { mutableStateOf("") }
    var normalizedPhone by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var fullName by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("Cianjur") }
    var emergencyName by rememberSaveable { mutableStateOf("") }
    var emergencyPhone by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }

    var selectedService by rememberSaveable { mutableStateOf("") }
    var vehicleType by rememberSaveable { mutableStateOf("MOTORCYCLE") }
    var vehicleId by rememberSaveable { mutableStateOf<String?>(null) }
    var plate by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }

    var plan by remember { mutableStateOf<KycPlan?>(null) }
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var pendingDoc by remember { mutableStateOf<String?>(null) }
    val expiryInputs = remember { mutableStateMapOf<String, String>() }

    fun fail(t: Throwable) {
        error = t.message ?: "Terjadi kesalahan."
        loading = false
    }

    fun launchTask(block: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            message = null
            runCatching { block() }
                .onFailure(::fail)
                .onSuccess { loading = false }
        }
    }

    suspend fun refreshStatus() {
        dashboard = api.dashboard()
        plan = api.kycPlan()
        selectedService = plan?.serviceCode ?: selectedService
        vehicleId = plan?.vehicles?.firstOrNull()?.id ?: vehicleId
    }

    suspend fun bootstrap() {
        if (api.readSession() == null) {
            screen = Screen.PHONE
            return
        }
        val d = api.dashboard()
        if (d == null) {
            api.registerPartner()
            screen = Screen.PROFILE
            return
        }
        dashboard = d
        plan = api.kycPlan()
        selectedService = plan?.serviceCode.orEmpty()
        vehicleId = plan?.vehicles?.firstOrNull()?.id
        screen = when {
            d.onboardingStatus == "PROFILE_INCOMPLETE" -> Screen.PROFILE
            selectedService.isBlank() -> Screen.SERVICE
            plan?.requiresVehicle == true && plan?.vehicles?.isEmpty() == true -> Screen.VEHICLE
            plan?.documents?.isEmpty() == true -> Screen.KYC
            else -> Screen.STATUS
        }
    }

    LaunchedEffect(Unit) {
        runCatching { bootstrap() }.onFailure {
            api.clearSession()
            screen = Screen.PHONE
            error = "Sesi lama tidak dapat dipakai. Silakan login kembali."
        }
    }

    LaunchedEffect(screen) {
        if (screen == Screen.STATUS) {
            while (isActive) {
                runCatching { refreshStatus() }
                delay(5_000)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val doc = pendingDoc
        pendingDoc = null
        if (uri != null && doc != null) {
            val expiry = expiryInputs[doc]
            val docVehicle = if (doc == "STNK") vehicleId else null
            launchTask {
                api.uploadDocument(uri, doc, expiry, docVehicle)
                refreshStatus()
                message = "$doc berhasil dikirim dan menunggu verifikasi."
            }
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GawoneGreen,
            primaryContainer = GawoneSoft,
            background = GawoneBg,
            surface = Color.White
        )
    ) {
        when (screen) {
            Screen.PHONE -> PhoneScreen(
                phone = phone,
                loading = loading,
                error = error,
                onPhone = { phone = it },
                onSend = {
                    val n = normalizePhone(phone)
                    if (n == null) {
                        error = "Nomor HP Indonesia tidak valid."
                    } else {
                        normalizedPhone = n
                        launchTask {
                            api.requestOtp(n)
                            message = "OTP diminta ke ${maskPhone(n)}."
                            screen = Screen.OTP
                        }
                    }
                }
            )
            Screen.OTP -> OtpScreen(
                phone = normalizedPhone,
                otp = otp,
                loading = loading,
                error = error,
                message = message,
                onOtp = { otp = it.filter { it.isDigit() }.take(6) },
                onVerify = {
                    launchTask {
                        api.verifyOtp(normalizedPhone, otp)
                        api.registerPartner()
                        screen = Screen.PROFILE
                    }
                },
                onBack = { screen = Screen.PHONE }
            )
            Screen.PROFILE -> ProfileScreen(
                fullName, address, city, emergencyName, emergencyPhone, bio,
                loading, error,
                onName = { fullName = it },
                onAddress = { address = it },
                onCity = { city = it },
                onEmergencyName = { emergencyName = it },
                onEmergencyPhone = { emergencyPhone = it },
                onBio = { bio = it },
                onSave = {
                    launchTask {
                        require(fullName.trim().length >= 2) { "Nama lengkap wajib diisi." }
                        require(address.trim().length >= 4) { "Alamat wajib diisi." }
                        require(emergencyName.trim().length >= 2) { "Kontak darurat wajib diisi." }
                        require(emergencyPhone.filter { it.isDigit() }.length >= 8) { "Nomor darurat belum valid." }
                        api.patchDisplayName(fullName.trim())
                        api.updatePartnerProfile(address, city, emergencyName, emergencyPhone, bio)
                        screen = Screen.SERVICE
                    }
                }
            )
            Screen.SERVICE -> ServiceScreen(
                loading = loading,
                error = error,
                onChoose = { code ->
                    selectedService = code
                    launchTask {
                        api.selectService(code)
                        plan = api.kycPlan()
                        val needsVehicle = code in setOf("RIDE", "CAR", "DELIVERY")
                        vehicleType = if (code == "CAR") "CAR" else "MOTORCYCLE"
                        screen = if (needsVehicle) Screen.VEHICLE else Screen.KYC
                    }
                }
            )
            Screen.VEHICLE -> VehicleScreen(
                service = selectedService,
                type = vehicleType,
                plate = plate,
                brand = brand,
                model = model,
                year = year,
                color = color,
                loading = loading,
                error = error,
                onType = { vehicleType = it },
                onPlate = { plate = it },
                onBrand = { brand = it },
                onModel = { model = it },
                onYear = { year = it.filter { it.isDigit() }.take(4) },
                onColor = { color = it },
                onSave = {
                    launchTask {
                        require(plate.trim().length >= 3) { "Nomor polisi wajib diisi." }
                        vehicleId = api.registerVehicle(
                            vehicleType, plate, brand, model, year.toIntOrNull(), color
                        )
                        plan = api.kycPlan()
                        screen = Screen.KYC
                    }
                }
            )
            Screen.KYC -> KycScreen(
                service = selectedService,
                vehicleType = vehicleType,
                plan = plan,
                expiryInputs = expiryInputs,
                loading = loading,
                error = error,
                message = message,
                onExpiry = { doc, value -> expiryInputs[doc] = value },
                onPick = { doc ->
                    if (doc in setOf("SIM_A", "SIM_C", "STNK") &&
                        expiryInputs[doc].orEmpty().isBlank()) {
                        error = "Isi tanggal berlaku $doc terlebih dahulu."
                    } else {
                        error = null
                        pendingDoc = doc
                        picker.launch("image/*")
                    }
                },
                onRefresh = {
                    launchTask {
                        refreshStatus()
                        message = "Status dokumen diperbarui."
                    }
                },
                onStatus = {
                    launchTask {
                        refreshStatus()
                        screen = Screen.STATUS
                    }
                }
            )
            Screen.STATUS -> StatusScreen(
                dashboard = dashboard,
                plan = plan,
                loading = loading,
                error = error,
                message = message,
                onRefresh = {
                    launchTask {
                        refreshStatus()
                        message = "Status terbaru dimuat."
                    }
                },
                onDocuments = { screen = Screen.KYC },
                onLogout = {
                    launchTask {
                        api.signOut()
                        phone = ""
                        otp = ""
                        screen = Screen.PHONE
                    }
                }
            )
        }
    }
}

private fun normalizePhone(input: String): String? {
    val d = input.filter { it.isDigit() }
    val n = when {
        d.startsWith("62") -> "+$d"
        d.startsWith("0") -> "+62${d.drop(1)}"
        d.startsWith("8") -> "+62$d"
        else -> return null
    }
    return n.takeIf { it.length in 11..16 }
}

private fun maskPhone(phone: String): String =
    if (phone.length < 8) phone else phone.take(5) + "••••" + phone.takeLast(3)

@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = GawoneGreen, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(38.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("G", color = Color.White, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("GAWONE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("Mitra • Stage 4E", color = GawoneGreen, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Page(
    title: String,
    subtitle: String,
    loading: Boolean = false,
    error: String? = null,
    message: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(containerColor = GawoneBg) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Brand()
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            message?.let {
                Surface(color = GawoneSoft, shape = RoundedCornerShape(14.dp)) {
                    Text(it, Modifier.padding(12.dp))
                }
            }
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PhoneScreen(
    phone: String, loading: Boolean, error: String?,
    onPhone: (String) -> Unit, onSend: () -> Unit
) = Page(
    "Masuk / Daftar Mitra",
    "OTP dikirim melalui Supabase Phone Auth. Tidak ada OTP demo pada APK ini.",
    loading, error
) {
    OutlinedTextField(
        value = phone, onValueChange = { onPhone(it.take(18)) },
        modifier = Modifier.fillMaxWidth(), label = { Text("Nomor HP") },
        leadingIcon = { Icon(Icons.Outlined.PhoneAndroid, null) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        singleLine = true
    )
    Button(onClick = onSend, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Kirim OTP")
    }
    Text(
        "Jika SMS provider Supabase belum diaktifkan, server akan menolak pengiriman OTP dan aplikasi akan menampilkan pesan error asli.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun OtpScreen(
    phone: String, otp: String, loading: Boolean, error: String?, message: String?,
    onOtp: (String) -> Unit, onVerify: () -> Unit, onBack: () -> Unit
) = Page("Verifikasi OTP", "Kode dikirim ke ${maskPhone(phone)}.", loading, error, message) {
    OutlinedTextField(
        value = otp, onValueChange = onOtp, modifier = Modifier.fillMaxWidth(),
        label = { Text("6 digit OTP") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
    Button(
        onClick = onVerify, enabled = otp.length == 6 && !loading,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Verifikasi & Daftar Mitra") }
    TextButton(onClick = onBack) { Text("Ganti nomor HP") }
}

@Composable
private fun ProfileScreen(
    fullName: String, address: String, city: String,
    emergencyName: String, emergencyPhone: String, bio: String,
    loading: Boolean, error: String?,
    onName: (String) -> Unit, onAddress: (String) -> Unit, onCity: (String) -> Unit,
    onEmergencyName: (String) -> Unit, onEmergencyPhone: (String) -> Unit,
    onBio: (String) -> Unit, onSave: () -> Unit
) = Page("Profil Mitra", "Data dasar sebelum memilih layanan.", loading, error) {
    Field("Nama lengkap", fullName, onName)
    Field("Alamat domisili", address, onAddress)
    Field("Kabupaten/Kota", city, onCity)
    Field("Nama kontak darurat", emergencyName, onEmergencyName)
    Field("Nomor kontak darurat", emergencyPhone, onEmergencyPhone)
    Field("Pengalaman singkat", bio, onBio)
    Button(onClick = onSave, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text("Simpan Profil")
    }
}

@Composable
private fun ServiceScreen(
    loading: Boolean, error: String?, onChoose: (String) -> Unit
) = Page(
    "Pilih Layanan",
    "Dokumen dan kendaraan berikutnya menyesuaikan layanan.",
    loading, error
) {
    val services = listOf(
        "RIDE" to "Ride / Ojek",
        "CAR" to "Car",
        "DELIVERY" to "Kirim",
        "DRIVER" to "Driver",
        "CLEANING" to "Cleaning",
        "HANDYMAN" to "Tukang",
        "TECHNICIAN" to "Teknisi",
        "HELPER" to "Helper"
    )
    services.forEach { (code, label) ->
        OutlinedButton(
            onClick = { onChoose(code) }, enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) { Text(label) }
    }
}

@Composable
private fun VehicleScreen(
    service: String, type: String, plate: String, brand: String, model: String,
    year: String, color: String, loading: Boolean, error: String?,
    onType: (String) -> Unit, onPlate: (String) -> Unit, onBrand: (String) -> Unit,
    onModel: (String) -> Unit, onYear: (String) -> Unit, onColor: (String) -> Unit,
    onSave: () -> Unit
) = Page(
    "Data Kendaraan",
    "Kendaraan diperlukan untuk $service sebelum STNK dapat ditautkan.",
    loading, error
) {
    if (service == "DELIVERY") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = type == "MOTORCYCLE", onClick = { onType("MOTORCYCLE") }, label = { Text("Motor") })
            FilterChip(selected = type == "CAR", onClick = { onType("CAR") }, label = { Text("Mobil") })
        }
    } else {
        StatusLine("Jenis", if (type == "CAR") "Mobil" else "Motor")
    }
    Field("Nomor polisi", plate, onPlate)
    Field("Merek", brand, onBrand)
    Field("Model", model, onModel)
    Field("Tahun", year, onYear)
    Field("Warna", color, onColor)
    Button(onClick = onSave, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text("Simpan Kendaraan")
    }
}

@Composable
private fun KycScreen(
    service: String,
    vehicleType: String,
    plan: KycPlan?,
    expiryInputs: SnapshotStateMap<String, String>,
    loading: Boolean,
    error: String?,
    message: String?,
    onExpiry: (String, String) -> Unit,
    onPick: (String) -> Unit,
    onRefresh: () -> Unit,
    onStatus: () -> Unit
) = Page(
    "KYC & Dokumen",
    "File benar-benar dikirim ke private Storage GAWONE. Status hanya berasal dari backend reviewer.",
    loading, error, message
) {
    val required = remember(service, vehicleType) {
        buildList {
            add("KTP")
            add("SELFIE")
            when (service) {
                "RIDE" -> { add("SIM_C"); add("STNK") }
                "CAR" -> { add("SIM_A"); add("STNK") }
                "DELIVERY" -> {
                    add(if (vehicleType == "CAR") "SIM_A" else "SIM_C")
                    add("STNK")
                }
                "DRIVER" -> add("SIM_A")
            }
        }
    }

    required.forEach { type ->
        val existing = plan?.documents?.firstOrNull { it.type == type }
        DocumentCard(
            type = type,
            status = existing,
            expiry = expiryInputs[type].orEmpty(),
            needsExpiry = type in setOf("SIM_A", "SIM_C", "STNK"),
            loading = loading,
            onExpiry = { onExpiry(type, it) },
            onPick = { onPick(type) }
        )
    }

    OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("Perbarui Status")
    }
    Button(onClick = onStatus, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text("Lihat Status Verifikasi")
    }
}

@Composable
private fun DocumentCard(
    type: String,
    status: DocStatus?,
    expiry: String,
    needsExpiry: Boolean,
    loading: Boolean,
    onExpiry: (String) -> Unit,
    onPick: () -> Unit
) {
    val current = status?.status ?: "BELUM DIKIRIM"
    val canUpload = status == null || status.canResubmit
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(type, fontWeight = FontWeight.Bold)
                Text(current, color = statusColor(current), fontWeight = FontWeight.Bold)
            }
            status?.expiresAt?.let { Text("Berlaku sampai: $it", style = MaterialTheme.typography.bodySmall) }
            if (status?.isExpired == true) {
                Text("Dokumen kedaluwarsa — kirim dokumen terbaru.", color = MaterialTheme.colorScheme.error)
            }
            status?.rejectionReason?.let {
                Text("Alasan: $it", color = MaterialTheme.colorScheme.error)
            }
            if (needsExpiry && canUpload) {
                OutlinedTextField(
                    value = expiry,
                    onValueChange = onExpiry,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Berlaku sampai (YYYY-MM-DD)") },
                    singleLine = true
                )
            }
            Button(
                onClick = onPick,
                enabled = canUpload && !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        status == null -> "Pilih & Upload"
                        status.canResubmit -> "Kirim Ulang"
                        else -> "Menunggu / Sudah Diverifikasi"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusScreen(
    dashboard: Dashboard?,
    plan: KycPlan?,
    loading: Boolean,
    error: String?,
    message: String?,
    onRefresh: () -> Unit,
    onDocuments: () -> Unit,
    onLogout: () -> Unit
) = Page(
    "Status Verifikasi",
    "Status otomatis diperbarui dari backend setiap 5 detik saat halaman ini aktif.",
    loading, error, message
) {
    StatusLine("Akun", dashboard?.accountStatus ?: "-")
    StatusLine("Onboarding", dashboard?.onboardingStatus ?: "-")
    StatusLine("Layanan", plan?.serviceName ?: plan?.serviceCode ?: "-")
    plan?.vehicles?.forEach {
        StatusLine("Kendaraan ${it.plate}", it.verificationStatus)
    }
    Divider()
    Text("Dokumen", fontWeight = FontWeight.Bold)
    plan?.documents?.forEach {
        StatusLine(it.type, if (it.isExpired) "EXPIRED" else it.status)
        it.rejectionReason?.let { reason ->
            Text("• ${it.type}: $reason", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (dashboard?.onboardingStatus == "ACTIVE") {
        Surface(color = GawoneSoft, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Mitra sudah ACTIVE", fontWeight = FontWeight.Bold)
                Text("Online, order, eksekusi, pendapatan, notifikasi, chat, dan realtime terhubung sampai Stage 4G.")
            }
        }
        Stage4CPresencePanel(plan?.serviceCode)
        Stage4DOfferPanel(plan?.serviceCode)
        Stage4EExecutionPanel()
        Stage4FWalletPanel()
    } else {
        Text(
            "ACTIVE hanya dapat diberikan reviewer GAWONE setelah KYC, skill, dan kendaraan yang diperlukan lolos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text("Refresh Sekarang")
    }
    Button(onClick = onDocuments, modifier = Modifier.fillMaxWidth()) {
        Text("Kembali ke Dokumen")
    }
    TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text("Keluar")
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true
    )
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.Bold, color = statusColor(value))
    }
}

private fun statusColor(status: String): Color = when (status) {
    "ACTIVE", "VERIFIED", "APPROVED" -> GawoneGreen
    "REJECTED", "EXPIRED", "REQUIRES_UPDATE", "BLOCKED" -> Color(0xFFB42318)
    else -> Color(0xFF9A6700)
}
