package com.mlh.skinanalyzer.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mlh.skinanalyzer.security.PinStore
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import com.mlh.skinanalyzer.ui.theme.Slate
import kotlinx.coroutines.launch

@Composable
fun PinLockScreen(
    clinicName: String,
    onUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun vibrateError() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = context.getSystemService(VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Vibrator::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    v?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(40)
                }
            }
        }
    }

    fun onDigit(d: Char) {
        if (digits.length >= 4) return
        error = false
        val next = digits + d
        digits = next
        if (next.length == 4) {
            scope.launch {
                val ok = PinStore.verify(context, next)
                if (ok) {
                    onUnlocked()
                } else {
                    error = true
                    vibrateError()
                    digits = ""
                }
            }
        }
    }

    fun onBackspace() {
        error = false
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Slate)))
            .padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            clinicName.ifBlank { "Dra. María Laura Hernández" },
            style = MaterialTheme.typography.headlineMedium,
            color = Paper,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Introduzca el PIN",
            style = MaterialTheme.typography.bodyLarge,
            color = Paper.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(36.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                error -> ColorError
                                i < digits.length -> Accent
                                else -> Paper.copy(alpha = 0.25f)
                            },
                        ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (error) "PIN incorrecto" else " ",
            color = ColorError,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.weight(1f))
        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf(' ', '0', '⌫'),
        )
        keys.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (key == ' ') androidx.compose.ui.graphics.Color.Transparent
                                else Slate.copy(alpha = 0.85f),
                            )
                            .then(
                                if (key != ' ') {
                                    Modifier.clickable {
                                        when (key) {
                                            '⌫' -> onBackspace()
                                            else -> onDigit(key)
                                        }
                                    }
                                } else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key != ' ') {
                            Text(
                                key.toString(),
                                color = Paper,
                                fontSize = 28.sp,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(16.dp))
    }
}

private val ColorError = androidx.compose.ui.graphics.Color(0xFFFF7A45)
