package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlanningScreen(vm: MainViewModel, session: SessionState, onNotice: (String) -> Unit) {
    if (!FinancialAccess.canView(session.profile.role)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Planning & Business Control hanya tersedia untuk Owner dan Manager.")
        }
        return
    }

    var tab by remember { mutableStateOf("Scorecard") }
    var targetDialog by remember { mutableStateOf<PlanningTargetRow?>(null) }
    var newTarget by remember { mutableStateOf(false) }
    var budgetDialog by remember { mutableStateOf<PlanningBudgetRow?>(null) }
    var newBudget by remember { mutableStateOf(false) }
    var weightDialog by remember { mutableStateOf<PipelineWeight?>(null) }
    val plan = vm.planningDashboard

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            SectionTitle("Planning & Business Control", "v2.3 • Target, budget, forecast & scenario")
            if (tab == "Target") {
                TextButton(onClick = { newTarget = true }) { Text("+ Target") }
            } else if (tab == "Budget") {
                TextButton(onClick = { newBudget = true }) { Text("+ Budget") }
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Scorecard", "Target", "Budget", "Forecast", "Simulator", "Program").forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(10.dp))

        if (plan == null) {
            if (vm.dataBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = GmuGreen)
            } else {
                EmptyCard(vm.planningError ?: "Planning v2.3 belum termuat.")
            }
            return@Column
        }

        when (tab) {
            "Scorecard" -> PlanningScorecardTab(plan)
            "Target" -> PlanningTargetTab(plan, onEdit = { targetDialog = it })
            "Budget" -> PlanningBudgetTab(
                plan,
                onEdit = { budgetDialog = it },
                onDelete = { row ->
                    vm.deletePlanningBudget(row.id) { _, msg -> onNotice(msg) }
                }
            )
            "Forecast" -> PlanningForecastTab(plan, onEditWeight = { weightDialog = it })
            "Simulator" -> PlanningSimulatorTab(vm, onNotice)
            else -> PlanningProgramTab(plan)
        }
    }

    if (newTarget || targetDialog != null) {
        PlanningTargetDialog(
            vm = vm,
            plan = plan,
            existing = targetDialog,
            busy = vm.actionBusy,
            onDismiss = { newTarget = false; targetDialog = null },
            onSaved = { msg ->
                newTarget = false
                targetDialog = null
                onNotice(msg)
            }
        )
    }

    if (newBudget || budgetDialog != null) {
        PlanningBudgetDialog(
            vm = vm,
            plan = plan,
            existing = budgetDialog,
            busy = vm.actionBusy,
            onDismiss = { newBudget = false; budgetDialog = null },
            onSaved = { msg ->
                newBudget = false
                budgetDialog = null
                onNotice(msg)
            }
        )
    }

    weightDialog?.let { existing ->
        PlanningWeightDialog(
            vm = vm,
            existing = existing,
            busy = vm.actionBusy,
            onDismiss = { weightDialog = null },
            onSaved = { msg ->
                weightDialog = null
                onNotice(msg)
            }
        )
    }
}

@Composable
private fun PlanningScorecardTab(plan: PlanningDashboard) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Revenue", rupiah(plan.revenue), Modifier.weight(1f), accent = true)
                MetricCard("Gross Profit", rupiah(plan.grossProfit), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Margin", pct(plan.marginPct), Modifier.weight(1f))
                MetricCard("Collection", pct(plan.collectionRatePct), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("AR Overdue", rupiah(plan.arOverdue), Modifier.weight(1f))
                MetricCard("AP Overdue", rupiah(plan.apOverdue), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    "Cash Runway",
                    plan.cashRunwayDays?.let { "$it hari" } ?: "> 180 hari",
                    Modifier.weight(1f)
                )
                MetricCard("Closing Ready", pct(plan.closingReadinessPct), Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Target vs Actual", fontWeight = FontWeight.Black, color = GmuDark)
                    Spacer(Modifier.height(8.dp))
                    TargetActualLine("Omzet", rupiah(plan.target.revenue), rupiah(plan.actual.revenue), plan.achievement.revenuePct)
                    TargetActualLine("Booking", plan.target.bookings.toString(), plan.actual.bookings.toString(), plan.achievement.bookingsPct)
                    TargetActualLine("Pax", plan.target.pax.toString(), plan.actual.pax.toString(), plan.achievement.paxPct)
                    TargetActualLine("Profit", rupiah(plan.target.profit), rupiah(plan.actual.profit), plan.achievement.profitPct)
                    TargetActualLine("Cash In", rupiah(plan.target.cashIn), rupiah(plan.actual.cashIn), plan.achievement.cashInPct)
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Business Control", fontWeight = FontWeight.Black, color = GmuDark)
                    Spacer(Modifier.height(8.dp))
                    SimpleLine("Budget variance", rupiah(plan.budgetVariance))
                    SimpleLine("Weighted pipeline 90D", rupiah(plan.weightedRevenue90d))
                    SimpleLine("Projected profit 90D", rupiah(plan.profit90d.profit))
                    SimpleLine("Projected margin 90D", pct(plan.profit90d.marginPct))
                }
            }
        }
    }
}

