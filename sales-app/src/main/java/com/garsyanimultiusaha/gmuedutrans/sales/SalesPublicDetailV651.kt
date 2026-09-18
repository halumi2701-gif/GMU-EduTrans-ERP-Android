package com.garsyanimultiusaha.gmuedutrans.sales

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

internal data class SalesPublicPriceTierV651(
    val sessionType: String,
    val minPax: Int,
    val maxPax: Int?,
    val unitPrice: Double,
    val salesCommissionPerPax: Double,
    val notes: String
)

internal data class SalesPublicPackageDetailV651(
    val packageCode: String,
    val packageName: String,
    val summary: String,
    val minPax: Int,
    val duration: String,
    val facilities: List<String>,
    val targetParticipants: List<String>,
    val sharedRules: List<String>,
    val sharedStatuses: List<String>,
    val bookingFlow: List<String>,
    val publicTerms: List<String>,
    val tiers: List<SalesPublicPriceTierV651>
)

private class SalesPublicDetailApiV651 {
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun load(accessToken: String, packageCode: String): SalesPublicPackageDetailV651? =
        withContext(Dispatchers.IO) {
            if (packageCode.isBlank()) return@withContext null

            val packages = JSONArray(
                request(
                    "/rest/v1/program_packages" +
                        "?select=package_code,name,sales_public_summary,min_pax,duration_text,facilities,target_participants,shared_rules,shared_statuses,booking_flow,public_terms" +
                        "&package_code=eq.$packageCode&limit=1",
                    accessToken
                )
            )
            if (packages.length() == 0) return@withContext null

            val p = packages.getJSONObject(0)
            val tiersJson = JSONArray(
                request(
                    "/rest/v1/program_package_price_tiers" +
                        "?select=session_type,min_pricing_pax,max_pricing_pax,unit_price,sales_commission_per_pax,notes" +
                        "&package_code=eq.$packageCode&channel=eq.DIRECT_PUBLIC&is_active=eq.true" +
                        "&order=session_type.asc,min_pricing_pax.asc",
                    accessToken
                )
            )

            val tiers = buildList {
                for (i in 0 until tiersJson.length()) {
                    val t = tiersJson.getJSONObject(i)
                    add(
                        SalesPublicPriceTierV651(
                            sessionType = t.optString("session_type", "PRIVATE"),
                            minPax = t.optInt("min_pricing_pax", 0),
                            maxPax = if (t.isNull("max_pricing_pax")) null else t.optInt("max_pricing_pax"),
                            unitPrice = t.optDouble("unit_price", 0.0),
                            salesCommissionPerPax = t.optDouble("sales_commission_per_pax", 0.0),
                            notes = t.optString("notes", "")
                        )
                    )
                }
            }

            SalesPublicPackageDetailV651(
                packageCode = p.optString("package_code", packageCode),
                packageName = p.optString("name", "Program GMU EduTrans"),
                summary = p.optString("sales_public_summary", ""),
                minPax = p.optInt("min_pax", 1),
                duration = p.optString("duration_text", ""),
                facilities = p.stringList("facilities"),
                targetParticipants = p.stringList("target_participants"),
                sharedRules = p.stringList("shared_rules"),
                sharedStatuses = p.stringList("shared_statuses"),
                bookingFlow = p.stringList("booking_flow"),
                publicTerms = p.stringList("public_terms"),
                tiers = tiers
            )
        }

    private fun request(path: String, accessToken: String): String {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
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
            throw IllegalStateException(message.ifBlank { "Detail program gagal dimuat (HTTP $code)." })
        }
        return response
    }
}

