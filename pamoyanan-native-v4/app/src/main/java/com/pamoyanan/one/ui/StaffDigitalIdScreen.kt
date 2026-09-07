package com.pamoyanan.one.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pamoyanan.one.ui.theme.Brand
import com.pamoyanan.one.ui.theme.BrandDark
import com.pamoyanan.one.ui.theme.Danger
import com.pamoyanan.one.ui.theme.Ink
import com.pamoyanan.one.ui.theme.Mint
import com.pamoyanan.one.ui.theme.Muted
import org.json.JSONArray
import org.json.JSONObject

private val StaffRadius = RoundedCornerShape(18.dp)

private fun JSONArray.staffObjects(): List<JSONObject> =
    (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONObject.staffText(key: String, fallback: String = "-"): String {
    val value = optString(key)
    return if (value.isBlank() || value == "null") fallback else value
}

private fun staffQrBitmap(dataUrl: String) = try {
    val raw = dataUrl.substringAfter(",", "")
    val bytes = Base64.decode(raw, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
} catch (_: Exception) { null }

@Composable
fun StaffDigitalIdScreen(vm: AppViewModel) {
    val rows by vm.residents
    val card by vm.staffDigitalId
    val selectedName by vm.staffDigitalResidentName
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadResidents() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("ID Digital Warga", fontWeight = FontWeight.Black, fontSize = 26.sp, color = Ink)
            Text("Cari warga sesuai scope RT/RW lalu buka kartu dan QR.", color = Muted, fontSize = 14.sp)
        }

        val current = card
        if (current != null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.clearResidentDigitalId() }) {
                        Icon(Icons.Outlined.ArrowBack, "Kembali")
                    }
                    Column {
                        Text(selectedName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Digital ID resmi RW 01", color = Muted, fontSize = 13.sp)
                    }
                }
            }
            item {
                Card(
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = BrandDark)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(current.staffText("name"), color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(current.staffText("member_no"), color = Color.White.copy(alpha = .72f), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        StaffInfo("Wilayah", "RT " + current.staffText("rt") + " / RW " + current.staffText("rw"))
                        StaffInfo("Alamat", current.staffText("address_line"))
                        StaffInfo("Status QR", current.staffText("qr_status"))
                        Spacer(Modifier.height(16.dp))

                        val qr = remember(current.optString("qr_data_url")) {
                            staffQrBitmap(current.optString("qr_data_url"))
                        }
                        if (qr != null) {
                            Surface(color = Color.White, shape = StaffRadius) {
                                Image(
                                    bitmap = qr,
                                    contentDescription = "QR Digital ID",
                                    modifier = Modifier.fillMaxWidth().height(230.dp).padding(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.rotateResidentDigitalId() },
                            colors = ButtonDefaults.buttonColors(containerColor = Brand)
                        ) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PERBARUI QR", fontSize = 12.sp)
                        }
                    }
                }
            }
            item {
                OutlinedButton(onClick = { vm.revokeResidentDigitalId() }, modifier = Modifier.fillMaxWidth()) {
                    Text("CABUT QR DIGITAL ID", color = Danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        if (it.isBlank() || it.length >= 2) vm.loadResidents(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cari warga", fontSize = 14.sp) },
                    singleLine = true,
                    shape = StaffRadius
                )
            }
            val list = rows?.staffObjects().orEmpty()
            items(list, key = { it.staffText("id") }) { resident ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        vm.openResidentDigitalId(resident.staffText("id"), resident.staffText("name"))
                    },
                    shape = StaffRadius,
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).clip(CircleShape).background(Mint), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.QrCode2, null, tint = Brand)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(resident.staffText("name"), fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text(resident.staffText("member_no"), color = Muted, fontSize = 12.sp)
                            Text("RT " + resident.staffText("rt") + " / RW " + resident.staffText("rw"), color = Muted, fontSize = 12.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffInfo(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Color.White.copy(alpha = .62f), fontSize = 12.sp, modifier = Modifier.width(76.dp))
        Text(value, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}
