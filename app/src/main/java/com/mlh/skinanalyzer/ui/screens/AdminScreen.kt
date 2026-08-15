package com.mlh.skinanalyzer.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.analysis.gushang.GushangLicense
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var rotation by remember { mutableIntStateOf(CapturePrefs.rotationDeg(context)) }
    var mirror by remember { mutableStateOf(CapturePrefs.mirrorHorizontal(context)) }
    var settleFirst by remember { mutableLongStateOf(CapturePrefs.settleFirstMs(context)) }
    var settleBetween by remember { mutableLongStateOf(CapturePrefs.settleBetweenMs(context)) }
    var settleAfter by remember { mutableLongStateOf(CapturePrefs.settleAfterMs(context)) }
    var preFirst by remember { mutableLongStateOf(CapturePrefs.preFirstMs(context)) }
    var captureSaved by remember { mutableStateOf(false) }
    var pinDraft by remember { mutableStateOf("") }
    var pinMsg by remember { mutableStateOf("") }
    var importMsg by remember { mutableStateOf("") }

    val licencePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                GushangLicense.importLicence(context, uri)
            }
            result.fold(
                onSuccess = {
                    importMsg = it
                    onImportLicenceResult(it)
                    onRefreshGushang()
                },
                onFailure = {
                    importMsg = it.message ?: "No se pudo importar la licencia."
                    onImportLicenceResult(importMsg)
                },
            )
        }
    }

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
            Text("Cámara", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Ajuste la orientación de las fotos guardadas (necesaria para el análisis).",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            Text("Rotación", style = MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 90, 180, 270).forEach { deg ->
                    FilterChip(
                        selected = rotation == deg,
                        onClick = {
                            rotation = deg
                            CapturePrefs.setRotationDeg(context, deg)
                        },
                        label = { Text("$deg°") },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Espejo horizontal", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Útil si la cámara frontal invierte la imagen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.55f),
                    )
                }
                Switch(
                    checked = mirror,
                    onCheckedChange = {
                        mirror = it
                        CapturePrefs.setMirrorHorizontal(context, it)
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Tiempos de captura", style = MaterialTheme.typography.titleLarge, color = Accent)
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
                text = "Restaurar valores por defecto",
                onClick = {
                    CapturePrefs.restoreDefaults(context)
                    rotation = CapturePrefs.DEFAULT_ROTATION
                    mirror = CapturePrefs.DEFAULT_MIRROR
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
                if (gushangActivated) "Equipo activado" else gushangUserMessage.ifBlank { gushangLicenseStatus },
                style = MaterialTheme.typography.bodyMedium,
                color = if (gushangActivated) Teal else Amber,
            )
            if (!gushangActivated) {
                Text(
                    gushangLicenseStatus,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.55f),
                )
            }
            RaisedButton(
                text = "Seleccionar archivo de licencia",
                onClick = { licencePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (importMsg.isNotBlank()) {
                Text(importMsg, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.7f))
            }
            if (Build.VERSION.SDK_INT >= 30 && !GushangLicense.isAllFilesAccessGranted()) {
                RaisedButton(
                    text = "Conceder acceso a archivos",
                    onClick = onOpenManageAllFiles,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Amber,
                )
            }
            RaisedOutlinedButton(
                text = if (gushangNeedsRestart) {
                    "Reactivar (cerrar app por completo)"
                } else {
                    "Recomprobar activación"
                },
                onClick = onRefreshGushang,
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
