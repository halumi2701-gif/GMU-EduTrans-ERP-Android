package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProgramPackageMediaDialogAuto(
    vm: MainViewModel,
    session: SessionState,
    table: String,
    entityId: String,
    entityName: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    onNotice: (String) -> Unit
) {
    var loading by remember(entityId) { mutableStateOf(true) }
    var initial by remember(entityId) { mutableStateOf(MasterMediaState()) }

    LaunchedEffect(table, entityId) {
        loading = true
        try {
            initial = MediaRepository.load(session.accessToken, table, entityId)
        } catch (e: Exception) {
            onNotice(e.message ?: "Media lama belum dapat dimuat.")
        } finally {
            loading = false
        }
    }

    if (loading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Media • $entityName") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    Text("Memuat media...")
                }
            },
            confirmButton = {}
        )
    } else {
        ProgramPackageMediaDialog(
            vm = vm,
            session = session,
            table = table,
            entityId = entityId,
            entityName = entityName,
            initial = initial,
            onDismiss = onDismiss,
            onSaved = onSaved,
            onNotice = onNotice
        )
    }
}
