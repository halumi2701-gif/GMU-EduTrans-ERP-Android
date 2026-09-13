package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val SCORE_EXEC_PREFIX = "[EXEC]"
private const val SCORE_OWNER_REVIEW = "Awaiting Owner Review"

private data class ExecutivePicScore(
    val id: String,
    val name: String,
    val role: String,
    val total: Int,
    val submitted: Int,
    val closed: Int,
    val active: Int,
    val overdue: Int,
    val returned: Int,
    val slaEvaluated: Int,
    val slaMet: Int,
    val avgSlaDeltaDays: Double?,
    val score: Int
)

/** Owner-only operational scorecard. No financial data or new backend permission is used. */
@Composable
fun GmuNativeAppWithExecutivePerformanceScorecard(vm: MainViewModel) {
    var open by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithExecutiveDecisionInbox(vm)
        val state = vm.state
        if (state is AppState.LoggedIn && state.session.profile.role == ErpRoles.OWNER && vm.currentPage == AppPage.DASHBOARD) {
            val scores = remember(vm.rows) { buildExecutiveScores(vm) }
            val average = scores.filter { it.total > 0 }.map { it.score }.average().takeIf { !it.isNaN() }?.roundToInt() ?: 0
            val overdue = scores.sumOf { it.overdue }

            Surface(
                onClick = { open = true },
                modifier = Modifier.align(Alignment.TopStart).padding(top = 184.dp, start = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = if (overdue > 0) Color(0xFFFFECEC) else Color.White,
                shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                    Text("Executive Scorecard", fontWeight = FontWeight.Black, color = GmuDark, fontSize = 11.sp)
                    Text(
                        if (scores.isEmpty()) "Belum ada PIC" else "Avg $average • $overdue overdue",
                        color = if (overdue > 0) GmuDanger else GmuGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                }
            }

            if (open) ExecutiveScorecardDialog(vm, scores, onDismiss = { open = false })
        }
    }
}

@Composable
private fun ExecutiveScorecardDialog(vm: MainViewModel, allScores: List<ExecutivePicScore>, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("All") }
    val scores = allScores.filter {
        filter == "All" || (filter == "Director" && ErpRoles.isDirector(it.role)) || (filter == "Manager" && ErpRoles.isManagerEduTrans(it.role))
    }.sortedWith(compareByDescending<ExecutivePicScore> { it.total > 0 }.thenByDescending { it.score }.thenBy { it.name })

    val totalActions = scores.sumOf { it.total }
    val submitted = scores.sumOf { it.submitted }
    val overdue = scores.sumOf { it.overdue }
    val returned = scores.sumOf { it.returned }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFF7F8FA)
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Executive Action Performance", fontWeight = FontWeight.Black, fontSize = 19.sp, color = GmuDark)
                        Text("Owner view • Direktur & Manager EduTrans", fontSize = 10.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onDismiss) { Text("Tutup") }
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ScoreMetric("Action", totalActions, GmuDark, Modifier.weight(1f))
                    ScoreMetric("Submitted", submitted, GmuGreen, Modifier.weight(1f))
                    ScoreMetric("Overdue", overdue, GmuDanger, Modifier.weight(1f))
                    ScoreMetric("Return", returned, GmuWarn, Modifier.weight(1f))
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Director", "Manager").forEach { value ->
                        FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(value, fontSize = 9.sp) })
                    }
                }

                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFF0F3EE)) {
                    Text(
                        "Score = 50% execution submission + 35% SLA compliance + 15% quality (minim return). SLA PIC berhenti saat PIC COMPLETION dikirim ke Owner, sehingga waktu Owner Review tidak membebani PIC.",
                        Modifier.fillMaxWidth().padding(11.dp),
                        fontSize = 9.sp,
                        color = Color.DarkGray
                    )
                }

                Spacer(Modifier.height(9.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    if (scores.isEmpty()) {
                        item { Text("Belum ada PIC Direktur/Manager yang dapat dinilai.", fontSize = 11.sp, color = Color.Gray) }
                    }
                    items(scores, key = { it.id }) { score -> ExecutivePicScoreCard(score) }
                }

                OutlinedButton(onClick = { vm.loadAll() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Refresh Scorecard", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ScoreMetric(label: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(10.dp)) {
            Text(value.toString(), fontWeight = FontWeight.Black, fontSize = 17.sp, color = accent)
            Text(label, fontSize = 8.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ExecutivePicScoreCard(item: ExecutivePicScore) {
    val accent = when {
        item.total == 0 -> Color.Gray
        item.score >= 85 -> GmuGreen
        item.score >= 70 -> GmuGold
        item.score >= 55 -> GmuWarn
        else -> GmuDanger
    }
    val executionPct = if (item.total == 0) 0 else ((item.submitted * 100.0) / item.total).roundToInt()
    val slaPct = if (item.slaEvaluated == 0) null else ((item.slaMet * 100.0) / item.slaEvaluated).roundToInt()

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = GmuDark)
                    Text(ErpRoles.displayName(item.role), fontSize = 9.sp, color = Color.Gray)
                }
                Surface(shape = RoundedCornerShape(50), color = accent.copy(alpha = .12f)) {
                    Text(
                        if (item.total == 0) "N/A" else "${item.score}/100",
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = accent
                    )
                }
            }

            Spacer(Modifier.height(9.dp))
            LinearProgressIndicator(
                progress = { if (item.total == 0) 0f else item.score / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = accent,
                trackColor = Color(0xFFE8EBE8)
            )
            Spacer(Modifier.height(9.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ScoreText("Assigned", item.total.toString())
                ScoreText("Execution", "$executionPct%")
                ScoreText("Closed", item.closed.toString())
                ScoreText("Active", item.active.toString())
            }
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ScoreText("SLA", slaPct?.let { "$it%" } ?: "N/A", if ((slaPct ?: 100) < 70) GmuDanger else GmuGreen)
                ScoreText("Overdue", item.overdue.toString(), if (item.overdue > 0) GmuDanger else GmuGreen)
                ScoreText("Return", item.returned.toString(), if (item.returned > 0) GmuWarn else GmuGreen)
                ScoreText("Avg delta", item.avgSlaDeltaDays?.let { String.format(Locale.US, "%+.1fd", it) } ?: "N/A")
            }
        }
    }
}

