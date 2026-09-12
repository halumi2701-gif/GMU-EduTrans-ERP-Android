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
    var mediaPackage by remember { mutableStateOf<PackageMasterItem?>(null) }

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
            PackageMediaMasterTab(
                packages = vm.packageMaster,
                busy = vm.actionBusy,
                onEditMedia = { mediaPackage = it }
            )
        }
    }

    mediaPackage?.let { pkg ->
        ProgramPackageMediaDialogAuto(
            vm = vm,
            session = session,
            table = "program_packages",
            entityId = pkg.id,
            entityName = pkg.name,
            onDismiss = { mediaPackage = null },
            onNotice = onNotice
        )
    }
}

@Composable
private fun PackageMediaMasterTab(
    packages: List<PackageMasterItem>,
    busy: Boolean,
    onEditMedia: (PackageMasterItem) -> Unit
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
                        "Upload cover dan galeri dari ERP. Paket ACTIVE otomatis memakai media ini di Web Customer.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (packages.isEmpty()) {
            item { EmptyCard("Belum ada paket untuk dikelola medianya.") }
        } else {
            items(packages, key = { it.id }) { pkg ->
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
                            onClick = { onEditMedia(pkg) },
                            enabled = !busy && pkg.id.isNotBlank()
                        ) { Text("Media") }
                    }
                }
            }
        }
    }
}
