package com.garsyanimultiusaha.gmuedutrans.erp

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PageRange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a shareable visual PNG and a print/save PDF without changing booking/payment records. */
object BookingPdfActions {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val NAVY = 0xFF102A43.toInt()
    private const val GOLD = 0xFFD5A52A.toInt()
    private const val GREEN = 0xFF247A58.toInt()
    private const val MUTED = 0xFF52606D.toInt()

    private fun money(value: Double): String = String.format(Locale("id", "ID"), "%,.0f", value)

    private fun drawBooking(canvas: Canvas, booking: Booking) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.WHITE)

        // Branded header
        paint.color = NAVY
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 178f, paint)
        paint.color = GOLD
        canvas.drawRect(0f, 178f, PAGE_WIDTH.toFloat(), 186f, paint)

        paint.color = Color.WHITE
        paint.textSize = 25f
        paint.isFakeBoldText = true
        canvas.drawText("GMU EDUTRANS", 36f, 55f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("EDUCATION • EXPERIENCE • JOURNEY", 36f, 78f, paint)
        paint.textSize = 19f
        paint.isFakeBoldText = true
        canvas.drawText("RINGKASAN BOOKING", 36f, 125f, paint)
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Nomor: ${booking.bookingNo}", 36f, 151f, paint)

        var y = 222f
        fun sectionTitle(text: String) {
            paint.color = NAVY
            paint.textSize = 13f
            paint.isFakeBoldText = true
            canvas.drawText(text, 36f, y, paint)
            y += 13f
            paint.color = GOLD
            canvas.drawRect(36f, y, 559f, y + 2f, paint)
            y += 18f
        }
        fun field(label: String, value: String, bold: Boolean = false) {
            paint.color = MUTED
            paint.textSize = 9.5f
            paint.isFakeBoldText = false
            canvas.drawText(label.uppercase(Locale("id", "ID")), 36f, y, paint)
            y += 14f
            paint.color = NAVY
            paint.textSize = if (bold) 14f else 12f
            paint.isFakeBoldText = bold
            val text = value.ifBlank { "-" }.take(78)
            canvas.drawText(text, 36f, y, paint)
            y += 25f
        }

        sectionTitle("INFORMASI PROGRAM")
        field("Customer / Sekolah", booking.customerName, true)
        field("Nama Program", booking.programName)
        field("Tanggal Kegiatan", booking.tripDate)
        field("Status Booking", booking.status)

        sectionTitle("DETAIL PESERTA & NILAI")
        field("Jumlah Peserta", "${booking.pax} peserta")
        field("Harga per Peserta", "Rp ${money(booking.pricePerPax)}")
        field("Total Nilai Booking", "Rp ${money(booking.omzet)}", true)

        sectionTitle("INFORMASI OPERASIONAL")
        field("Grup / Kelas", booking.participantGroup.ifBlank { "-" })
        field("Titik Kumpul", booking.meetingPoint.ifBlank { "-" })
        field("Fasilitas", booking.facilities.ifBlank { "-" })
        field("Kebutuhan Khusus", booking.specialRequirements.ifBlank { "-" })

        // Notes are kept concise to fit the page.
        if (y < 730f) field("Catatan", booking.notes.ifBlank { "-" })

        paint.color = GREEN
        paint.textSize = 9f
        paint.isFakeBoldText = true
        canvas.drawText("GMU EDUTRANS • DOKUMEN BOOKING", 36f, 795f, paint)
        paint.color = MUTED
        paint.textSize = 8f
        paint.isFakeBoldText = false
        canvas.drawText("Dibuat dari GMU EduTrans ERP • ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date())}", 36f, 812f, paint)
    }

    fun render(booking: Booking): ByteArray {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        drawBooking(page.canvas, booking)
        document.finishPage(page)
        val output = java.io.ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    fun renderPng(booking: Booking): ByteArray {
        val bitmap = Bitmap.createBitmap(1080, 1528, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(1080f / PAGE_WIDTH, 1528f / PAGE_HEIGHT)
        drawBooking(canvas, booking)
        val output = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        bitmap.recycle()
        return output.toByteArray()
    }

    private fun safeName(booking: Booking): String =
        booking.bookingNo.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "booking" }

    private fun cacheFile(context: Context, folder: String, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, folder).apply { mkdirs() }
        return File(dir, name).also { it.writeBytes(bytes) }
    }

    private fun shareFile(context: Context, booking: Booking, bytes: ByteArray, folder: String, extension: String, mimeType: String, chooserTitle: String) {
        require(bytes.isNotEmpty()) { "File dokumen kosong. Silakan coba buat ulang." }
        val file = cacheFile(context, folder, "${safeName(booking)}.$extension", bytes)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GMU EduTrans ${booking.bookingNo}")
            putExtra(Intent.EXTRA_TEXT, "Dokumen booking GMU EduTrans ${booking.bookingNo}")
            clipData = ClipData.newUri(context.contentResolver, "Dokumen booking GMU", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(chooser)
    }

    fun shareVisualPng(context: Context, booking: Booking, bytes: ByteArray) =
        shareFile(context, booking, bytes, "booking-visual", "png", "image/png", "Bagikan visual booking PNG")

    fun sharePdf(context: Context, booking: Booking, bytes: ByteArray) =
        shareFile(context, booking, bytes, "booking-pdf", "pdf", "application/pdf", "Bagikan PDF booking")

    fun print(context: Context, booking: Booking, bytes: ByteArray) {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: throw IllegalStateException("Layanan cetak tidak tersedia pada perangkat ini.")
        manager.print("GMU-${booking.bookingNo}", object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder("GMU-${safeName(booking)}.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                try {
                    android.os.ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output -> output.write(bytes); output.flush() }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message ?: "Gagal menyiapkan PDF untuk cetak.")
                }
            }
        }, PrintAttributes.Builder().build())
    }
}

