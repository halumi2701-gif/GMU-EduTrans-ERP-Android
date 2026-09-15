package com.garsyanimultiusaha.gmuedutrans.erp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

// GMU EduTrans ERP v20 design tokens.
val GmuGreen = Color(0xFF0A6A3F)
val GmuDark = Color(0xFF06482F)
val GmuGold = Color(0xFFD5A300)
val GmuBg = Color(0xFFF5F7F6)
val GmuSoft = Color(0xFFEAF3EE)
val GmuSurfaceMuted = Color(0xFFF0F4F2)
val GmuLine = Color(0xFFE1E8E4)
val GmuText = Color(0xFF17201C)
val GmuMuted = Color(0xFF65706A)
val GmuDanger = Color(0xFFB42318)
val GmuWarn = Color(0xFFB54708)

object GmuSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object GmuRadii {
    val field = 14.dp
    val card = 20.dp
    val hero = 26.dp
}

private val GmuColorScheme = lightColorScheme(
    primary = GmuGreen,
    onPrimary = Color.White,
    primaryContainer = GmuSoft,
    onPrimaryContainer = GmuDark,
    secondary = GmuGold,
    onSecondary = GmuDark,
    background = GmuBg,
    onBackground = GmuText,
    surface = Color.White,
    onSurface = GmuText,
    surfaceVariant = GmuSurfaceMuted,
    onSurfaceVariant = GmuMuted,
    outline = GmuLine,
    error = GmuDanger,
    onError = Color.White
)

private val GmuTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Black),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold)
)

private val GmuShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun GmuV20Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GmuColorScheme,
        typography = GmuTypography,
        shapes = GmuShapes,
        content = content
    )
}

fun rupiah(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).format(value).replace(",00", "")

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = GmuDark
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = GmuMuted
            )
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(GmuRadii.card),
        border = BorderStroke(1.dp, if (accent) GmuGold.copy(alpha = .35f) else GmuLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = if (accent) Color(0xFFFFF8DD) else Color.White)
    ) {
        Column(Modifier.padding(GmuSpacing.md)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = GmuMuted)
            Spacer(Modifier.height(5.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (accent) GmuDark else GmuText
            )
        }
    }
}

@Composable
fun StatusChip(text: String) {
    val bg = when (text.lowercase()) {
        "approved", "active", "completed", "closed", "ready", "confirmed", "present", "healthy" -> Color(0xFFE9F7EF)
        "rejected", "inactive", "overdue", "absent", "critical" -> Color(0xFFFFEAEA)
        "pending", "draft", "quotation", "dp", "preparation", "attention" -> Color(0xFFFFF6DF)
        else -> GmuSoft
    }
    val fg = when (text.lowercase()) {
        "rejected", "inactive", "overdue", "absent", "critical" -> GmuDanger
        "pending", "draft", "quotation", "dp", "preparation", "attention" -> GmuWarn
        else -> GmuGreen
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text.ifBlank { "-" },
            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
fun EmptyCard(text: String) {
    Card(
        shape = RoundedCornerShape(GmuRadii.card),
        border = BorderStroke(1.dp, GmuLine),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Text(
            text,
            Modifier.fillMaxWidth().padding(GmuSpacing.lg),
            color = GmuMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun GmuGradientHeader(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(GmuDark, GmuGreen)))
            .padding(horizontal = GmuSpacing.lg, vertical = GmuSpacing.xl),
        content = content
    )
}

@Composable
fun GmuSectionCard(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(GmuRadii.card),
        border = BorderStroke(1.dp, if (accent) GmuGold.copy(alpha = .3f) else GmuLine),
        colors = CardDefaults.cardColors(containerColor = if (accent) Color(0xFFFFF8DD) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(GmuSpacing.md), content = content)
    }
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
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = GmuMuted,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(GmuRadii.field)
            ) {
                Text(value.ifBlank { "Pilih $label" }, modifier = Modifier.weight(1f))
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
