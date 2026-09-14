package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.layout.*
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
import kotlin.math.max

/**
 * GMU EduTrans Company Operating System v1.
 *
 * Prinsip inti:
 * - Direktur menentukan hasil.
 * - Manager mengelola unit bisnis.
 * - ERP mengatur proses, tugas, kontrol, dan eskalasi.
 * - AI membantu analisis/rekomendasi, bukan mengambil keputusan sensitif.
 * - Tim bekerja dari sistem, bukan menunggu instruksi harian Direktur.
 */
object GmuCompanyOperatingSystem {
    const val VERSION = "1.0"

    // Sasaran perusahaan
    const val TARGET_LABA_BERSIH_BULANAN: Double = 15_000_000.0
    const val TARGET_MARGIN_SEHAT_PCT: Double = 25.0
    const val MARGIN_KRITIS_PCT: Double = 20.0

    // Kebijakan biaya & SDM yang sudah dikunci
    const val TRANSPORT_MANAGER_PER_HARI: Long = 30_000L
    const val UTILITAS_KANTOR_PER_BULAN: Long = 1_000_000L

    // Kewenangan Manager
    const val BATAS_BIAYA_RAB_MANAGER: Long = 1_000_000L
    const val BATAS_BIAYA_DI_LUAR_RAB_MANAGER: Long = 250_000L
    const val BATAS_BIAYA_DARURAT_TRIP_MANAGER: Long = 500_000L
    const val BATAS_DISKON_MANAGER_PCT: Double = 5.0

    // Fokus wilayah tahap pertama
    val wilayahOperasionalPrioritas = listOf("Cianjur", "Sukabumi")

    val segmenPasar = listOf(
        "Sekolah & Madrasah",
        "Pesantren",
        "Perguruan Tinggi",
        "Instansi Pemerintah",
        "Komunitas & Organisasi",
        "Perusahaan / Corporate",
        "Partner & Mitra"
    )

    val kategoriBiayaTeknologi = listOf(
        "Server & Hosting",
        "Domain",
        "Cloud & Database",
        "AI & Otomatisasi",
        "Email & Notifikasi",
        "Penyimpanan & Backup",
        "Software & Langganan",
        "Keamanan Digital",
        "API Pihak Ketiga"
    )

    val siklusBisnis = listOf(
        "Calon Pelanggan",
        "Peluang Penjualan",
        "Penawaran Harga",
        "Negosiasi",
        "Menunggu DP",
        "Pemesanan Terkonfirmasi",
        "Persiapan Operasional",
        "Siap Dilaksanakan",
        "Kegiatan Berjalan",
        "Selesai Operasional",
        "Tinjauan Keuangan",
        "Ditutup",
        "Tindak Lanjut / Pesanan Ulang"
    )

    val biayaUtama = listOf(
        "Biaya Langsung Program",
        "Biaya SDM",
        "Biaya Penjualan & Pemasaran",
        "Biaya Operasional Kantor",
        "Biaya Teknologi & Infrastruktur Digital",
        "Biaya Administrasi",
        "Biaya Pengembangan",
        "Biaya Aset / Investasi",
        "Biaya Darurat"
    )

    fun statusLaba(labaAktual: Double): String = when {
        labaAktual >= TARGET_LABA_BERSIH_BULANAN -> "SESUAI TARGET"
        labaAktual >= TARGET_LABA_BERSIH_BULANAN * .75 -> "PERLU PERHATIAN"
        else -> "BERISIKO"
    }

    fun statusMargin(margin: Double): String = when {
        margin >= TARGET_MARGIN_SEHAT_PCT -> "SEHAT"
        margin >= MARGIN_KRITIS_PCT -> "PERLU TINJAUAN MANAGER"
        else -> "KRITIS · PERLU PERSETUJUAN DIREKTUR"
    }

    fun misiJabatan(role: String): String = when {
        role == ErpRoles.OWNER || ErpRoles.isDirector(role) ->
            "Menentukan target, arah, batas risiko, dan keputusan strategis. Tidak mengelola pekerjaan harian."
        ErpRoles.isManagerEduTrans(role) ->
            "Bertanggung jawab atas laba unit, penjualan, pemesanan, operasional, biaya, tim, pelanggan, dan rencana pemulihan."
        role == "Sales" ->
            "Mengubah target menjadi calon pelanggan, tindak lanjut, penawaran, negosiasi, dan pemesanan yang sehat marginnya."
        role == "Finance" ->
            "Menjaga tagihan, pembayaran, piutang, kewajiban, biaya aktual, penggajian, kas, dan penutupan keuangan."
        role == "Admin" || role == "Operation" ->
            "Menjaga pemesanan, dokumen, manifest, rundown, vendor, crew, dan kesiapan kegiatan."
        role == "TL" ->
            "Menjalankan kegiatan sesuai penugasan, rundown, keselamatan, pelayanan peserta, dan laporan."
        else -> "Menjalankan tugas sesuai jabatan, SOP, target, dan kewenangan di ERP."
    }
}

