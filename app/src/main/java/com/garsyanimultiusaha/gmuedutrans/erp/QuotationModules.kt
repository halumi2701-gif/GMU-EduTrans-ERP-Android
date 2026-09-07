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
fun QuotationPricingScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    val canPrepare = session.profile.role in listOf("Owner", "Manager", "Admin")
    val canApprove = session.profile.role in listOf("Owner", "Manager")
    val canPrice = FinancialAccess.canView(session.profile.role)

    if (!canPrepare) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            EmptyCard("Quotation Workflow tidak tersedia untuk role ini.")
        }
        return
    }

    var tab by remember { mutableStateOf("Queue") }
    var policyDialog by remember { mutableStateOf<PricingPolicyItem?>(null) }
    var newPolicy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        SectionTitle(
            "Quotation & Pricing",
            "v2.4 • Draft, pricing guardrail, PDF & acceptance"
        )
        Spacer(Modifier.height(10.dp))

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            listOf("Queue", "Editor").forEach { label ->
                FilterChip(
                    selected = tab == label,
                    onClick = { tab = label },
                    label = { Text(label) }
                )
            }
            if (canPrice) {
                FilterChip(
                    selected = tab == "Pricing",
                    onClick = { tab = "Pricing" },
                    label = { Text("Pricing Policy") }
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        when (tab) {
            "Queue" -> QuotationQueueTab(
                vm = vm,
                canPrice = canPrice,
                onOpen = { requestId ->
                    vm.openQuotationRequest(requestId) { ok, msg ->
                        if (ok) tab = "Editor" else onNotice(msg)
                    }
                },
                onCreate = { requestId ->
                    vm.createQuotationDraft(requestId) { ok, msg ->
                        onNotice(msg)
                        if (ok) tab = "Editor"
                    }
                }
            )
            "Editor" -> QuotationEditorTab(
                vm = vm,
                session = session,
                canApprove = canApprove,
                onNotice = onNotice
            )
            else -> PricingPolicyTab(
                vm = vm,
                onNew = { newPolicy = true },
                onEdit = { policyDialog = it }
            )
        }
    }

    if (canPrice && (newPolicy || policyDialog != null)) {
        PricingPolicyDialog(
            vm = vm,
            existing = policyDialog,
            busy = vm.actionBusy,
            onDismiss = {
                newPolicy = false
                policyDialog = null
            },
            onSaved = { msg ->
                newPolicy = false
                policyDialog = null
                onNotice(msg)
            }
        )
    }
}

