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
    val canManagePackages = session.profile.role in setOf("Owner", "Director", "Direktur", "Admin")
    var tab by remember(session.profile.role) { mutableStateOf(if (canManagePackages) "Paket" else "Media") }
    var mediaTable by remember { mutableStateOf("") }
    var mediaId by remember { mutableStateOf("") }
    var mediaName by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf(MediaMasterCatalog()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab, session.accessToken) {
        if (tab != "Media") return@LaunchedEffect
        catalogLoading = true
        catalogError = null
        try {
            catalog = MediaMasterCatalogRepository.load(session.accessToken)
        } catch (e: Exception) {
            catalogError = e.message ?: "Media Master gagal dimuat."
        } finally {
            catalogLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canManagePackages) {
                FilterChip(
                    selected = tab == "Paket",
                    onClick = { tab = "Paket" },
                    label = { Text("Paket") }
                )
            }
            FilterChip(
                selected = tab == "Media",
                onClick = { tab = "Media" },
                label = { Text("Media") }
            )
        }

        if (tab == "Paket" && canManagePackages) {
            PackageMasterScreen(vm, session, onNotice)
        } else {
            ProgramPackageMediaMasterTab(
                catalog = catalog,
                loading = catalogLoading,
                error = catalogError,
                busy = vm.actionBusy,
                onEditProgram = { program ->
                    mediaTable = "programs"
                    mediaId = program.id
                    mediaName = program.name
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
    catalog: MediaMasterCatalog,
    loading: Boolean,
    error: String?,
    busy: Boolean,
    onEditProgram: (MediaProgramItem) -> Unit,
    onEditPackage: (MediaPackageItem) -> Unit
) {
    val programNames = remember(catalog.programs) { catalog.programs.associate { it.id to it.name } }

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
                        "Upload cover dan galeri Program maupun Paket dari ERP. Loader Media hanya membaca data konten publik, tanpa HPP, margin, atau fee internal.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (loading) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Memuat Media Master…", fontSize = 12.sp)
                    }
                }
            }
        }
        if (!error.isNullOrBlank()) {
            item { EmptyCard(error) }
        }

        item {
            Text("Program", fontWeight = FontWeight.Black, fontSize = 16.sp, color = GmuDark)
        }
        if (!loading && catalog.programs.isEmpty()) {
            item { EmptyCard("Belum ada Program aktif untuk dikelola medianya.") }
        } else {
            items(catalog.programs, key = { "program|" + it.id }) { program ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(program.name, fontWeight = FontWeight.Black, color = GmuDark)
                            Text(program.category, fontSize = 10.sp, color = Color.Gray)
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
        if (!loading && catalog.packages.isEmpty()) {
            item { EmptyCard("Belum ada Paket untuk dikelola medianya.") }
        } else {
            items(catalog.packages, key = { "package|" + it.id }) { pkg ->
                Card(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(pkg.name, fontWeight = FontWeight.Black, color = GmuDark)
                            Text(
                                listOf(pkg.packageCode, programNames[pkg.programId].orEmpty())
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • "),
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
