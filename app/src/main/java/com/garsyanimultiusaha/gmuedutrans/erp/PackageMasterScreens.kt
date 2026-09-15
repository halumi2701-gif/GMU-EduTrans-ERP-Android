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
    val canEdit = ErpRolePolicy.canManagePackages(session.profile.role)
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