@Composable
private fun ScoreText(label: String, value: String, valueColor: Color = GmuDark) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Black, color = valueColor)
        Text(label, fontSize = 8.sp, color = Color.Gray)
    }
}

private fun buildExecutiveScores(vm: MainViewModel): List<ExecutivePicScore> {
    val assignments = vm.table("staff_assignments").filter { it.text("title").startsWith(SCORE_EXEC_PREFIX) }
    val profiles = vm.table("profiles").filter {
        it.text("is_active") != "false" && (ErpRoles.isDirector(it.text("role")) || ErpRoles.isManagerEduTrans(it.text("role")))
    }
    return profiles.map { profile ->
        val rows = assignments.filter { it.text("staff_id") == profile.id }
        val total = rows.size
        val closed = rows.count { scoreDone(it) }
        val submitted = rows.count { scoreCompletionDate(it) != null || it.text("status") == SCORE_OWNER_REVIEW || scoreDone(it) }
        val active = rows.count { !scoreDone(it) && it.text("status") != SCORE_OWNER_REVIEW }
        val returned = rows.count { it.text("notes").contains("OWNER RETURN") }
        val evaluated = rows.mapNotNull { row -> scoreSlaEvaluation(row) }
        val slaMet = evaluated.count { it <= 0 }
        val overdue = rows.count { row ->
            !scoreDone(row) && row.text("status") != SCORE_OWNER_REVIEW && scoreDaysUntil(row.text("due_date"))?.let { it < 0 } == true
        }
        val completedDeltas = rows.mapNotNull { scoreCompletionDelta(it) }
        val avgDelta = completedDeltas.takeIf { it.isNotEmpty() }?.average()
        val executionRate = if (total == 0) 0.0 else submitted.toDouble() / total
        val slaRate = if (evaluated.isEmpty()) 1.0 else slaMet.toDouble() / evaluated.size
        val qualityRate = if (total == 0) 1.0 else (1.0 - returned.toDouble() / total).coerceIn(0.0, 1.0)
        val score = if (total == 0) 0 else (executionRate * 50.0 + slaRate * 35.0 + qualityRate * 15.0).roundToInt()

        ExecutivePicScore(
            id = profile.id,
            name = profile.text("full_name").ifBlank { "Staff ${profile.id.take(8)}" },
            role = profile.text("role"),
            total = total,
            submitted = submitted,
            closed = closed,
            active = active,
            overdue = overdue,
            returned = returned,
            slaEvaluated = evaluated.size,
            slaMet = slaMet,
            avgSlaDeltaDays = avgDelta,
            score = score.coerceIn(0, 100)
        )
    }
}

private fun scoreDone(row: ErpRow): Boolean = row.text("status") in setOf("Done", "Completed", "Closed", "Resolved")

private fun scoreCompletionDate(row: ErpRow): Date? {
    val notes = row.text("notes")
    val match = Regex("PIC COMPLETION (\\d{4}-\\d{2}-\\d{2}) (\\d{2}:\\d{2}):").findAll(notes).lastOrNull() ?: return null
    return runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { isLenient = false }.parse("${match.groupValues[1]} ${match.groupValues[2]}") }.getOrNull()
}

private fun scoreDueDate(row: ErpRow): Date? = runCatching {
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(row.text("due_date"))
}.getOrNull()

private fun scoreCompletionDelta(row: ErpRow): Double? {
    val completion = scoreCompletionDate(row) ?: return null
    val due = scoreDueDate(row) ?: return null
    val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val completionDay = dayFormatter.parse(dayFormatter.format(completion)) ?: return null
    return (completionDay.time - due.time) / 86_400_000.0
}

private fun scoreSlaEvaluation(row: ErpRow): Double? {
    scoreCompletionDelta(row)?.let { return it }
    if (row.text("status") == SCORE_OWNER_REVIEW || scoreDone(row)) return null
    val days = scoreDaysUntil(row.text("due_date")) ?: return null
    return if (days < 0) (-days).toDouble() else null
}

private fun scoreDaysUntil(date: String): Int? = runCatching {
    if (date.isBlank()) return@runCatching null
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val target = f.parse(date) ?: return@runCatching null
    val today = f.parse(f.format(Date())) ?: return@runCatching null
    ((target.time - today.time).toDouble() / 86_400_000.0).roundToInt()
}.getOrNull()