@Composable
private fun QuotationQueueTab(
    vm: MainViewModel,
    canPrice: Boolean,
    onOpen: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    val queue = vm.quotationQueue
    val pricing = vm.pricingDashboard

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (canPrice && pricing != null) {
            item {
                val attention = pricing.watchlist.count {
                    it.status in listOf("BELOW_FLOOR", "REVIEW", "NEEDS_COST_DATA", "NEEDS_MARGIN_POLICY")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("Queue", queue.size.toString(), Modifier.weight(1f), accent = true)
                    MetricCard("Pricing Attention", attention.toString(), Modifier.weight(1f), accent = attention > 0)
                }
            }
        }

        if (vm.quotationError != null) {
            item { EmptyCard(vm.quotationError ?: "Quotation Workflow gagal dimuat.") }
        }

        if (queue.isEmpty()) {
            item { EmptyCard("Belum ada pengajuan dalam Queue Quotation.") }
        } else {
            items(queue, key = { it.requestId }) { row ->
                Card(
                    onClick = {
                        if (row.quotationId.isBlank()) onCreate(row.requestId)
                        else onOpen(row.requestId)
                    },
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.institutionName.ifBlank { row.bookingCode },
                                    fontWeight = FontWeight.Black,
                                    color = GmuDark
                                )
                                Text(
                                    row.bookingCode + " • " + row.programName,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            StatusChip(
                                if (row.quotationStatus.isNotBlank()) row.quotationStatus
                                else row.requestStatus
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        QuoteLine("Trip", row.tripDate)
                        QuoteLine("Pax", row.pax.toString())
                        if (row.quotationNo.isNotBlank()) {
                            QuoteLine("Quotation", row.quotationNo)
                            QuoteLine("Total", rupiah(row.total))
                            QuoteLine("Valid Until", row.validUntil)
                        } else {
                            Text(
                                "Belum punya Draft Quotation",
                                fontSize = 11.sp,
                                color = GmuWarn,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (row.quotationId.isBlank()) "Buat Draft →" else "Buka Editor →",
                            color = GmuGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotationEditorTab(
    vm: MainViewModel,
    session: SessionState,
    canApprove: Boolean,
    onNotice: (String) -> Unit
) {
    val detail = vm.quotationDetail

    if (detail == null) {
        Box(Modifier.fillMaxSize()) {
            EmptyCard(
                vm.quotationError
                    ?: "Pilih pengajuan dari tab Queue untuk membuka editor quotation."
            )
        }
        return
    }

    if (detail.quotationId.isBlank()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(detail.bookingCode, fontWeight = FontWeight.Black, color = GmuDark)
                        Text(detail.institutionName, fontSize = 12.sp, color = Color.Gray)
                        Spacer(Modifier.height(10.dp))
                        Text("Belum ada Draft Quotation.", fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                vm.createQuotationDraft(detail.requestId) { _, msg -> onNotice(msg) }
                            },
                            enabled = !vm.actionBusy
                        ) {
                            Text("Buat Draft Quotation")
                        }
                    }
                }
            }
        }
        return
    }

    val stateKey = detail.quotationId + "|" + detail.quotationStatus + "|" +
        detail.subtotal.toString() + "|" + detail.items.size.toString()
    var editItems by remember(stateKey) {
        mutableStateOf(
            if (detail.items.isEmpty()) listOf(QuotationLine())
            else detail.items
        )
    }
    var discount by remember(stateKey) { mutableStateOf(detail.discount.toString()) }
    var tax by remember(stateKey) { mutableStateOf(detail.tax.toString()) }
    var validUntil by remember(stateKey) { mutableStateOf(detail.validUntil) }
    var notes by remember(stateKey) { mutableStateOf(detail.notesCustomer) }
    var terms by remember(stateKey) { mutableStateOf(detail.terms) }
    var rejectDialog by remember { mutableStateOf(false) }
    var overrideDialog by remember { mutableStateOf(false) }

    val isDraft = detail.quotationStatus == "DRAFT"
    val isSent = detail.quotationStatus == "SENT"
    val localSubtotal = editItems.sumOf { it.qty * it.unitPrice }
    val localTotal = (localSubtotal - (discount.toDoubleOrNull() ?: 0.0))
        .coerceAtLeast(0.0) + (tax.toDoubleOrNull() ?: 0.0)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(detail.quotationNo, fontWeight = FontWeight.Black, color = GmuDark)
                            Text(
                                detail.bookingCode + " • " + detail.institutionName,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        StatusChip(detail.quotationStatus)
                    }
                    Spacer(Modifier.height(8.dp))
                    QuoteLine("Program", detail.programName)
                    QuoteLine("Trip", detail.tripDate)
                    QuoteLine("Pax", detail.pax.toString())
                }
            }
        }

        detail.pricing?.let { price ->
            item {
                PricingInsightCard(price)
            }
        }

        if (isDraft) {
            if (detail.items.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3EE))
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text(
                                "Quotation Draft Assistant",
                                fontWeight = FontWeight.Black,
                                color = GmuDark
                            )
                            Text(
                                "Ambil recommended price dari Package/Pricing Master tanpa menebak harga.",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(8.dp))

                            vm.quotationSuggestion?.let { suggestion ->
                                QuoteLine("Source", suggestion.source.ifBlank { "-" })
                                QuoteLine(
                                    "Estimasi Subtotal",
                                    if (suggestion.estimatedSubtotal > 0) rupiah(suggestion.estimatedSubtotal) else "-"
                                )
                                if (suggestion.blockers.isNotEmpty()) {
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        suggestion.blockers.joinToString(" • "),
                                        fontSize = 10.sp,
                                        color = GmuWarn
                                    )
                                }
                                if (suggestion.warnings.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        suggestion.warnings.joinToString(" • "),
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                if (suggestion.proposedItems.isNotEmpty()) {
                                    Spacer(Modifier.height(7.dp))
                                    suggestion.proposedItems.forEach { line ->
                                        QuoteLine(
                                            line.description,
                                            quoteNumberText(line.qty) + " × " + rupiah(line.unitPrice)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        vm.loadQuotationDraftSuggestion { _, msg -> onNotice(msg) }
                                    },
                                    enabled = !vm.actionBusy
                                ) {
                                    Text("Saran Harga Otomatis")
                                }
                                if (vm.quotationSuggestion?.ready == true) {
                                    Button(
                                        onClick = {
                                            vm.applyQuotationDraftSuggestion { _, msg -> onNotice(msg) }
                                        },
                                        enabled = !vm.actionBusy
                                    ) {
                                        Text("Apply Suggestion")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Item Quotation",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            items(editItems.size) { index ->
                val line = editItems[index]
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        OutlinedTextField(
                            value = line.description,
                            onValueChange = { value ->
                                editItems = editItems.toMutableList().also {
                                    it[index] = line.copy(description = value)
                                }
                            },
                            label = { Text("Deskripsi") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = quoteNumberText(line.qty),
                                onValueChange = { value ->
                                    editItems = editItems.toMutableList().also {
                                        it[index] = line.copy(qty = value.toDoubleOrNull() ?: 0.0)
                                    }
                                },
                                label = { Text("Qty") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = line.unit,
                                onValueChange = { value ->
                                    editItems = editItems.toMutableList().also {
                                        it[index] = line.copy(unit = value)
                                    }
                                },
                                label = { Text("Unit") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = quoteNumberText(line.unitPrice),
                            onValueChange = { value ->
                                editItems = editItems.toMutableList().also {
                                    it[index] = line.copy(unitPrice = value.toDoubleOrNull() ?: 0.0)
                                }
                            },
                            label = { Text("Harga Satuan") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Amount " + rupiah(line.qty * line.unitPrice),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            if (editItems.size > 1) {
                                TextButton(
                                    onClick = {
                                        editItems = editItems.toMutableList().also { it.removeAt(index) }
                                    }
                                ) {
                                    Text("Hapus", color = GmuDanger, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        editItems = editItems + QuotationLine()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Tambah Item")
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Commercial Summary", fontWeight = FontWeight.Black, color = GmuDark)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = discount,
                            onValueChange = { discount = it },
                            label = { Text("Discount (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = tax,
                            onValueChange = { tax = it },
                            label = { Text("Tax (Rp)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = validUntil,
                            onValueChange = { validUntil = it },
                            label = { Text("Valid Until (YYYY-MM-DD)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Catatan Customer") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2
                        )
                        Spacer(Modifier.height(7.dp))
                        OutlinedTextField(
                            value = terms,
                            onValueChange = { terms = it },
                            label = { Text("Terms & Conditions") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3
                        )
                        Spacer(Modifier.height(10.dp))
                        QuoteLine("Subtotal", rupiah(localSubtotal))
                        QuoteLine("Total", rupiah(localTotal))
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        vm.saveQuotationDraft(
                            items = editItems,
                            discount = discount.toDoubleOrNull() ?: 0.0,
                            tax = tax.toDoubleOrNull() ?: 0.0,
                            validUntil = validUntil,
                            notesCustomer = notes,
                            terms = terms
                        ) { _, msg -> onNotice(msg) }
                    },
                    enabled = !vm.actionBusy &&
                        editItems.isNotEmpty() &&
                        editItems.all {
                            it.description.isNotBlank() && it.qty > 0 && it.unitPrice >= 0
                        },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Simpan Draft")
                }
            }

            if (canApprove) {
                item {
                    val pricingRequired = detail.pricing?.approvalRequired == true
                    Button(
                        onClick = {
                            if (pricingRequired) overrideDialog = true
                            else vm.publishQuotation("") { _, msg -> onNotice(msg) }
                        },
                        enabled = !vm.actionBusy &&
                            detail.total > 0 &&
                            !(pricingRequired && session.profile.role != "Owner"),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GmuGreen)
                    ) {
                        Text(
                            when {
                                pricingRequired && session.profile.role != "Owner" -> "Butuh Owner Approval"
                                pricingRequired -> "Publish dengan Owner Override"
                                else -> "Publish Quotation & PDF"
                            }
                        )
                    }
                }
            }
        } else {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(15.dp)) {
                        Text("Quotation Summary", fontWeight = FontWeight.Black, color = GmuDark)
                        Spacer(Modifier.height(8.dp))
                        detail.items.forEach { line ->
                            QuoteLine(
                                line.description,
                                quoteNumberText(line.qty) + " × " + rupiah(line.unitPrice)
                            )
                        }
                        Divider(Modifier.padding(vertical = 8.dp))
                        QuoteLine("Subtotal", rupiah(detail.subtotal))
                        QuoteLine("Discount", rupiah(detail.discount))
                        QuoteLine("Tax", rupiah(detail.tax))
                        QuoteLine("Total", rupiah(detail.total))
                    }
                }
            }
        }

        if (isSent && canApprove) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { rejectDialog = true },
                        modifier = Modifier.weight(1f),
                        enabled = !vm.actionBusy
                    ) {
                        Text("Reject")
                    }
                    Button(
                        onClick = {
                            vm.acceptQuotation { _, msg -> onNotice(msg) }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !vm.actionBusy
                    ) {
                        Text("Accept → Waiting DP")
                    }
                }
            }
        }
    }

    if (rejectDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!vm.actionBusy) rejectDialog = false },
            title = { Text("Reject Quotation") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Alasan") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.rejectQuotation(reason) { ok, msg ->
                            onNotice(msg)
                            if (ok) rejectDialog = false
                        }
                    },
                    enabled = !vm.actionBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = GmuDanger)
                ) {
                    Text("Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectDialog = false }) { Text("Batal") }
            }
        )
    }

    if (overrideDialog) {
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!vm.actionBusy) overrideDialog = false },
            title = { Text("Owner Pricing Override") },
            text = {
                Column {
                    Text(
                        "Harga berada di bawah pricing floor. Jelaskan alasan override sebelum publish.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("Alasan override") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.publishQuotation(reason) { ok, msg ->
                            onNotice(msg)
                            if (ok) overrideDialog = false
                        }
                    },
                    enabled = !vm.actionBusy && reason.trim().length >= 10
                ) {
                    Text("Override & Publish")
                }
            },
            dismissButton = {
                TextButton(onClick = { overrideDialog = false }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun PricingInsightCard(price: PricingSnapshot) {
    val attention = price.status in listOf(
        "BELOW_FLOOR", "REVIEW", "NEEDS_COST_DATA", "NEEDS_MARGIN_POLICY"
    )
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (attention) Color(0xFFFFF7E8) else Color(0xFFEAF7EF)
        )
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Pricing Intelligence", fontWeight = FontWeight.Black, color = GmuDark)
                    Text(
                        "Confidence " + price.confidence,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
                StatusChip(price.status.ifBlank { "N/A" })
            }
            Spacer(Modifier.height(8.dp))
            QuoteLine(
                "Recommended",
                price.recommendedNet?.let { rupiah(it) } ?: "-"
            )
            QuoteLine(
                "Recommended / Pax",
                price.recommendedPerPax?.let { rupiah(it) } ?: "-"
            )
            QuoteLine(
                "Floor",
                price.floorNet?.let { rupiah(it) } ?: "-"
            )
            QuoteLine(
                "Floor / Pax",
                price.floorPerPax?.let { rupiah(it) } ?: "-"
            )
            QuoteLine(
                "Candidate Margin",
                price.candidateMarginPct?.let { quotePct(it) } ?: "-"
            )
            QuoteLine(
                "Max Discount",
                price.effectiveMaxDiscountPct?.let { quotePct(it) } ?: "-"
            )
            if (price.riskFlags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    price.riskFlags.joinToString(" • "),
                    fontSize = 10.sp,
                    color = if (attention) GmuWarn else Color.Gray
                )
            }
        }
    }
}

