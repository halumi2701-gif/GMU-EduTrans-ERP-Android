package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun ProgramPackageMediaDialog(
    vm: MainViewModel,
    session: SessionState,
    table: String,
    entityId: String,
    entityName: String,
    initial: MasterMediaState = MasterMediaState(),
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
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
    var uploadedThisSession by remember(entityId) { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }

    fun uploadPicked(uri: android.net.Uri, coverMode: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val type = if (table == "programs") "program" else "package"
                val url = GmuMediaSync.upload(context, session.accessToken, uri, type, entityId)
                uploadedThisSession = (uploadedThisSession + url).distinct()
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

    fun cancelMedia() {
        if (busy) return
        val discard = uploadedThisSession
        if (discard.isEmpty()) {
            onDismiss()
            return
        }
        busy = true
        scope.launch {
            try {
                MediaRepository.discard(
                    accessToken = session.accessToken,
                    table = table,
                    entityId = entityId,
                    urls = discard
                )
            } catch (e: Exception) {
                onNotice("Media belum disimpan. Cleanup file sementara akan dicoba lagi saat pengelolaan berikutnya.")
            } finally {
                uploadedThisSession = emptyList()
                busy = false
                onDismiss()
            }
        }
    }

    fun saveMedia() {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val media = MasterMediaState(cover, gallery).normalized()
                MediaRepository.save(
                    accessToken = session.accessToken,
                    table = table,
                    entityId = entityId,
                    media = media
                )

                val retained = (listOf(media.coverImageUrl) + media.galleryUrls)
                    .filter { it.isNotBlank() }
                    .toSet()
                val unusedUploads = uploadedThisSession.filterNot { it in retained }
                if (unusedUploads.isNotEmpty()) {
                    runCatching {
                        MediaRepository.discard(
                            accessToken = session.accessToken,
                            table = table,
                            entityId = entityId,
                            urls = unusedUploads
                        )
                    }
                }

                uploadedThisSession = emptyList()
                cover = media.coverImageUrl
                gallery = media.galleryUrls
                onNotice("Media berhasil disimpan.")
                onSaved()
                onDismiss()
            } catch (e: Exception) {
                onNotice(e.message ?: "Media gagal disimpan.")
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
        onDismissRequest = { if (!busy) cancelMedia() },
        title = { Text("Media • $entityName") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("Cover", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    if (cover.isBlank()) {
                        Text("Belum ada cover.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        AsyncImage(
                            model = cover,
                            contentDescription = "Preview cover $entityName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            cover,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Foto galeri ${index + 1} $entityName",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(86.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Foto ${index + 1}", fontWeight = FontWeight.Bold)
                                Text(
                                    url,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                TextButton(
                                    onClick = {
                                        cover = url
                                        gallery = gallery.toMutableList().also { it.removeAt(index) }
                                    },
                                    enabled = !busy,
                                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                                ) { Text("Jadikan Cover") }
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (index > 0) {
                                        TextButton(onClick = {
                                            gallery = gallery.toMutableList().also {
                                                val item = it.removeAt(index)
                                                it.add(index - 1, item)
                                            }
                                        }, enabled = !busy) { Text("↑") }
                                    }
                                    if (index < gallery.lastIndex) {
                                        TextButton(onClick = {
                                            gallery = gallery.toMutableList().also {
                                                val item = it.removeAt(index)
                                                it.add(index + 1, item)
                                            }
                                        }, enabled = !busy) { Text("↓") }
                                    }
                                    TextButton(onClick = {
                                        gallery = gallery.toMutableList().also { it.removeAt(index) }
                                    }, enabled = !busy) { Text("Hapus") }
                                }
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
                        "Format: JPG, PNG, WebP • Maks. 8 MB/foto • Cover disarankan rasio 16:9. Media ACTIVE otomatis dipakai Web Customer.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = ::saveMedia,
                enabled = !busy && !vm.actionBusy
            ) { Text(if (busy) "Menyimpan…" else "Simpan Media") }
        },
        dismissButton = {
            TextButton(onClick = ::cancelMedia, enabled = !busy) { Text("Batal") }
        }
    )
}