@Composable
internal fun SalesPublicDetailV651Section(
    accessToken: String,
    packageCode: String,
    visible: Boolean
) {
    if (!visible || packageCode.isBlank()) return

    val api = remember { SalesPublicDetailApiV651() }
    var detail by remember(packageCode) { mutableStateOf<SalesPublicPackageDetailV651?>(null) }
    var busy by remember(packageCode) { mutableStateOf(false) }
    var error by remember(packageCode) { mutableStateOf<String?>(null) }
    var expanded by remember(packageCode) { mutableStateOf(true) }

    LaunchedEffect(accessToken, packageCode) {
        busy = true
        error = null
        runCatching { api.load(accessToken, packageCode) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "Detail program belum dapat dimuat." }
        busy = false
    }

    Spacer(Modifier.height(8.dp))

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FBF5))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("DETAIL PROGRAM PUBLIC", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF07580F))
                    Text(detail?.packageName ?: "Master program dari ERP", fontSize = 9.sp, color = Color.Gray)
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Ringkas" else "Lihat Detail")
                }
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                return@Column
            }

            error?.let {
                Text(it, fontSize = 9.sp, color = Color(0xFFB3261E))
                return@Column
            }

            val d = detail ?: return@Column
            val minPublicPrice = d.tiers.minOfOrNull { it.unitPrice } ?: 0.0
            val commission = d.tiers.maxOfOrNull { it.salesCommissionPerPax } ?: 0.0

            if (minPublicPrice > 0) {
                Text(
                    "Mulai ${v651Rupiah(minPublicPrice)}/peserta",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF07580F)
                )
            }
            Text("Minimum registrasi ${d.minPax} peserta / sekolah", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

            if (!expanded) return@Column

            if (d.summary.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                V651SectionTitle("Ringkasan Program")
                Text(d.summary, fontSize = 10.sp, lineHeight = 15.sp)
            }

            if (d.tiers.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651SectionTitle("Price List PUBLIC / UMUM")
                d.tiers.forEach { tier ->
                    val range = if (tier.maxPax != null) "${tier.minPax}–${tier.maxPax} pax" else "${tier.minPax}+ pax"
                    val label = if (tier.sessionType == "SHARED") "Shared • total sesi $range" else "Private • $range"
                    V651PriceLine(label, "${v651Rupiah(tier.unitPrice)}/pax")
                }
                Text(
                    "Shared: minimal registrasi tiap sekolah ${d.minPax} peserta; harga mengikuti total gabungan sesi.",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }

            if (commission > 0) {
                Spacer(Modifier.height(10.dp))
                Surface(color = Color(0xFFEAF6E8), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Catatan Sales • Komisi ${v651Rupiah(commission)}/pax pada booking yang eligible.",
                        Modifier.padding(10.dp),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF07580F)
                    )
                }
            }

            if (d.facilities.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651BulletSection("Yang Didapatkan Peserta", d.facilities)
            }

            if (d.duration.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                V651SectionTitle("Durasi")
                Text(d.duration, fontSize = 10.sp, lineHeight = 15.sp)
            }

            if (d.targetParticipants.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651SectionTitle("Target Peserta")
                Text(d.targetParticipants.joinToString(" • "), fontSize = 10.sp, lineHeight = 15.sp)
            }

            if (d.sharedRules.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651BulletSection("Aturan Shared Education Session", d.sharedRules)
            }

            if (d.sharedStatuses.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651BulletSection("Status Shared Session", d.sharedStatuses)
            }

            if (d.bookingFlow.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651SectionTitle("Alur Booking")
                Text(d.bookingFlow.joinToString("  →  "), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, lineHeight = 16.sp)
            }

            if (d.publicTerms.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                V651BulletSection("Ketentuan PUBLIC", d.publicTerms)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Harga booking final tetap dihitung otomatis oleh Master Pricing ERP. Sales tidak dapat mengubah harga manual. HPP, laba, dan margin perusahaan tidak ditampilkan.",
                fontSize = 9.sp,
                color = Color.Gray,
                lineHeight = 13.sp
            )
        }
    }
}

@Composable
private fun V651SectionTitle(title: String) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF07580F), modifier = Modifier.padding(bottom = 5.dp))
}

@Composable
private fun V651BulletSection(title: String, items: List<String>) {
    V651SectionTitle(title)
    items.forEach { item ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text("•", fontSize = 10.sp, modifier = Modifier.width(14.dp))
            Text(item, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun V651PriceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

private fun JSONObject.stringList(key: String): List<String> {
    val arr = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val value = arr.optString(i, "").trim()
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun v651Rupiah(value: Double): String = NumberFormat
    .getCurrencyInstance(Locale("id", "ID"))
    .format(value)
    .replace(",00", "")
