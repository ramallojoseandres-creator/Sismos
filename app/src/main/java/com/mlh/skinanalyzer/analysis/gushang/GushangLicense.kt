package com.mlh.skinanalyzer.analysis.gushang

import android.content.Context
import android.os.Environment
import android.util.Log
import com.gushang.skindetect.JniInterface
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Licencia offline Gushang / SkinDetect.
 *
 * El .so lee `/sdcard/skindetect` (equiv. `/storage/emulated/0/skindetect`).
 * Archivo: `licence` (32 bytes en equipos verificados).
 * OEM register(): **0 = activado** (única fuente de verdad).
 *
 * Las comprobaciones Java de exists/canRead son solo diagnóstico tras fallo —
 * no bloquean la llamada a [JniInterface.register].
 */
object GushangLicense {
    private const val TAG = "GushangLicense"
    const val SKINDETECT_DIR = "/storage/emulated/0/skindetect"
    private const val LICENCE_FILE = "licence"
    /** Original de fábrica en algunos equipos MJ-008. */
    private const val FACTORY_LICENCE = "/storage/emulated/0/licence"

    private val registerAttempted = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val lastCode = AtomicInteger(-1)
    @Volatile var lastMessage: String = "Licencia no comprobada"
        private set
    @Volatile var needsAppRestart: Boolean = false
        private set

    val isActivated: Boolean get() = registered.get()
    val registerCode: Int get() = lastCode.get()

    fun skindetectDir(): File = File(SKINDETECT_DIR)
    fun licenceFile(): File = File(skindetectDir(), LICENCE_FILE)

    fun skindetectReadable(): Boolean {
        val f = licenceFile()
        return f.exists() && f.canRead()
    }

    fun diagnose(): String = buildString {
        appendLine("Gushang SkinDetect")
        appendLine("activated=$isActivated registerCode=$registerCode")
        appendLine("message=$lastMessage")
        appendLine("needsAppRestart=$needsAppRestart")
        appendLine("nativeDepsReady=${NativeLibraryLoader.depsReady} ${NativeLibraryLoader.lastError}")
        appendLine("allFilesAccess=${isAllFilesAccessGranted()}")
        val dir = skindetectDir()
        val file = licenceFile()
        val factory = File(FACTORY_LICENCE)
        appendLine("dir=$SKINDETECT_DIR exists=${dir.exists()} readable=${dir.canRead()}")
        appendLine(
            "licence=${file.absolutePath} exists=${file.exists()} " +
                "readable=${file.canRead()} bytes=${if (file.exists()) file.length() else -1}",
        )
        appendLine(
            "factory=$FACTORY_LICENCE exists=${factory.exists()} " +
                "bytes=${if (factory.exists()) factory.length() else -1}",
        )
        if (dir.isDirectory) {
            dir.listFiles()?.sortedBy { it.name }?.take(30)?.forEach {
                appendLine("  ${it.name} (${it.length()} bytes)")
            }
        }
    }

    private fun isAllFilesAccessGranted(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    /** Copia el serial de fábrica a skindetect/ si falta o difiere de tamaño. */
    private fun ensureLicenceCopiedFromFactory() {
        val origen = File(FACTORY_LICENCE)
        val destino = licenceFile()
        if (!origen.exists() || origen.length() <= 0L) return
        if (destino.exists() && destino.length() == origen.length()) return
        runCatching {
            destino.parentFile?.mkdirs()
            origen.copyTo(destino, overwrite = true)
            destino.setReadable(true, false)
            Log.i(TAG, "Licencia recopiada: ${destino.length()} bytes → ${destino.absolutePath}")
        }.onFailure {
            Log.w(TAG, "No se pudo copiar licencia de fábrica: ${it.message}")
        }
    }

    /** Texto de ayuda solo cuando register ya falló. */
    private fun buildDiagnostic(code: Int): String {
        val f = licenceFile()
        return buildString {
            append("register devolvió $code. ")
            append("Archivo: exists=${f.exists()} ")
            append("size=${if (f.exists()) f.length() else 0} ")
            append("canRead=${f.canRead()} · ")
            append("MANAGE_EXTERNAL_STORAGE=${isAllFilesAccessGranted()}")
        }
    }

    /**
     * Una sola llamada a register() por proceso.
     * No usa exists/canRead como barrera previa.
     */
    fun ensureRegistered(context: Context): Boolean {
        if (registered.get()) return true
        if (registerAttempted.get()) {
            needsAppRestart = true
            if (!lastMessage.contains("cerrar", ignoreCase = true)) {
                lastMessage = "$lastMessage · ${retryHint()}"
            }
            return false
        }

        if (!NativeLibraryLoader.preloadDeps()) {
            lastMessage = "Nativas incompletas: ${NativeLibraryLoader.lastError}. " +
                "Cierre la app por completo y reinstale el APK."
            needsAppRestart = true
            Log.e(TAG, lastMessage)
            return false
        }

        ensureLicenceCopiedFromFactory()

        val file = licenceFile()
        Log.i(
            TAG,
            "pre-register licence exists=${file.exists()} " +
                "canRead=${file.canRead()} bytes=${if (file.exists()) file.length() else -1} " +
                "allFiles=${isAllFilesAccessGranted()}",
        )

        if (!registerAttempted.compareAndSet(false, true)) return registered.get()

        return try {
            listOf("pyramidbox.nb", "facekeypoints.nb", "maskclassifier.nb").forEach { name ->
                runCatching { context.assets.open(name).close() }
                    .onFailure { Log.w(TAG, "asset missing: $name") }
            }
            val token = UUID.randomUUID().toString().replace("-", "")
            val code = JniInterface.register(token)
            lastCode.set(code)
            // OEM: 0 = 激活成功
            val ok = code == 0
            registered.set(ok)
            lastMessage = if (ok) {
                "Licencia Gushang activa (register=0, licence=" +
                    "${if (file.exists()) file.length() else "?"} bytes)"
            } else {
                "El motor rechazó la licencia. ${buildDiagnostic(code)}"
            }
            Log.i(TAG, lastMessage)
            ok
        } catch (e: UnsatisfiedLinkError) {
            needsAppRestart = true
            lastMessage = "libSkinDetect no cargó: ${e.message}. Cierre y reabra la app."
            Log.e(TAG, "register lanzó excepción", e)
            false
        } catch (e: NoClassDefFoundError) {
            needsAppRestart = true
            lastMessage =
                "JniInterface falló al iniciar (clase marcada). " +
                    "Cierre la app por completo y vuelva a abrirla. ${e.message}"
            Log.e(TAG, "register lanzó excepción", e)
            false
        } catch (t: Throwable) {
            needsAppRestart = true
            lastMessage = "El motor nativo no respondió: ${t.message}. ${retryHint()}"
            Log.e(TAG, "register lanzó excepción", t)
            false
        }
    }

    /** Solo útil tras reinicio de proceso; documenta eso en UI. */
    fun retryHint(): String =
        "Para reintentar la activación hay que cerrar la app por completo " +
            "(quitar de recientes) y abrirla de nuevo."
}
