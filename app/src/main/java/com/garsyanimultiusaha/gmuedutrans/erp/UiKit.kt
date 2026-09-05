package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

val GmuGreen = Color(0xFF0A6A3F)
val GmuDark = Color(0xFF06482F)
val GmuGold = Color(0xFFD5A300)
val GmuBg = Color(0xFFF4F7F5)
val GmuSoft = Color(0xFFEAF3EE)
val GmuDanger = Color(0xFFB42318)
val GmuWarn = Color(0xFFB54708)

fun rupiah(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Black, color = GmuDark)
        if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (accent) Color(0xFFFFF7D6) else Color.White)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 11.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = if (accent) GmuDark else Color.Black)
        }
    }
}

@Composable
fun StatusChip(text: String) {
    val bg = when (text.lowercase()) {
        "approved", "active", "completed", "closed", "ready", "confirmed", "present" -> Color(0xFFE9F7EF)
        "rejected", "inactive", "overdue", "absent" -> Color(0xFFFFEAEA)
        "pending", "draft", "quotation", "dp", "preparation" -> Color(0xFFFFF6DF)
        else -> GmuSoft
    }
    val fg = when (text.lowercase()) {
        "rejected", "inactive", "overdue", "absent" -> GmuDanger
        "pending", "draft", "quotation", "dp", "preparation" -> GmuWarn
        else -> GmuGreen
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(text.ifBlank { "-" }, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), fontSize = 10.sp, color = fg, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyCard(text: String) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Text(text, Modifier.fillMaxWidth().padding(20.dp), color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
fun GmuGradientHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GmuDark, GmuGreen)))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        content = content
    )
}

@Composable
fun GmuSelect(
    value: String,
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(start = 2.dp, bottom = 4.dp))
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(value.ifBlank { "Pilih " + label }, modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

fun bookingLabel(vm: MainViewModel, bookingId: String): String {
    val b = vm.bookingById(bookingId)
    return b?.let { it.bookingNo + " • " + it.programName } ?: bookingId.ifBlank { "-" }
}
