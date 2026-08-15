package com.mlh.skinanalyzer.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.mlh.skinanalyzer.data.PatientAge
import com.mlh.skinanalyzer.data.PatientSex
import com.mlh.skinanalyzer.data.PendingPatientImport
import com.mlh.skinanalyzer.data.PhoneNormalizer
import fi.iki.elonen.NanoHTTPD
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

/**
 * Servidor HTTP local (solo WiFi) para cargar fichas desde iPhone/PC.
 * Vive únicamente mientras la pantalla de importación está abierta.
 */
class PatientWifiServer(
    private val context: Context,
    private val pin: String,
    private val onBatchReceived: (List<PendingPatientImport>) -> Unit,
) : NanoHTTPD(PORT) {

    private val gson = Gson()
    private val sessionQueue = CopyOnWriteArrayList<PendingPatientImport>()

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
        sessionQueue.clear()
    }

    fun pendingSnapshot(): List<PendingPatientImport> = sessionQueue.toList()

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.substringBefore('?')
        return when {
            session.method == Method.GET && (uri == "/" || uri.isEmpty()) ->
                assetResponse("wifi_patients/index.html", "text/html; charset=utf-8")
            session.method == Method.GET && uri == "/api/pendientes" ->
                jsonOk(mapOf("count" to sessionQueue.size, "items" to sessionQueue))
            session.method == Method.POST && uri == "/api/pacientes" ->
                handlePostPacientes(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404")
        }
    }

    private fun handlePostPacientes(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        return try {
            session.parseBody(body)
            val raw = body["postData"] ?: session.inputStream.bufferedReader().readText()
            val root = gson.fromJson(raw, JsonObject::class.java)
            val submittedPin = root.get("pin")?.asString.orEmpty()
            if (submittedPin != pin) {
                return jsonError(401, "PIN incorrecto")
            }
            val arr = root.getAsJsonArray("pacientes") ?: JsonArray()
            if (arr.size() == 0) return jsonError(400, "Sin fichas")
            val accepted = mutableListOf<PendingPatientImport>()
            for (el in arr) {
                val o = el.asJsonObject
                val first = o.get("firstName")?.asString?.trim().orEmpty()
                val last = o.get("lastName")?.asString?.trim().orEmpty()
                val birth = o.get("birthDate")?.asString?.trim().orEmpty()
                val phoneRaw = o.get("phone")?.asString?.trim().orEmpty()
                val sex = PatientSex.fromCode(o.get("sex")?.asString.orEmpty()).code
                val address = o.get("address")?.asString?.trim().orEmpty()
                val phone = PhoneNormalizer.normalize(phoneRaw)
                if (first.isBlank() || last.isBlank() || !PatientAge.isValidBirthDate(birth) || phone.length < 7) {
                    continue
                }
                accepted += PendingPatientImport(
                    firstName = first,
                    lastName = last,
                    birthDate = birth,
                    phoneRaw = phoneRaw,
                    phone = phone,
                    sex = sex,
                    address = address,
                )
            }
            if (accepted.isEmpty()) return jsonError(400, "Ninguna ficha válida")
            sessionQueue.addAll(accepted)
            onBatchReceived(accepted)
            jsonOk(mapOf("received" to accepted.size, "pendingTotal" to sessionQueue.size))
        } catch (e: Exception) {
            Log.e(TAG, "POST /api/pacientes", e)
            jsonError(500, e.message ?: "error")
        }
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
        private const val TAG = "PatientWifi"
        const val PORT = 8080

        fun generatePin(): String = "%04d".format(Random.nextInt(0, 10000))

        @Suppress("DEPRECATION")
        fun wifiIpv4(context: Context): String? {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val ip = wm.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            return Formatter.formatIpAddress(ip)
        }
    }
}
