package com.josh.photosweep.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF07110E)
val Surface = Color(0xFF10251F)
val SurfaceHigh = Color(0xFF18342C)
val Mint = Color(0xFF65E6B2)
val MintSoft = Color(0xFFB9F6DD)
val Coral = Color(0xFFFF6F68)
val Sand = Color(0xFFF4F0E8)
val Muted = Color(0xFF9EB4AC)

private val PhotoSweepColors = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = MintSoft,
    background = Ink,
    onBackground = Sand,
    surface = Surface,
    onSurface = Sand,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Muted,
    error = Coral
)

@Composable
fun PhotoSweepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PhotoSweepColors,
        content = content
    )
}
