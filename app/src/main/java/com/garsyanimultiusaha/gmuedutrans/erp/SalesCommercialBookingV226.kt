package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class SalesCommercialPackageV226(
    val programName: String,
    val packageCode: String,
    val packageName: String,
    val minPax: Int,
    val marketingStartPrice: Double,
    val marketingPriceNote: String
)

private data class SalesB2BPartnerV226(
    val id: String,
    val code: String,
    val name: String,
    val type: String,
    val agreementNo: String
)

private data class SalesPricePreviewV226(
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

private data class SalesBookingDraftV226(
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

private class SalesCommercialApiV226 {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun catalog(accessToken: String): List<SalesCommercialPackageV226> = withContext(Dispatchers.IO) {
        val body = request(
            "GET",
            "/rest/v1/v_sales_commercial_catalog_v226?select=*&order=program_sort_order.asc,package_sort_order.asc",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesCommercialPackageV226(
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

    suspend fun partners(accessToken: String): List<SalesB2BPartnerV226> = withContext(Dispatchers.IO) {
        val body = request(
            "GET",
            "/rest/v1/v_sales_b2b_partners_v226?select=*&order=name.asc",
            null,
            accessToken
        )
        val arr = JSONArray(body)
        buildList {
            for (i in 0 until arr.length()) {
                val x = arr.getJSONObject(i)
                add(
                    SalesB2BPartnerV226(
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

    suspend fun preview(
        accessToken: String,
        packageCode: String,
        bookingPax: Int,
        channel: String,
        partnerId: String?,
        sessionType: String,
        pricingPax: Int,
        asOf: String?
    ): SalesPricePreviewV226 = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("p_package_code", packageCode)
            .put("p_booking_pax", bookingPax)
            .put("p_channel", channel)
            .put("p_partner_id", partnerId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_session_type", sessionType)
            .put("p_pricing_pax", pricingPax)
        if (!asOf.isNullOrBlank()) payload.put("p_as_of", asOf)
        parsePreview(JSONObject(request("POST", "/rest/v1/rpc/resolve_sales_price_v226", payload.toString(), accessToken)))
    }

    suspend fun createBooking(accessToken: String, draft: SalesBookingDraftV226): String = withContext(Dispatchers.IO) {
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
            .put("p_participant_group", draft.participantGroup.ifBlank { JSONObject.NULL })
            .put("p_meeting_point", draft.meetingPoint.ifBlank { JSONObject.NULL })
            .put("p_shared_session_code", draft.sharedSessionCode.ifBlank { JSONObject.NULL })
        val root = JSONObject(request("POST", "/rest/v1/rpc/create_sales_booking_v226", payload.toString(), accessToken))
        if (!root.optBoolean("ok", false)) throw IllegalStateException("Booking gagal dibuat.")
        root.optString("booking_no", "Booking")
    }

    private fun parsePreview(x: JSONObject): SalesPricePreviewV226 = SalesPricePreviewV226(
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
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream ?: throw IllegalStateException("Response kosong"))).use { it.readText() }
        conn.disconnect()
        if (code !in 200..299) {
            val message = runCatching { JSONObject(response).optString("message", response) }.getOrDefault(response)
            throw IllegalStateException(message.ifBlank { "HTTP $code" })
        }
        return response
    }
}

@Composable
fun SalesCommercialBookingV226Screen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("All") }
    var add by remember { mutableStateOf(false) }
    val stages = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed")
    val filtered = vm.bookings.filter {
        (stage == "All" || it.status == stage) &&
            (query.isBlank() || it.bookingNo.contains(query, true) || it.programName.contains(query, true) || it.customerName.contains(query, true))
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle("Sales Booking", "Public & B2B • harga otomatis dari Master ERP")
            Button(onClick = { add = true }, shape = RoundedCornerShape(14.dp)) { Text("+ Booking") }
        }

        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GmuSoft)) {
            Column(Modifier.padding(14.dp)) {
                Text("Pricing Program Stasiun", fontWeight = FontWeight.Black, color = GmuDark)
                Text("UMUM Private: 20–24 Rp65.000 • 25–39 Rp58.000 • 40+ Rp49.500", fontSize = 11.sp)
                Text("UMUM Shared: Rp49.500 • total sesi minimal 40 pax", fontSize = 11.sp)
                Text("B2B Net: 40–59 Rp45.000 • 60–79 Rp44.500 • 80–99 Rp44.000 • 100+ Rp43.500", fontSize = 11.sp)
                Text("B2B: harga sekolah Rp49.500 • cashback sekolah Rp2.500/pax • komisi Sales Rp5.000/pax", fontSize = 11.sp, color = GmuGreen)
            }
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari booking / customer / program") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All").plus(stages).forEach { item ->
                FilterChip(selected = stage == item, onClick = { stage = item }, label = { Text(item) })
            }
        }

        Spacer(Modifier.height(8.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { EmptyCard("Belum ada booking pada filter ini.") }
            items(filtered, key = { it.id }) { booking ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(booking.bookingNo, fontWeight = FontWeight.Black, color = GmuDark)
                            StatusChip(booking.status)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(booking.programName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(booking.customerName, color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(7.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(booking.tripDate, fontSize = 11.sp)
                            Text("${booking.pax} pax", fontSize = 11.sp, color = GmuGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (add) {
        SalesCommercialBookingDialogV226(
            customers = vm.customers,
            session = session,
            busy = vm.actionBusy,
            onDismiss = { add = false },
            onCreated = { draft ->
                val api = SalesCommercialApiV226()
                val scopeSession = session
                // Keep write on the same live Supabase backend used by the ERP.
                vm.actionBusy = true
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
                    try {
                        val bookingNo = api.createBooking(scopeSession.accessToken, draft)
                        onNotice("$bookingNo berhasil dibuat dengan harga Master ERP.")
                        add = false
                        vm.loadAll(scopeSession)
                    } catch (e: Exception) {
                        onNotice(e.message ?: "Booking gagal dibuat.")
                    } finally {
                        vm.actionBusy = false
                    }
                }
            }
        )
    }
}

@Composable
private fun SalesCommercialBookingDialogV226(
    customers: List<Customer>,
    session: SessionState,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreated: (SalesBookingDraftV226) -> Unit
) {
    val api = remember { SalesCommercialApiV226() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<SalesCommercialPackageV226>>(emptyList()) }
    var partners by remember { mutableStateOf<List<SalesB2BPartnerV226>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    var customer by remember { mutableStateOf(customers.firstOrNull()) }
    var customerMenu by remember { mutableStateOf(false) }
    var selectedPackage by remember { mutableStateOf<SalesCommercialPackageV226?>(null) }
    var packageMenu by remember { mutableStateOf(false) }
    var channelLabel by remember { mutableStateOf("PUBLIC / UMUM") }
    var sessionType by remember { mutableStateOf("PRIVATE") }
    var partner by remember { mutableStateOf<SalesB2BPartnerV226?>(null) }
    var partnerMenu by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf("") }
    var pax by remember { mutableStateOf("") }
    var pricingPaxText by remember { mutableStateOf("") }
    var sharedCode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Lead") }
    var group by remember { mutableStateOf("") }
    var meeting by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<SalesPricePreviewV226?>(null) }
    var previewBusy by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }

    val channel = if (channelLabel.startsWith("B2B")) "B2B_MOU" else "DIRECT_PUBLIC"
    val ownPax = pax.toIntOrNull() ?: 0
    val isStation = selectedPackage?.packageCode == "STATION-PROF-2026"
    val pricingPax = when {
        channel == "DIRECT_PUBLIC" && sessionType == "PRIVATE" -> ownPax
        else -> pricingPaxText.toIntOrNull() ?: ownPax
    }

    LaunchedEffect(Unit) {
        loading = true
        try {
            packages = api.catalog(session.accessToken)
            partners = api.partners(session.accessToken)
            selectedPackage = packages.firstOrNull { it.packageCode == "STATION-PROF-2026" } ?: packages.firstOrNull()
            loadError = null
        } catch (e: Exception) {
            loadError = e.message ?: "Master pricing belum dapat dimuat."
        }
        loading = false
    }

    LaunchedEffect(selectedPackage?.packageCode, channel, sessionType, partner?.id, ownPax, pricingPax, date) {
        preview = null
        previewError = null
        val pkg = selectedPackage ?: return@LaunchedEffect
        if (ownPax <= 0) return@LaunchedEffect
        if (channel == "B2B_MOU" && partner == null) {
            previewError = if (partners.isEmpty()) "Belum ada Partner B2B dengan MoU/PKS aktif." else "Pilih Partner B2B aktif."
            return@LaunchedEffect
        }
        if ((sessionType == "SHARED" || channel == "B2B_MOU") && pricingPax <= 0) return@LaunchedEffect
        previewBusy = true
        try {
            preview = api.preview(
                accessToken = session.accessToken,
                packageCode = pkg.packageCode,
                bookingPax = ownPax,
                channel = channel,
                partnerId = partner?.id,
                sessionType = if (isStation) sessionType else "PRIVATE",
                pricingPax = pricingPax,
                asOf = date.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            )
        } catch (e: Exception) {
            previewError = e.message ?: "Harga belum dapat dihitung."
        }
        previewBusy = false
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Booking Sales • Master Pricing") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                loadError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }

                Text("Customer / Sekolah", fontSize = 11.sp, color = Color.Gray)
                Box {
                    OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(customer?.name ?: "Pilih Customer")
                    }
                    DropdownMenu(customerMenu, onDismissRequest = { customerMenu = false }) {
                        customers.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, onClick = { customer = c; customerMenu = false })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Program / Paket", fontSize = 11.sp, color = Color.Gray)
                Box {
                    OutlinedButton(onClick = { packageMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedPackage?.let { "${it.programName} — ${it.packageName}" } ?: "Pilih Paket")
                    }
                    DropdownMenu(packageMenu, onDismissRequest = { packageMenu = false }) {
                        packages.forEach { p ->
                            DropdownMenuItem(
                                text = { Column { Text(p.programName); Text(p.packageName, fontSize = 11.sp, color = Color.Gray) } },
                                onClick = { selectedPackage = p; packageMenu = false }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = channelLabel,
                    label = "Channel Penjualan",
                    options = listOf("PUBLIC / UMUM", "B2B / PARTNER"),
                    onSelect = {
                        channelLabel = it
                        partner = null
                        if (it.startsWith("B2B")) sessionType = "PRIVATE"
                    }
                )

                if (isStation) {
                    GmuSelect(
                        value = sessionType,
                        label = "Tipe Sesi",
                        options = listOf("PRIVATE", "SHARED"),
                        onSelect = { sessionType = it }
                    )
                }

                if (channel == "B2B_MOU") {
                    Text("Partner B2B", fontSize = 11.sp, color = Color.Gray)
                    Box {
                        OutlinedButton(onClick = { partnerMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(partner?.name ?: "Pilih Partner dengan MoU/PKS aktif")
                        }
                        DropdownMenu(partnerMenu, onDismissRequest = { partnerMenu = false }) {
                            if (partners.isEmpty()) {
                                DropdownMenuItem(text = { Text("Belum ada Partner aktif") }, onClick = { partnerMenu = false })
                            }
                            partners.forEach { p ->
                                DropdownMenuItem(
                                    text = { Column { Text(p.name); Text(listOf(p.code, p.agreementNo).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 10.sp, color = Color.Gray) } },
                                    onClick = { partner = p; partnerMenu = false }
                                )
                            }
                        }
                    }
                }

                GmuField(date, { date = it.filter { ch -> ch.isDigit() || ch == '-' } }, "Tanggal Trip (YYYY-MM-DD) *")
                GmuField(pax, { pax = it.filter(Char::isDigit) }, "Pax Sekolah / Booking *")

                if ((isStation && sessionType == "SHARED") || channel == "B2B_MOU") {
                    GmuField(
                        pricingPaxText,
                        { pricingPaxText = it.filter(Char::isDigit) },
                        if (sessionType == "SHARED") "Total Pax Sesi Gabungan *" else "Total Pax Event B2B *"
                    )
                }

                if (isStation && sessionType == "SHARED") {
                    GmuField(sharedCode, { sharedCode = it }, "Kode Shared Session")
                }

                Spacer(Modifier.height(8.dp))
                if (previewBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                previewError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                preview?.let { p ->
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = GmuSoft)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Harga Otomatis", fontWeight = FontWeight.Black, color = GmuDark)
                                StatusChip(p.status)
                            }
                            DetailLine("Harga resmi sekolah", rupiah(p.customerPricePerPax) + "/pax")
                            if (p.channel == "B2B_MOU") {
                                DetailLine("Net GMU → Partner", rupiah(p.gmuNetPricePerPax) + "/pax")
                                DetailLine("Cashback sekolah", rupiah(p.schoolCashbackPerPax) + "/pax")
                                DetailLine("Margin Partner", rupiah(p.partnerMarginPerPax) + "/pax")
                            }
                            DetailLine("Komisi Sales", rupiah(p.salesCommissionPerPax) + "/pax")
                            DetailLine("Potensi komisi booking", rupiah(p.salesCommissionTotal))
                            if (p.sessionType == "SHARED") DetailLine("Total pax sesi", p.pricingPax.toString())
                            if (p.reason.isNotBlank()) Text(p.reason, fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                GmuSelect(
                    value = status,
                    label = "Status",
                    options = listOf("Lead", "Quotation", "DP", "Confirmed", "Preparation", "Trip", "Completed", "Closed"),
                    onSelect = { status = it }
                )
                GmuField(group, { group = it }, "Kelas / Usia / Grup")
                GmuField(meeting, { meeting = it }, "Titik Kumpul")
            }
        },
        confirmButton = {
            val p = preview
            val validDate = date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))
            val canSave = !busy && !previewBusy && customer != null && selectedPackage != null &&
                ownPax > 0 && pricingPax > 0 && validDate && p != null && p.eligible && p.status == "SAFE"
            Button(
                onClick = {
                    onCreated(
                        SalesBookingDraftV226(
                            customerId = customer!!.id,
                            packageCode = selectedPackage!!.packageCode,
                            tripDate = date,
                            bookingPax = ownPax,
                            channel = channel,
                            partnerId = partner?.id,
                            sessionType = if (isStation) sessionType else "PRIVATE",
                            pricingPax = pricingPax,
                            status = status,
                            participantGroup = group,
                            meetingPoint = meeting,
                            sharedSessionCode = sharedCode
                        )
                    )
                },
                enabled = canSave
            ) { Text("Simpan Booking") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}
