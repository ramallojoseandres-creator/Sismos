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
import com.mlh.skinanalyzer.analysis.ReportGenerator
import com.mlh.skinanalyzer.analysis.SkinAnalysisResult
import com.mlh.skinanalyzer.analysis.SkinAnalyzer
import com.mlh.skinanalyzer.data.AnalysisSession
import com.mlh.skinanalyzer.data.AppDatabase
import com.mlh.skinanalyzer.data.Patient
import com.mlh.skinanalyzer.hardware.Mj008Hardware
import com.mlh.skinanalyzer.hardware.SerialLightController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val patientsDao = db.patientDao()
    private val sessionsDao = db.sessionDao()
    private val gson = Gson()

    var patients by mutableStateOf<List<Patient>>(emptyList())
        private set
    var hardwareStatus by mutableStateOf("Comprobando MJ-008…")
        private set
    var mj008Detection by mutableStateOf<Mj008Hardware.Detection?>(null)
        private set
    var analyzing by mutableStateOf(false)
        private set
    var lastResult by mutableStateOf<SkinAnalysisResult?>(null)
        private set

    val lightController = SerialLightController()

    init {
        viewModelScope.launch {
            runCatching {
                patientsDao.observeAll().collectLatest { patients = it }
            }.onFailure { Log.e("MLH", "patients flow", it) }
        }
        refreshHardware()
    }

    fun refreshHardware() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val detection = Mj008Hardware.detect(getApplication())
                mj008Detection = detection
                lightController.setCameraVariant(detection.cameraVariant)
                val ok = lightController.open()
                hardwareStatus = if (ok) {
                    "MJ-008 listo · LED ${if (lightController.usingLegacyBinary) "legacy 9600" else "115200"} · cámara ${detection.cameraVariant.name}"
                } else {
                    detection.summary + " · " +
                        (lightController.lastError ?: "LED no conectado; captura disponible")
                }
            }.onFailure {
                Log.e("MLH", "refreshHardware", it)
                hardwareStatus = "MJ-008: hardware no disponible (${it.message})"
            }
        }
    }

    fun savePatient(patient: Patient, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) { patientsDao.upsert(patient) }
            onDone(if (patient.id == 0L) id else patient.id)
        }
    }

    fun deletePatient(patient: Patient) {
        viewModelScope.launch(Dispatchers.IO) { patientsDao.delete(patient) }
    }

    fun observeSessions(patientId: Long) = sessionsDao.observeForPatient(patientId)

    suspend fun getSession(id: Long) = sessionsDao.getById(id)
    suspend fun getPatient(id: Long) = patientsDao.getById(id)

    fun runAnalysis(
        patientId: Long,
        imagePaths: Map<String, String>,
        moisture: Float?,
        onDone: (Long) -> Unit,
    ) {
        viewModelScope.launch {
            analyzing = true
            try {
                val patient = withContext(Dispatchers.IO) { patientsDao.getById(patientId) } ?: return@launch
                val bitmaps = withContext(Dispatchers.IO) {
                    val map = LinkedHashMap<String, Bitmap>()
                    imagePaths.forEach { (key, path) ->
                        decodeBitmap(path)?.let { map[key] = it }
                    }
                    // Derive Blue/Brown/Red if missing
                    val derived = SkinAnalyzer.deriveSpectralMaps(
                        white = map["White"],
                        uv = map["UV"],
                        woods = map["Wood's"],
                    )
                    derived.forEach { (k, bmp) ->
                        if (!map.containsKey(k)) {
                            val out = File(getApplication<Application>().cacheDir, "derived_${k}_${System.currentTimeMillis()}.jpg")
                            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                            map[k] = bmp
                            (imagePaths as? MutableMap)?.put(k, out.absolutePath)
                        }
                    }
                    map to imagePaths.toMutableMap().also { m ->
                        derived.forEach { (k, bmp) ->
                            if (!m.containsKey(k)) {
                                val out = File(getApplication<Application>().cacheDir, "derived_${k}_${System.currentTimeMillis()}.jpg")
                                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                                m[k] = out.absolutePath
                            }
                        }
                    }
                }
                val result = withContext(Dispatchers.Default) {
                    SkinAnalyzer.analyze(bitmaps.first, patient.age, moisture)
                }
                lastResult = result
                val session = AnalysisSession(
                    patientId = patientId,
                    skinType = result.skinType,
                    skinAge = result.skinAge,
                    overview = result.overview,
                    metricsJson = gson.toJson(result),
                    imagePathsJson = gson.toJson(bitmaps.second),
                    recommendations = result.metrics
                        .sortedByDescending { it.score }
                        .take(3)
                        .joinToString("\n") { "• ${it.name}: ${it.recommendation}" },
                    moisturePercent = moisture,
                )
                val sid = withContext(Dispatchers.IO) { sessionsDao.insert(session) }
                onDone(sid)
            } finally {
                analyzing = false
            }
        }
    }

    fun buildSharePayload(patient: Patient, result: SkinAnalysisResult, moisture: Float?, time: Long): Pair<String, File> {
        val text = ReportGenerator.buildTextReport(patient, result, moisture, time)
        val pdf = ReportGenerator.writePdf(getApplication(), patient, result, moisture, time)
        return text to pdf
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
