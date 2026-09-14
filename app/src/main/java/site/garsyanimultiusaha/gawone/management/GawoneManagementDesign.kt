package site.garsyanimultiusaha.gawone.management

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal object GawoneManagementTokens {
    val Primary = Color(0xFF8B5CF6)
    val PrimaryStrong = Color(0xFF7C3AED)
    val PrimarySoft = Color(0xFFF3E8FF)
    val Ink = Color(0xFF1F2937)
    val Muted = Color(0xFF64748B)
    val Canvas = Color(0xFFF8FAFC)
    val Surface = Color.White
    val Border = Color(0xFFE5E7EB)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
}

@Composable
internal fun GawoneManagementTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GawoneManagementTokens.Primary,
            onPrimary = Color.White,
            primaryContainer = GawoneManagementTokens.PrimarySoft,
            onPrimaryContainer = GawoneManagementTokens.Ink,
            background = GawoneManagementTokens.Canvas,
            onBackground = GawoneManagementTokens.Ink,
            surface = GawoneManagementTokens.Surface,
            onSurface = GawoneManagementTokens.Ink,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = GawoneManagementTokens.Muted,
            outline = GawoneManagementTokens.Border
        ),
        content = content
    )
}

@Composable
internal fun GawoneManagementBrandHeader(
    subtitle: String = "Untuk operasional yang lebih efisien",
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            color = GawoneManagementTokens.Primary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("g", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("gawone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Surface(color = GawoneManagementTokens.PrimarySoft, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        "Management",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = GawoneManagementTokens.PrimaryStrong,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GawoneManagementTokens.Muted)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun GawoneManagementCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = GawoneManagementTokens.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GawoneManagementTokens.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
internal fun GawoneManagementStatusPill(label: String, positive: Boolean = true) {
    val bg = if (positive) Color(0xFFECFDF5) else Color(0xFFFFF7ED)
    val fg = if (positive) GawoneManagementTokens.Success else Color(0xFFC2410C)
    Surface(color = bg, shape = RoundedCornerShape(999.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = fg,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