@Composable
fun BookingPdfActionRow(booking: Booking) {
    val context = LocalContext.current
    val pdfBytes = remember(booking) { BookingPdfActions.render(booking) }
    val pngBytes = remember(booking) { BookingPdfActions.renderPng(booking) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                require(pdfBytes.isNotEmpty()) { "PDF kosong. Silakan coba ulang." }
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(pdfBytes)
                    output.flush()
                } ?: throw IllegalStateException("Tidak dapat membuka lokasi penyimpanan PDF.")
            }.onSuccess {
                android.widget.Toast.makeText(context, "PDF booking berhasil disimpan.", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "Gagal menyimpan PDF: ${it.message ?: "Kesalahan tidak diketahui"}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                runCatching { BookingPdfActions.shareVisualPng(context, booking, pngBytes) }.onFailure {
                    android.widget.Toast.makeText(context, "Gagal membagikan PNG: ${it.message ?: "Kesalahan tidak diketahui"}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) { Text("Bagikan Visual PNG") }
        OutlinedButton(
            onClick = {
                runCatching { BookingPdfActions.sharePdf(context, booking, pdfBytes) }.onFailure {
                    android.widget.Toast.makeText(context, "Gagal membagikan PDF: ${it.message ?: "Kesalahan tidak diketahui"}", android.widget.Toast.LENGTH_LONG).show()
                }
            },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) { Text("Bagikan PDF") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { saveLauncher.launch("${booking.bookingNo}.pdf") },
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) { Text("Simpan PDF") }
            OutlinedButton(
                onClick = {
                    runCatching { BookingPdfActions.print(context, booking, pdfBytes) }.onFailure {
                        android.widget.Toast.makeText(context, it.message ?: "Gagal mencetak PDF", android.widget.Toast.LENGTH_LONG).show()
                    }
                },
                modifier = androidx.compose.ui.Modifier.weight(1f)
            ) { Text("Cetak PDF") }
        }
    }
}
