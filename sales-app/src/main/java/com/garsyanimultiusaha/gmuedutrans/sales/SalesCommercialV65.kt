package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

internal data class SalesCommercialPackageV65(
    val programName: String,
    val packageCode: String,
    val packageName: String,
    val minPax: Int,
    val marketingStartPrice: Double,
    val marketingPriceNote: String
)

internal data class SalesCommercialPartnerV65(
    val id: String,
    val code: String,
    val name: String,
    val type: String,
    val agreementNo: String
)

internal data class SalesCommercialCustomerV65(
    val id: String,
    val name: String
)

internal data class SalesCommercialPreviewV65(
    val eligible: Boolean,
    val status: String,
    val reason: String,
    val programName: String,
    val packageCode: String,
    val packageName: String,
    val channel: String,
    val sessionType: String,
    val bookingPax: Int,
    val pricingPax: Int,
    val customerPricePerPax: Double,
    val gmuNetPricePerPax: Double,
    val schoolCashbackPerPax: Double,
    val partnerMarginPerPax: Double,
    val salesCommissionPerPax: Double,
    val customerTotal: Double,
    val gmuNetTotal: Double,
    val schoolCashbackTotal: Double,
    val partnerMarginTotal: Double,
    val salesCommissionTotal: Double,
    val approvalRequired: Boolean
)

internal data class SalesCommercialBookingDraftV65(
    val customerId: String,
    val packageCode: String,
    val tripDate: String,
    val bookingPax: Int,
    val channel: String,
    val partnerId: String?,
    val sessionType: String,
    val pricingPax: Int,
    val status: String,
    val participantGroup: String,
    val meetingPoint: String,
    val sharedSessionCode: String
)