@Composable
private fun PlanningTargetTab(plan: PlanningDashboard, onEdit: (PlanningTargetRow) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7D6))) {
                Column(Modifier.padding(16.dp)) {
                    Text("Target bulan ${plan.periodMonth}", fontWeight = FontWeight.Black, color = GmuDark)
                    Text("Company • Sales • Program", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    SimpleLine("Target omzet", rupiah(plan.target.revenue))
                    SimpleLine("Target booking", plan.target.bookings.toString())
                    SimpleLine("Target pax", plan.target.pax.toString())
                    SimpleLine("Target profit", rupiah(plan.target.profit))
                    SimpleLine("Target margin", pct(plan.target.marginPct))
                    SimpleLine("Target cash in", rupiah(plan.target.cashIn))
                }
            }
        }
        if (plan.targets.isEmpty()) {
            item { EmptyCard("Belum ada target planning. Tekan + Target untuk membuat.") }
        } else {
            items(plan.targets, key = { it.id }) { row ->
                Card(onClick = { onEdit(row) }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.scopeType, fontWeight = FontWeight.Black, color = GmuDark)
                            StatusChip("Active")
                        }
                        Text("Omzet " + rupiah(row.targetRevenue) + " • Profit " + rupiah(row.targetProfit), fontSize = 12.sp)
                        Text(row.targetBookings.toString() + " booking • " + row.targetPax + " pax • " + pct(row.targetMarginPct) + " margin", fontSize = 11.sp, color = Color.Gray)
                        if (row.notes.isNotBlank()) Text(row.notes, fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningBudgetTab(
    plan: PlanningDashboard,
    onEdit: (PlanningBudgetRow) -> Unit,
    onDelete: (PlanningBudgetRow) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Management", rupiah(plan.managementBudgetTotal), Modifier.weight(1f))
                MetricCard("Trip RAB", rupiah(plan.tripRabTotal), Modifier.weight(1f), accent = true)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Committed", rupiah(plan.tripCommittedTotal), Modifier.weight(1f))
                MetricCard("Actual", rupiah(plan.tripActualTotal), Modifier.weight(1f))
            }
        }
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text("Trip budget source: RAB", fontWeight = FontWeight.Black, color = GmuDark)
                    Text(
                        "Budget trip tidak diduplikasi. v2.3 membaca RAB v1.7/v1.8 sebagai budget trip authoritative.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    SimpleLine("Trip variance", rupiah(plan.tripActualTotal - plan.tripRabTotal))
                    SimpleLine("Variance %", pct(plan.tripVariancePct))
                }
            }
        }
        if (plan.budgets.isEmpty()) {
            item { EmptyCard("Belum ada budget management bulanan.") }
        } else {
            items(plan.budgets, key = { it.id }) { row ->
                Card(onClick = { onEdit(row) }, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(row.category, fontWeight = FontWeight.Black, color = GmuDark)
                                Text(row.budgetType.replace("_", " "), fontSize = 11.sp, color = Color.Gray)
                            }
                            Text(rupiah(row.amount), fontWeight = FontWeight.Black, color = GmuGreen)
                        }
                        if (row.notes.isNotBlank()) Text(row.notes, fontSize = 10.sp, color = Color.Gray)
                        TextButton(onClick = { onDelete(row) }, contentPadding = PaddingValues(0.dp)) {
                            Text("Hapus", color = GmuDanger, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningForecastTab(plan: PlanningDashboard, onEditWeight: (PipelineWeight) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Gross Pipeline 90D", rupiah(plan.grossPipeline90d), Modifier.weight(1f))
                MetricCard("Weighted 90D", rupiah(plan.weightedRevenue90d), Modifier.weight(1f), accent = true)
            }
        }
        item { ForecastCard("30 Hari", plan.weightedRevenue30d, plan.profit30d) }
        item { ForecastCard("60 Hari", plan.weightedRevenue60d, plan.profit60d) }
        item { ForecastCard("90 Hari", plan.weightedRevenue90d, plan.profit90d) }
        item {
            Text("Pipeline probability", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
        }
        items(plan.pipelineWeights, key = { it.status }) { row ->
            Card(onClick = { onEditWeight(row) }, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(row.status, fontWeight = FontWeight.Bold)
                        Text(row.notes, fontSize = 10.sp, color = Color.Gray)
                    }
                    Text(pct(row.probabilityPct), color = GmuGreen, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ForecastCard(title: String, weightedRevenue: Double, forecast: ForecastPeriod) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, fontWeight = FontWeight.Black, color = GmuDark)
                StatusChip(if (forecast.marginPct >= 25) "Healthy" else "Watch")
            }
            Spacer(Modifier.height(8.dp))
            SimpleLine("Weighted revenue", rupiah(weightedRevenue))
            SimpleLine("Projected cost", rupiah(forecast.cost))
            SimpleLine("Projected profit", rupiah(forecast.profit))
            SimpleLine("Projected margin", pct(forecast.marginPct))
        }
    }
}

