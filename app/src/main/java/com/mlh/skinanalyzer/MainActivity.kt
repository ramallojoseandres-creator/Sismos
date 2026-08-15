package com.mlh.skinanalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.mlh.skinanalyzer.analysis.gushang.GushangLicense
import com.mlh.skinanalyzer.ui.AppNav
import com.mlh.skinanalyzer.ui.theme.MlhTheme
import com.mlh.skinanalyzer.ui.theme.Paper
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val storagePermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        Log.i(
            "MLH",
            "storage permission granted=$granted · skindetect=${GushangLicense.skindetectReadable()}",
        )
        thread(name = "gushang-reregister", isDaemon = true) {
            (application as MlhApp).refreshGushangLicense()
        }
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
                }
            }
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
        }
    }
}
