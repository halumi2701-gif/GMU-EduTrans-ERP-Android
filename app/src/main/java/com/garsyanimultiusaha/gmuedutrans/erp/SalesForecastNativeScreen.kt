package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private const val SALES_TARGET_PAX = 400
private const val SALES_BEP_PAX = 60
private const val SALES_PRODUCTIVE_PAX = 200
private const val SALES_STRETCH_PAX = 600
private const val SALES_AVG_PAX_PER_SCHOOL = 40
private const val SALES_PIPELINE_MULTIPLE = 3.0
private const val SALES_LEADS_MONTHLY = 200
private const val SALES_CONTACTED_MONTHLY = 160
private const val LEAD_TO_CONTACTED = 80.0
private const val CONTACTED_TO_QUALIFIED = 40.0
private const val QUALIFIED_TO_QUOTATION = 60.0
private const val QUOTATION_TO_CLOSE = 30.0
private const val DEFAULT_RETAINER = 600_000.0
private const val DEFAULT_FEE_PER_PAX = 2_500.0

private data class SalesForecastSnapshot(
    val paidPax: Int = 0,
    val paidBookings: Int = 0,
    val targetPax: Int = SALES_TARGET_PAX,
    val salesRetainer: Double = DEFAULT_RETAINER,
    val feePerPaidPax: Double = DEFAULT_FEE_PER_PAX,
    val leads: Int = 0,
    val contacted: Int = 0,
    val qualified: Int = 0,
    val quotations: Int = 0,
    val won: Int = 0,
    val openPipelinePax: Int = 0,
    val weightedPipelinePax: Double = 0.0,
    val hotPipelinePax: Int = 0,
    val followUpsDue: Int = 0,
    val wonPax: Int = 0,
    val error: String? = null
)

private data class SalesForecastComputed(
    val target: Int,
    val paid: Int,
    val achievement: Double,
    val remaining: Int,
    val day: Int,
    val totalDays: Int,
    val daysLeft: Int,
    val paceToday: Int,
    val paceForecast: Int,
    val requiredPerDay: Int,
    val weightedPotential: Int,
    val maxPotential: Int,
    val coverage: Double,
    val status: String,
    val statusNote: String,
    val schoolsNeeded: Int,
    val quotationsNeeded: Int,
    val qualifiedNeeded: Int,
    val contactedNeeded: Int,
    val leadsNeeded: Int,
    val avgWonPax: Double,
    val remainingSchools: Int
)

private fun SalesForecastSnapshot.compute(): SalesForecastComputed {
    val target = max(SALES_TARGET_PAX, targetPax)
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysLeft = max(totalDays - day, 0)
    val remaining = max(target - paidPax, 0)
    val paceToday = (target.toDouble() * day / totalDays).roundToInt()
    val paceForecast = if (paidPax >= target) paidPax else (paidPax.toDouble() / max(day, 1) * totalDays).roundToInt()
    val requiredPerDay = if (remaining == 0) 0 else ceil(remaining.toDouble() / max(daysLeft, 1)).toInt()
    val weightedPotential = paidPax + weightedPipelinePax.roundToInt()
    val maxPotential = paidPax + openPipelinePax
    val coverage = if (remaining > 0) openPipelinePax.toDouble() / remaining else SALES_PIPELINE_MULTIPLE
    val avgWon = if (won > 0 && wonPax > 0) wonPax.toDouble() / won else SALES_AVG_PAX_PER_SCHOOL.toDouble()

    val schoolsNeeded = ceil(target.toDouble() / SALES_AVG_PAX_PER_SCHOOL).toInt()
    val quotationsNeeded = ceil(schoolsNeeded / (QUOTATION_TO_CLOSE / 100.0)).toInt()
    val qualifiedNeeded = ceil(quotationsNeeded / (QUALIFIED_TO_QUOTATION / 100.0)).toInt()
    val contactedNeeded = ceil(qualifiedNeeded / (CONTACTED_TO_QUALIFIED / 100.0)).toInt()
    val modelLeads = ceil(contactedNeeded / (LEAD_TO_CONTACTED / 100.0)).toInt()
    val leadsNeeded = max(SALES_LEADS_MONTHLY, modelLeads)
    val remainingSchools = if (remaining == 0) 0 else ceil(remaining / max(avgWon, 1.0)).toInt()

    val (status, note) = when {
        paidPax >= target -> "TARGET TERCAPAI" to "Paid pax sudah mencapai target bulan ini."
        weightedPotential >= target && paidPax >= paceToday * 0.9 -> "ON TRACK" to "Pace paid pax dan weighted pipeline masih mendukung target akhir bulan."
        maxPotential >= target && (weightedPotential >= target || coverage >= SALES_PIPELINE_MULTIPLE) -> "MASIH MUNGKIN" to "Volume pipeline cukup, tetapi conversion dan pembayaran harus dijaga."
        maxPotential >= target -> "BERISIKO" to "Secara jumlah masih mungkin, tetapi probability/coverage pipeline belum aman."
        else -> "PIPELINE BELUM CUKUP" to "Seluruh pipeline aktif belum cukup untuk menutup kekurangan target."
    }

    return SalesForecastComputed(
        target = target,
        paid = paidPax,
        achievement = if (target > 0) paidPax.toDouble() / target * 100 else 0.0,
        remaining = remaining,
        day = day,
        totalDays = totalDays,
        daysLeft = daysLeft,
        paceToday = paceToday,
        paceForecast = paceForecast,
        requiredPerDay = requiredPerDay,
        weightedPotential = weightedPotential,
        maxPotential = maxPotential,
        coverage = coverage,
        status = status,
        statusNote = note,
        schoolsNeeded = schoolsNeeded,
        quotationsNeeded = quotationsNeeded,
        qualifiedNeeded = qualifiedNeeded,
        contactedNeeded = contactedNeeded,
        leadsNeeded = leadsNeeded,
        avgWonPax = avgWon,
        remainingSchools = remainingSchools
    )
}

