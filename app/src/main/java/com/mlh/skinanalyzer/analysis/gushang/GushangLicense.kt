package com.mlh.skinanalyzer.analysis.gushang

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.gushang.skindetect.JniInterface
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Licencia offline Gushang / SkinDetect.
 * OEM register(): **0 = activado** — única fuente de verdad.
 */
object GushangLicense {
    private const val TAG = "GushangLicense"
    const val SKINDETECT_DIR = "/storage/emulated/0/skindetect"
    private const val LICENCE_FILE = "licence"
    private const val FACTORY_LICENCE = "/storage/emulated/0/licence"
    const val EXPECTED_BYTES = 32

    private val registerAttempted = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val lastCode = AtomicInteger(-1)
    @Volatile var lastMessage: String = "Licencia no comprobada"
        private set
    @Volatile var needsAppRestart: Boolean = false
        private set
    /** Mensaje amable para pantallas clínicas (sin códigos). */
    @Volatile var userFacingMessage: String = "Comprobando activación del equipo…"
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
        appendLine("userFacing=$userFacingMessage")
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

    fun isAllFilesAccessGranted(): Boolean =
        android.os.Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()

    private fun ensureLicenceCopiedFromFactory() {
        val origen = File(FACTORY_LICENCE)
        val destino = licenceFile()
        if (!origen.exists() || origen.length() <= 0L) return
        if (destino.exists() && destino.length() == origen.length()) return
        runCatching {
            destino.parentFile?.mkdirs()
            origen.copyTo(destino, overwrite = true)
            destino.setReadable(true, false)
            Log.i(TAG, "Licencia recopiada: ${destino.length()} bytes")
        }.onFailure {
            Log.w(TAG, "No se pudo copiar licencia de fábrica: ${it.message}")
        }
    }

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

    private fun setUserFacing(active: Boolean, failedNeedsRestart: Boolean = false) {
        userFacingMessage = when {
            active -> "Equipo activado"
            failedNeedsRestart ->
                "El equipo necesita activarse. Cierre la app por completo y ábrala de nuevo, " +
                    "luego use Admin → Licencia."
            else -> "El equipo necesita activarse. Abra Admin → Licencia."
        }
    }

    /**
     * Importa el archivo de licencia (32 bytes) desde el selector.
     * Escribe en filesDir y en /sdcard/skindetect/licence.
     */
    fun importLicence(context: Context, uri: Uri): Result<String> {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return Result.failure(IllegalStateException("No se pudo leer el archivo."))
            if (bytes.size != EXPECTED_BYTES) {
                return Result.failure(
                    IllegalArgumentException(
                        "Ese archivo no parece la licencia del equipo " +
                            "(${bytes.size} bytes en lugar de $EXPECTED_BYTES).",
                    ),
                )
            }
            File(context.filesDir, LICENCE_FILE).writeBytes(bytes)
            var sdOk = false
            runCatching {
                val destino = licenceFile()
                destino.parentFile?.mkdirs()
                destino.writeBytes(bytes)
                destino.setReadable(true, false)
                sdOk = destino.exists() && destino.length() == EXPECTED_BYTES.toLong()
            }.onFailure {
                Log.w(TAG, "Escritura a skindetect falló: ${it.message}")
            }
            // Allow a fresh register attempt after import in a new process;
            // within this process, if already attempted and class is dead, needs restart.
            if (registerAttempted.get() && !registered.get()) {
                needsAppRestart = true
                lastMessage = "Licencia guardada (${bytes.size} B). ${retryHint()}"
                setUserFacing(false, failedNeedsRestart = true)
                return Result.success(
                    if (sdOk) {
                        "Licencia guardada. Cierre la app por completo y ábrala de nuevo para activar."
                    } else {
                        "Licencia guardada en la app. Si hace falta, conceda acceso a todos los archivos " +
                            "y reinicie la app."
                    },
                )
            }
            // Reset attempt flag only if we never touched JniInterface yet.
            if (!registerAttempted.get()) {
                val ok = ensureRegistered(context)
                return if (ok) {
                    Result.success("Equipo activado correctamente.")
                } else {
                    Result.success(lastMessage)
                }
            }
            Result.success("Licencia escrita (${bytes.size} bytes).")
        } catch (e: Exception) {
            Log.e(TAG, "importLicence", e)
            Result.failure(e)
        }
    }

    fun ensureRegistered(context: Context): Boolean {
        if (registered.get()) {
            setUserFacing(true)
            return true
        }
        if (registerAttempted.get()) {
            needsAppRestart = true
            setUserFacing(false, failedNeedsRestart = true)
            if (!lastMessage.contains("cerrar", ignoreCase = true)) {
                lastMessage = "$lastMessage · ${retryHint()}"
            }
            return false
        }

        if (!NativeLibraryLoader.preloadDeps()) {
            lastMessage = "Nativas incompletas: ${NativeLibraryLoader.lastError}. " +
                "Cierre la app por completo y reinstale el APK."
            needsAppRestart = true
            setUserFacing(false, failedNeedsRestart = true)
            Log.e(TAG, lastMessage)
            return false
        }

        ensureLicenceCopiedFromFactory()
        // Prefer app-private copy if sdcard unreadable
        val appLicence = File(context.filesDir, LICENCE_FILE)
        val sdLicence = licenceFile()
        if (appLicence.exists() && appLicence.length() == EXPECTED_BYTES.toLong()) {
            if (!sdLicence.exists() || sdLicence.length() != appLicence.length()) {
                runCatching {
                    sdLicence.parentFile?.mkdirs()
                    sdLicence.writeBytes(appLicence.readBytes())
                    sdLicence.setReadable(true, false)
                }
            }
        }

        Log.i(
            TAG,
            "pre-register licence exists=${sdLicence.exists()} " +
                "canRead=${sdLicence.canRead()} bytes=${if (sdLicence.exists()) sdLicence.length() else -1} " +
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
            val ok = code == 0
            registered.set(ok)
            lastMessage = if (ok) {
                "Licencia Gushang activa (register=0, licence=" +
                    "${if (sdLicence.exists()) sdLicence.length() else "?"} bytes)"
            } else {
                "El motor rechazó la licencia. ${buildDiagnostic(code)}"
            }
            setUserFacing(ok)
            Log.i(TAG, lastMessage)
            ok
        } catch (e: UnsatisfiedLinkError) {
            needsAppRestart = true
            lastMessage = "libSkinDetect no cargó: ${e.message}. Cierre y reabra la app."
            setUserFacing(false, failedNeedsRestart = true)
            Log.e(TAG, "register lanzó excepción", e)
            false
        } catch (e: NoClassDefFoundError) {
            needsAppRestart = true
            lastMessage = "JniInterface falló al iniciar. ${retryHint()} ${e.message}"
            setUserFacing(false, failedNeedsRestart = true)
            Log.e(TAG, "register lanzó excepción", e)
            false
        } catch (t: Throwable) {
            needsAppRestart = true
            lastMessage = "El motor nativo no respondió: ${t.message}. ${retryHint()}"
            setUserFacing(false, failedNeedsRestart = true)
            Log.e(TAG, "register lanzó excepción", t)
            false
        }
    }

    fun retryHint(): String =
        "Para reintentar la activación hay que cerrar la app por completo " +
            "(quitar de recientes) y abrirla de nuevo."
}
