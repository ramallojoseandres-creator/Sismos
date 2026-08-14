package com.mlh.skinanalyzer.ui

import android.app.Application
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
import com.mlh.skinanalyzer.analysis.oem.OemAnalysisBundle
import com.mlh.skinanalyzer.analysis.oem.OemSkinEngine
import com.mlh.skinanalyzer.analysis.ReportGenerator
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinAnalyzer
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.data.AppDatabase
import com.mlh.skinanalyzer.data.CareGuide
import com.mlh.skinanalyzer.data.ClinicProfile
import com.mlh.skinanalyzer.data.IndicatorPref
import com.mlh.skinanalyzer.data.LocalCatalog
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.data.ProductRec
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.Mj008LightController
import kotlinx.coroutines.Dispatchers
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
    private val gson = Gson()

    var patients by mutableStateOf<List<Patient>>(emptyList())
        private set
    private var allPatients: List<Patient> = emptyList()
    var recentSessions by mutableStateOf<List<AnalysisSession>>(emptyList())
        private set
    var clinic by mutableStateOf(ClinicProfile())
        private set
    var indicatorPrefs by mutableStateOf<List<IndicatorPref>>(emptyList())
        private set
    var hardwareStatus by mutableStateOf("Comprobando MJ-008…")
        private set
    var hardwareDiagnostics by mutableStateOf("")
        private set
    var mj008Detection by mutableStateOf<Mj008Hardware.Detection?>(null)
        private set
    var analyzing by mutableStateOf(false)
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

    val lightController = Mj008LightController(app)

    fun clearUserMessage() {
        userMessage = null
    }

    fun findPatientById(id: Long): Patient? = allPatients.find { it.id == id }

    /** Resolve patient then invoke [onNavigate] on the main thread. */
    fun openCapture(patientId: Long, onNavigate: (Long) -> Unit) {
        if (patientId <= 0L) {
            userMessage = "Paciente sin ID válido. Edite la ficha y guarde de nuevo."
            Log.w("MLH", "openCapture rejected: invalid id=$patientId")
            return
        }
        findPatientById(patientId)?.let { cached ->
            capturePatient = cached
            Log.i("MLH", "openCapture cached id=$patientId name=${cached.name}")
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
                it.name.contains(q, ignoreCase = true) ||
                    it.phone.contains(q, ignoreCase = true) ||
                    it.email.contains(q, ignoreCase = true) ||
                    it.notes.contains(q, ignoreCase = true)
            }
        }
    }

    fun refreshHardware() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val detection = Mj008Hardware.detect(getApplication())
                mj008Detection = detection
                lightController.setCameraVariant(detection.cameraVariant)
                hardwareDiagnostics = detection.diagnostics
                // USB-XU and UVC share one device — never keep LED USB open when analyzer cam present.
                if (detection.usbXuCameraPresent) {
                    lightController.releaseUsbForUvc()
                }
                val ok = if (detection.usbXuCameraPresent) {
                    lightController.openSerialOnly()
                } else {
                    lightController.open()
                }
                hardwareStatus = if (ok) {
                    buildString {
                        append("MJ-008 listo · LED ${lightController.backendLabel}")
                        append(" · cámara ${detection.cameraVariant.name}")
                        if (detection.usbCameras.isNotEmpty()) {
                            append(" · USB3.0 detectada")
                            append(" · abra Captura y acepte permiso USB")
                        } else {
                            append(" · sin USB cámara")
                        }
                        append(" · sin nube")
                    }
                } else {
                    buildString {
                        append(detection.summary)
                        append(" · ")
                        append(lightController.lastError ?: "LED no conectado")
                        if (detection.usbCameras.isNotEmpty()) {
                            append(" · USB3.0 detectada — Captura + permiso USB")
                        }
                    }
                }
                Log.i("MLH", "HW diagnostics:\n${detection.diagnostics}")
            }.onFailure {
                Log.e("MLH", "refreshHardware", it)
                hardwareStatus = "MJ-008: hardware no disponible (${it.message})"
                hardwareDiagnostics = it.stackTraceToString().take(800)
            }
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
            val now = System.currentTimeMillis()
            val toSave = patient.copy(
                updatedAt = now,
                createdAt = if (patient.id == 0L) now else patient.createdAt,
            )
            val id = withContext(Dispatchers.IO) { patientsDao.upsert(toSave) }
            onDone(if (patient.id == 0L) id else patient.id)
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
            val oemEngine = OemSkinEngine(getApplication())
            try {
                val patient = withContext(Dispatchers.IO) { patientsDao.getById(patientId) } ?: return@launch
                var oemBundle: OemAnalysisBundle? = null
                var result: SkinAnalysisResult
                val pathsOut: Map<String, String>

                if (oemEngine.canAnalyze(sessionDir)) {
                    oemBundle = withContext(Dispatchers.Default) {
                        oemEngine.analyze(sessionDir, patient.age)
                    }
                }
                if (oemBundle != null) {
                    result = filterMetrics(oemEngine.toSkinAnalysisResult(oemBundle, patient.age))
                    pathsOut = imagePaths
                } else {
                    val bitmaps = withContext(Dispatchers.IO) {
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
                        map to imagePaths.toMutableMap().also { m ->
                            derived.forEach { (k, bmp) ->
                                if (!m.containsKey(k)) {
                                    val                                     out = File(
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
                    }
                    result = filterMetrics(
                        withContext(Dispatchers.Default) {
                            SkinAnalyzer.analyze(bitmaps.first, patient.age, moisture)
                        },
                    )
                    pathsOut = bitmaps.second
                }

                lastResult = result
                val session = AnalysisSession(
                    patientId = patientId,
                    skinType = result.skinType,
                    skinAge = result.skinAge,
                    overview = result.overview,
                    metricsJson = gson.toJson(result),
                    imagePathsJson = gson.toJson(pathsOut),
                    recommendations = result.metrics
                        .sortedByDescending { it.score }
                        .take(3)
                        .joinToString("\n") { "• ${it.name}: ${it.recommendation}" },
                    moisturePercent = moisture,
                    facialRatioJson = oemBundle?.facialRatioJson?.takeIf { it.isNotBlank() }
                        ?: gson.toJson(
                            result.facial ?: FacialProportionAnalyzer.analyze(
                                decodeBitmap(imagePaths["White"] ?: imagePaths.values.firstOrNull().orEmpty()),
                            ),
                        ),
                    oemIndicatorsJson = oemBundle?.let { gson.toJson(it.indicators) } ?: "",
                    sessionDir = sessionDir,
                )
                val sid = withContext(Dispatchers.IO) { sessionsDao.insert(session) }
                onDone(sid)
            } finally {
                oemEngine.close()
                analyzing = false
            }
        }
    }

    suspend fun buildSharePayload(
        patient: Patient,
        result: SkinAnalysisResult,
        moisture: Float?,
        time: Long,
    ): Pair<String, File> {
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
        return text to pdf
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
        lightController.close()
        super.onCleared()
    }
}
