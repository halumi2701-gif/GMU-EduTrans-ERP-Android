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

@Composable
fun PackageMasterHubScreen(
    vm: MainViewModel,
    session: SessionState,
    onNotice: (String) -> Unit
) {
    var tab by remember { mutableStateOf("Paket") }
    var mediaTable by remember { mutableStateOf("") }
    var mediaId by remember { mutableStateOf("") }
    var mediaName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = tab == "Paket",
                onClick = { tab = "Paket" },
                label = { Text("Paket") }
            )
            FilterChip(
                selected = tab == "Media",
                onClick = { tab = "Media" },
                label = { Text("Media") }
            )
        }

        if (tab == "Paket") {
            PackageMasterScreen(vm, session, onNotice)
        } else {
            ProgramPackageMediaMasterTab(
                programs = vm.table("programs").filter { it.text("is_active") != "false" },
                packages = vm.packageMaster,
                busy = vm.actionBusy,
                onEditProgram = { program ->
                    mediaTable = "programs"
                    mediaId = program.id
                    mediaName = program.text("name").ifBlank { "Program GMU EduTrans" }
                },
                onEditPackage = { pkg ->
                    mediaTable = "program_packages"
                    mediaId = pkg.id
                    mediaName = pkg.name
                }
            )
        }
    }

    if (mediaTable.isNotBlank() && mediaId.isNotBlank()) {
        ProgramPackageMediaDialogAuto(
            vm = vm,
            session = session,
            table = mediaTable,
            entityId = mediaId,
            entityName = mediaName,
            onDismiss = {
                mediaTable = ""
                mediaId = ""
                mediaName = ""
            },
            onNotice = onNotice
        )
    }
}

@Composable
private fun ProgramPackageMediaMasterTab(
    programs: List<ErpRow>,
    packages: List<PackageMasterItem>,
    busy: Boolean,
    onEditProgram: (ErpRow) -> Unit,
    onEditPackage: (PackageMasterItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF3EE))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Media Sync", fontWeight = FontWeight.Black)
                    Text(
                        "Upload cover dan galeri Program maupun Paket dari ERP. Media publik tersinkron ke Web Customer.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        item {
            Text("Program", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
        }
        if (programs.isEmpty()) {
            item { EmptyCard("Belum ada Program aktif untuk dikelola medianya.") }
        } else {
            items(programs, key = { "program|" + it.id }) { program ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                program.text("name").ifBlank { "Program GMU EduTrans" },
                                fontWeight = FontWeight.Black,
                                color = GmuDark
                            )
                            Text(
                                program.text("category").ifBlank { "Program Edukasi" },
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                        Button(
                            onClick = { onEditProgram(program) },
                            enabled = !busy && program.id.isNotBlank()
                        ) { Text("Media") }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Paket", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
        }
        if (packages.isEmpty()) {
            item { EmptyCard("Belum ada Paket untuk dikelola medianya.") }
        } else {
            items(packages, key = { "package|" + it.id }) { pkg ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pkg.name, fontWeight = FontWeight.Black, color = GmuDark)
                            Text(
                                pkg.packageCode + " • " + pkg.programName,
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Spacer(Modifier.height(4.dp))
                            StatusChip(pkg.status)
                        }
                        Button(
                            onClick = { onEditPackage(pkg) },
                            enabled = !busy && pkg.id.isNotBlank()
                        ) { Text("Media") }
                    }
                }
            }
        }
    }
}
