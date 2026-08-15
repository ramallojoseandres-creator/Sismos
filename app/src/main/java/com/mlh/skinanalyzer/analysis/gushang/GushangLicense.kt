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
 * OEM register(): **0 = activado**.
 *
 * [JniInterface] solo se toca si las nativas precargaron bien, y solo una vez
 * por proceso (si &lt;clinit&gt; falla, Android no permite reintentar sin reiniciar).
 */
object GushangLicense {
    private const val TAG = "GushangLicense"
    const val SKINDETECT_DIR = "/storage/emulated/0/skindetect"
    private const val LICENCE_FILE = "licence"

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
        appendLine("dir=$SKINDETECT_DIR exists=${dir.exists()} readable=${dir.canRead()}")
        appendLine(
            "licence=${file.absolutePath} exists=${file.exists()} " +
                "readable=${file.canRead()} bytes=${if (file.exists()) file.length() else -1}",
        )
        if (dir.isDirectory) {
            dir.listFiles()?.sortedBy { it.name }?.take(30)?.forEach {
                appendLine("  ${it.name} (${it.length()} bytes)")
            }
        }
    }

    private fun isAllFilesAccessGranted(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    /**
     * Diagnóstico separado (permiso / carpeta / archivo / lectura / motor).
     * Una sola llamada a register() por proceso.
     */
    fun ensureRegistered(context: Context): Boolean {
        if (registered.get()) return true
        if (registerAttempted.get()) {
            // Already tried this process — do not touch JniInterface again.
            return false
        }

        if (!NativeLibraryLoader.preloadDeps()) {
            lastMessage = "Nativas incompletas: ${NativeLibraryLoader.lastError}. " +
                "Cierre la app por completo y reinstale el APK."
            needsAppRestart = true
            Log.e(TAG, lastMessage)
            return false
        }

        val dir = skindetectDir()
        val file = licenceFile()
        when {
            !isAllFilesAccessGranted() -> {
                lastMessage =
                    "Falta permiso «Acceso a todos los archivos». " +
                        "Ajustes → Apps → MLH Skin → Acceso a todos los archivos."
                Log.w(TAG, lastMessage)
                return false
            }
            !dir.exists() -> {
                lastMessage = "No existe la carpeta /skindetect en el almacenamiento."
                Log.w(TAG, lastMessage)
                return false
            }
            !file.exists() -> {
                lastMessage = "Falta el archivo de licencia en /skindetect/licence."
                Log.w(TAG, lastMessage)
                return false
            }
            !file.canRead() -> {
                lastMessage = "El archivo licence existe pero no se puede leer."
                Log.w(TAG, lastMessage)
                return false
            }
        }

        Log.i(TAG, "licence found bytes=${file.length()} path=${file.absolutePath}")

        // Only one attempt — if <clinit> fails, class is dead for this process.
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
                "Licencia Gushang activa (register=0, licence=${file.length()} bytes)"
            } else {
                "El motor rechazó la licencia (register=$code). " +
                    "Archivo presente: ${file.length()} bytes."
            }
            Log.i(TAG, lastMessage)
            ok
        } catch (e: UnsatisfiedLinkError) {
            needsAppRestart = true
            lastMessage = "libSkinDetect no cargó: ${e.message}. Cierre y reabra la app."
            Log.e(TAG, lastMessage, e)
            false
        } catch (e: NoClassDefFoundError) {
            needsAppRestart = true
            lastMessage =
                "JniInterface falló al iniciar (clase marcada). " +
                    "Cierre la app por completo y vuelva a abrirla. ${e.message}"
            Log.e(TAG, lastMessage, e)
            false
        } catch (e: Throwable) {
            needsAppRestart = true
            lastMessage = "Error licencia: ${e.message}. Puede hacer falta reiniciar la app."
            Log.e(TAG, lastMessage, e)
            false
        }
    }

    /** Solo útil tras reinicio de proceso; documenta eso en UI. */
    fun retryHint(): String =
        "Para reintentar la activación hay que cerrar la app por completo " +
            "(quitar de recientes) y abrirla de nuevo."
}
