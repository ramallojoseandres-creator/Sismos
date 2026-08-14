package com.mlh.skinanalyzer.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.R
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intro de marca: el logo nace lejos (pequeño, al fondo) y se acerca
 * hasta tamaño normal; luego aparece el nombre y entra a Inicio.
 */
@Composable
fun WelcomeScreen(
    clinicName: String,
    onContinue: () -> Unit,
) {
    // Empieza “en el fondo” (~8%) y crece a tamaño hero.
    val logoScale = remember { Animatable(0.08f) }
    val logoAlpha = remember { Animatable(0.25f) }
    val vignette = remember { Animatable(0.72f) }
    val textAlpha = remember { Animatable(0f) }
    val continued = remember { mutableStateOf(false) }

    // Acercamiento cinematográfico: lento al inicio, frena al llegar.
    val zoomEasing = remember {
        CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    }

    fun go() {
        if (continued.value) return
        continued.value = true
        onContinue()
    }

    LaunchedEffect(Unit) {
        // Zoom del logo desde el fondo
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2200, easing = zoomEasing),
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            )
        }
        launch {
            vignette.animateTo(
                targetValue = 0.12f,
                animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
            )
        }
        delay(1900)
        textAlpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
        delay(1600)
        go()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Paper, Cream.copy(alpha = 0.95f), Paper),
                ),
            )
            .clickable { go() },
        contentAlignment = Alignment.Center,
    ) {
        // Velo que se aclara mientras el logo “sale” del fondo
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.copy(alpha = vignette.value)),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_mlh),
                contentDescription = "Logo MLH",
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    },
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                clinicName.ifBlank { "Dra. María Laura Hernández" },
                style = MaterialTheme.typography.displayLarge,
                color = Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value),
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(72.dp)
                    .height(2.dp)
                    .background(Accent)
                    .alpha(textAlpha.value),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Skin Analyzer Pro",
                style = MaterialTheme.typography.headlineMedium,
                color = Accent,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Análisis facial · MJ-008 · 100% local",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value),
            )
            Spacer(Modifier.height(36.dp))
            Text(
                "Toque para continuar",
                style = MaterialTheme.typography.labelLarge,
                color = Ink.copy(alpha = 0.4f),
                modifier = Modifier.alpha(textAlpha.value),
            )
        }
    }
}