internal class SalesCommercialApiV65 {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun catalog(accessToken: String): List<SalesCommercialPackageV65> = withContext(Dispatchers.IO) {
        val arr = JSONArray(
            request(
                "GET",
                "/rest/v1/v_sales_commercial_catalog_v226?select=*&order=program_sort_order.asc,package_sort_order.asc",
                null,
                accessToken
            )
        )
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesCommercialPackageV65(
                        programName = x.optString("program_name", "Program GMU EduTrans"),
                        packageCode = x.optString("package_code", ""),
                        packageName = x.optString("package_name", "Paket"),
                        minPax = x.optInt("min_pax", 1),
                        marketingStartPrice = x.optDouble("marketing_start_price", 0.0),
                        marketingPriceNote = x.optString("marketing_price_note", "")
                    )
                )
            }
        }
    }

    suspend fun partners(accessToken: String): List<SalesCommercialPartnerV65> = withContext(Dispatchers.IO) {
        val arr = JSONArray(
            request(
                "GET",
                "/rest/v1/v_sales_b2b_partners_v226?select=*&order=name.asc",
                null,
                accessToken
            )
        )
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesCommercialPartnerV65(
                        id = x.optString("id", ""),
                        code = x.optString("partner_code", ""),
                        name = x.optString("name", "Partner B2B"),
                        type = x.optString("partner_type", ""),
                        agreementNo = x.optString("agreement_no", "")
                    )
                )
            }
        }
    }

    suspend fun customers(accessToken: String): List<SalesCommercialCustomerV65> = withContext(Dispatchers.IO) {
        val arr = JSONArray(
            request(
                "GET",
                "/rest/v1/customers?select=id,name&order=name.asc&limit=250",
                null,
                accessToken
            )
        )
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                val id = x.optString("id", "")
                val name = x.optString("name", "")
                if (id.isNotBlank() && name.isNotBlank()) add(SalesCommercialCustomerV65(id, name))
            }
        }
    }

    suspend fun preview(
        accessToken: String,
        packageCode: String,
        bookingPax: Int,
        channel: String,
        partnerId: String?,
        sessionType: String,
        pricingPax: Int,
        asOf: String?
    ): SalesCommercialPreviewV65 = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("p_package_code", packageCode)
            .put("p_booking_pax", bookingPax)
            .put("p_channel", channel)
            .put("p_partner_id", partnerId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_session_type", sessionType)
            .put("p_pricing_pax", pricingPax)
        if (!asOf.isNullOrBlank()) payload.put("p_as_of", asOf)
        parsePreview(
            JSONObject(
                request(
                    "POST",
                    "/rest/v1/rpc/resolve_sales_price_v226",
                    payload.toString(),
                    accessToken
                )
            )
        )
    }

    suspend fun createBooking(
        accessToken: String,
        draft: SalesCommercialBookingDraftV65
    ): String = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("p_customer_id", draft.customerId)
            .put("p_package_code", draft.packageCode)
            .put("p_trip_date", draft.tripDate)
            .put("p_booking_pax", draft.bookingPax)
            .put("p_channel", draft.channel)
            .put("p_partner_id", draft.partnerId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_session_type", draft.sessionType)
            .put("p_pricing_pax", draft.pricingPax)
            .put("p_status", draft.status)
            .put("p_participant_group", draft.participantGroup.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_meeting_point", draft.meetingPoint.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_shared_session_code", draft.sharedSessionCode.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val root = JSONObject(
            request(
                "POST",
                "/rest/v1/rpc/create_sales_booking_v226",
                payload.toString(),
                accessToken
            )
        )
        if (!root.optBoolean("ok", false)) throw IllegalStateException("Booking gagal dibuat.")
        root.optString("booking_no", "Booking")
    }

    private fun parsePreview(x: JSONObject) = SalesCommercialPreviewV65(
        eligible = x.optBoolean("eligible", false),
        status = x.optString("status", "BLOCKED"),
        reason = x.optString("reason", ""),
        programName = x.optString("program_name", ""),
        packageCode = x.optString("package_code", ""),
        packageName = x.optString("package_name", ""),
        channel = x.optString("channel", ""),
        sessionType = x.optString("session_type", ""),
        bookingPax = x.optInt("booking_pax", 0),
        pricingPax = x.optInt("pricing_pax", 0),
        customerPricePerPax = x.optDouble("customer_price_per_pax", 0.0),
        gmuNetPricePerPax = x.optDouble("gmu_net_price_per_pax", 0.0),
        schoolCashbackPerPax = x.optDouble("school_cashback_per_pax", 0.0),
        partnerMarginPerPax = x.optDouble("partner_margin_per_pax", 0.0),
        salesCommissionPerPax = x.optDouble("sales_commission_per_pax", 0.0),
        customerTotal = x.optDouble("customer_total", 0.0),
        gmuNetTotal = x.optDouble("gmu_net_total", 0.0),
        schoolCashbackTotal = x.optDouble("school_cashback_total", 0.0),
        partnerMarginTotal = x.optDouble("partner_margin_total", 0.0),
        salesCommissionTotal = x.optDouble("sales_commission_total", 0.0),
        approvalRequired = x.optBoolean("approval_required", false)
    )

    private fun request(method: String, path: String, body: String?, accessToken: String): String {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = BufferedReader(
            InputStreamReader(stream ?: throw IllegalStateException("Response server kosong."))
        ).use { it.readText() }
        connection.disconnect()
        if (code !in 200..299) {
            val message = runCatching {
                val root = JSONObject(response)
                root.optString("message")
                    .ifBlank { root.optString("msg") }
                    .ifBlank { root.optString("error") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { "Server merespons HTTP $code" })
        }
        return response
    }
}

@Composable
internal fun SalesCommercialV65Sheet(
    session: SalesSession,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val api = remember { SalesCommercialApiV65() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<SalesCommercialPackageV65>>(emptyList()) }
    var partners by remember { mutableStateOf<List<SalesCommercialPartnerV65>>(emptyList()) }
    var customers by remember { mutableStateOf<List<SalesCommercialCustomerV65>>(emptyList()) }
    var selectedPackage by remember { mutableStateOf<SalesCommercialPackageV65?>(null) }
    var selectedPartner by remember { mutableStateOf<SalesCommercialPartnerV65?>(null) }
    var selectedCustomer by remember { mutableStateOf<SalesCommercialCustomerV65?>(null) }
    var packageMenu by remember { mutableStateOf(false) }
    var partnerMenu by remember { mutableStateOf(false) }
    var customerMenu by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf("DIRECT_PUBLIC") }
    var sessionType by remember { mutableStateOf("PRIVATE") }
    var tripDate by remember { mutableStateOf("") }
    var paxText by remember { mutableStateOf("") }
    var pricingPaxText by remember { mutableStateOf("") }
    var sharedSessionCode by remember { mutableStateOf("") }
    var participantGroup by remember { mutableStateOf("") }
    var meetingPoint by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SalesCommercialPreviewV65?>(null) }
    var loading by remember { mutableStateOf(true) }
    var previewBusy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }

    val bookingPax = paxText.toIntOrNull() ?: 0
    val isStation = selectedPackage?.packageCode == "STATION-PROF-2026"
    val pricingPax = if (channel == "DIRECT_PUBLIC" && sessionType == "PRIVATE") {
        bookingPax
    } else {
        pricingPaxText.toIntOrNull() ?: bookingPax
    }
    val validDate = tripDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))

    LaunchedEffect(session.accessToken) {
        loading = true
        error = null
        runCatching {
            val loadedPackages = api.catalog(session.accessToken)
            val loadedPartners = api.partners(session.accessToken)
            val loadedCustomers = api.customers(session.accessToken)
            Triple(loadedPackages, loadedPartners, loadedCustomers)
        }.onSuccess { (loadedPackages, loadedPartners, loadedCustomers) ->
            packages = loadedPackages
            partners = loadedPartners
            customers = loadedCustomers
            selectedPackage = loadedPackages.firstOrNull { it.packageCode == "STATION-PROF-2026" }
                ?: loadedPackages.firstOrNull()
            selectedCustomer = loadedCustomers.firstOrNull()
        }.onFailure {
            error = it.message ?: "Master komersial belum dapat dimuat."
        }
        loading = false
    }

    LaunchedEffect(
        selectedPackage?.packageCode,
        selectedPartner?.id,
        channel,
        sessionType,
        bookingPax,
        pricingPax,
        tripDate
    ) {
        preview = null
        if (loading) return@LaunchedEffect
        val pkg = selectedPackage ?: return@LaunchedEffect
        if (bookingPax <= 0) return@LaunchedEffect
        if (channel == "B2B_MOU" && selectedPartner == null) {
            error = if (partners.isEmpty()) "Belum ada Partner B2B dengan MoU/PKS aktif." else "Pilih Partner B2B aktif."
            return@LaunchedEffect
        }
        if ((sessionType == "SHARED" || channel == "B2B_MOU") && pricingPax <= 0) return@LaunchedEffect

        previewBusy = true
        error = null
        runCatching {
            api.preview(
                accessToken = session.accessToken,
                packageCode = pkg.packageCode,
                bookingPax = bookingPax,
                channel = channel,
                partnerId = selectedPartner?.id,
                sessionType = if (isStation) sessionType else "PRIVATE",
                pricingPax = pricingPax,
                asOf = tripDate.takeIf { validDate }
            )
        }.onSuccess {
            preview = it
        }.onFailure {
            error = it.message ?: "Harga otomatis belum dapat dihitung."
        }
        previewBusy = false
    }

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text("PUBLIC / B2B", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF07580F))
        Text("Harga & booking otomatis dari Master ERP • Sales tidak dapat mengubah harga manual.", fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFE9E7)), shape = RoundedCornerShape(14.dp)) {
                Text(it, Modifier.padding(12.dp), fontSize = 10.sp, color = Color(0xFFB3261E))
            }
            Spacer(Modifier.height(8.dp))
        }
        result?.let {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6E8)), shape = RoundedCornerShape(14.dp)) {
                Text(it, Modifier.padding(12.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF07580F))
            }
            Spacer(Modifier.height(8.dp))
        }

        V65Picker(
            label = "Customer / Sekolah",
            value = selectedCustomer?.name ?: "Pilih customer",
            expanded = customerMenu,
            onExpanded = { customerMenu = it }
        ) {
            if (customers.isEmpty()) {
                DropdownMenuItem(text = { Text("Belum ada customer") }, onClick = { customerMenu = false })
            }
            customers.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { selectedCustomer = c; customerMenu = false })
            }
        }

        V65Picker(
            label = "Program / Paket",
            value = selectedPackage?.let { "${it.programName} — ${it.packageName}" } ?: "Pilih paket",
            expanded = packageMenu,
            onExpanded = { packageMenu = it }
        ) {
            packages.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.programName)
                            Text(p.packageName, fontSize = 10.sp, color = Color.Gray)
                        }
                    },
                    onClick = { selectedPackage = p; packageMenu = false }
                )
            }
        }

        Text("Channel Penjualan", fontSize = 10.sp, color = Color.Gray)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = channel == "DIRECT_PUBLIC",
                onClick = { channel = "DIRECT_PUBLIC"; selectedPartner = null },
                label = { Text("PUBLIC / UMUM") }
            )
            FilterChip(
                selected = channel == "B2B_MOU",
                onClick = { channel = "B2B_MOU"; sessionType = "PRIVATE" },
                label = { Text("B2B / PARTNER") }
            )
        }

        if (isStation) {
            Text("Tipe Sesi", fontSize = 10.sp, color = Color.Gray)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = sessionType == "PRIVATE", onClick = { sessionType = "PRIVATE" }, label = { Text("PRIVATE") })
                FilterChip(
                    selected = sessionType == "SHARED",
                    onClick = { if (channel == "DIRECT_PUBLIC") sessionType = "SHARED" },
                    enabled = channel == "DIRECT_PUBLIC",
                    label = { Text("SHARED") }
                )
            }
        }

        SalesPublicDetailV651Section(
            accessToken = session.accessToken,
            packageCode = selectedPackage?.packageCode.orEmpty(),
            visible = channel == "DIRECT_PUBLIC"
        )

        if (channel == "B2B_MOU") {
            V65Picker(
                label = "Partner B2B dengan MoU/PKS aktif",
                value = selectedPartner?.name ?: "Pilih partner",
                expanded = partnerMenu,
                onExpanded = { partnerMenu = it }
            ) {
                if (partners.isEmpty()) {
                    DropdownMenuItem(text = { Text("Belum ada Partner aktif") }, onClick = { partnerMenu = false })
                }
                partners.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(p.name)
                                Text(listOf(p.code, p.agreementNo).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 9.sp, color = Color.Gray)
                            }
                        },
                        onClick = { selectedPartner = p; partnerMenu = false }
                    )
                }
            }
        }

        V65Field(tripDate, { tripDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) }, "Tanggal trip (YYYY-MM-DD)")
        V65Field(paxText, { paxText = it.filter(Char::isDigit).take(5) }, "Pax sekolah / booking")

        if ((isStation && sessionType == "SHARED") || channel == "B2B_MOU") {
            V65Field(
                pricingPaxText,
                { pricingPaxText = it.filter(Char::isDigit).take(5) },
                if (sessionType == "SHARED") "Total pax sesi gabungan" else "Total pax event B2B"
            )
        }
        if (isStation && sessionType == "SHARED") {
            V65Field(sharedSessionCode, { sharedSessionCode = it.take(60) }, "Kode Shared Session (opsional)")
        }

        if (previewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        preview?.let { p ->
            Spacer(Modifier.height(8.dp))
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6E8))) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Harga Otomatis ERP", fontWeight = FontWeight.Black, color = Color(0xFF07580F))
                        Text(p.status, fontWeight = FontWeight.Black, fontSize = 10.sp, color = if (p.status == "SAFE") Color(0xFF07580F) else Color(0xFFB3261E))
                    }
                    V65Line("Harga resmi sekolah", "${v65Rupiah(p.customerPricePerPax)}/pax")
                    V65Line("Total customer", v65Rupiah(p.customerTotal))
                    if (p.channel == "B2B_MOU") {
                        V65Line("Net GMU → Partner", "${v65Rupiah(p.gmuNetPricePerPax)}/pax")
                        V65Line("Cashback sekolah", "${v65Rupiah(p.schoolCashbackPerPax)}/pax")
                        V65Line("Margin Partner", "${v65Rupiah(p.partnerMarginPerPax)}/pax")
                    }
                    V65Line("Komisi Sales", "${v65Rupiah(p.salesCommissionPerPax)}/pax")
                    V65Line("Potensi komisi booking", v65Rupiah(p.salesCommissionTotal))
                    if (p.sessionType == "SHARED") V65Line("Total pax sesi", p.pricingPax.toString())
                    if (p.reason.isNotBlank()) Text(p.reason, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        V65Field(participantGroup, { participantGroup = it.take(120) }, "Kelas / usia / grup (opsional)")
        V65Field(meetingPoint, { meetingPoint = it.take(160) }, "Titik kumpul (opsional)")

        val safe = preview?.let { it.eligible && it.status == "SAFE" } == true
        Button(
            onClick = {
                val customer = selectedCustomer ?: return@Button
                val pkg = selectedPackage ?: return@Button
                if (!safe || !validDate || bookingPax <= 0 || pricingPax <= 0) return@Button
                saving = true
                error = null
                result = null
                scope.launch {
                    runCatching {
                        api.createBooking(
                            accessToken = session.accessToken,
                            draft = SalesCommercialBookingDraftV65(
                                customerId = customer.id,
                                packageCode = pkg.packageCode,
                                tripDate = tripDate,
                                bookingPax = bookingPax,
                                channel = channel,
                                partnerId = selectedPartner?.id,
                                sessionType = if (isStation) sessionType else "PRIVATE",
                                pricingPax = pricingPax,
                                status = "Lead",
                                participantGroup = participantGroup,
                                meetingPoint = meetingPoint,
                                sharedSessionCode = sharedSessionCode
                            )
                        )
                    }.onSuccess { bookingNo ->
                        result = "$bookingNo berhasil dibuat dengan harga Master ERP."
                        onCreated(bookingNo)
                    }.onFailure {
                        error = it.message ?: "Booking gagal dibuat."
                    }
                    saving = false
                }
            },
            enabled = !loading && !previewBusy && !saving && selectedCustomer != null && selectedPackage != null && validDate && bookingPax > 0 && pricingPax > 0 && safe,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(if (saving) "Menyimpan…" else "Buat Booking dari Harga ERP", fontWeight = FontWeight.Black)
        }
        if (!safe && preview != null) {
            Text("Booking belum bisa dibuat karena hasil pricing belum SAFE. Harga tidak dapat dioverride dari aplikasi Sales.", fontSize = 9.sp, color = Color(0xFFB3261E), modifier = Modifier.padding(top = 6.dp))
        }
        TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Tutup") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun V65Picker(
    label: String,
    value: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(label, fontSize = 10.sp, color = Color.Gray)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { onExpanded(true) }, modifier = Modifier.fillMaxWidth()) { Text(value, maxLines = 2) }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }, content = content)
    }
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun V65Field(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun V65Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 9.sp, color = Color.Gray)
        Text(value, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

private fun v65Rupiah(value: Double): String = NumberFormat
    .getCurrencyInstance(Locale("id", "ID"))
    .format(value)
    .replace(",00", "")