@Composable
fun GmuNativeAppWithCompanyOperatingSystem(vm: MainViewModel) {
    Box(Modifier.fillMaxSize()) {
        GmuNativeAppWithCorrectivePlanReviewCalendar(vm)

        val state = vm.state
        if (state is AppState.LoggedIn) {
            var open by remember { mutableStateOf(false) }
            val role = state.session.profile.role

            ExtendedFloatingActionButton(
                onClick = { open = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 86.dp),
                containerColor = GmuDark,
                contentColor = Color.White,
                text = { Text(if (role == "Sales") "Target & Pasar" else "Kendali Perusahaan", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                icon = { Text("✦", fontWeight = FontWeight.Black) }
            )

            if (open) {
                CompanyOperatingSystemDialog(
                    vm = vm,
                    session = state.session,
                    onDismiss = { open = false }
                )
            }
        }
    }
}

@Composable
private fun CompanyOperatingSystemDialog(
    vm: MainViewModel,
    session: SessionState,
    onDismiss: () -> Unit
) {
    val s = vm.dashboardStats()
    val role = session.profile.role
    val gap = max(0.0, GmuCompanyOperatingSystem.TARGET_LABA_BERSIH_BULANAN - s.profit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Sistem Kendali GMU EduTrans", fontWeight = FontWeight.Black, color = GmuDark)
                Text("Versi ${GmuCompanyOperatingSystem.VERSION} · Bahasa Indonesia", fontSize = 10.sp, color = Color.Gray)
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OperatingSection("Mandat Jabatan") {
                    Text(GmuCompanyOperatingSystem.misiJabatan(role), fontSize = 12.sp, lineHeight = 18.sp)
                }

                if (role == ErpRoles.OWNER || ErpRoles.isDirector(role)) {
                    OperatingSection("Kendali Direktur") {
                        OperatingMetric("Target laba bersih", rupiah(GmuCompanyOperatingSystem.TARGET_LABA_BERSIH_BULANAN))
                        OperatingMetric("Laba tercatat", rupiah(s.profit))
                        OperatingMetric("Kekurangan target", rupiah(gap))
                        OperatingMetric("Omzet", rupiah(s.omzet))
                        OperatingMetric("Margin", String.format("%.1f%% · %s", s.margin, GmuCompanyOperatingSystem.statusMargin(s.margin)))
                        OperatingMetric("Kondisi target", GmuCompanyOperatingSystem.statusLaba(s.profit))
                        Spacer(Modifier.height(8.dp))
                        Text("Prinsip: Direktur hanya menangani pengecualian strategis, kas kritis, margin kritis, refund besar, investasi, fraud, risiko hukum, dan insiden serius.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (ErpRoles.isManagerEduTrans(role)) {
                    OperatingSection("Pusat Kendali Manager") {
                        OperatingMetric("Target laba bersih unit", rupiah(GmuCompanyOperatingSystem.TARGET_LABA_BERSIH_BULANAN))
                        OperatingMetric("Laba tercatat", rupiah(s.profit))
                        OperatingMetric("Kekurangan target", rupiah(gap))
                        OperatingMetric("Pemesanan bulan ini", s.bookingsMonth.toString())
                        OperatingMetric("Peserta", s.pax.toString())
                        OperatingMetric("Margin", String.format("%.1f%% · %s", s.margin, GmuCompanyOperatingSystem.statusMargin(s.margin)))
                        Spacer(Modifier.height(8.dp))
                        Text("Batas persetujuan: biaya dalam RAB ≤ Rp1.000.000; di luar RAB ≤ Rp250.000; keadaan darurat trip ≤ Rp500.000; diskon ≤5%. Margin <20% wajib Direktur.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (role == "Sales") {
                    OperatingSection("Fokus Penjualan") {
                        Text("Wilayah prioritas: Cianjur & Sukabumi.", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        GmuCompanyOperatingSystem.segmenPasar.forEach { Text("• $it", fontSize = 11.sp) }
                        Spacer(Modifier.height(7.dp))
                        Text("Setiap calon pelanggan harus mempunyai sumber, PIC Sales, status, tanggal tindak lanjut berikutnya, nilai potensi, dan hasil akhir.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (role == "Finance" || ErpRoles.isManagerEduTrans(role) || role == ErpRoles.OWNER || ErpRoles.isDirector(role)) {
                    OperatingSection("Biaya Perusahaan") {
                        Text("Utilitas kantor baseline: ${rupiah(GmuCompanyOperatingSystem.UTILITAS_KANTOR_PER_BULAN.toDouble())}/bulan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        GmuCompanyOperatingSystem.kategoriBiayaTeknologi.forEach { Text("• $it", fontSize = 11.sp) }
                        Spacer(Modifier.height(6.dp))
                        Text("Target laba baru dianggap tercapai setelah seluruh biaya program, SDM, pemasaran, teknologi, kantor, administrasi, dan overhead dikurangi.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                if (role == "Admin" || role == "Operation" || role == "TL" || ErpRoles.isManagerEduTrans(role)) {
                    OperatingSection("Siklus Operasional") {
                        Text("Pemesanan Terkonfirmasi → H-7 persiapan → H-3 konfirmasi → H-1 kesiapan akhir → kegiatan → H+1 laporan → H+3 penutupan keuangan.", fontSize = 11.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Masalah normal diselesaikan PIC → TL/Koordinator → Manager. Direktur hanya menerima eskalasi strategis/kritis.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                OperatingSection("Wilayah Operasional Tahap 1") {
                    Text(GmuCompanyOperatingSystem.wilayahOperasionalPrioritas.joinToString(" • "), fontWeight = FontWeight.Black, color = GmuGreen)
                    Text("Wilayah lain tetap dapat dilayani bila menguntungkan dan mampu dijalankan, tetapi ekspansi aktif ditahan sampai dua wilayah inti stabil.", fontSize = 11.sp, color = Color.Gray)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun OperatingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9F8))
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Text(title, fontWeight = FontWeight.Black, color = GmuDark, fontSize = 13.sp)
            Spacer(Modifier.height(7.dp))
            content()
        }
    }
}

@Composable
private fun OperatingMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GmuDark)
    }
}
