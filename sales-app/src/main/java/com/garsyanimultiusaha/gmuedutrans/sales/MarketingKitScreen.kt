package com.garsyanimultiusaha.gmuedutrans.sales

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val KitGreen = Color(0xFF168400)
private val KitGreenDark = Color(0xFF0B5F10)
private val KitGold = Color(0xFFD7A600)
private val KitBg = Color(0xFFF6F8F4)

@Composable
fun MarketingKitScreen(
    session: SalesSession,
    onClose: () -> Unit
) {
    val api = remember { SalesKitApi() }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf<List<SalesKitProgram>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            try {
                catalog = api.loadCatalog(session)
            } catch (e: Exception) {
                error = e.message ?: "Marketing Kit gagal dimuat."
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(session.userId) {
        loading = true
        error = null
        try {
            catalog = api.loadCatalog(session)
        } catch (e: Exception) {
            error = e.message ?: "Marketing Kit gagal dimuat."
        } finally {
            loading = false
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = KitGreen,
            secondary = KitGold,
            background = KitBg,
            surface = Color.White,
            onPrimary = Color.White
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = KitBg
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Marketing & Sales Kit", fontWeight = FontWeight.Black, fontSize = 22.sp, color = KitGreenDark)
                        Text("Materi jual resmi GMU EduTrans", fontSize = 12.sp, color = Color.Gray)
                    }
                    TextButton(onClick = onClose) { Text("Tutup") }
                }

                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Program", "Script", "Playbook").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> ProgramKitTab(catalog, loading, error, ::reload)
                    1 -> SalesScriptsTab()
                    else -> SalesPlaybookTab()
                }
            }
        }
    }
}

@Composable
private fun ProgramKitTab(
    catalog: List<SalesKitProgram>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = KitGreenDark)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Harga resmi dari master program", color = Color.White, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Sales tidak boleh mengubah harga atau memberi diskon sendiri. Harga B2B/MoU yang muncul adalah referensi channel Sales dan tidak dibagikan sebagai harga publik.",
                        color = Color.White.copy(alpha = .86f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KitGreen)
                }
            }
        } else if (error != null) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Katalog belum dapat dimuat", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(error, color = Color.Gray, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Coba lagi") }
                    }
                }
            }
        } else if (catalog.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Text("Belum ada program aktif di master program.", Modifier.padding(18.dp), color = Color.Gray)
                }
            }
        } else {
            items(catalog, key = { it.id }) { program ->
                ProgramKitCard(
                    program = program,
                    onCopy = { clipboard.setText(AnnotatedString(programShareText(program))) },
                    onShare = { shareText(context, program.name, programShareText(program)) }
                )
            }
        }
    }
}

@Composable
private fun ProgramKitCard(
    program: SalesKitProgram,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(program.category.ifBlank { "Program Edukasi" }.uppercase(), color = KitGreen, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(program.name, fontWeight = FontWeight.Black, fontSize = 19.sp, color = KitGreenDark)
            if (program.shortDescription.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(program.shortDescription, color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniKitStat(
                    label = "Harga mulai",
                    value = program.marketingStartPrice?.let(::rupiah) ?: "By quotation",
                    modifier = Modifier.weight(1f)
                )
                MiniKitStat("Minimum", "${program.minPax} pax", Modifier.weight(1f))
            }
            if (program.marketingPriceNote.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(program.marketingPriceNote, color = Color.Gray, fontSize = 11.sp)
            }

            if (program.packages.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Paket aktif", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                program.packages.forEach { pkg -> PackageKitCard(pkg) }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCopy, modifier = Modifier.weight(1f)) { Text("Salin pitch") }
                Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Bagikan") }
            }
        }
    }
}

