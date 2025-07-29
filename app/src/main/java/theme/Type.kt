package com.d4vram.psychologger.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.d4vram.psychologger.R

private val AppFont = FontFamily(
    // Fuente normal
    Font(
        resId = R.font.montserrat_variable,
        weight = FontWeight.Normal,
        style = FontStyle.Normal
    ),
    // Fuente medium
    Font(
        resId = R.font.montserrat_variable,
        weight = FontWeight.Medium,
        style = FontStyle.Normal
    ),
    // Fuente bold
    Font(
        resId = R.font.montserrat_variable,
        weight = FontWeight.Bold,
        style = FontStyle.Normal
    ),
    // Fuente italic
    Font(
        resId = R.font.montserrat_variable_italic,
        weight = FontWeight.Normal,
        style = FontStyle.Italic
    )
)

val AppTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight  = FontWeight.Bold,
        fontSize    = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = AppFont,
        fontWeight  = FontWeight.Normal,
        fontSize    = 16.sp
    )
    // Puedes añadir aquí más TextStyles (labelSmall, headlineMedium, etc.)
)
