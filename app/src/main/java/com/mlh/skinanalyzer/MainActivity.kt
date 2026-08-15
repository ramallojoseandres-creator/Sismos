package com.mlh.skinanalyzer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mlh.skinanalyzer.analysis.gushang.GushangLicense
import com.mlh.skinanalyzer.ui.AppNav
import com.mlh.skinanalyzer.ui.theme.Accent
import com.mlh.skinanalyzer.ui.theme.MlhTheme
import com.mlh.skinanalyzer.ui.theme.Paper
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var showManageStorageDialog by mutableStateOf(false)
    private var needsAllFilesAccess by mutableStateOf(false)

    private val runtimePermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        Log.i("MLH", "runtime permissions: $result · skindetect=${GushangLicense.skindetectReadable()}")
        refreshStorageGate()
        if (needsAllFilesAccess) {
            showManageStorageDialog = true
        } else {
            reregisterLicense()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshStorageGate()
        Log.i(
            "MLH",
            "MANAGE_EXTERNAL_STORAGE return · manager=${isAllFilesAccessGranted()} · " +
                "skindetect=${GushangLicense.skindetectReadable()}",
        )
        if (isAllFilesAccessGranted()) {
            showManageStorageDialog = false
            reregisterLicense()
        } else {
            // Keep prompting — Android 11 cannot grant this silently.
            showManageStorageDialog = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("MLH", "Uncaught crash", e)
        }
        requestAllPermissionsAggressively()
        setContent {
            MlhTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = Paper,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (needsAllFilesAccess) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFB71C1C))
                                    .padding(12.dp),
                            ) {
                                Text(
                                    "Falta «Acceso a todos los archivos» — sin esto no se lee " +
                                        "/sdcard/skindetect y la licencia Gushang no activa.",
                                    color = Color.White,
                                )
                                Text(
                                    "Ajustes → Apps → MLH Skin → Acceso a todos los archivos → Activar",
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Button(
                                    onClick = { openManageAllFilesSettings() },
                                    modifier = Modifier.padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                ) { Text("Abrir ajustes ahora") }
                            }
                        }
                        AppNav()
                    }
                    if (showManageStorageDialog) {
                        AlertDialog(
                            onDismissRequest = { /* no cerrar sin decidir */ },
                            title = { Text("Permiso obligatorio para la licencia") },
                            text = {
                                Text(
                                    "En Android 11 no basta con declarar el permiso: hay que " +
                                        "activarlo a mano una vez.\n\n" +
                                        "Ruta: Ajustes → Apps → MLH Skin Analyzer → " +
                                        "Acceso a todos los archivos → Activar.\n\n" +
                                        "Sirve solo para leer la licencia en /sdcard/skindetect. " +
                                        "Nada se sube a internet.",
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { openManageAllFilesSettings() }) {
                                    Text("Ir a Ajustes")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { showManageStorageDialog = false },
                                ) { Text("Más tarde") }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStorageGate()
        if (isAllFilesAccessGranted()) {
            reregisterLicense()
        }
    }

    private fun isAllFilesAccessGranted(): Boolean =
        Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    private fun refreshStorageGate() {
        needsAllFilesAccess = Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()
    }

    private fun reregisterLicense() {
        thread(name = "gushang-reregister", isDaemon = true) {
            (application as MlhApp).refreshGushangLicense()
        }
    }

    /** Pide de golpe cámara + almacenamiento legible; luego fuerza all-files en API 30+. */
    private fun requestAllPermissionsAggressively() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed += Manifest.permission.CAMERA
        }
        if (Build.VERSION.SDK_INT <= 32) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
        if (Build.VERSION.SDK_INT <= 28) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        refreshStorageGate()
        if (needed.isNotEmpty()) {
            runtimePermissions.launch(needed.toTypedArray())
        } else if (needsAllFilesAccess) {
            showManageStorageDialog = true
        } else {
            reregisterLicense()
        }
    }

    private fun openManageAllFilesSettings() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        } catch (e: Exception) {
            Log.w("MLH", "fallback MANAGE_ALL_FILES settings", e)
            manageStorageLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
