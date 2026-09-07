package com.pamoyanan.one.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF0B704B)
val BrandDark = Color(0xFF102D22)
val Mint = Color(0xFFEDF8F2)
val Page = Color(0xFFF7F9F8)
val Ink = Color(0xFF10251B)
val Muted = Color(0xFF56665E)
val Hairline = Color(0xFFDCE5DF)
val Gold = Color(0xFFD5B45E)
val Danger = Color(0xFFB74A43)
val Warning = Color(0xFFA67618)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = BrandDark,
    secondary = BrandDark,
    background = Page,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    outline = Hairline,
    error = Danger
)

private val Dark = darkColorScheme(
    primary = Color(0xFF65D7A2),
    onPrimary = Color(0xFF05291A),
    background = Color(0xFF0D1712),
    surface = Color(0xFF132019),
    onSurface = Color(0xFFE8F0EB)
)

@Composable
fun PamoyananTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
