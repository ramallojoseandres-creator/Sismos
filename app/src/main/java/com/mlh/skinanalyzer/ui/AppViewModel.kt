package com.mlh.skinanalyzer.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.mlh.skinanalyzer.analysis.FacialProportionAnalyzer
import com.mlh.skinanalyzer.analysis.HtmlReportExporter
import com.mlh.skinanalyzer.analysis.gushang.GushangLicense
import com.mlh.skinanalyzer.analysis.gushang.GushangSkinEngine
import com.mlh.skinanalyzer.analysis.ReportGenerator
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinAnalyzer
import com.mlh.skinanalyzer.MlhApp
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.data.AppDatabase
import com.mlh.skinanalyzer.data.CareGuide
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.IndicatorPref
import com.mlh.skinanalyzer.data.LocalCatalog
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.data.PhoneNormalizer
import com.mlh.skinanalyzer.data.PendingPatientImport
import com.mlh.skinanalyzer.data.ProductRec
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.Mj008LightController
import com.mlh.skinanalyzer.hardware.Mj008UvcSession
import com.mlh.skinanalyzer.ui.screens.PatientListRow
import com.mlh.skinanalyzer.ui.screens.WifiImportPreview
import com.mlh.skinanalyzer.wifi.PatientWifiServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val patientsDao = db.patientDao()
    private val sessionsDao = db.sessionDao()
    private val clinicDao = db.clinicDao()
    private val indicatorDao = db.indicatorPrefDao()
    private val careDao = db.careGuideDao()
    private val productDao = db.productDao()
    private val pendingImportDao = db.pendingImportDao()
    private val gson = Gson()

    var patients by mutableStateOf<List<Patient>>(emptyList())
        private set
    var patientRows by mutableStateOf<List<PatientListRow>>(emptyList())
        private set
    private var allPatients: List<Patient> = emptyList()
    var recentSessions by mutableStateOf<List<AnalysisSession>>(emptyList())
        private set
    var clinic by mutableStateOf(ClinicProfile())
        private set
    var indicatorPrefs by mutableStateOf<List<IndicatorPref>>(emptyList())
        private set
    var hardwareStatus by mutableStateOf("Comprobando equipo…")
        private set
    var hardwareDiagnostics by mutableStateOf("")
        private set
    var mj008Detection by mutableStateOf<Mj008Hardware.Detection?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var analyzingPhase by mutableStateOf("")
        private set
    var lastResult by mutableStateOf<SkinAnalysisResult?>(null)
        private set
    var searchQuery by mutableStateOf("")
        private set
    /** Pre-loaded patient for Captura — avoids blank screen / failed navigation. */
    var capturePatient by mutableStateOf<Patient?>(null)
        private set
    var userMessage by mutableStateOf<String?>(null)
        private set
    /** True while Captura screen owns the USB3.0 analyzer camera. */
    var isCaptureScreenActive by mutableStateOf(false)
        private set

    /**
     * Demo / Simulación: skip USB3.0 UVC + LEDs. Use phone/emulator camera
     * or synthetic frames so UI + informe can be tested without the tablet.
     */
    var demoMode by mutableStateOf(false)
        private set

    /** Gushang SkinDetect license status (updated from Application + refresh). */
    var gushangLicenseStatus by mutableStateOf("Comprobando activación…")
        private set
    var gushangUserMessage by mutableStateOf("Comprobando activación del equipo…")
        private set
    var gushangNeedsRestart by mutableStateOf(false)
        private set
    var gushangActivated by mutableStateOf(false)
        private set

    var phoneDuplicate by mutableStateOf<Patient?>(null)
        private set

    private val prefs = app.getSharedPreferences("mlh_prefs", Context.MODE_PRIVATE)

    val lightController = Mj008LightController(app)

    private var uvcSession: Mj008UvcSession? = null
    private var wifiServer: PatientWifiServer? = null
    var wifiPin by mutableStateOf<String?>(null)
        private set
    var wifiUrl by mutableStateOf<String?>(null)
        private set
    var wifiServerError by mutableStateOf<String?>(null)
        private set

    fun clearUserMessage() {
        userMessage = null
    }

    fun showUserMessage(msg: String) {
        userMessage = msg
    }

    fun findPatientById(id: Long): Patient? = allPatients.find { it.id == id }

    /** Resolve patient then invoke [onNavigate] on the main thread. */
    fun openCapture(patientId: Long, onNavigate: (Long) -> Unit) {
        if (patientId <= 0L) {
            userMessage = "Paciente sin ID válido. Edite la ficha y guarde de nuevo."
            Log.w("MLH", "openCapture rejected: invalid id=$patientId")
            return
        }
        releaseUvcSession()
        findPatientById(patientId)?.let { cached ->
            capturePatient = cached
            Log.i("MLH", "openCapture cached id=$patientId name=${cached.displayName}")
            onNavigate(patientId)
            return
        }
        viewModelScope.launch {
            val fromDb = withContext(Dispatchers.IO) { patientsDao.getById(patientId) }
            if (fromDb != null) {
                capturePatient = fromDb
                Log.i("MLH", "openCapture from DB id=$patientId")
                onNavigate(patientId)
            } else {
                userMessage = "No se encontró el paciente (id=$patientId)."
                Log.e("MLH", "openCapture: patient not found id=$patientId")
            }
        }
    }

    fun markCaptureActive(active: Boolean) {
        isCaptureScreenActive = active
    }

    /** Release UVC without blocking the caller (USB close can hang forever). */
    fun releaseUvcSession() {
        val previous = uvcSession ?: return
        uvcSession = null
        Thread({
            try {
                previous.release()
            } catch (e: Exception) {
                Log.e("MLH", "releaseUvcSession", e)
            }
        }, "mlh-uvc-release").apply { isDaemon = true; start() }
    }

    fun getUvcSession(): Mj008UvcSession? = uvcSession

    /**
     * Create a fresh UVC session without waiting on any USB I/O.
     *
     * Closing a prior USB-XU / UVC handle can block forever on MJ-008 firmware.
     * [withTimeout] cannot interrupt that, so Captura froze on “preparando…”.
     * Release happens on daemon threads; we never join them.
     */
    fun prepareUvcSession(activity: android.app.Activity): Mj008UvcSession {
        val previous = uvcSession
        uvcSession = null
        if (previous != null) {
            Thread({
                runCatching { previous.release() }
                    .onFailure { Log.w("MLH", "bg UVC release: ${it.message}") }
            }, "mlh-uvc-release").apply { isDaemon = true; start() }
        }
        // No USB-XU path left to release; UART closed if any.
        Thread({
            runCatching { lightController.close() }
                .onFailure { Log.w("MLH", "bg light close: ${it.message}") }
        }, "mlh-light-close").apply { isDaemon = true; start() }
        return Mj008UvcSession(activity).also { uvcSession = it }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { LocalCatalog.ensureSeeded(db) }
        }
        viewModelScope.launch {
            runCatching {
                patientsDao.observeAll().collectLatest {
                    allPatients = it
                    applySearch()
                }
            }.onFailure { Log.e("MLH", "patients flow", it) }
        }
        viewModelScope.launch {
            runCatching {
                sessionsDao.observeRecent().collectLatest { recentSessions = it }
            }.onFailure { Log.e("MLH", "recent sessions", it) }
        }
        viewModelScope.launch {
            runCatching {
                clinicDao.observe().collectLatest { clinic = it ?: ClinicProfile() }
            }
        }
        viewModelScope.launch {
            runCatching {
                indicatorDao.observeAll().collectLatest { indicatorPrefs = it }
            }
        }
        val savedDemo = prefs.getBoolean(KEY_DEMO_MODE, false)
        val autoDemo = !savedDemo && isProbablyEmulator() && !prefs.contains(KEY_DEMO_MODE)
        demoMode = if (autoDemo) {
            prefs.edit().putBoolean(KEY_DEMO_MODE, true).apply()
            true
        } else {
            savedDemo
        }
        refreshGushangStatus()
        refreshHardware()
    }

    fun refreshGushangStatus() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                if (app is MlhApp) {
                    app.refreshGushangLicense()
                } else {
                    GushangLicense.reset()
                    GushangLicense.ensureRegistered()
                }
            }
            // Compose state must be written on Main — never from Dispatchers.IO.
            gushangActivated = ok
            gushangLicenseStatus = if (ok) "register -> 0 · ACTIVADO" else "El equipo necesita activarse"
            gushangUserMessage = if (ok) "Equipo activado" else "El equipo necesita activarse"
            gushangNeedsRestart = !ok
        }
    }

    fun enableDemoMode(enabled: Boolean) {
        demoMode = enabled
        prefs.edit().putBoolean(KEY_DEMO_MODE, enabled).apply()
        refreshHardware()
    }

    fun updateSearchQuery(q: String) {
        searchQuery = q
        applySearch()
    }

    private fun applySearch() {
        val q = searchQuery.trim()
        patients = if (q.isBlank()) {
            allPatients
        } else {
            allPatients.filter {
                it.firstName.contains(q, ignoreCase = true) ||
                    it.lastName.contains(q, ignoreCase = true) ||
                    it.phone.contains(q, ignoreCase = true) ||
                    it.phoneRaw.contains(q, ignoreCase = true) ||
                    it.displayName.contains(q, ignoreCase = true)
            }
        }
        refreshPatientRows()
    }

    private fun refreshPatientRows() {
        viewModelScope.launch(Dispatchers.IO) {
            val rows = patients.map { p ->
                PatientListRow(
                    patient = p,
                    sessionCount = patientsDao.sessionCount(p.id),
                    lastSessionAt = patientsDao.lastSessionAt(p.id),
                )
            }
            withContext(Dispatchers.Main) { patientRows = rows }
        }
    }

    fun checkPhoneDuplicate(rawPhone: String, excludeId: Long = 0L) {
        viewModelScope.launch(Dispatchers.IO) {
            val normalized = PhoneNormalizer.normalize(rawPhone)
            if (normalized.length < 7) {
                withContext(Dispatchers.Main) { phoneDuplicate = null }
                return@launch
            }
            val found = patientsDao.findByPhone(normalized)
            withContext(Dispatchers.Main) {
                phoneDuplicate = found?.takeIf { it.id != excludeId }
            }
        }
    }

    fun clearPhoneDuplicate() {
        phoneDuplicate = null
    }

    fun refreshHardware() {
        if (isCaptureScreenActive) {
            Log.i("MLH", "refreshHardware skipped — Captura activa")
            return
        }
        if (demoMode) {
            hardwareStatus = "Modo de prueba activo"
            hardwareDiagnostics =
                "Demo: captura simulada sin el analizador.\n" +
                    "Desactive la simulación en Admin para la consulta real."
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val detection = Mj008Hardware.detect(getApplication())
                lightController.setCameraVariant(detection.cameraVariant)
                // NEVER open USB here — only USBMonitor in Captura owns the device.
                lightController.close()
                val status = when {
                    detection.usbCameras.isNotEmpty() || detection.usbXuCameraPresent ->
                        "Equipo conectado"
                    else ->
                        "No se detecta el equipo. Revise la conexión."
                }
                Log.i("MLH", "HW diagnostics (no USB open):\n${detection.diagnostics}")
                withContext(Dispatchers.Main) {
                    mj008Detection = detection
                    hardwareDiagnostics = detection.diagnostics
                    hardwareStatus = status
                }
            }.onFailure {
                Log.e("MLH", "refreshHardware", it)
                withContext(Dispatchers.Main) {
                    hardwareStatus = "No se detecta el equipo. Revise la conexión."
                    hardwareDiagnostics = it.stackTraceToString().take(800)
                }
            }
        }
    }

    companion object {
        private const val KEY_DEMO_MODE = "demo_mode"

        fun isProbablyEmulator(): Boolean {
            val fp = Build.FINGERPRINT.lowercase()
            val model = Build.MODEL.lowercase()
            val product = Build.PRODUCT.lowercase()
            val manufacturer = Build.MANUFACTURER.lowercase()
            return fp.contains("generic") ||
                fp.contains("emulator") ||
                model.contains("emulator") ||
                model.contains("android sdk") ||
                product.contains("sdk") ||
                product.contains("emulator") ||
                manufacturer.contains("genymotion") ||
                Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish")
        }
    }

    fun saveClinic(profile: ClinicProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            clinicDao.upsert(profile.copy(id = 1, updatedAt = System.currentTimeMillis()))
        }
    }

    fun setIndicatorEnabled(key: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = indicatorDao.listAll().firstOrNull { it.key == key }
            if (existing != null) {
                indicatorDao.upsert(existing.copy(enabled = enabled))
            }
        }
    }

    fun savePatient(patient: Patient, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val normalized = PhoneNormalizer.normalize(patient.phoneRaw.ifBlank { patient.phone })
            if (normalized.length < 7) {
                userMessage = "Teléfono obligatorio para guardar la ficha."
                return@launch
            }
            val existing = withContext(Dispatchers.IO) { patientsDao.findByPhone(normalized) }
            if (existing != null && existing.id != patient.id) {
                phoneDuplicate = existing
                userMessage = "Ya existe una ficha con ese teléfono: ${existing.displayName}"
                return@launch
            }
            val now = System.currentTimeMillis()
            val toSave = patient.copy(
                phone = normalized,
                phoneRaw = patient.phoneRaw.ifBlank { patient.phone },
                updatedAt = now,
                createdAt = if (patient.id == 0L) now else patient.createdAt,
            )
            val id = withContext(Dispatchers.IO) {
                if (toSave.id == 0L) patientsDao.insert(toSave) else {
                    patientsDao.update(toSave)
                    toSave.id
                }
            }
            phoneDuplicate = null
            onDone(id)
        }
    }

    fun startWifiImportSession() {
        stopWifiImportSession()
        val pin = PatientWifiServer.generatePin()
        wifiPin = pin
        val ip = PatientWifiServer.wifiIpv4(getApplication())
        if (ip == null) {
            wifiUrl = null
            wifiServerError = "Conecta la tablet a una red WiFi"
            return
        }
        wifiUrl = "http://$ip:${PatientWifiServer.PORT}"
        val server = PatientWifiServer(getApplication(), pin) { batch ->
            viewModelScope.launch(Dispatchers.IO) {
                pendingImportDao.insertAll(batch)
            }
        }
        wifiServer = server
        if (!server.startSafe()) {
            wifiServerError = server.lastError ?: "No se pudo abrir el puerto 8080"
            wifiServer = null
        } else {
            wifiServerError = null
        }
    }

    fun stopWifiImportSession() {
        wifiServer?.stopSafe()
        wifiServer = null
        wifiPin = null
        wifiUrl = null
        wifiServerError = null
    }

    fun observePendingImports(): Flow<List<PendingPatientImport>> = pendingImportDao.observeAll()

    fun previewPendingImports(items: List<PendingPatientImport>): List<WifiImportPreview> {
        // Sync lookup from cached patients; refine in confirm
        return items.map { item ->
            val existing = allPatients.firstOrNull { it.phone == item.phone }
            WifiImportPreview(item, existing?.displayName)
        }
    }

    fun confirmPendingImports(previews: List<WifiImportPreview>, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            var n = 0
            withContext(Dispatchers.IO) {
                previews.forEach { row ->
                    val item = row.item
                    val existing = patientsDao.findByPhone(item.phone)
                    val now = System.currentTimeMillis()
                    if (existing != null) {
                        patientsDao.update(
                            existing.copy(
                                firstName = item.firstName,
                                lastName = item.lastName,
                                birthDate = item.birthDate,
                                phoneRaw = item.phoneRaw,
                                sex = item.sex,
                                address = item.address,
                                updatedAt = now,
                            ),
                        )
                    } else {
                        patientsDao.insert(
                            Patient(
                                firstName = item.firstName,
                                lastName = item.lastName,
                                birthDate = item.birthDate,
                                phone = item.phone,
                                phoneRaw = item.phoneRaw,
                                sex = item.sex,
                                address = item.address,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                    }
                    n++
                }
                pendingImportDao.clear()
            }
            onDone(n)
        }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch(Dispatchers.IO) {
            patientsDao.deleteSessionsForPatient(patient.id)
            patientsDao.delete(patient)
        }
    }

    fun deleteSession(session: AnalysisSession) {
        viewModelScope.launch(Dispatchers.IO) { sessionsDao.delete(session) }
    }

    fun observeSessions(patientId: Long) = sessionsDao.observeForPatient(patientId)

    suspend fun listSessions(patientId: Long) = sessionsDao.listForPatient(patientId)

    suspend fun getSession(id: Long) = sessionsDao.getById(id)
    suspend fun getPatient(id: Long) = patientsDao.getById(id)

    suspend fun guidesFor(keys: List<String>): List<CareGuide> =
        if (keys.isEmpty()) emptyList() else careDao.forMetrics(keys)

    suspend fun productsFor(keys: List<String>): List<ProductRec> =
        if (keys.isEmpty()) emptyList() else productDao.forMetrics(keys)

    fun filterMetrics(result: SkinAnalysisResult): SkinAnalysisResult {
        val enabled = indicatorPrefs.filter { it.enabled }.map { it.key }.toSet()
        if (enabled.isEmpty()) return result
        val filtered = result.metrics.filter { it.key in enabled }
        return result.copy(metrics = filtered.ifEmpty { result.metrics })
    }

    fun runAnalysis(
        patientId: Long,
        imagePaths: Map<String, String>,
        moisture: Float?,
        sessionDir: String,
        onDone: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            analyzing = true
            analyzingPhase = "Preparando análisis…"
            val gushangEngine = GushangSkinEngine(getApplication())
            try {
                val patient = withContext(Dispatchers.IO) { patientsDao.getById(patientId) }
                if (patient == null) {
                    userMessage = "No se encontró el paciente para analizar."
                    return@launch
                }

                analyzingPhase = "Comprobando activación…"
                val licensed = withContext(Dispatchers.IO) {
                    GushangLicense.ensureRegistered()
                }
                gushangActivated = licensed
                gushangLicenseStatus = if (licensed) "register -> 0 · ACTIVADO" else "El equipo necesita activarse"
                gushangUserMessage = if (licensed) "Equipo activado" else "El equipo necesita activarse"
                gushangNeedsRestart = !licensed

                val result: SkinAnalysisResult
                val pathsOut: Map<String, String>

                when {
                    // Explicit Demo (Settings only) — never as silent USB fallback.
                    demoMode -> {
                        analyzingPhase = "Modo Demo — estimando indicadores…"
                        val bitmaps = loadHeuristicBitmaps(imagePaths)
                        val ageAt = patient.currentAge()
                        val demo = withContext(Dispatchers.Default) {
                            SkinAnalyzer.analyze(bitmaps.first, ageAt, moisture)
                        }
                        result = filterMetrics(
                            demo.copy(
                                overview = "MODO DEMO — cifras simuladas, no medidas por el motor licenciado.\n" +
                                    demo.overview,
                                analysisEngine = SkinAnalysisResult.ENGINE_DEMO,
                                isClinicalLicensed = false,
                            ),
                        )
                        pathsOut = bitmaps.second
                    }
                    !GushangLicense.ensureRegistered() -> {
                        userMessage = "El equipo necesita activarse"
                        Log.e("MLH", "Analysis blocked — license not activated")
                        return@launch
                    }
                    else -> {
                        analyzingPhase = "Analizando indicadores de piel…"
                        val ageAt = patient.currentAge()
                        val gushangResult = withContext(Dispatchers.Default) {
                            gushangEngine.analyze(sessionDir, ageAt)
                        }
                        if (gushangResult == null) {
                            userMessage =
                                "No se obtuvieron resultados. Revise que las fotos estén " +
                                    "bien orientadas y reintente la captura."
                            Log.e("MLH", "Gushang analyze returned null — hard stop")
                            return@launch
                        }
                        result = filterMetrics(gushangResult)
                        pathsOut = imagePaths
                    }
                }

                analyzingPhase = "Guardando sesión…"
                Log.i(
                    "MLH",
                    "Analysis engine=${result.analysisEngine} clinical=${result.isClinicalLicensed}",
                )

                lastResult = result
                val ageAt = patient.ageAt(System.currentTimeMillis())
                val session = AnalysisSession(
                    patientId = patientId,
                    skinType = result.skinType,
                    skinAge = result.skinAge,
                    ageAtAnalysis = ageAt,
                    overview = result.overview,
                    metricsJson = gson.toJson(result),
                    imagePathsJson = gson.toJson(pathsOut),
                    recommendations = result.metrics
                        .sortedByDescending { it.score }
                        .take(3)
                        .joinToString("\n") { "• ${it.name}: ${it.recommendation}" },
                    moisturePercent = moisture,
                    facialRatioJson = gson.toJson(
                        result.facial ?: FacialProportionAnalyzer.analyze(
                            decodeBitmap(imagePaths["White"] ?: imagePaths.values.firstOrNull().orEmpty()),
                        ),
                    ),
                    oemIndicatorsJson = "",
                    sessionDir = sessionDir,
                )
                val sid = withContext(Dispatchers.IO) { sessionsDao.insert(session) }
                onDone(sid)
            } catch (e: Exception) {
                Log.e("MLH", "runAnalysis failed", e)
                userMessage = "Error al analizar: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                gushangEngine.close()
                analyzing = false
                analyzingPhase = ""
            }
        }
    }

    private suspend fun loadHeuristicBitmaps(
        imagePaths: Map<String, String>,
    ): Pair<LinkedHashMap<String, Bitmap>, Map<String, String>> = withContext(Dispatchers.IO) {
        val map = LinkedHashMap<String, Bitmap>()
        imagePaths.forEach { (key, path) ->
            decodeBitmap(path)?.let { map[key] = it }
        }
        val derived = SkinAnalyzer.deriveSpectralMaps(
            white = map["White"],
            uv = map["UV"],
            woods = map["Wood's"],
        )
        map["Orange"]?.let { map.putIfAbsent("Brown", it) }
        map["Brown"]?.let { map.putIfAbsent("Orange", it) }
        derived.forEach { (k, bmp) ->
            if (!map.containsKey(k)) map[k] = bmp
        }
        val paths = imagePaths.toMutableMap().also { m ->
            derived.forEach { (k, bmp) ->
                if (!m.containsKey(k)) {
                    val out = File(
                        getApplication<Application>().filesDir,
                        "sessions/derived_${k}_${System.currentTimeMillis()}.jpg",
                    ).also { it.parentFile?.mkdirs() }
                    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    m[k] = out.absolutePath
                }
            }
            m["Orange"]?.let { m.putIfAbsent("Brown", it) }
            m["Brown"]?.let { m.putIfAbsent("Orange", it) }
        }
        map to paths
    }

    suspend fun buildSharePayload(
        patient: Patient,
        result: SkinAnalysisResult,
        moisture: Float?,
        time: Long,
        session: AnalysisSession? = null,
    ): Triple<String, File, File> {
        val keys = result.priorityKeys.ifEmpty {
            result.metrics.sortedByDescending { it.score }.take(3).map { it.key }
        }
        val guides = guidesFor(keys)
        val products = productsFor(keys)
        val profile = clinicDao.get() ?: clinic
        val filtered = filterMetrics(result)
        val text = ReportGenerator.buildTextReport(
            patient, filtered, moisture, time, profile, guides, products,
        )
        val pdf = ReportGenerator.writePdf(
            getApplication(), patient, filtered, moisture, time, profile, guides, products,
        )
        val htmlSession = session ?: AnalysisSession(
            patientId = patient.id,
            createdAt = time,
            skinType = filtered.skinType,
            skinAge = filtered.skinAge,
            ageAtAnalysis = patient.ageAt(time),
            overview = filtered.overview,
            recommendations = "",
        )
        val html = HtmlReportExporter.writeHtml(
            getApplication(), patient, htmlSession, filtered, profile,
        )
        return Triple(text, pdf, html)
    }

    /** Compare two sessions offline (OEM ComparisonHistory without cloud). */
    data class SessionCompare(
        val left: AnalysisSession,
        val right: AnalysisSession,
        val leftResult: SkinAnalysisResult?,
        val rightResult: SkinAnalysisResult?,
        val deltas: List<MetricDelta>,
    )

    data class MetricDelta(
        val key: String,
        val name: String,
        val leftScore: Float,
        val rightScore: Float,
        val delta: Float,
    )

    suspend fun compareSessions(leftId: Long, rightId: Long): SessionCompare? {
        val left = sessionsDao.getById(leftId) ?: return null
        val right = sessionsDao.getById(rightId) ?: return null
        val lr = runCatching {
            gson.fromJson(left.metricsJson, SkinAnalysisResult::class.java)
        }.getOrNull()
        val rr = runCatching {
            gson.fromJson(right.metricsJson, SkinAnalysisResult::class.java)
        }.getOrNull()
        val deltas = mutableListOf<MetricDelta>()
        if (lr != null && rr != null) {
            val rightMap = rr.metrics.associateBy { it.key }
            for (m in lr.metrics) {
                val other = rightMap[m.key] ?: continue
                deltas += MetricDelta(
                    key = m.key,
                    name = m.name,
                    leftScore = m.score,
                    rightScore = other.score,
                    delta = other.score - m.score,
                )
            }
        }
        return SessionCompare(left, right, lr, rr, deltas.sortedByDescending { kotlin.math.abs(it.delta) })
    }

    private fun decodeBitmap(path: String): Bitmap? {
        return try {
            if (path.startsWith("content:")) {
                val uri = Uri.parse(path)
                val cr = getApplication<Application>().contentResolver
                if (Build.VERSION.SDK_INT >= 28) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri))
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(cr, uri)
                }
            } else {
                BitmapFactory.decodeFile(path)
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onCleared() {
        releaseUvcSession()
        lightController.close()
        super.onCleared()
    }
}
