package site.garsyanimultiusaha.gawone.mitra

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

internal object GawoneMitraTokens {
    val Primary = Color(0xFF3B82F6)
    val PrimaryStrong = Color(0xFF2563EB)
    val PrimarySoft = Color(0xFFEFF6FF)
    val Ink = Color(0xFF1F2937)
    val Muted = Color(0xFF64748B)
    val Canvas = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E7EB)
    val Success = Color(0xFF16A34A)
    val Warning = Color(0xFFF59E0B)
}

@Composable
internal fun GawoneMitraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GawoneMitraTokens.Primary,
            onPrimary = Color.White,
            primaryContainer = GawoneMitraTokens.PrimarySoft,
            onPrimaryContainer = GawoneMitraTokens.Ink,
            background = GawoneMitraTokens.Canvas,
            onBackground = GawoneMitraTokens.Ink,
            surface = GawoneMitraTokens.Surface,
            onSurface = GawoneMitraTokens.Ink,
            surfaceVariant = Color(0xFFF1F5F9),
            onSurfaceVariant = GawoneMitraTokens.Muted,
            outline = GawoneMitraTokens.Border
        ),
        content = content
    )
}

@Composable
internal fun GawoneMitraBrandHeader(
    eyebrow: String = "WORKS FOR A BETTER YOU",
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = GawoneMitraTokens.Primary,
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
                Surface(color = GawoneMitraTokens.PrimarySoft, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        "Mitra",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = GawoneMitraTokens.PrimaryStrong,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Text(eyebrow, color = GawoneMitraTokens.Muted, style = MaterialTheme.typography.labelSmall)
        }
        trailing?.invoke()
    }
}

@Composable
internal fun GawoneMitraSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        if (!subtitle.isNullOrBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = GawoneMitraTokens.Muted)
        }
    }
}

@Composable
internal fun GawoneMitraCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = GawoneMitraTokens.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GawoneMitraTokens.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
internal fun GawoneMitraStatusPill(label: String, positive: Boolean = true) {
    val bg = if (positive) Color(0xFFECFDF5) else Color(0xFFFFF7ED)
    val fg = if (positive) GawoneMitraTokens.Success else Color(0xFFC2410C)
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
