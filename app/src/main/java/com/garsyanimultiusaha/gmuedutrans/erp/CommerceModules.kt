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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PACKAGE_FACILITIES = listOf(
    "Transportasi", "Makan", "Snack", "Dokumentasi", "Sertifikat",
    "Worksheet", "Tour Leader", "Guide", "Asuransi", "Merchandise"
)

@Composable
fun PackageMasterScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    val canEdit = session.profile.role in listOf("Owner", "Manager", "Admin")
    val canApprove = FinancialAccess.canView(session.profile.role)
    if (!canEdit) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Master Paket tidak tersedia untuk role ini.")
        }
        return
    }

    var editor by remember { mutableStateOf<PackageMasterItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    val packages = vm.packageMaster
    val matrix = vm.pricingMaster?.matrix.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Package Master", "Paket internal → pricing guard → Web Customer")
            Button(onClick = { creating = true }, enabled = !vm.actionBusy) {
                Text("+ Paket")
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Total Paket", packages.size.toString(), Modifier.weight(1f))
            MetricCard(
                "ACTIVE",
                packages.count { it.status == "ACTIVE" && it.active }.toString(),
                Modifier.weight(1f),
                accent = true
            )
        }
        Spacer(Modifier.height(10.dp))

        if (vm.packageMasterError != null) {
            EmptyCard(vm.packageMasterError ?: "Master Paket gagal dimuat.")
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (packages.isEmpty()) {
                item { EmptyCard("Belum ada paket. Buat Draft Paket pertama dari tombol + Paket.") }
            } else {
                items(packages, key = { it.id }) { pkg ->
                    val ready = matrix.firstOrNull { it.packageId == pkg.id }
                    Card(shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(15.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(pkg.name, fontWeight = FontWeight.Black, color = GmuDark)
                                    Text(
                                        pkg.packageCode + " • " + pkg.programName,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(pkg.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            CommerceLine("Harga / pax", if (pkg.pricePerPax > 0) rupiah(pkg.pricePerPax) else "Belum diisi")
                            CommerceLine("Minimum pax", pkg.minPax.toString())
                            CommerceLine(
                                "Pricing Master",
                                ready?.setupStatus?.ifBlank { "-" } ?: "-"
                            )
                            ready?.recommendedPricePerPax?.let {
                                CommerceLine("Recommended / pax", rupiah(it))
                            }
                            ready?.floorPricePerPax?.let {
                                CommerceLine("Floor / pax", rupiah(it))
                            }
                            if (ready?.blockers?.isNotEmpty() == true) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    ready.blockers.joinToString(" • "),
                                    color = GmuWarn,
                                    fontSize = 10.sp
                                )
                            }
                            if (pkg.facilities.isNotEmpty()) {
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    pkg.facilities.joinToString(" • "),
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                if (pkg.status == "DRAFT") {
                                    OutlinedButton(
                                        onClick = { editor = pkg },
                                        enabled = !vm.actionBusy
                                    ) { Text("Edit") }

                                    OutlinedButton(
                                        onClick = {
                                            vm.clonePackage(pkg.id) { _, msg -> onNotice(msg) }
                                        },
                                        enabled = !vm.actionBusy
                                    ) { Text("Clone") }

                                    if (canApprove &&
                                        pkg.pricePerPax <= 0 &&
                                        ready?.recommendedPricePerPax != null
                                    ) {
                                        Button(
                                            onClick = {
                                                vm.applyRecommendedPackagePrice(pkg.id) { _, msg ->
                                                    onNotice(msg)
                                                }
                                            },
                                            enabled = !vm.actionBusy
                                        ) { Text("Apply Recommended") }
                                    }

                                    if (canApprove && pkg.pricePerPax > 0) {
                                        Button(
                                            onClick = {
                                                vm.activatePackage(pkg.id) { _, msg -> onNotice(msg) }
                                            },
                                            enabled = !vm.actionBusy
                                        ) { Text("Activate") }
                                    }
                                } else if (pkg.status == "ACTIVE") {
                                    OutlinedButton(
                                        onClick = {
                                            vm.clonePackage(pkg.id) { _, msg -> onNotice(msg) }
                                        },
                                        enabled = !vm.actionBusy
                                    ) { Text("Clone Revision") }

                                    if (canApprove) {
                                        OutlinedButton(
                                            onClick = {
                                                vm.archivePackage(pkg.id) { _, msg -> onNotice(msg) }
                                            },
                                            enabled = !vm.actionBusy
                                        ) { Text("Archive") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creating || editor != null) {
        PackageEditorDialog(
            vm = vm,
            existing = editor,
            busy = vm.actionBusy,
            onDismiss = {
                creating = false
                editor = null
            },
            onSave = { id, programId, name, desc, price, minPax, facilities, note, from, until, sort ->
                vm.savePackageDraft(
                    id, programId, name, desc, price, minPax, facilities,
                    note, from, until, sort
                ) { ok, msg ->
                    onNotice(msg)
                    if (ok) {
                        creating = false
                        editor = null
                    }
                }
            }
        )
    }
}

@Composable
private fun PackageEditorDialog(
    vm: MainViewModel,
    existing: PackageMasterItem?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        String?, String, String, String, Double, Int, List<String>,
        String, String, String, Int
    ) -> Unit
) {
    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    var programId by remember(existing?.id) { mutableStateOf(existing?.programId.orEmpty()) }
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var price by remember(existing?.id) {
        mutableStateOf(if ((existing?.pricePerPax ?: 0.0) > 0) existing!!.pricePerPax.toString() else "")
    }
    var minPax by remember(existing?.id) { mutableStateOf((existing?.minPax ?: 20).toString()) }
    var priceNote by remember(existing?.id) { mutableStateOf(existing?.priceNote.orEmpty()) }
    var effectiveFrom by remember(existing?.id) { mutableStateOf(existing?.effectiveFrom.orEmpty()) }
    var effectiveUntil by remember(existing?.id) { mutableStateOf(existing?.effectiveUntil.orEmpty()) }
    var sortOrder by remember(existing?.id) { mutableStateOf((existing?.sortOrder ?: 0).toString()) }
    var selectedFacilities by remember(existing?.id) {
        mutableStateOf(existing?.facilities?.toSet() ?: emptySet())
    }

    val programLabel = programs.firstOrNull { it.id == programId }?.text("name")
        ?: existing?.programName.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Buat Draft Paket" else "Edit Draft Paket") },
        text = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                GmuSelect(
                    value = programLabel,
                    label = "Program",
                    options = programs.map { it.text("name") },
                    onSelect = { label ->
                        programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                    }
                )
                Spacer(Modifier.height(7.dp))
                CommerceField("Nama Paket", name) { name = it }
                CommerceField("Deskripsi", description, singleLine = false) { description = it }
                CommerceField("Harga / pax", price) { price = it }
                CommerceField("Minimum Pax", minPax) { minPax = it }
                CommerceField("Price Note", priceNote, singleLine = false) { priceNote = it }
                CommerceField("Effective From (YYYY-MM-DD)", effectiveFrom) { effectiveFrom = it }
                CommerceField("Effective Until (opsional)", effectiveUntil) { effectiveUntil = it }
                CommerceField("Sort Order", sortOrder) { sortOrder = it }

                Spacer(Modifier.height(8.dp))
                Text("Fasilitas", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                PACKAGE_FACILITIES.forEach { facility ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = facility in selectedFacilities,
                            onCheckedChange = { checked ->
                                selectedFacilities = if (checked) {
                                    selectedFacilities + facility
                                } else {
                                    selectedFacilities - facility
                                }
                            }
                        )
                        Text(facility, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            val p = price.toDoubleOrNull() ?: 0.0
            val pax = minPax.toIntOrNull() ?: 0
            Button(
                onClick = {
                    onSave(
                        existing?.id, programId, name, description, p, pax,
                        selectedFacilities.toList(), priceNote,
                        effectiveFrom, effectiveUntil, sortOrder.toIntOrNull() ?: 0
                    )
                },
                enabled = !busy && programId.isNotBlank() && name.trim().length >= 2 && p >= 0 && pax > 0
            ) { Text("Simpan Draft") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

@Composable
fun PricingMasterScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    if (!FinancialAccess.canView(session.profile.role)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Pricing Master hanya tersedia untuk Owner / Manager.")
        }
        return
    }

    val data = vm.pricingMaster
    var costDialog by remember { mutableStateOf(false) }
    var policyDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle(
            "Pricing Master",
            "Cost template • margin policy • package readiness"
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { costDialog = true }, enabled = !vm.actionBusy) {
                Text("+ Cost Template")
            }
            OutlinedButton(onClick = { policyDialog = true }, enabled = !vm.actionBusy) {
                Text("+ Pricing Policy")
            }
            OutlinedButton(onClick = vm::refreshCommerce, enabled = !vm.actionBusy) {
                Text("Refresh")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (data == null) {
            EmptyCard(vm.pricingMasterError ?: "Pricing Master belum termuat.")
            return
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard(
                "Program Ready",
                data.programReady.toString() + "/" + data.programTotal,
                Modifier.weight(1f),
                accent = data.programReady == data.programTotal && data.programTotal > 0
            )
            MetricCard(
                "Cost Template",
                data.setup.activeCostTemplates.toString(),
                Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(10.dp))

        if (!data.setup.automaticPricingReady) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8))
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text("Setup belum lengkap", fontWeight = FontWeight.Black, color = GmuWarn)
                    if (data.setup.blockers.isNotEmpty()) {
                        Text(
                            data.setup.blockers.joinToString(" • "),
                            fontSize = 11.sp,
                            color = GmuWarn
                        )
                    }
                    if (data.setup.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            data.setup.warnings.joinToString(" • "),
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (data.actions.isNotEmpty()) {
                item {
                    Text(
                        "Master Action Plan",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = GmuDark
                    )
                }
                items(data.actions.take(20), key = { it.priority.toString() + it.programId + it.packageId + it.actionCode }) { a ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(a.programName, fontWeight = FontWeight.Bold)
                                    if (a.packageName.isNotBlank()) {
                                        Text(a.packageName, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                StatusChip(a.severity)
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(a.message, fontSize = 11.sp, color = Color.Gray)

                            if (a.actionCode == "APPLY_RECOMMENDED_PACKAGE_PRICE" && a.packageId.isNotBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Button(
                                    onClick = {
                                        vm.applyRecommendedPackagePrice(a.packageId) { _, msg -> onNotice(msg) }
                                    },
                                    enabled = !vm.actionBusy
                                ) { Text("Apply Recommended") }
                            }
                            if (a.actionCode == "ACTIVATE_PACKAGE" && a.packageId.isNotBlank()) {
                                Spacer(Modifier.height(7.dp))
                                Button(
                                    onClick = {
                                        vm.activatePackage(a.packageId) { _, msg -> onNotice(msg) }
                                    },
                                    enabled = !vm.actionBusy
                                ) { Text("Activate Package") }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Readiness Matrix",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GmuDark
                )
            }

            if (data.matrix.isEmpty()) {
                item { EmptyCard("Belum ada program/package pada readiness matrix.") }
            } else {
                items(
                    data.matrix,
                    key = { it.programId + "|" + it.packageId.ifBlank { "PROGRAM" } }
                ) { row ->
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.programName, fontWeight = FontWeight.Black)
                                    Text(
                                        row.packageName.ifBlank { "Program baseline" },
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(row.setupStatus)
                            }
                            Spacer(Modifier.height(7.dp))
                            CommerceLine("Pax simulasi", row.representativePax.toString())
                            CommerceLine("Base Cost", rupiah(row.baseCost))
                            CommerceLine(
                                "Recommended / pax",
                                row.recommendedPricePerPax?.let { rupiah(it) } ?: "-"
                            )
                            CommerceLine(
                                "Floor / pax",
                                row.floorPricePerPax?.let { rupiah(it) } ?: "-"
                            )
                            CommerceLine(
                                "Target Margin",
                                row.targetMarginPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-"
                            )
                            CommerceLine(
                                "Floor Margin",
                                row.floorMarginPct?.let { String.format(Locale.US, "%.1f%%", it) } ?: "-"
                            )
                            if (row.blockers.isNotEmpty()) {
                                Spacer(Modifier.height(5.dp))
                                Text(row.blockers.joinToString(" • "), color = GmuWarn, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Cost Templates",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = GmuDark
                )
            }
            if (data.templates.isEmpty()) {
                item { EmptyCard("Belum ada Cost Template aktif.") }
            } else {
                items(data.templates, key = { it.id }) { t ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(t.description, fontWeight = FontWeight.Bold)
                                StatusChip(if (t.active) "ACTIVE" else "DRAFT")
                            }
                            CommerceLine("Scope", t.scopeType)
                            CommerceLine("Category", t.category)
                            CommerceLine("Mode", t.costMode)
                            CommerceLine("Amount", rupiah(t.amount))
                        }
                    }
                }
            }
        }
    }

    if (costDialog) {
        CostTemplateDialog(
            vm = vm,
            busy = vm.actionBusy,
            onDismiss = { costDialog = false },
            onSaved = { msg ->
                costDialog = false
                onNotice(msg)
            }
        )
    }

    if (policyDialog) {
        MasterPricingPolicyDialog(
            vm = vm,
            busy = vm.actionBusy,
            onDismiss = { policyDialog = false },
            onSaved = { msg ->
                policyDialog = false
                onNotice(msg)
            }
        )
    }
}

@Composable
private fun CostTemplateDialog(
    vm: MainViewModel,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val data = vm.pricingMaster
    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    val packages = vm.packageMaster
    var scope by remember { mutableStateOf("PROGRAM") }
    var programId by remember { mutableStateOf("") }
    var packageId by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(data?.categories?.firstOrNull()?.code.orEmpty()) }
    var costMode by remember { mutableStateOf(data?.categories?.firstOrNull()?.defaultCostMode ?: "FIXED") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var minPax by remember { mutableStateOf("") }
    var maxPax by remember { mutableStateOf("") }
    var effectiveFrom by remember { mutableStateOf(commerceToday()) }
    var effectiveUntil by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }

    val programLabel = programs.firstOrNull { it.id == programId }?.text("name").orEmpty()
    val packageOptions = packages.filter { packageId.isNotBlank() || it.programId == programId }
    val packageLabel = packages.firstOrNull { it.id == packageId }?.name.orEmpty()
    val categoryLabel = data?.categories?.firstOrNull { it.code == category }?.let {
        it.code + " — " + it.label
    }.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Cost Template") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                GmuSelect(
                    value = scope,
                    label = "Scope",
                    options = listOf("COMPANY", "PROGRAM", "PACKAGE"),
                    onSelect = {
                        scope = it
                        if (scope == "COMPANY") {
                            programId = ""
                            packageId = ""
                        }
                        if (scope == "PROGRAM") packageId = ""
                    }
                )
                Spacer(Modifier.height(7.dp))
                if (scope != "COMPANY") {
                    GmuSelect(
                        value = programLabel,
                        label = "Program",
                        options = programs.map { it.text("name") },
                        onSelect = { label ->
                            programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                            packageId = ""
                        }
                    )
                    Spacer(Modifier.height(7.dp))
                }
                if (scope == "PACKAGE") {
                    GmuSelect(
                        value = packageLabel,
                        label = "Package",
                        options = packages.filter { it.programId == programId }.map { it.name },
                        onSelect = { label ->
                            packageId = packages.firstOrNull {
                                it.programId == programId && it.name == label
                            }?.id.orEmpty()
                        }
                    )
                    Spacer(Modifier.height(7.dp))
                }
                GmuSelect(
                    value = categoryLabel,
                    label = "Category",
                    options = data?.categories?.map { it.code + " — " + it.label }.orEmpty(),
                    onSelect = { label ->
                        val cat = data?.categories?.firstOrNull { label.startsWith(it.code + " ") }
                        category = cat?.code.orEmpty()
                        costMode = cat?.defaultCostMode ?: costMode
                    }
                )
                Spacer(Modifier.height(7.dp))
                GmuSelect(
                    value = costMode,
                    label = "Cost Mode",
                    options = listOf("FIXED", "PER_PAX"),
                    onSelect = { costMode = it }
                )
                CommerceField("Deskripsi", description) { description = it }
                CommerceField("Amount", amount) { amount = it }
                CommerceField("Min Pax (opsional)", minPax) { minPax = it }
                CommerceField("Max Pax (opsional)", maxPax) { maxPax = it }
                CommerceField("Effective From", effectiveFrom) { effectiveFrom = it }
                CommerceField("Effective Until (opsional)", effectiveUntil) { effectiveUntil = it }
                CommerceField("Catatan", notes, singleLine = false) { notes = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (active) "ACTIVE" else "DRAFT / Inactive", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            val v = amount.toDoubleOrNull()
            val scopeValid = scope == "COMPANY" ||
                (scope == "PROGRAM" && programId.isNotBlank()) ||
                (scope == "PACKAGE" && packageId.isNotBlank())
            Button(
                onClick = {
                    vm.saveCostTemplate(
                        null, scope,
                        programId.takeIf { it.isNotBlank() },
                        packageId.takeIf { it.isNotBlank() },
                        category, description, costMode, v ?: 0.0,
                        minPax.toIntOrNull(), maxPax.toIntOrNull(),
                        effectiveFrom, effectiveUntil.takeIf { it.isNotBlank() },
                        notes, active
                    ) { ok, msg -> if (ok) onSaved(msg) }
                },
                enabled = !busy && scopeValid && category.isNotBlank() &&
                    description.isNotBlank() && v != null && v >= 0 && effectiveFrom.isNotBlank()
            ) { Text("Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

@Composable
private fun MasterPricingPolicyDialog(
    vm: MainViewModel,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    var scope by remember { mutableStateOf("COMPANY") }
    var programId by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var floor by remember { mutableStateOf("") }
    var maxDiscount by remember { mutableStateOf("") }
    var contingency by remember { mutableStateOf("5") }
    var rounding by remember { mutableStateOf("1000") }
    var effectiveFrom by remember { mutableStateOf(commerceToday()) }
    var effectiveUntil by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val programLabel = programs.firstOrNull { it.id == programId }?.text("name").orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Pricing Policy") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                GmuSelect(
                    value = scope,
                    label = "Scope",
                    options = listOf("COMPANY", "PROGRAM"),
                    onSelect = {
                        scope = it
                        if (it == "COMPANY") programId = ""
                    }
                )
                Spacer(Modifier.height(7.dp))
                if (scope == "PROGRAM") {
                    GmuSelect(
                        value = programLabel,
                        label = "Program",
                        options = programs.map { it.text("name") },
                        onSelect = { label ->
                            programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                        }
                    )
                }
                CommerceField("Target Margin %", target) { target = it }
                CommerceField("Floor Margin %", floor) { floor = it }
                CommerceField("Max Discount %", maxDiscount) { maxDiscount = it }
                CommerceField("Contingency %", contingency) { contingency = it }
                CommerceField("Rounding Increment", rounding) { rounding = it }
                CommerceField("Effective From", effectiveFrom) { effectiveFrom = it }
                CommerceField("Effective Until (opsional)", effectiveUntil) { effectiveUntil = it }
                CommerceField("Catatan", notes, singleLine = false) { notes = it }
            }
        },
        confirmButton = {
            val t = target.toDoubleOrNull()
            val f = floor.toDoubleOrNull()
            val validScope = scope == "COMPANY" || programId.isNotBlank()
            Button(
                onClick = {
                    vm.savePricingPolicy(
                        policyId = null,
                        scopeType = scope,
                        programId = programId.takeIf { scope == "PROGRAM" && it.isNotBlank() },
                        targetMarginPct = t,
                        floorMarginPct = f,
                        maxDiscountPct = maxDiscount.toDoubleOrNull(),
                        contingencyPct = contingency.toDoubleOrNull() ?: 0.0,
                        roundingIncrement = rounding.toDoubleOrNull() ?: 1000.0,
                        effectiveFrom = effectiveFrom,
                        effectiveUntil = effectiveUntil.takeIf { it.isNotBlank() },
                        notes = notes
                    ) { ok, msg ->
                        if (ok) {
                            vm.refreshCommerce()
                            onSaved(msg)
                        }
                    }
                },
                enabled = !busy && validScope && t != null && f != null &&
                    t >= 0 && f >= 0 && f <= t && effectiveFrom.isNotBlank()
            ) { Text("Aktifkan Policy") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

@Composable
fun PaymentGatewayScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    if (!FinancialAccess.canView(session.profile.role)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Payment Gateway hanya tersedia untuk Owner / Manager.")
        }
        return
    }

    val data = vm.paymentGateway

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle(
                "Payment Gateway",
                "QRIS • Virtual Account • E-Wallet • Auto Confirmation"
            )
            OutlinedButton(onClick = vm::refreshCommerce, enabled = !vm.actionBusy) {
                Text("Refresh")
            }
        }
        Spacer(Modifier.height(10.dp))

        if (data == null) {
            EmptyCard(vm.paymentGatewayError ?: "Payment Gateway belum termuat.")
            return
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (data.providerConfigured) Color(0xFFEAF7EF) else Color(0xFFFFF7E8)
            )
        ) {
            Column(Modifier.padding(15.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Midtrans", fontWeight = FontWeight.Black, color = GmuDark)
                    StatusChip(if (data.providerConfigured) data.providerEnvironment else "NOT CONFIGURED")
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    if (data.providerConfigured) {
                        "Merchant credential tersedia. Channel dapat diaktifkan satu per satu."
                    } else {
                        "Merchant credential belum tersedia. Semua channel live tetap terkunci."
                    },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Pending", data.pending.toString(), Modifier.weight(1f))
            MetricCard("Paid", data.paid.toString(), Modifier.weight(1f), accent = data.paid > 0)
            MetricCard("Review", data.review.toString(), Modifier.weight(1f), accent = data.review > 0)
        }
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "Payment Channels",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            items(data.channels, key = { it.code }) { ch ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(ch.displayName, fontWeight = FontWeight.Black)
                                Text(
                                    ch.code + " • " + ch.methodType + " • " + ch.integrationMode,
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            StatusChip(if (ch.enabled) "ACTIVE" else "OFF")
                        }
                        Spacer(Modifier.height(7.dp))
                        CommerceLine("Expiry", ch.expiryMinutes.toString() + " menit")
                        CommerceLine("Provider Ready", if (ch.providerReady) "YES" else "NO")
                        if (ch.requiresPhone) {
                            Text("Memerlukan nomor HP customer.", fontSize = 10.sp, color = GmuWarn)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.setPaymentChannel(ch.code, !ch.enabled) { _, msg -> onNotice(msg) }
                            },
                            enabled = !vm.actionBusy && (ch.enabled || ch.providerReady),
                            colors = if (ch.enabled) {
                                ButtonDefaults.buttonColors(containerColor = GmuDanger)
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(if (ch.enabled) "Disable" else "Enable")
                        }
                    }
                }
            }

            item {
                Text(
                    "Recent Payment Orders",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            if (data.recentOrders.isEmpty()) {
                item {
                    EmptyCard(
                        "Belum ada payment order live. Order akan muncul setelah Customer Portal membuat pembayaran."
                    )
                }
            } else {
                items(data.recentOrders.take(50), key = { it.id }) { o ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(o.orderNo, fontWeight = FontWeight.Bold)
                                    Text(
                                        o.channelCode + " • " + o.paymentType,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(o.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            CommerceLine("Amount", rupiah(o.amount))
                            CommerceLine("Provider", o.providerStatus.ifBlank { "-" })
                            if (o.expiresAt.isNotBlank()) CommerceLine("Expires", o.expiresAt)
                            if (o.paidAt.isNotBlank()) CommerceLine("Paid", o.paidAt)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommerceLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CommerceField(
    label: String,
    value: String,
    singleLine: Boolean = true,
    onValue: (String) -> Unit
) {
    Spacer(Modifier.height(7.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(14.dp)
    )
}

private fun commerceToday(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