@Composable
private fun PricingPolicyTab(
    vm: MainViewModel,
    onNew: () -> Unit,
    onEdit: (PricingPolicyItem) -> Unit
) {
    val dashboard = vm.pricingDashboard
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pricing Policy", fontWeight = FontWeight.Black, color = GmuDark)
                    Text(
                        "Target margin • floor • discount guardrail",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                Button(onClick = onNew, enabled = !vm.actionBusy) {
                    Text("+ Policy")
                }
            }
        }

        if (dashboard == null) {
            item { EmptyCard(vm.pricingError ?: "Pricing Intelligence belum termuat.") }
        } else {
            if (dashboard.policies.isEmpty()) {
                item {
                    EmptyCard(
                        "Belum ada Pricing Policy. Recommendation akan menampilkan NEEDS_MARGIN_POLICY sampai policy dibuat."
                    )
                }
            } else {
                items(dashboard.policies, key = { it.id }) { policy ->
                    Card(
                        onClick = { onEdit(policy) },
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    policy.scopeType,
                                    fontWeight = FontWeight.Black,
                                    color = GmuDark
                                )
                                StatusChip(if (policy.isActive) "Active" else "Inactive")
                            }
                            Spacer(Modifier.height(7.dp))
                            QuoteLine(
                                "Target Margin",
                                policy.targetMarginPct?.let { quotePct(it) } ?: "-"
                            )
                            QuoteLine(
                                "Floor Margin",
                                policy.floorMarginPct?.let { quotePct(it) } ?: "-"
                            )
                            QuoteLine(
                                "Max Discount",
                                policy.maxDiscountPct?.let { quotePct(it) } ?: "-"
                            )
                            QuoteLine("Contingency", quotePct(policy.contingencyPct))
                            QuoteLine("Rounding", rupiah(policy.roundingIncrement))
                            QuoteLine("Effective", policy.effectiveFrom)
                            if (policy.notes.isNotBlank()) {
                                Text(policy.notes, fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Pricing Watchlist",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = GmuDark
                )
            }

            if (dashboard.watchlist.isEmpty()) {
                item { EmptyCard("Tidak ada booking aktif dalam pricing watchlist.") }
            } else {
                items(dashboard.watchlist, key = { it.bookingId }) { row ->
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.bookingNo, fontWeight = FontWeight.Bold)
                                    Text(
                                        row.programName + " • " + row.tripDate,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                StatusChip(row.status)
                            }
                            Spacer(Modifier.height(6.dp))
                            QuoteLine(
                                "Recommended",
                                row.recommendedNet?.let { rupiah(it) } ?: "-"
                            )
                            QuoteLine(
                                "Floor",
                                row.floorNet?.let { rupiah(it) } ?: "-"
                            )
                            QuoteLine(
                                "Margin",
                                row.candidateMarginPct?.let { quotePct(it) } ?: "-"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PricingPolicyDialog(
    vm: MainViewModel,
    existing: PricingPolicyItem?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    var scope by remember(existing?.id) {
        mutableStateOf(existing?.scopeType ?: "COMPANY")
    }
    var programId by remember(existing?.id) {
        mutableStateOf(existing?.programId.orEmpty())
    }
    var target by remember(existing?.id) {
        mutableStateOf(existing?.targetMarginPct?.toString().orEmpty())
    }
    var floor by remember(existing?.id) {
        mutableStateOf(existing?.floorMarginPct?.toString().orEmpty())
    }
    var maxDiscount by remember(existing?.id) {
        mutableStateOf(existing?.maxDiscountPct?.toString().orEmpty())
    }
    var contingency by remember(existing?.id) {
        mutableStateOf(existing?.contingencyPct?.toString() ?: "0")
    }
    var rounding by remember(existing?.id) {
        mutableStateOf(existing?.roundingIncrement?.toString() ?: "1000")
    }
    var effectiveFrom by remember(existing?.id) {
        mutableStateOf(existing?.effectiveFrom?.ifBlank { quoteToday() } ?: quoteToday())
    }
    var effectiveUntil by remember(existing?.id) {
        mutableStateOf(existing?.effectiveUntil.orEmpty())
    }
    var notes by remember(existing?.id) {
        mutableStateOf(existing?.notes.orEmpty())
    }

    val programs = vm.table("programs").filter { it.text("is_active") != "false" }
    val programLabel = programs.firstOrNull { it.id == programId }?.text("name").orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (existing == null) "Pricing Policy" else "Edit Pricing Policy") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                GmuSelect(
                    value = scope,
                    label = "Scope",
                    options = listOf("COMPANY", "PROGRAM"),
                    onSelect = { scope = it }
                )
                Spacer(Modifier.height(8.dp))
                if (scope == "PROGRAM") {
                    GmuSelect(
                        value = programLabel,
                        label = "Program",
                        options = programs.map { it.text("name") },
                        onSelect = { label ->
                            programId = programs.firstOrNull { it.text("name") == label }?.id.orEmpty()
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                QuoteField("Target Margin %", target) { target = it }
                QuoteField("Floor Margin %", floor) { floor = it }
                QuoteField("Max Discount %", maxDiscount) { maxDiscount = it }
                QuoteField("Contingency %", contingency) { contingency = it }
                QuoteField("Rounding Increment", rounding) { rounding = it }
                QuoteField("Effective From", effectiveFrom) { effectiveFrom = it }
                QuoteField("Effective Until (opsional)", effectiveUntil) { effectiveUntil = it }
                QuoteField("Catatan", notes) { notes = it }
            }
        },
        confirmButton = {
            val targetValue = target.toDoubleOrNull()
            val floorValue = floor.toDoubleOrNull()
            Button(
                onClick = {
                    vm.savePricingPolicy(
                        policyId = existing?.id,
                        scopeType = scope,
                        programId = programId.takeIf { scope == "PROGRAM" && it.isNotBlank() },
                        targetMarginPct = targetValue,
                        floorMarginPct = floorValue,
                        maxDiscountPct = maxDiscount.toDoubleOrNull(),
                        contingencyPct = contingency.toDoubleOrNull() ?: 0.0,
                        roundingIncrement = rounding.toDoubleOrNull() ?: 1000.0,
                        effectiveFrom = effectiveFrom,
                        effectiveUntil = effectiveUntil.takeIf { it.isNotBlank() },
                        notes = notes
                    ) { ok, msg ->
                        if (ok) onSaved(msg)
                    }
                },
                enabled = !busy &&
                    targetValue != null &&
                    floorValue != null &&
                    floorValue <= targetValue &&
                    (scope != "PROGRAM" || programId.isNotBlank())
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}

@Composable
private fun QuoteLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuoteField(label: String, value: String, onValue: (String) -> Unit) {
    Spacer(Modifier.height(7.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

private fun quotePct(value: Double): String =
    String.format(Locale.US, "%.1f%%", value)

private fun quoteNumberText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.US, "%.2f", value)

private fun quoteToday(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
