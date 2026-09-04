package com.mlh.skinanalyzer.analysis.gushang

import android.util.Log
import com.gushang.skindetect.JniInterface
import java.util.Random

/**
 * Activación del motor Gushang SkinDetect.
 *
 * Reglas que impone libSkinDetect.so:
 *  1. El argumento de register() debe medir EXACTAMENTE 32 caracteres,
 *     todos dígitos.
 *  2. Codifica la hora epoch actual; el motor exige menos de 60 s de
 *     desfase, así que hay que generarlo justo antes de llamar.
 *  3. register() devuelve 0 = ÉXITO, -1 = FALLO.
 */
object GushangLicense {

    private const val TAG = "GushangLicense"

    @Volatile private var ready: Boolean? = null

    fun generateSerialNumber(): String {
        val r = Random()
        val epoch = (System.currentTimeMillis() / 1000L).toInt()
        val ts = epoch.toString()

        val sb = StringBuilder(32)
        for (i in 0 until 32) sb.append(r.nextInt(10))
        val random32 = sb.toString()

        val first  = StringBuilder(random32.substring(0, 16))
        val second = StringBuilder(random32.substring(16, 32))

        val tsFirst  = ts.substring(0, 5)
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
    fun ensureRegistered(): Boolean {
        ready?.let { return it }

        val serial = generateSerialNumber()

        if (serial.length != 32 || !serial.all { it.isDigit() }) {
            Log.e(TAG, "Serial mal formado: len=${serial.length}")
            ready = false
            return false
        }

        val code = try {
            JniInterface.register(serial)
        } catch (t: Throwable) {
            Log.e(TAG, "El motor nativo no respondió", t)
            ready = false
            return false
        }

        val ok = (code == 0)
        Log.i(TAG, "register -> $code · ${if (ok) "ACTIVADO" else "RECHAZADO"}")
        ready = ok
        return ok
    }

    @Synchronized
    fun reset() { ready = null }
}
