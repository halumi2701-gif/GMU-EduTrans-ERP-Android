package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray

@Composable
fun ProgramPackageMediaDialog(
    vm: MainViewModel,
    session: SessionState,
    table: String,
    entityId: String,
    entityName: String,
    initial: MasterMediaState = MasterMediaState(),
    onDismiss: () -> Unit,
    onNotice: (String) -> Unit
) {
    val allowedRole = session.profile.role in listOf(
        "Owner", "Director", "Direktur", "Manager", "Manager EduTrans", "Admin"
    )
    if (!allowedRole || table !in setOf("programs", "program_packages")) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cover by remember(entityId) { mutableStateOf(initial.coverImageUrl) }
    var gallery by remember(entityId) { mutableStateOf(initial.galleryUrls.take(GmuMediaSync.MAX_GALLERY_IMAGES)) }
    var busy by remember { mutableStateOf(false) }

    fun uploadPicked(uri: android.net.Uri, coverMode: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val type = if (table == "programs") "program" else "package"
                val url = GmuMediaSync.upload(context, session.accessToken, uri, type, entityId)
                if (coverMode) cover = url
                else if (gallery.size < GmuMediaSync.MAX_GALLERY_IMAGES) gallery = gallery + url
                onNotice("Gambar berhasil diupload.")
            } catch (e: Exception) {
                onNotice(e.message ?: "Upload gambar gagal.")
            } finally {
                busy = false
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadPicked(uri, true)
    }
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadPicked(uri, false)
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Media • $entityName") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Cover", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    if (cover.isBlank()) Text("Belum ada cover.", style = MaterialTheme.typography.bodySmall)
                    else Text(cover, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { coverPicker.launch("image/*") },
                            enabled = !busy
                        ) { Text(if (cover.isBlank()) "Upload Cover" else "Ganti Cover") }
                        if (cover.isNotBlank()) {
                            OutlinedButton(onClick = { cover = "" }, enabled = !busy) { Text("Hapus") }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        "Galeri (${gallery.size}/${GmuMediaSync.MAX_GALLERY_IMAGES})",
                        fontWeight = FontWeight.Black
                    )
                }

                itemsIndexed(gallery) { index, url ->
                    Card {
                        Column(Modifier.padding(10.dp)) {
                            Text("Foto ${index + 1}", fontWeight = FontWeight.Bold)
                            Text(url, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (index > 0) {
                                    TextButton(onClick = {
                                        gallery = gallery.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index - 1, item)
                                        }
                                    }) { Text("↑") }
                                }
                                if (index < gallery.lastIndex) {
                                    TextButton(onClick = {
                                        gallery = gallery.toMutableList().also {
                                            val item = it.removeAt(index)
                                            it.add(index + 1, item)
                                        }
                                    }) { Text("↓") }
                                }
                                TextButton(onClick = {
                                    gallery = gallery.toMutableList().also { it.removeAt(index) }
                                }) { Text("Hapus") }
                            }
                        }
                    }
                }

                if (gallery.size < GmuMediaSync.MAX_GALLERY_IMAGES) {
                    item {
                        OutlinedButton(
                            onClick = { galleryPicker.launch("image/*") },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("+ Tambah Foto Galeri") }
                    }
                }

                item {
                    Text(
                        "Format: JPG, PNG, WebP • Maks. 8 MB/foto. Media ACTIVE otomatis dipakai Web Customer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val media = MasterMediaState(cover, gallery).normalized()
                    vm.update(
                        table = table,
                        id = entityId,
                        values = mapOf(
                            "cover_image_url" to media.coverImageUrl.ifBlank { null },
                            "gallery_urls" to JSONArray(media.galleryUrls)
                        ),
                        successMessage = "Media berhasil disimpan."
                    ) { ok, msg ->
                        onNotice(msg)
                        if (ok) onDismiss()
                    }
                },
                enabled = !busy && !vm.actionBusy
            ) { Text("Simpan Media") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Batal") }
        }
    )
}