@Composable
private fun PackageKitCard(pkg: SalesKitPackage) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = KitBg
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(pkg.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(pkg.code, fontSize = 10.sp, color = Color.Gray)
                }
                Text(rupiah(pkg.publicPricePerPax), color = KitGreenDark, fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
            Text("Harga publik / pax • min. ${pkg.minPax} pax", color = Color.Gray, fontSize = 10.sp)
            if (pkg.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(pkg.description, fontSize = 11.sp, color = Color.DarkGray)
            }
            if (pkg.facilities.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                pkg.facilities.take(6).forEach { facility ->
                    Text("• $facility", fontSize = 10.sp, color = Color.DarkGray)
                }
                if (pkg.facilities.size > 6) Text("+ ${pkg.facilities.size - 6} fasilitas lainnya", fontSize = 10.sp, color = Color.Gray)
            }
            pkg.b2bNetPricePerPax?.let { b2b ->
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = KitGold.copy(alpha = .16f)) {
                    Text(
                        "Channel B2B/MoU: ${rupiah(b2b)}/pax — jangan dibagikan sebagai harga publik",
                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 10.sp,
                        color = KitGreenDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SalesScriptsTab() {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scripts = remember { salesScripts() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Script siap pakai", fontWeight = FontWeight.Black, fontSize = 20.sp, color = KitGreenDark)
            Text("Gunakan sebagai kerangka. Sesuaikan nama sekolah, PIC, program, dan konteks pembicaraan.", color = Color.Gray, fontSize = 12.sp)
        }
        items(scripts, key = { it.title }) { script ->
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(script.title, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text(script.purpose, color = KitGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Text(script.text, color = Color.DarkGray, fontSize = 12.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(script.text)) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Salin") }
                        Button(
                            onClick = { shareText(context, script.title, script.text) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Bagikan") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesPlaybookTab() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 14.dp, 16.dp, 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = KitGreenDark)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Target utama", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
                    Text("400 paid pax / bulan", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("Minimum produktif 200 pax • stretch 600 pax", color = Color.White.copy(alpha = .85f), fontSize = 11.sp)
                }
            }
        }
        item { PlaybookCard("Ritme harian", listOf("10 lead baru", "5–10 follow-up aktif", "1–2 percakapan serius dengan pengambil keputusan", "Prioritaskan quotation, negosiasi, dan lead menunggu DP")) }
        item { PlaybookCard("Pertanyaan kualifikasi", listOf("Jumlah peserta potensial berapa?", "Jenjang/kelas peserta?", "Rencana bulan atau tanggal kegiatan?", "Program yang dibutuhkan: edukasi, perjalanan, budaya, pertanian, atau custom?", "Kisaran budget per peserta?", "Siapa yang mengambil keputusan akhir?")) }
        item { PlaybookCard("Urutan closing", listOf("Pastikan kebutuhan dan jumlah pax", "Rekomendasikan maksimal 2–3 pilihan relevan", "Kirim harga resmi dari master program", "Tentukan tanggal/estimasi peserta", "Buat quotation", "Follow-up keputusan", "Booking + pembayaran/DP tervalidasi", "Handover ke Admin/Manager/Ops")) }
        item { PlaybookCard("Larangan Sales", listOf("Tidak membuat harga sendiri", "Tidak menjanjikan diskon tanpa approval", "Tidak membagikan HPP, laba, margin, atau biaya internal", "Tidak menganggap quotation sebagai closing", "Tidak meninggalkan lead aktif tanpa next follow-up")) }
        item { PlaybookCard("After sales", listOf("Ucapkan terima kasih setelah kegiatan", "Minta testimoni dan izin materi dokumentasi", "Tawarkan program semester berikutnya", "Minta referral sekolah/lembaga lain", "Catat repeat opportunity di CRM")) }
    }
}

@Composable
private fun PlaybookCard(title: String, points: List<String>) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontWeight = FontWeight.Black, fontSize = 16.sp, color = KitGreenDark)
            Spacer(Modifier.height(8.dp))
            points.forEach { Text("• $it", fontSize = 12.sp, color = Color.DarkGray, modifier = Modifier.padding(bottom = 5.dp)) }
        }
    }
}

@Composable
private fun MiniKitStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = KitBg) {
        Column(Modifier.padding(10.dp)) {
            Text(label, color = Color.Gray, fontSize = 9.sp)
            Text(value, color = KitGreenDark, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

private fun programShareText(program: SalesKitProgram): String {
    val price = program.marketingStartPrice?.let { "Mulai ${rupiah(it)}/peserta" } ?: "Harga menyesuaikan kebutuhan / quotation"
    return buildString {
        append("GMU EduTrans\n")
        append(program.name)
        append("\n\n")
        if (program.shortDescription.isNotBlank()) {
            append(program.shortDescription)
            append("\n\n")
        }
        append(price)
        append("\nMinimum ${program.minPax} peserta")
        if (program.marketingPriceNote.isNotBlank()) append("\n${program.marketingPriceNote}")
        append("\n\nKami dapat membantu simulasi program, jadwal, jumlah peserta, dan quotation resmi untuk sekolah/lembaga.")
    }
}

private fun salesScripts(): List<SalesKitScript> = listOf(
    SalesKitScript(
        "Pembuka WhatsApp",
        "Prospek baru",
        "Selamat pagi/siang Bapak/Ibu. Perkenalkan saya [Nama] dari GMU EduTrans, penyelenggara program edukasi, edutrip, perjalanan rombongan, dan pembelajaran luar kelas untuk sekolah. Apakah semester ini sekolah Bapak/Ibu memiliki agenda outing class, field trip, P5, edukasi profesi, atau perjalanan edukasi siswa? Jika ada, saya bisa bantu rekomendasikan program sesuai usia peserta dan budget sekolah."
    ),
    SalesKitScript(
        "Kualifikasi kebutuhan",
        "Setelah calon customer menunjukkan minat",
        "Baik Bapak/Ibu. Supaya rekomendasinya tepat, boleh saya tahu perkiraan jumlah peserta, jenjang/kelas, rencana bulan atau tanggal kegiatan, jenis program yang diminati, dan kisaran budget per peserta?"
    ),
    SalesKitScript(
        "Follow-up proposal",
        "H+1 setelah penawaran",
        "Selamat siang Bapak/Ibu, izin memastikan proposal GMU EduTrans yang saya kirim sudah diterima. Dari pilihan tersebut, kira-kira program mana yang paling sesuai dengan kebutuhan sekolah? Jika ada yang perlu disesuaikan dari jumlah peserta atau budget, boleh diinformasikan dan saya bantu cek pilihan resminya."
    ),
    SalesKitScript(
        "Keberatan harga",
        "Customer mengatakan mahal",
        "Baik Bapak/Ibu. Supaya saya tidak salah menyesuaikan, boleh diketahui kisaran budget yang direncanakan sekolah per peserta? Nanti saya cek pilihan program/paket resmi yang paling mendekati kebutuhan tersebut."
    ),
    SalesKitScript(
        "Closing tanggal",
        "Mendorong langkah konkret",
        "Untuk rencana sekolah, lebih memungkinkan pelaksanaan minggu kedua atau minggu ketiga? Jika programnya sudah sesuai, saya bantu siapkan booking dan quotation resminya terlebih dahulu."
    ),
    SalesKitScript(
        "Repeat order",
        "Setelah kegiatan selesai",
        "Terima kasih Bapak/Ibu sudah mempercayakan kegiatan bersama GMU EduTrans. Semoga kegiatannya bermanfaat untuk anak-anak. Kami juga memiliki beberapa program lain untuk agenda semester berikutnya. Jika berkenan, saya bisa kirimkan pilihan yang sesuai untuk perencanaan berikutnya."
    )
)

private fun shareText(context: Context, subject: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan materi Sales"))
}

private fun rupiah(value: Double): String = NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
    maximumFractionDigits = 0
    minimumFractionDigits = 0
}.format(value)
