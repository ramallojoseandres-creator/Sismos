package com.mlh.skinanalyzer.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mlh.skinanalyzer.ui.screens.CaptureScreen
import com.mlh.skinanalyzer.ui.screens.HomeScreen
import com.mlh.skinanalyzer.ui.screens.PatientFormScreen
import com.mlh.skinanalyzer.ui.screens.PatientsScreen
import com.mlh.skinanalyzer.ui.screens.ReportScreen
import com.mlh.skinanalyzer.ui.screens.SessionListScreen

object Routes {
    const val HOME = "home"
    const val PATIENTS = "patients"
    const val PATIENT_FORM = "patient_form?id={id}"
    const val CAPTURE = "capture/{patientId}"
    const val REPORT = "report/{sessionId}"
    const val SESSIONS = "sessions/{patientId}"
}

@Composable
fun AppNav(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onNewAnalysis = { nav.navigate("patient_form?id=-1") },
                onPatients = { nav.navigate(Routes.PATIENTS) },
                hardwareStatus = vm.hardwareStatus,
                onRefreshHardware = { vm.refreshHardware() },
            )
        }
        composable(Routes.PATIENTS) {
            PatientsScreen(
                patients = vm.patients,
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("patient_form?id=-1") },
                onOpen = { id -> nav.navigate("sessions/$id") },
                onAnalyze = { id -> nav.navigate("capture/$id") },
                onEdit = { id -> nav.navigate("patient_form?id=$id") },
                onDelete = { vm.deletePatient(it) },
            )
        }
        composable(
            route = "patient_form?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            PatientFormScreen(
                existing = if (id > 0) vm.patients.find { it.id == id } else null,
                onBack = { nav.popBackStack() },
                onSave = { patient, startCapture ->
                    vm.savePatient(patient) { newId ->
                        if (startCapture) nav.navigate("capture/$newId") {
                            popUpTo(Routes.HOME)
                        } else nav.popBackStack()
                    }
                },
            )
        }
        composable(
            route = Routes.CAPTURE,
            arguments = listOf(navArgument("patientId") { type = NavType.LongType }),
        ) { entry ->
            val patientId = entry.arguments!!.getLong("patientId")
            val patient = vm.patients.find { it.id == patientId }
            CaptureScreen(
                patient = patient,
                controller = vm.lightController,
                onBack = { nav.popBackStack() },
                onFinished = { paths, moisture ->
                    vm.runAnalysis(patientId, paths, moisture) { sessionId ->
                        nav.navigate("report/$sessionId") {
                            popUpTo(Routes.HOME)
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
                onBack = { nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
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
                onNew = { nav.navigate("capture/$patientId") },
            )
        }
    }
}
