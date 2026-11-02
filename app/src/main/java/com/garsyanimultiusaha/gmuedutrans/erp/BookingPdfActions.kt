package com.garsyanimultiusaha.gmuedutrans.erp

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
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
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared PDF renderer for booking documents. Does not alter booking/payment records. */
object BookingPdfActions {
    fun render(booking: Booking): ByteArray {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }
        var y = 54f
        fun line(text: String, bold: Boolean = false, size: Float = 12f) {
            paint.textSize = size
            paint.isFakeBoldText = bold
            canvas.drawText(text.take(92), 42f, y, paint)
            y += if (size >= 18f) 30f else 22f
        }
        line("GMU EDUTRANS", true, 22f)
        line("DOKUMEN BOOKING / RINGKASAN PESANAN", true, 15f)
        y += 8f
        line("Nomor Booking: ${booking.bookingNo}", true)
        line("Customer: ${booking.customerName}")
        line("Program: ${booking.programName}")
        line("Tanggal Trip: ${booking.tripDate}")
        line("Jumlah Peserta: ${booking.pax} pax")
        line("Harga per Peserta: Rp ${money(booking.pricePerPax)}")
        line("Total Nilai Booking: Rp ${money(booking.omzet)}", true)
        line("Status: ${booking.status}")
        y += 8f
        line("Grup Peserta: ${booking.participantGroup.ifBlank { "-" }}")
        line("Titik Kumpul: ${booking.meetingPoint.ifBlank { "-" }}")
        line("Fasilitas: ${booking.facilities.ifBlank { "-" }}")
        line("Kebutuhan Khusus: ${booking.specialRequirements.ifBlank { "-" }}")
        line("Catatan: ${booking.notes.ifBlank { "-" }}")
        y += 24f
        line("Dicetak: ${SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date())}", size = 9f)
        line("Dokumen ini merupakan ringkasan booking dari GMU EduTrans ERP.", size = 9f)
        document.finishPage(page)
        val output = java.io.ByteArrayOutputStream()
        document.writeTo(output)
        document.close()
        return output.toByteArray()
    }

    private fun money(value: Double): String = String.format(Locale("id", "ID"), "%,.0f", value)

    private fun cachePdf(context: Context, booking: Booking, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "booking-pdf").apply { mkdirs() }
        val safeName = booking.bookingNo.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "${safeName}.pdf").also { file -> file.writeBytes(bytes) }
    }

    fun share(context: Context, booking: Booking, bytes: ByteArray) {
        val file = cachePdf(context, booking, bytes)
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Dokumen Booking ${booking.bookingNo}")
            putExtra(Intent.EXTRA_TEXT, "Dokumen booking GMU EduTrans ${booking.bookingNo}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan PDF booking"))
    }

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
                val info = PrintDocumentInfo.Builder("GMU-${booking.bookingNo}.pdf")
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
                    FileOutputStream(destination.fileDescriptor).use { it.write(bytes) }
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
    val bytes = remember(booking) { BookingPdfActions.render(booking) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: throw IllegalStateException("Tidak dapat membuka lokasi penyimpanan PDF.")
            }.onFailure { android.widget.Toast.makeText(context, it.message ?: "Gagal menyimpan PDF", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = { BookingPdfActions.share(context, booking, bytes) }) { Text("Bagikan PDF") }
        OutlinedButton(onClick = { saveLauncher.launch("${booking.bookingNo}.pdf") }) { Text("Simpan") }
        OutlinedButton(onClick = { runCatching { BookingPdfActions.print(context, booking, bytes) }.onFailure {
            android.widget.Toast.makeText(context, it.message ?: "Gagal mencetak PDF", android.widget.Toast.LENGTH_LONG).show()
        } }) { Text("Cetak") }
    }
}