@Composable
private fun PlanningSimulatorTab(vm: MainViewModel, onNotice: (String) -> Unit) {
    var booking by remember { mutableStateOf(vm.bookings.firstOrNull()) }
    var pax by remember(booking?.id) { mutableStateOf(booking?.pax?.toString().orEmpty()) }
    var price by remember(booking?.id) { mutableStateOf(booking?.pricePerPax?.toString().orEmpty()) }
    var vendor by remember { mutableStateOf("0") }
    var transport by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("0") }
    var variableShare by remember { mutableStateOf("50") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Scenario Simulator", fontWeight = FontWeight.Black, color = GmuDark)
                    Text("Uji pax, harga, vendor, transport, diskon dan struktur biaya sebelum keputusan.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.height(10.dp))
                    val labels = vm.bookings.map { it.bookingNo + " • " + it.programName }
                    GmuSelect(
                        value = booking?.let { it.bookingNo + " • " + it.programName }.orEmpty(),
                        label = "Booking",
                        options = labels,
                        onSelect = { label ->
                            booking = vm.bookings.firstOrNull { it.bookingNo + " • " + it.programName == label }
                            pax = booking?.pax?.toString().orEmpty()
                            price = booking?.pricePerPax?.toString().orEmpty()
                            vm.clearPlanningScenario()
                        }
                    )
                    SmallField("Pax", pax) { pax = it }
                    SmallField("Harga / Pax", price) { price = it }
                    SmallField("Kenaikan Vendor %", vendor) { vendor = it }
                    SmallField("Kenaikan Transport %", transport) { transport = it }
                    SmallField("Diskon Customer %", discount) { discount = it }
                    SmallField("Variable Cost Share %", variableShare) { variableShare = it }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val b = booking ?: return@Button
                            vm.runPlanningScenario(
                                bookingId = b.id,
                                pax = pax.toIntOrNull(),
                                pricePerPax = price.toDoubleOrNull(),
                                vendorIncreasePct = vendor.toDoubleOrNull() ?: 0.0,
                                transportIncreasePct = transport.toDoubleOrNull() ?: 0.0,
                                discountPct = discount.toDoubleOrNull() ?: 0.0,
                                variableCostSharePct = variableShare.toDoubleOrNull() ?: 50.0
                            ) { _, msg -> onNotice(msg) }
                        },
                        enabled = booking != null && !vm.actionBusy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Hitung Scenario") }
                }
            }
        }

        vm.planningScenario?.let { r ->
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF7EF))) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Scenario Result", fontWeight = FontWeight.Black, color = GmuDark)
                        Spacer(Modifier.height(8.dp))
                        SimpleLine("Projected Revenue", rupiah(r.projectedRevenue))
                        SimpleLine("Projected Cost", rupiah(r.projectedCost))
                        SimpleLine("Projected Profit", rupiah(r.projectedProfit))
                        SimpleLine("Projected Margin", pct(r.projectedMarginPct))
                        SimpleLine("Profit / Pax", rupiah(r.profitPerPax))
                        SimpleLine("Cost / Pax", rupiah(r.costPerPax))
                        SimpleLine("Break-even Pax", r.breakEvenPax?.toString() ?: "-")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningProgramTab(plan: PlanningDashboard) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (plan.programs.isEmpty()) {
            item { EmptyCard("Belum ada data program untuk periode ini.") }
        } else {
            items(plan.programs, key = { it.programName }) { p ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(p.programName, fontWeight = FontWeight.Black, color = GmuDark, modifier = Modifier.weight(1f))
                            StatusChip(p.flag)
                        }
                        Spacer(Modifier.height(8.dp))
                        SimpleLine("Booking / Pax", p.bookingCount.toString() + " / " + p.paxTotal)
                        SimpleLine("Revenue", rupiah(p.revenue))
                        SimpleLine("Gross Profit", rupiah(p.grossProfit))
                        SimpleLine("Margin", pct(p.marginPct))
                        SimpleLine("Profit / Pax", rupiah(p.profitPerPax))
                        SimpleLine("Avg Selling / Pax", rupiah(p.averageSellingPrice))
                        SimpleLine("Avg Cost / Pax", rupiah(p.averageCostPerPax))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanningTargetDialog(
    vm: MainViewModel,
    plan: PlanningDashboard,
    existing: PlanningTargetRow?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var scope by remember(existing?.id) { mutableStateOf(existing?.scopeType ?: "COMPANY") }
    var salesId by remember(existing?.id) { mutableStateOf(existing?.salesId.orEmpty()) }
    var programId by remember(existing?.id) { mutableStateOf(existing?.programId.orEmpty()) }
    var revenue by remember(existing?.id) { mutableStateOf(existing?.targetRevenue?.toString() ?: "") }
    var bookings by remember(existing?.id) { mutableStateOf(existing?.targetBookings?.toString() ?: "") }
    var pax by remember(existing?.id) { mutableStateOf(existing?.targetPax?.toString() ?: "") }
    var profit by remember(existing?.id) { mutableStateOf(existing?.targetProfit?.toString() ?: "") }
    var margin by remember(existing?.id) { mutableStateOf(existing?.targetMarginPct?.toString() ?: "") }
    var cash by remember(existing?.id) { mutableStateOf(existing?.targetCashIn?.toString() ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }

    val sales = vm.table("profiles").filter { it.text("role") == "Sales" && it.text("is_active") != "false" }
    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    val salesLabel = sales.firstOrNull { it.id == salesId }?.text("full_name").orEmpty()
    val programLabel = programs.firstOrNull { it.id == programId }?.text("name").orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Target Planning" else "Edit Target") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                GmuSelect(scope, "Scope", listOf("COMPANY", "SALES", "PROGRAM"), { scope = it })
                Spacer(Modifier.height(8.dp))
                if (scope == "SALES") {
                    GmuSelect(salesLabel, "Sales", sales.map { it.text("full_name") }, { label ->
                        salesId = sales.firstOrNull { it.text("full_name") == label }?.id.orEmpty()
                    })
                    Spacer(Modifier.height(8.dp))
                }
                if (scope == "PROGRAM") {
                    GmuSelect(programLabel, "Program", programs.map { it.text("name") }, { label ->
                        programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                    })
                    Spacer(Modifier.height(8.dp))
                }
                SmallField("Target Omzet", revenue) { revenue = it }
                SmallField("Target Booking", bookings) { bookings = it }
                SmallField("Target Pax", pax) { pax = it }
                SmallField("Target Profit", profit) { profit = it }
                SmallField("Target Margin %", margin) { margin = it }
                SmallField("Target Cash In", cash) { cash = it }
                SmallField("Catatan", notes) { notes = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val month = plan.periodMonth.ifBlank { currentMonthStart() }
                    vm.savePlanningTarget(
                        existing?.id, month, scope,
                        salesId.takeIf { scope == "SALES" && it.isNotBlank() },
                        programId.takeIf { scope == "PROGRAM" && it.isNotBlank() },
                        revenue.toDoubleOrNull() ?: 0.0,
                        bookings.toIntOrNull() ?: 0,
                        pax.toIntOrNull() ?: 0,
                        profit.toDoubleOrNull() ?: 0.0,
                        margin.toDoubleOrNull() ?: 0.0,
                        cash.toDoubleOrNull() ?: 0.0,
                        notes
                    ) { ok, msg -> if (ok) onSaved(msg) }
                },
                enabled = !busy && when (scope) {
                    "SALES" -> salesId.isNotBlank()
                    "PROGRAM" -> programId.isNotBlank()
                    else -> true
                }
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun PlanningBudgetDialog(
    vm: MainViewModel,
    plan: PlanningDashboard,
    existing: PlanningBudgetRow?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var type by remember(existing?.id) { mutableStateOf(existing?.budgetType ?: "OPERATIONAL") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var programId by remember(existing?.id) { mutableStateOf(existing?.programId.orEmpty()) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.amount?.toString() ?: "") }
    var notes by remember(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    val programLabel = programs.firstOrNull { it.id == programId }?.text("name").orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Budget Management" else "Edit Budget") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                GmuSelect(type, "Jenis", listOf("OPERATIONAL", "MARKETING_SALES", "OVERHEAD", "PROGRAM"), { type = it })
                Spacer(Modifier.height(8.dp))
                if (type == "PROGRAM") {
                    GmuSelect(programLabel, "Program", programs.map { it.text("name") }, { label ->
                        programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                    })
                    Spacer(Modifier.height(8.dp))
                }
                SmallField("Kategori", category) { category = it }
                SmallField("Jumlah Budget", amount) { amount = it }
                SmallField("Catatan", notes) { notes = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.savePlanningBudget(
                        existing?.id,
                        plan.periodMonth.ifBlank { currentMonthStart() },
                        type,
                        category,
                        programId.takeIf { type == "PROGRAM" && it.isNotBlank() },
                        amount.toDoubleOrNull() ?: 0.0,
                        notes
                    ) { ok, msg -> if (ok) onSaved(msg) }
                },
                enabled = !busy && category.isNotBlank() && (type != "PROGRAM" || programId.isNotBlank())
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun PlanningWeightDialog(
    vm: MainViewModel,
    existing: PipelineWeight,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var probability by remember(existing.status) { mutableStateOf(existing.probabilityPct.toString()) }
    var notes by remember(existing.status) { mutableStateOf(existing.notes) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Pipeline Weight • ${existing.status}") },
        text = {
            Column {
                SmallField("Probability %", probability) { probability = it }
                SmallField("Catatan", notes) { notes = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.setPlanningPipelineWeight(existing.status, probability.toDoubleOrNull() ?: 0.0, notes) { ok, msg ->
                        if (ok) onSaved(msg)
                    }
                },
                enabled = !busy
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") } }
    )
}

@Composable
private fun TargetActualLine(label: String, target: String, actual: String, achievement: Double?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text(achievement?.let { pct(it) } ?: "-", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GmuGreen)
        }
        Text("Target " + target + "  •  Actual " + actual, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SimpleLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallField(label: String, value: String, onChange: (String) -> Unit) {
    Spacer(Modifier.height(7.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

private fun pct(value: Double): String = String.format(Locale.US, "%.1f%%", value)

private fun currentMonthStart(): String =
    SimpleDateFormat("yyyy-MM-01", Locale.US).format(Date())
