package com.mlh.skinanalyzer.ui

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.ui.screens.CaptureScreen
import com.mlh.skinanalyzer.ui.screens.CompareScreen
import com.mlh.skinanalyzer.ui.screens.DiagnosticScreen
import com.mlh.skinanalyzer.ui.screens.HomeScreen
import com.mlh.skinanalyzer.ui.screens.LightTestScreen
import com.mlh.skinanalyzer.ui.screens.PatientFormScreen
import com.mlh.skinanalyzer.ui.screens.PatientsScreen
import com.mlh.skinanalyzer.ui.screens.ReportScreen
import com.mlh.skinanalyzer.ui.screens.SessionListScreen
import com.mlh.skinanalyzer.ui.screens.SettingsScreen
import com.mlh.skinanalyzer.ui.screens.WelcomeScreen
import com.mlh.skinanalyzer.ui.screens.WifiImportScreen

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val PATIENTS = "patients"
    const val PATIENT_FORM = "patient_form?id={id}"
    const val WIFI_IMPORT = "wifi_import"
    const val CAPTURE = "capture/{patientId}"
    const val REPORT = "report/{sessionId}"
    const val SESSIONS = "sessions/{patientId}"
    const val COMPARE = "compare/{patientId}"
    const val SETTINGS = "settings"
    const val DIAGNOSTIC = "diagnostic"
    const val LIGHT_TEST = "light_test"
}

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.userMessage) {
        vm.userMessage?.let { msg ->
            snackbar.showSnackbar(msg)
            vm.clearUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(8.dp)) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.WELCOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    clinicName = vm.clinic.doctorName,
                    onContinue = {
                        nav.navigate(Routes.HOME) {
                            popUpTo(Routes.WELCOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onNewAnalysis = { nav.navigate(Routes.PATIENTS) },
                    onPatients = { nav.navigate(Routes.PATIENTS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenSession = { id -> nav.navigate("report/$id") },
                    recentSessions = vm.recentSessions,
                    clinicName = vm.clinic.doctorName,
                    hardwareStatus = vm.hardwareStatus,
                    onRefreshHardware = { vm.refreshHardware() },
                    demoMode = vm.demoMode,
                    patientNameFor = { id -> vm.findPatientById(id)?.displayName },
                    appVersion = "${com.mlh.skinanalyzer.BuildConfig.VERSION_NAME} (${com.mlh.skinanalyzer.BuildConfig.VERSION_CODE})",
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    clinic = vm.clinic,
                    indicators = vm.indicatorPrefs,
                    hardwareStatus = vm.hardwareStatus,
                    hardwareDiagnostics = vm.hardwareDiagnostics,
                    demoMode = vm.demoMode,
                    onDemoModeChange = { vm.enableDemoMode(it) },
                    appVersion = "${com.mlh.skinanalyzer.BuildConfig.VERSION_NAME} (${com.mlh.skinanalyzer.BuildConfig.VERSION_CODE})",
                    onBack = { nav.popBackStack() },
                    onSaveClinic = { vm.saveClinic(it) },
                    onToggleIndicator = { key, enabled -> vm.setIndicatorEnabled(key, enabled) },
                    onRefreshHardware = { vm.refreshHardware() },
                    onOpenDiagnostic = { nav.navigate(Routes.DIAGNOSTIC) },
                    onOpenLightTest = { nav.navigate(Routes.LIGHT_TEST) },
                    gushangLicenseStatus = vm.gushangLicenseStatus,
                    gushangNeedsRestart = vm.gushangNeedsRestart,
                    onRefreshGushang = { vm.refreshGushangStatus() },
                )
            }
            composable(Routes.DIAGNOSTIC) {
                DiagnosticScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.LIGHT_TEST) {
                LightTestScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.WIFI_IMPORT) {
                WifiImportScreen(vm = vm, onBack = { nav.popBackStack() })
            }
            composable(Routes.PATIENTS) {
                PatientsScreen(
                    rows = vm.patientRows,
                    searchQuery = vm.searchQuery,
                    onSearch = { vm.updateSearchQuery(it) },
                    onBack = { nav.popBackStack() },
                    onAdd = { nav.navigate("patient_form?id=-1") },
                    onWifiImport = { nav.navigate(Routes.WIFI_IMPORT) },
                    onOpenProfile = { id -> nav.navigate("sessions/$id") },
                    onEdit = { id -> nav.navigate("patient_form?id=$id") },
                    onDelete = { vm.deletePatient(it) },
                )
            }
            composable(
                route = "patient_form?id={id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: -1L
                var existing by remember(id) { mutableStateOf<Patient?>(null) }
                LaunchedEffect(id) {
                    existing = if (id > 0) vm.getPatient(id) else null
                    vm.clearPhoneDuplicate()
                }
                PatientFormScreen(
                    existing = existing,
                    duplicateHint = vm.phoneDuplicate,
                    onBack = { nav.popBackStack() },
                    onOpenExisting = { existingId ->
                        nav.navigate("sessions/$existingId") {
                            popUpTo(Routes.PATIENTS)
                        }
                    },
                    onClearDuplicate = { vm.clearPhoneDuplicate() },
                    onCheckPhone = { raw -> vm.checkPhoneDuplicate(raw, excludeId = id.coerceAtLeast(0)) },
                    onSave = { patient ->
                        vm.savePatient(patient) { newId ->
                            nav.navigate("sessions/$newId") {
                                popUpTo(Routes.PATIENTS)
                                launchSingleTop = true
                            }
                        }
                    },
                )
            }
            composable(
                route = Routes.CAPTURE,
                arguments = listOf(navArgument("patientId") { type = NavType.LongType }),
            ) { entry ->
                val patientId = entry.arguments!!.getLong("patientId")
                CaptureScreen(
                    patientId = patientId,
                    vm = vm,
                    controller = vm.lightController,
                    onBack = {
                        vm.releaseUvcSession()
                        nav.popBackStack()
                    },
                    onFinished = { paths, moisture, sessionDir ->
                        vm.runAnalysis(patientId, paths, moisture, sessionDir) { sessionId ->
                            vm.releaseUvcSession()
                            nav.navigate("report/$sessionId") {
                                popUpTo("sessions/$patientId")
                            }
                        }
                    },
                )
            }
            composable(
                route = Routes.REPORT,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
            ) { entry ->
                val sessionId = entry.arguments!!.getLong("sessionId")
                ReportScreen(
                    sessionId = sessionId,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                route = Routes.SESSIONS,
                arguments = listOf(navArgument("patientId") { type = NavType.LongType }),
            ) { entry ->
                val patientId = entry.arguments!!.getLong("patientId")
                SessionListScreen(
                    patientId = patientId,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                    onOpen = { sid -> nav.navigate("report/$sid") },
                    onNew = {
                        vm.openCapture(patientId) { id ->
                            Log.i("MLH", "navigate capture/$id from profile")
                            nav.navigate(
                                "capture/$id",
                                navOptions { launchSingleTop = true },
                            )
                        }
                    },
                    onCompare = { nav.navigate("compare/$patientId") },
                    onDelete = { vm.deleteSession(it) },
                )
            }
            composable(
                route = Routes.COMPARE,
                arguments = listOf(navArgument("patientId") { type = NavType.LongType }),
            ) { entry ->
                val patientId = entry.arguments!!.getLong("patientId")
                CompareScreen(
                    patientId = patientId,
                    vm = vm,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
