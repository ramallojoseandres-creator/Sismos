package com.mlh.skinanalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fondo profundo — captura / PIN */
val Ink = Color(0xFF0F1419)
/** Superficies elevadas */
val Slate = Color(0xFF1C2530)
/** Fondo de lectura / informes */
val Paper = Color(0xFFF2F4F7)
/** Superficie suave (antes Cream) */
val Cream = Color(0xFFE6EAF0)
/** Acción principal */
val Accent = Color(0xFF3B6FE8)
val AccentSoft = Color(0xFF5B8AF0)
/** Confirmación */
val Teal = Color(0xFF00C2A8)
/** Atención / empeoramiento */
val Amber = Color(0xFFFF7A45)
val Gold = Amber
val SoftLine = Color(0xFFC5CCD6)

/** Colores de los 8 modos de luz (hardware). */
object LightColors {
    val White = Color(0xFFF5F7FA)
    val Xpl = Color(0xFFB8C4D4)
    val Ppl = Color(0xFF7A6B9A)
    val Uv = Color(0xFF6B4FFF)
    val Blue = Color(0xFF3B6FE8)
    val Woods = Color(0xFF4A90A4)
    val Orange = Color(0xFFFF7A45)
    val Red = Color(0xFFE23B3B)
}

private val LightScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Teal,
    onSecondary = Color.White,
    tertiary = Amber,
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Cream,
    outline = SoftLine,
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        fontFeatureSettings = "tnum",
    ),
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
    ),
)

@Composable
fun MlhTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        typography = AppTypography,
        content = content,
    )
}
