package com.mlh.skinanalyzer.wifi

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException

/**
 * Servidor HTTP local (solo WiFi) para revisar informes y editar recomendaciones desde PC.
 * Puerto distinto al de fichas (8080) para poder usarse en paralelo si hiciera falta.
 */
class ReportWifiServer(
    private val context: Context,
    private val pin: String,
    private val listReports: suspend () -> List<WifiReportSummary>,
    private val getReport: suspend (Long) -> WifiReportDetail?,
    private val saveRecommendations: suspend (Long, EditableRecommendations) -> Boolean,
    private val generateHtml: suspend (Long) -> File?,
    private val generatePdf: suspend (Long) -> File?,
) : NanoHTTPD(PORT) {

    private val gson = Gson()

    @Volatile var lastError: String? = null
        private set

    fun startSafe(): Boolean =
        try {
            start(SOCKET_READ_TIMEOUT, false)
            lastError = null
            true
        } catch (e: IOException) {
            lastError = e.message
            Log.e(TAG, "start failed", e)
            false
        }

    fun stopSafe() {
        runCatching { stop() }
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.substringBefore('?')
        return when {
            session.method == Method.GET && (uri == "/" || uri.isEmpty()) ->
                assetResponse("wifi_reports/index.html", "text/html; charset=utf-8")
            session.method == Method.POST && uri == "/api/informes" ->
                handleList(session)
            session.method == Method.POST && uri.startsWith("/api/informe/") && uri.endsWith("/recomendaciones") ->
                handleSave(session, uri)
            session.method == Method.GET && uri.startsWith("/api/informe/") && uri.endsWith("/html") ->
                handleDownload(session, uri, "html")
            session.method == Method.GET && uri.startsWith("/api/informe/") && uri.endsWith("/pdf") ->
                handleDownload(session, uri, "pdf")
            session.method == Method.GET && uri.matches(Regex("/api/informe/\\d+")) ->
                handleGetDetail(session, uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404")
        }
    }

    private fun handleList(session: IHTTPSession): Response {
        return try {
            val pinOk = readPinFromPost(session) ?: return jsonError(401, "PIN incorrecto")
            if (pinOk != pin) return jsonError(401, "PIN incorrecto")
            val items = runBlocking { listReports() }
            jsonOk(mapOf("count" to items.size, "items" to items))
        } catch (e: Exception) {
            Log.e(TAG, "POST /api/informes", e)
            jsonError(500, e.message ?: "error")
        }
    }

    private fun handleGetDetail(session: IHTTPSession, uri: String): Response {
        return try {
            val pinQuery = session.parameters["pin"]?.firstOrNull().orEmpty()
            if (pinQuery != pin) return jsonError(401, "PIN incorrecto")
            val id = uri.substringAfterLast('/').toLongOrNull() ?: return jsonError(400, "ID inválido")
            val detail = runBlocking { getReport(id) } ?: return jsonError(404, "Informe no encontrado")
            jsonOk(detail)
        } catch (e: Exception) {
            Log.e(TAG, "GET informe", e)
            jsonError(500, e.message ?: "error")
        }
    }

    private fun handleSave(session: IHTTPSession, uri: String): Response {
        return try {
            val id = uri.removePrefix("/api/informe/").removeSuffix("/recomendaciones").toLongOrNull()
                ?: return jsonError(400, "ID inválido")
            val body = HashMap<String, String>()
            session.parseBody(body)
            val raw = body["postData"] ?: session.inputStream.bufferedReader().readText()
            val root = gson.fromJson(raw, JsonObject::class.java)
            val submittedPin = root.get("pin")?.asString.orEmpty()
            if (submittedPin != pin) return jsonError(401, "PIN incorrecto")
            val editable = EditableRecommendations(
                medicamentos = root.get("medicamentos")?.asString.orEmpty(),
                rutinas = root.get("rutinas")?.asString.orEmpty(),
                observaciones = root.get("observaciones")?.asString.orEmpty(),
            )
            val ok = runBlocking { saveRecommendations(id, editable) }
            if (!ok) return jsonError(404, "Informe no encontrado")
            jsonOk(mapOf("saved" to true, "sessionId" to id))
        } catch (e: Exception) {
            Log.e(TAG, "POST recomendaciones", e)
            jsonError(500, e.message ?: "error")
        }
    }

    private fun handleDownload(session: IHTTPSession, uri: String, kind: String): Response {
        return try {
            val pinQuery = session.parameters["pin"]?.firstOrNull().orEmpty()
            if (pinQuery != pin) return jsonError(401, "PIN incorrecto")
            val id = uri.removePrefix("/api/informe/").removeSuffix("/$kind").toLongOrNull()
                ?: return jsonError(400, "ID inválido")
            val file = runBlocking {
                when (kind) {
                    "html" -> generateHtml(id)
                    else -> generatePdf(id)
                }
            } ?: return jsonError(404, "No se pudo generar el archivo")
            if (!file.exists()) return jsonError(404, "Archivo no encontrado")
            val mime = if (kind == "html") "text/html; charset=utf-8" else "application/pdf"
            val bytes = file.readBytes()
            val response = newFixedLengthResponse(Response.Status.OK, mime, bytes.inputStream(), bytes.size.toLong())
            response.addHeader("Content-Disposition", "attachment; filename=\"${file.name}\"")
            response
        } catch (e: Exception) {
            Log.e(TAG, "GET download $kind", e)
            jsonError(500, e.message ?: "error")
        }
    }

    private fun readPinFromPost(session: IHTTPSession): String? {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val raw = body["postData"] ?: session.inputStream.bufferedReader().readText()
        val root = gson.fromJson(raw, JsonObject::class.java)
        return root.get("pin")?.asString
    }

    private fun assetResponse(path: String, mime: String): Response {
        val bytes = context.assets.open(path).use { it.readBytes() }
        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            bytes.inputStream(),
            bytes.size.toLong(),
        )
    }

    private fun jsonOk(payload: Any): Response =
        newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            gson.toJson(payload),
        )

    private fun jsonError(code: Int, message: String): Response {
        val status = Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR
        return newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            gson.toJson(mapOf("error" to message)),
        )
    }

    companion object {
        private const val TAG = "ReportWifi"
        const val PORT = 8081
    }
}

data class WifiReportSummary(
    val id: Long,
    val patientName: String,
    val createdAt: Long,
    val skinAge: Int,
    val skinType: String,
    val hasDoctorNotes: Boolean,
)

data class WifiReportDetail(
    val id: Long,
    val patientName: String,
    val patientSex: String,
    val patientAge: Int,
    val createdAt: Long,
    val skinAge: Int,
    val skinType: String,
    val overview: String,
    val analysisEngine: String,
    val isClinicalLicensed: Boolean,
    val aiRecommendations: String,
    val medicamentos: String,
    val rutinas: String,
    val observaciones: String,
    val metrics: List<WifiMetricSummary>,
)

data class WifiMetricSummary(
    val name: String,
    val layer: String,
    val score: Float,
    val levelLabel: String,
    val levelValue: Int,
    val recommendation: String,
)
