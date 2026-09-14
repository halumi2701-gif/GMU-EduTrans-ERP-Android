package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

@Composable
fun GmuCompanyOperatingSystemOverlay(vm: MainViewModel) {
    val state = vm.state as? AppState.LoggedIn ?: return
    var open by remember { mutableStateOf(false) }
    val role = state.session.profile.role

    ExtendedFloatingActionButton(
        onClick = { open = true },
        modifier = Modifier.padding(end = 16.dp, bottom = 86.dp),
        containerColor = GmuDark,
        contentColor = Color.White,
        text = {
            Text(
                if (role == "Sales") "Target & Pasar" else "Kendali Perusahaan",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        },
        icon = { Text("✦", fontWeight = FontWeight.Black) }
    )

    if (open) {
        val s = vm.dashboardStats()
        val gap = max(0.0, GmuCompanyOperatingSystem.TARGET_LABA_BERSIH_BULANAN - s.profit)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Kendali GMU EduTrans", fontWeight = FontWeight.Black, color = GmuDark) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(GmuCompanyOperatingSystem.misiJabatan(role), fontSize = 12.sp, lineHeight = 18.sp)
                    HorizontalDivider()

                    if (role == ErpRoles.OWNER || ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)) {
                        Text("Target & Hasil", fontWeight = FontWeight.Black, color = GmuGreen)
                        CompanyControlLine("Target laba bersih", rupiah(GmuCompanyOperatingSystem.TARGET_LABA_BERSIH_BULANAN))
                        CompanyControlLine("Laba tercatat", rupiah(s.profit))
                        CompanyControlLine("Kekurangan target", rupiah(gap))
                        CompanyControlLine("Omzet", rupiah(s.omzet))
                        CompanyControlLine("Margin", String.format("%.1f%%", s.margin))
                        CompanyControlLine("Kondisi", GmuCompanyOperatingSystem.statusLaba(s.profit))
                    }

                    if (ErpRoles.isManagerEduTrans(role)) {
                        HorizontalDivider()
                        Text("Batas Kewenangan Manager", fontWeight = FontWeight.Black, color = GmuGreen)
                        Text("• Dalam RAB: sampai Rp1.000.000/transaksi", fontSize = 11.sp)
                        Text("• Di luar RAB: sampai Rp250.000", fontSize = 11.sp)
                        Text("• Darurat saat trip: sampai Rp500.000", fontSize = 11.sp)
                        Text("• Diskon: sampai 5%", fontSize = 11.sp)
                        Text("• Margin 20–24,99%: tinjauan Manager", fontSize = 11.sp)
                        Text("• Margin <20%: persetujuan Direktur", fontSize = 11.sp)
                    }

                    if (role == "Sales" || ErpRoles.isManagerEduTrans(role)) {
                        HorizontalDivider()
                        Text("Fokus Pasar Tahap 1", fontWeight = FontWeight.Black, color = GmuGreen)
                        Text("Cianjur • Sukabumi", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        GmuCompanyOperatingSystem.segmenPasar.forEach { Text("• $it", fontSize = 11.sp) }
                        Text("Setiap calon klien wajib memiliki PIC Sales, sumber, status, potensi, dan tanggal tindak lanjut.", fontSize = 10.sp, color = Color.Gray)
                    }

                    if (role == "Finance" || role == ErpRoles.OWNER || ErpRoles.isDirector(role) || ErpRoles.isManagerEduTrans(role)) {
                        HorizontalDivider()
                        Text("Biaya yang Wajib Dihitung", fontWeight = FontWeight.Black, color = GmuGreen)
                        Text("Utilitas kantor: ${rupiah(GmuCompanyOperatingSystem.UTILITAS_KANTOR_PER_BULAN.toDouble())}/bulan", fontSize = 11.sp)
                        GmuCompanyOperatingSystem.kategoriBiayaTeknologi.forEach { Text("• $it", fontSize = 11.sp) }
                        Text("Laba bersih dihitung setelah seluruh biaya program, SDM, pemasaran, teknologi, kantor, administrasi, dan overhead.", fontSize = 10.sp, color = Color.Gray)
                    }

                    if (role == "Admin" || role == "Operation" || role == "TL" || ErpRoles.isManagerEduTrans(role)) {
                        HorizontalDivider()
                        Text("Siklus Kegiatan", fontWeight = FontWeight.Black, color = GmuGreen)
                        Text("Terkonfirmasi → H-7 → H-3 → H-1 → Pelaksanaan → H+1 Laporan → H+3 Penutupan Keuangan", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Tutup") } }
        )
    }
}

@Composable
private fun CompanyControlLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GmuDark)
    }
}