@Composable
fun SalesForecastScreen(vm: MainViewModel, session: SessionState) {
    var loading by remember(session.userId) { mutableStateOf(true) }
    var snapshot by remember(session.userId) { mutableStateOf(SalesForecastSnapshot()) }

    LaunchedEffect(session.accessToken, session.userId) {
        loading = true
        snapshot = runCatching {
            SalesForecastApi().load(session.accessToken, session.userId, session.profile.role)
        }.getOrElse { SalesForecastSnapshot(error = it.message ?: "Forecast Sales gagal dimuat.") }
        loading = false
    }

    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text("Memuat Sales Funnel & Forecast…")
        }
        return
    }

    val c = snapshot.compute()
    val progress = (c.achievement / 100.0).coerceIn(0.0, 1.0).toFloat()
    val commission = snapshot.paidPax * snapshot.feePerPaidPax
    val modeledIncome = snapshot.salesRetainer + commission
    val bonusLabel = if (snapshot.paidPax >= c.target) "ELIGIBLE" else "BELUM"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Sales Funnel & Forecast", "Target 400 paid pax • seluruh program GMU EduTrans")
            Spacer(Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GmuSoft)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("TARGET BULAN INI", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text("${c.paid} / ${c.target} pax", fontSize = 26.sp, fontWeight = FontWeight.Black, color = GmuDark)
                        }
                        StatusChip(c.status)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(7.dp))
                    Text("${formatPct(c.achievement)}% • kurang ${c.remaining} pax", fontSize = 12.sp, color = GmuGreen, fontWeight = FontWeight.Bold)
                    Text(c.statusNote, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        item {
            ForecastPair(
                "Pace hari ini", "${c.paceToday} pax", "Hari ${c.day}/${c.totalDays}",
                "Kebutuhan sisa", "${c.requiredPerDay} pax/hari", "${c.daysLeft} hari tersisa"
            )
        }
        item {
            ForecastPair(
                "Pace forecast", "${c.paceForecast} pax", "Jika pace saat ini bertahan",
                "Weighted potential", "${c.weightedPotential} pax", "Paid + pipeline × probability"
            )
        }
        item {
            ForecastPair(
                "Maximum potential", "${c.maxPotential} pax", "Paid + seluruh pipeline aktif",
                "Pipeline coverage", "${formatPct(c.coverage)}×", "Minimum aman ${SALES_PIPELINE_MULTIPLE.toInt()}× kebutuhan sisa"
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("Funnel Minimum", "Model 40 pax/sekolah untuk menghasilkan 400 pax")
        }
        item { FunnelLine("Lead baru", snapshot.leads, c.leadsNeeded, "Target operasional 200 lead/bulan") }
        item { FunnelLine("Contacted", snapshot.contacted, SALES_CONTACTED_MONTHLY, "Lead → contacted minimal ${LEAD_TO_CONTACTED.toInt()}%") }
        item { FunnelLine("Qualified", snapshot.qualified, c.qualifiedNeeded, "Contacted → qualified minimal ${CONTACTED_TO_QUALIFIED.toInt()}%") }
        item { FunnelLine("Quotation", snapshot.quotations, c.quotationsNeeded, "Qualified → quotation minimal ${QUALIFIED_TO_QUOTATION.toInt()}%") }
        item { FunnelLine("Sekolah closing", snapshot.won, c.schoolsNeeded, "Quotation → closing minimal ${QUOTATION_TO_CLOSE.toInt()}%") }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("Prioritas Eksekusi", "Apa yang harus dikejar sebelum akhir bulan")
        }
        item {
            ForecastPair(
                "Gap lead", "${max(c.leadsNeeded - snapshot.leads, 0)}", "Lead baru yang masih dibutuhkan",
                "Gap quotation", "${max(c.quotationsNeeded - snapshot.quotations, 0)}", "Quotation yang masih dibutuhkan"
            )
        }
        item {
            ForecastPair(
                "Sisa sekolah", "${c.remainingSchools}", "Avg closing ${formatPct(c.avgWonPax)} pax/sekolah",
                "Follow-up jatuh tempo", "${snapshot.followUpsDue}", "Hot pipeline ${snapshot.hotPipelinePax} pax"
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            SectionTitle("Penghasilan Sales", "Komisi dihitung dari paid pax; bonus tetap melalui guardrail")
        }
        item {
            ForecastPair(
                "Fixed", rupiah(snapshot.salesRetainer), "Retainer bulanan",
                "Komisi berjalan", rupiah(commission), "${rupiah(snapshot.feePerPaidPax)} × ${snapshot.paidPax} paid pax"
            )
        }
        item {
            ForecastPair(
                "Bonus target", bonusLabel, "Eligible ≠ otomatis dibayar",
                "Income ter-model", rupiah(modeledIncome), "Belum memasukkan bonus yang belum disetujui"
            )
        }

        if (session.profile.role == "Sales") {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        "Privasi: Sales hanya melihat target, funnel, paid pax dan kompensasi miliknya. Laba, margin, kas perusahaan dan payroll pihak lain tetap tersembunyi.",
                        modifier = Modifier.padding(14.dp),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        snapshot.error?.takeIf { it.isNotBlank() }?.let { error ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(error, Modifier.padding(14.dp), fontSize = 11.sp)
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun ForecastPair(
    leftLabel: String,
    leftValue: String,
    leftNote: String,
    rightLabel: String,
    rightValue: String,
    rightNote: String
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ForecastCard(leftLabel, leftValue, leftNote, Modifier.weight(1f))
        ForecastCard(rightLabel, rightValue, rightNote, Modifier.weight(1f))
    }
}

@Composable
private fun ForecastCard(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label.uppercase(Locale("id", "ID")), fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, color = GmuDark, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(note, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun FunnelLine(label: String, actual: Int, target: Int, note: String) {
    val progress = if (target <= 0) 0f else (actual.toFloat() / target).coerceIn(0f, 1f)
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, fontWeight = FontWeight.Bold, color = GmuDark)
                Text("$actual / $target", fontWeight = FontWeight.Black, color = GmuGreen)
            }
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(5.dp))
            Text(note, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

private fun formatPct(value: Double): String = String.format(Locale("id", "ID"), "%.1f", value)

private class SalesForecastApi {
    private val base = BuildConfig.SUPABASE_URL
    private val key = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    suspend fun load(accessToken: String, userId: String, role: String): SalesForecastSnapshot = withContext(Dispatchers.IO) {
        val month = SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date())
        val summaryArr = JSONArray(request("POST", "/rest/v1/rpc/gmu_sales_portfolio_summary", JSONObject().put("p_period_month", month).toString(), accessToken))
        val summary = if (summaryArr.length() > 0) summaryArr.getJSONObject(0) else JSONObject()

        val salesScope = role == "Sales"
        val requestsPath = buildString {
            append("/rest/v1/booking_requests?select=id,pax,assigned_sales,created_at,converted_booking_id,status")
            if (salesScope) append("&assigned_sales=eq.").append(userId)
        }
        val controlsPath = buildString {
            append("/rest/v1/crm_lead_controls?select=booking_request_id,stage,owner_id,probability_pct,next_follow_up_at,won_at")
            if (salesScope) append("&owner_id=eq.").append(userId)
        }
        val requests = JSONArray(request("GET", requestsPath, null, accessToken))
        val controls = JSONArray(request("GET", controlsPath, null, accessToken))

        val paxByRequest = mutableMapOf<String, Int>()
        val createdByRequest = mutableMapOf<String, String>()
        for (i in 0 until requests.length()) {
            val x = requests.getJSONObject(i)
            val id = x.optString("id")
            paxByRequest[id] = x.optInt("pax", 0)
            createdByRequest[id] = x.optString("created_at", "")
        }

        fun isThisMonth(value: String): Boolean = value.startsWith(month.substring(0, 7))
        fun stageRank(stage: String): Int = when (stage.uppercase(Locale.US)) {
            "NEW" -> 0
            "CONTACTED", "NURTURE" -> 1
            "QUALIFIED" -> 2
            "QUOTATION" -> 3
            "NEGOTIATION" -> 4
            "WAITING_DP" -> 5
            "WON" -> 6
            "LOST" -> -1
            else -> 0
        }

        var leads = 0
        for (created in createdByRequest.values) if (isThisMonth(created)) leads++

        var contacted = 0
        var qualified = 0
        var quotations = 0
        var won = 0
        var wonPax = 0
        var openPipelinePax = 0
        var weightedPipeline = 0.0
        var hotPipelinePax = 0
        var followUpsDue = 0
        val todayUtc = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

        for (i in 0 until controls.length()) {
            val x = controls.getJSONObject(i)
            val requestId = x.optString("booking_request_id")
            val stage = x.optString("stage", "NEW").uppercase(Locale.US)
            val created = createdByRequest[requestId].orEmpty()
            val pax = paxByRequest[requestId] ?: 0
            val rank = stageRank(stage)

            if (isThisMonth(created)) {
                if (rank >= 1) contacted++
                if (rank >= 2) qualified++
                if (rank >= 3) quotations++
            }
            if (stage == "WON" && isThisMonth(x.optString("won_at", ""))) {
                won++
                wonPax += pax
            }
            if (stage != "WON" && stage != "LOST") {
                openPipelinePax += pax
                weightedPipeline += pax * x.optDouble("probability_pct", 10.0).coerceIn(0.0, 100.0) / 100.0
                if (stage == "NEGOTIATION" || stage == "WAITING_DP") hotPipelinePax += pax
                val due = x.optString("next_follow_up_at", "")
                if (due.isNotBlank() && due.take(10) <= todayUtc) followUpsDue++
            }
        }

        SalesForecastSnapshot(
            paidPax = summary.optInt("paid_pax", 0),
            paidBookings = summary.optInt("paid_bookings", 0),
            targetPax = max(SALES_TARGET_PAX, summary.optInt("target_paid_pax", SALES_TARGET_PAX)),
            salesRetainer = summary.optDouble("sales_retainer", DEFAULT_RETAINER),
            feePerPaidPax = summary.optDouble("sales_fee_per_paid_pax", DEFAULT_FEE_PER_PAX),
            leads = leads,
            contacted = contacted,
            qualified = qualified,
            quotations = quotations,
            won = won,
            openPipelinePax = openPipelinePax,
            weightedPipelinePax = weightedPipeline,
            hotPipelinePax = hotPipelinePax,
            followUpsDue = followUpsDue,
            wonPax = wonPax
        )
    }

    private fun request(method: String, path: String, body: String?, accessToken: String): String {
        val conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            if (body != null) doOutput = true
        }
        if (body != null) conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (conn.responseCode !in 200..299) throw IllegalStateException("Sales Forecast ${conn.responseCode}: $response")
        return response
    }
}
