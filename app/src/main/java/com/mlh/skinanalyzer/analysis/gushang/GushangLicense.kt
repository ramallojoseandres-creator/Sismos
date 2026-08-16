package com.mlh.skinanalyzer.analysis.gushang

import android.content.Context
import android.util.Log
import com.gushang.skindetect.JniInterface
import java.util.Random

/**
 * Activación Gushang SkinDetect.
 *
 * register(String) espera un serial de **32 dígitos** con desafío de tiempo
 * (reconstruido del .so). UUID u otras cadenas fallan siempre.
 * OEM: **register == 0 → activado**.
 *
 * Algoritmo de [generateSerialNumber] — no alterar.
 */
object GushangLicense {
    private const val TAG = "GushangLicense"

    @Volatile private var ready: Boolean? = null

    @Volatile var lastMessage: String = "Licencia no comprobada"
        private set
    @Volatile var needsAppRestart: Boolean = false
        private set
    @Volatile var userFacingMessage: String = "Comprobando activación del equipo…"
        private set
    @Volatile var registerCode: Int = -1
        private set

    val isActivated: Boolean get() = ready == true

    /**
     * Serial de 32 dígitos que espera el motor.
     * 32 dígitos aleatorios en dos mitades de 16. En cada mitad se inserta un
     * dígito del epoch en la posición igual a su propio valor; luego cada mitad
     * se invierte. El .so deshace esto leyendo los índices (15 - dígito) y
     * (31 - dígito).
     */
    fun generateSerialNumber(): String {
        val r = Random()
        val epoch = (System.currentTimeMillis() / 1000L).toInt()
        val ts = epoch.toString() // 10 dígitos
        val sb = StringBuilder(32)
        for (i in 0 until 32) sb.append(r.nextInt(10))
        val random32 = sb.toString()
        val first = StringBuilder(random32.substring(0, 16))
        val second = StringBuilder(random32.substring(16, 32))
        val tsFirst = ts.substring(0, 5)
        val tsSecond = ts.substring(5, 10)
        for (i in 0 until 5) {
            val c = tsFirst[i]
            first.setCharAt(Character.getNumericValue(c), c)
        }
        for (i in 0 until 5) {
            val c = tsSecond[i]
            second.setCharAt(Character.getNumericValue(c), c)
        }
        return first.reverse().toString() + second.reverse().toString()
    }

    @Synchronized
    fun ensureRegistered(context: Context? = null): Boolean {
        ready?.let {
            userFacingMessage = if (it) "Equipo activado" else "El equipo necesita activarse"
            return it
        }

        // No tocar JniInterface si las nativas no cargaron (<clinit> marca la clase).
        if (!NativeLibraryLoader.preloadDeps()) {
            lastMessage = "Nativas incompletas: ${NativeLibraryLoader.lastError}"
            needsAppRestart = true
            userFacingMessage = "El equipo necesita activarse"
            ready = false
            Log.e(TAG, lastMessage)
            return false
        }

        context?.let { ctx ->
            listOf("pyramidbox.nb", "facekeypoints.nb", "maskclassifier.nb").forEach { name ->
                runCatching { ctx.assets.open(name).close() }
                    .onFailure { Log.w(TAG, "asset missing: $name") }
            }
        }

        val serial = generateSerialNumber()
        if (serial.length != 32 || !serial.all { it.isDigit() }) {
            Log.e(TAG, "Serial mal formado: len=${serial.length}")
            lastMessage = "Serial mal formado: len=${serial.length}"
            userFacingMessage = "El equipo necesita activarse"
            ready = false
            return false
        }

        val code = try {
            JniInterface.register(serial)
        } catch (t: Throwable) {
            Log.e(TAG, "El motor nativo no respondió", t)
            lastMessage = "El motor nativo no respondió: ${t.message}"
            needsAppRestart = true
            userFacingMessage = "El equipo necesita activarse"
            ready = false
            return false
        }

        registerCode = code
        val ok = (code == 0) // 0 = ÉXITO. No invertir.
        Log.i(TAG, "register -> $code · ${if (ok) "ACTIVADO" else "RECHAZADO"}")
        lastMessage = if (ok) {
            "register -> 0 · ACTIVADO"
        } else {
            "register -> $code · RECHAZADO"
        }
        userFacingMessage = if (ok) "Equipo activado" else "El equipo necesita activarse"
        if (!ok) needsAppRestart = true
        ready = ok
        return ok
    }

    @Synchronized
    fun reset() {
        ready = null
        needsAppRestart = false
        lastMessage = "Licencia no comprobada"
        userFacingMessage = "Comprobando activación del equipo…"
        registerCode = -1
    }

    fun diagnose(): String = buildString {
        appendLine("Gushang SkinDetect")
        appendLine("activated=$isActivated registerCode=$registerCode")
        appendLine("message=$lastMessage")
        appendLine("needsAppRestart=$needsAppRestart")
        appendLine("nativeDepsReady=${NativeLibraryLoader.depsReady} ${NativeLibraryLoader.lastError}")
    }

    fun retryHint(): String =
        "Cierre la app por completo (quitar de recientes) y ábrala de nuevo."

    /** Compat: ya no se usa el archivo licence para activar. */
    fun skindetectReadable(): Boolean = isActivated
}
