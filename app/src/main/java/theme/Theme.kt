package com.d4vram.psychologger.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// —— PALETA ——
// (asegúrate de tener estas vals en Color.kt)
private val LightStart   = PrimaryPurple    // p. ej. Color(0xFF8B5CF6)
private val LightEnd     = SecondaryPink    // p. ej. Color(0xFFEC4899)
private val DarkStart    = DarkBg           // p. ej. Color(0xFF0F0F23)
private val DarkEnd      = DarkerBg         // p. ej. Color(0xFF080813)

// —— ESQUEMAS DE COLOR ——
private val LightColors = lightColorScheme(
    primary       = LightStart,
    onPrimary     = TextLight,
    secondary     = AccentCyan,
    onSecondary   = TextLight,
    background    = Color.White,
    onBackground  = Color.Black,
    surface       = Color.White,
    onSurface     = Color.Black
)

private val DarkColors = darkColorScheme(
    primary       = DarkStart,
    onPrimary     = TextLight,
    secondary     = AccentOrange,
    onSecondary   = TextLight,
    background    = DarkStart,
    onBackground  = TextLight,
    surface       = CardBg,
    onSurface     = TextLight
)

@Composable
fun PsychoLoggerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // 1) transición infinita
    val transition = rememberInfiniteTransition()
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1000f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 10_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    // 2) Brush a partir de los colores de tu paleta
    val gradientBrush = Brush.linearGradient(
        colors = if (darkTheme)
            listOf(DarkStart, DarkEnd)
        else
            listOf(LightStart, LightEnd),
        start = Offset(shift.value, shift.value),
        end   = Offset(shift.value + 800f, shift.value + 1200f)
    )

    // 3) Capa de fondo animada
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // 4) Aplica MaterialTheme normalmente
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography  = AppTypography,
            shapes      = AppShapes,
            content     = content
        )
    }
}
