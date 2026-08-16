package com.mlh.skinanalyzer.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.hardware.CapturePrefs
import com.mlh.skinanalyzer.security.PinStore
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Amber
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper
import com.mlh.skinanalyzer.ui.theme.RaisedButton
import com.mlh.skinanalyzer.ui.theme.RaisedOutlinedButton
import com.mlh.skinanalyzer.ui.theme.Teal
import kotlinx.coroutines.launch

@Composable
fun AdminScreen(
    hardwareStatus: String,
    hardwareDiagnostics: String = "",
    demoMode: Boolean = false,
    onDemoModeChange: (Boolean) -> Unit = {},
    appVersion: String = "",
    gushangLicenseStatus: String = "",
    gushangUserMessage: String = "",
    gushangNeedsRestart: Boolean = false,
    gushangActivated: Boolean = false,
    onBack: () -> Unit,
    onRefreshHardware: () -> Unit,
    onOpenDiagnostic: () -> Unit = {},
    onOpenLightTest: () -> Unit = {},
    onRefreshGushang: () -> Unit = {},
    onOpenManageAllFiles: () -> Unit = {},
    onImportLicenceResult: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var settleFirst by remember { mutableLongStateOf(CapturePrefs.settleFirstMs(context)) }
    var settleBetween by remember { mutableLongStateOf(CapturePrefs.settleBetweenMs(context)) }
    var settleAfter by remember { mutableLongStateOf(CapturePrefs.settleAfterMs(context)) }
    var preFirst by remember { mutableLongStateOf(CapturePrefs.preFirstMs(context)) }
    var captureSaved by remember { mutableStateOf(false) }
    var pinDraft by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
            }
            Text("Admin", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Captura", style = MaterialTheme.typography.titleLarge, color = Accent)
            var rotationMsg by remember { mutableStateOf("") }
            val rotDeg = CapturePrefs.captureRotationDeg(context)
            val rotCalibrated = CapturePrefs.isRotationCalibrated(context)
            Text(
                if (rotCalibrated) {
                    "Orientación calibrada: $rotDeg°. " +
                        "Si las fotos salen apaisadas o al revés, pulse Recalibrar."
                } else {
                    "Orientación aún no calibrada (default $rotDeg°). " +
                        "Se detecta sola en la primera captura con cara."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            RaisedOutlinedButton(
                text = "Recalibrar orientación",
                onClick = {
                    CapturePrefs.clearRotationCalibration(context)
                    rotationMsg =
                        "Calibración borrada. En la próxima captura se detectará de nuevo."
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (rotationMsg.isNotBlank()) {
                Text(rotationMsg, style = MaterialTheme.typography.bodySmall, color = Teal)
            }
            TimingField("Estabilización 1.ª luz (ms)", settleFirst) { settleFirst = it; captureSaved = false }
            TimingField("Entre luces (ms)", settleBetween) { settleBetween = it; captureSaved = false }
            TimingField("Tras disparo (ms)", settleAfter) { settleAfter = it; captureSaved = false }
            TimingField("Antes del 1.er disparo (ms)", preFirst) { preFirst = it; captureSaved = false }
            RaisedButton(
                text = if (captureSaved) "Tiempos guardados" else "Guardar tiempos",
                onClick = {
                    CapturePrefs.setTimings(context, settleFirst, settleBetween, settleAfter, preFirst)
                    captureSaved = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            RaisedOutlinedButton(
                text = "Restaurar tiempos por defecto",
                onClick = {
                    CapturePrefs.restoreDefaults(context)
                    settleFirst = CapturePrefs.DEFAULT_SETTLE_FIRST_MS
                    settleBetween = CapturePrefs.DEFAULT_SETTLE_BETWEEN_MS
                    settleAfter = CapturePrefs.DEFAULT_SETTLE_AFTER_MS
                    preFirst = CapturePrefs.DEFAULT_PRE_FIRST_MS
                    captureSaved = true
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Equipo", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                hardwareStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.7f),
            )
            RaisedButton(
                text = "Reconectar equipo",
                onClick = onRefreshHardware,
                modifier = Modifier.fillMaxWidth(),
                enabled = !demoMode,
            )
            RaisedOutlinedButton(
                text = "Diagnóstico de conexión",
                onClick = onOpenDiagnostic,
                modifier = Modifier.fillMaxWidth(),
            )
            RaisedButton(
                text = "Prueba de luces",
                onClick = onOpenLightTest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !demoMode,
                containerColor = Teal,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Licencia", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                if (gushangActivated) "Equipo activado" else "El equipo necesita activarse",
                style = MaterialTheme.typography.bodyMedium,
                color = if (gushangActivated) Teal else Amber,
            )
            RaisedOutlinedButton(
                text = "Reactivar",
                onClick = {
                    onImportLicenceResult(
                        "Si falla, cierre la app por completo (quitar de recientes) y ábrala de nuevo.",
                    )
                    onRefreshGushang()
                },
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("PIN de acceso", style = MaterialTheme.typography.titleLarge, color = Accent)
            OutlinedTextField(
                value = pinDraft,
                onValueChange = { pinDraft = it.filter(Char::isDigit).take(4) },
                label = { Text("Nuevo PIN (4 dígitos)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
            )
            RaisedButton(
                text = "Cambiar PIN",
                onClick = {
                    scope.launch {
                        if (pinDraft.length != 4) {
                            pinMsg = "El PIN debe tener 4 dígitos."
                        } else {
                            PinStore.setPin(context, pinDraft)
                            pinMsg = "PIN actualizado."
                            pinDraft = ""
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (pinMsg.isNotBlank()) {
                Text(pinMsg, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Modo de prueba", style = MaterialTheme.typography.titleLarge, color = Accent)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Simulación sin equipo", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (demoMode) "Activo — solo para pruebas" else "Desactivado — consulta real",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.55f),
                    )
                }
                Switch(checked = demoMode, onCheckedChange = onDemoModeChange)
            }

            if (hardwareDiagnostics.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Registro técnico", style = MaterialTheme.typography.titleLarge, color = Accent)
                Text(
                    hardwareDiagnostics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
            }

            if (appVersion.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Versión $appVersion",
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TimingField(label: String, valueMs: Long, onChange: (Long) -> Unit) {
    OutlinedTextField(
        value = valueMs.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(6)
            onChange(digits.toLongOrNull() ?: 0L)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
    )
}

fun openManageAllFilesIntent(packageName: String): Intent =
    try {
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:$packageName")
        }
    } catch (_: Exception) {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
