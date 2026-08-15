package com.mlh.skinanalyzer.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.IndicatorPref
import com.mlh.skinanalyzer.hardware.CapturePrefs
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.Cream
import com.mlh.skinanalyzer.ui.theme.Ink
import com.mlh.skinanalyzer.ui.theme.Paper

private val indicatorLabels = mapOf(
    "sebum" to "Sebo / grasa",
    "pores" to "Poros dilatados",
    "pigmentation" to "Manchas",
    "wrinkles" to "Arrugas",
    "acne" to "Acné",
    "blackheads" to "Puntos negros",
    "dark_circles" to "Ojeras",
    "moisture" to "Hidratación",
    "texture" to "Textura",
    "sensitivity" to "Sensibilidad",
    "uv_spots" to "Manchas UV",
    "deep_pigment" to "Pigmento profundo",
    "deep_acne" to "Acné profundo",
    "collagen" to "Colágeno",
)

@Composable
fun SettingsScreen(
    clinic: ClinicProfile,
    indicators: List<IndicatorPref>,
    hardwareStatus: String,
    hardwareDiagnostics: String = "",
    demoMode: Boolean = false,
    onDemoModeChange: (Boolean) -> Unit = {},
    appVersion: String = "",
    gushangLicenseStatus: String = "",
    gushangNeedsRestart: Boolean = false,
    onBack: () -> Unit,
    onSaveClinic: (ClinicProfile) -> Unit,
    onToggleIndicator: (String, Boolean) -> Unit,
    onRefreshHardware: () -> Unit,
    onOpenDiagnostic: () -> Unit = {},
    onOpenLightTest: () -> Unit = {},
    onRefreshGushang: () -> Unit = {},
) {
    val context = LocalContext.current
    var name by remember(clinic) { mutableStateOf(clinic.clinicName) }
    var doctor by remember(clinic) { mutableStateOf(clinic.doctorName) }
    var specialty by remember(clinic) { mutableStateOf(clinic.specialty) }
    var phone by remember(clinic) { mutableStateOf(clinic.phone) }
    var email by remember(clinic) { mutableStateOf(clinic.email) }
    var whatsapp by remember(clinic) { mutableStateOf(clinic.whatsapp) }
    var address by remember(clinic) { mutableStateOf(clinic.address) }
    var footer by remember(clinic) { mutableStateOf(clinic.footerNote) }
    var saved by remember { mutableStateOf(false) }

    var rotation by remember { mutableIntStateOf(CapturePrefs.rotationDeg(context)) }
    var mirror by remember { mutableStateOf(CapturePrefs.mirrorHorizontal(context)) }
    var settleFirst by remember { mutableLongStateOf(CapturePrefs.settleFirstMs(context)) }
    var settleBetween by remember { mutableLongStateOf(CapturePrefs.settleBetweenMs(context)) }
    var settleAfter by remember { mutableLongStateOf(CapturePrefs.settleAfterMs(context)) }
    var preFirst by remember { mutableLongStateOf(CapturePrefs.preFirstMs(context)) }
    var captureSaved by remember { mutableStateOf(false) }

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
            Text("Ajustes · offline", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Sin login ni servidores chinos. Pacientes, informes y catálogo viven en esta tablet (Room).",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(4.dp))
            Text("Consultorio", style = MaterialTheme.typography.titleLarge, color = Accent)
            Field("Nombre del consultorio", name) { name = it }
            Field("Doctora / profesional", doctor) { doctor = it }
            Field("Especialidad", specialty) { specialty = it }
            Field("Teléfono", phone) { phone = it }
            Field("WhatsApp", whatsapp) { whatsapp = it }
            Field("Email", email) { email = it }
            Field("Dirección", address) { address = it }
            Field("Pie de informe", footer) { footer = it }
            Button(
                onClick = {
                    onSaveClinic(
                        clinic.copy(
                            clinicName = name.trim(),
                            doctorName = doctor.trim(),
                            specialty = specialty.trim(),
                            phone = phone.trim(),
                            email = email.trim(),
                            whatsapp = whatsapp.trim(),
                            address = address.trim(),
                            footerNote = footer.trim(),
                        ),
                    )
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text(if (saved) "Guardado" else "Guardar consultorio") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Indicadores visibles en informes", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Equivalente a la configuración de indicadores del software OEM, sin nube.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            indicators.forEach { pref ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(4.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        indicatorLabels[pref.key] ?: pref.key,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Switch(
                        checked = pref.enabled,
                        onCheckedChange = { onToggleIndicator(pref.key, it) },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Prueba sin tablet (Demo)", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Solo desde Ajustes — nunca se activa por fallo de USB. " +
                    "Sirve para emulador/teléfono: pacientes → captura → informe sin MJ-008. " +
                    "Los informes Demo llevan marca «NO CLÍNICO» y no sustituyen a Gushang.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Modo Demo / Simulación", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (demoMode) "Captura simulada (sin analizador)" else "Captura real USB MJ-008",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.55f),
                    )
                }
                Switch(checked = demoMode, onCheckedChange = onDemoModeChange)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Cámara", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Rotación y espejo se aplican al JPEG guardado (lo que lee Gushang), " +
                    "no solo al preview. Valor inicial típico: 270 + espejo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            Text("Rotación", style = MaterialTheme.typography.bodyLarge)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 90, 180, 270).forEach { deg ->
                    FilterChip(
                        selected = rotation == deg,
                        onClick = {
                            rotation = deg
                            CapturePrefs.setRotationDeg(context, deg)
                            captureSaved = false
                        },
                        label = { Text("$deg°") },
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Cream, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Espejo horizontal", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Cámaras frontales UVC suelen necesitarlo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink.copy(alpha = 0.55f),
                    )
                }
                Switch(
                    checked = mirror,
                    onCheckedChange = {
                        mirror = it
                        CapturePrefs.setMirrorHorizontal(context, it)
                        captureSaved = false
                    },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Captura", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                "Tiempos por luz (~5 s). No bajar de 1500 ms entre encender LED y disparo.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.55f),
            )
            TimingField("Estabilización 1.ª luz (ms)", settleFirst) {
                settleFirst = it
                captureSaved = false
            }
            TimingField("Estabilización entre luces (ms)", settleBetween) {
                settleBetween = it
                captureSaved = false
            }
            TimingField("Espera tras disparo (ms)", settleAfter) {
                settleAfter = it
                captureSaved = false
            }
            TimingField("Antes del 1.er disparo (ms)", preFirst) {
                preFirst = it
                captureSaved = false
            }
            Button(
                onClick = {
                    CapturePrefs.setTimings(
                        context,
                        settleFirst,
                        settleBetween,
                        settleAfter,
                        preFirst,
                    )
                    captureSaved = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(4.dp),
            ) { Text(if (captureSaved) "Tiempos guardados" else "Guardar tiempos de captura") }
            OutlinedButton(
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
                shape = RoundedCornerShape(4.dp),
            ) { Text("Restaurar valores por defecto") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Hardware MJ-008", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(hardwareStatus, style = MaterialTheme.typography.bodyMedium, color = Ink.copy(alpha = 0.65f))
            Button(
                onClick = onRefreshHardware,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = !demoMode,
            ) { Text("Reconectar luces / USB") }
            OutlinedButton(
                onClick = onOpenDiagnostic,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            ) { Text("Diagnóstico USB / cámara (copiar)") }
            Button(
                onClick = onOpenLightTest,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                enabled = !demoMode,
            ) { Text("Prueba luces (etapa 3 · preview + 8 LEDs)") }
            Text(
                "No avance a captura/análisis hasta que el preview siga vivo al cambiar luces.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(8.dp))
            Text("Licencia Gushang", style = MaterialTheme.typography.titleLarge, color = Accent)
            Text(
                gushangLicenseStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.7f),
            )
            OutlinedButton(
                onClick = onRefreshGushang,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    if (gushangNeedsRestart) {
                        "Reintentar activación (cerrar app por completo)"
                    } else {
                        "Recomprobar licencia /sdcard/skindetect"
                    },
                )
            }
            if (gushangNeedsRestart) {
                Text(
                    "JniInterface falló en este proceso. Cierre la app por completo " +
                        "(quitar de recientes) y ábrala de nuevo; luego pulse de nuevo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.65f),
                )
            }
            if (hardwareDiagnostics.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Diagnóstico USB / serie", style = MaterialTheme.typography.titleLarge)
                Text(
                    hardwareDiagnostics,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.7f),
                )
                Text(
                    "Si hay 2 USB (Dual USB camera ON): la app elige la del analizador (USB3.0 / USB Camera). " +
                        "Acepte el permiso USB al capturar. Menú eng: Screen Rotation 270, Camera Rotation 0.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.copy(alpha = 0.5f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Sustituye: login, tienda en la nube, Aliyun OSS, ai.aiskin.vip y device.aiskin.vip.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.copy(alpha = 0.45f),
            )
            if (appVersion.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
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
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = label != "Pie de informe" && label != "Dirección",
        minLines = if (label == "Pie de informe") 2 else 1,
        shape = RoundedCornerShape(4.dp),
    )
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
        shape = RoundedCornerShape(4.dp),
    )
}
