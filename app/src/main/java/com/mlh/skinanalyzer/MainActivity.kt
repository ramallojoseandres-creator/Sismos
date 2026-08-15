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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mlh.skinanalyzer.analysis.gushang.GushangLicense
import com.mlh.skinanalyzer.ui.AppNav
import com.mlh.skinanalyzer.ui.theme.MlhTheme
import com.mlh.skinanalyzer.ui.theme.Paper
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var showManageStorageDialog by mutableStateOf(false)

    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        Log.i(
            "MLH",
            "storage permission granted=$granted · skindetect=${GushangLicense.skindetectReadable()}",
        )
        maybeAskManageExternalStorage()
        reregisterLicense()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        Log.i(
            "MLH",
            "MANAGE_EXTERNAL_STORAGE=" +
                (Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()) +
                " · skindetect=${GushangLicense.skindetectReadable()}",
        )
        reregisterLicense()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            Log.e("MLH", "Uncaught crash", e)
        }
        requestStorageIfNeeded()
        setContent {
            MlhTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    color = Paper,
                ) {
                    AppNav()
                    if (showManageStorageDialog) {
                        AlertDialog(
                            onDismissRequest = { showManageStorageDialog = false },
                            title = { Text("Acceso a la licencia") },
                            text = {
                                Text(
                                    "La app necesita permiso de «Acceso a todos los archivos» " +
                                        "para leer la licencia Gushang en /sdcard/skindetect " +
                                        "(calibración del equipo). Sin ese acceso el motor " +
                                        "clínico no puede activarse. Los datos no se suben a internet.",
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showManageStorageDialog = false
                                        openManageAllFilesSettings()
                                    },
                                ) { Text("Conceder") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showManageStorageDialog = false }) {
                                    Text("Ahora no")
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
            reregisterLicense()
        }
    }

    private fun reregisterLicense() {
        thread(name = "gushang-reregister", isDaemon = true) {
            (application as MlhApp).refreshGushangLicense()
        }
    }

    private fun requestStorageIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT <= 32) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (Build.VERSION.SDK_INT <= 28 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed += Manifest.permission.WRITE_EXTERNAL_STORAGE
            }
        }
        if (needed.isNotEmpty()) {
            storagePermission.launch(needed.toTypedArray())
        } else {
            maybeAskManageExternalStorage()
        }
    }

    private fun maybeAskManageExternalStorage() {
        if (Build.VERSION.SDK_INT < 30) return
        if (Environment.isExternalStorageManager()) return
        showManageStorageDialog = true
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
