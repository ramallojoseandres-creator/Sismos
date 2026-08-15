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
 * El .so lee `/sdcard/skindetect` (calib + licencia del equipo).
 * OEM [JniInterface.registerResult]: **0 = activado** (log «激活成功»).
 * El brief invertía el comentario; seguimos el código OEM del .so.
 */
object GushangLicense {
    private const val TAG = "GushangLicense"
    const val SKINDETECT_DIR = "/sdcard/skindetect"

    private val registered = AtomicBoolean(false)
    private val lastCode = AtomicInteger(-1)
    @Volatile var lastMessage: String = "Licencia no comprobada"
        private set

    val isActivated: Boolean get() = registered.get()
    val registerCode: Int get() = lastCode.get()

    fun skindetectDir(): File = File(SKINDETECT_DIR)

    fun skindetectReadable(): Boolean {
        val dir = skindetectDir()
        return dir.exists() && dir.canRead()
    }

    fun diagnose(): String = buildString {
        appendLine("Gushang SkinDetect")
        appendLine("activated=$isActivated registerCode=$registerCode")
        appendLine("message=$lastMessage")
        val dir = skindetectDir()
        appendLine("dir=$SKINDETECT_DIR exists=${dir.exists()} readable=${dir.canRead()}")
        if (dir.isDirectory) {
            dir.listFiles()?.sortedBy { it.name }?.take(20)?.forEach {
                appendLine("  ${it.name} (${it.length()} bytes)")
            }
        }
        val ext = Environment.getExternalStorageDirectory()
        appendLine("extStorage=${ext?.absolutePath}")
        listOf("licence.gushang", "licence", "brandcode").forEach { name ->
            val f = File(ext, name)
            appendLine("  /$name exists=${f.exists()}")
        }
    }

    /**
     * Call once at app start (off main thread OK). Loads [JniInterface] → libSkinDetect.
     * @return true if register() == 0 (OEM success).
     */
    fun ensureRegistered(context: Context): Boolean {
        if (registered.get()) return true
        return try {
            // Touch assets so models are present (SDK may copy from assets itself).
            listOf("pyramidbox.nb", "facekeypoints.nb", "maskclassifier.nb").forEach { name ->
                runCatching { context.assets.open(name).close() }
                    .onFailure { Log.w(TAG, "asset missing: $name") }
            }
            if (!skindetectReadable()) {
                lastMessage =
                    "Sin acceso a $SKINDETECT_DIR — conceda almacenamiento y conserve la licencia del equipo."
                Log.w(TAG, lastMessage)
            }
            val token = UUID.randomUUID().toString().replace("-", "")
            val code = JniInterface.register(token)
            lastCode.set(code)
            // OEM registerResult: 0 = 激活成功
            val ok = code == 0
            registered.set(ok)
            lastMessage = if (ok) {
                "Licencia Gushang activa (register=0)"
            } else {
                "Licencia no encontrada o inválida (register=$code). Revise $SKINDETECT_DIR"
            }
            Log.i(TAG, lastMessage)
            ok
        } catch (e: UnsatisfiedLinkError) {
            lastMessage = "libSkinDetect no cargó: ${e.message}"
            Log.e(TAG, lastMessage, e)
            false
        } catch (e: NoClassDefFoundError) {
            lastMessage = "JniInterface no disponible (nativas): ${e.message}"
            Log.e(TAG, lastMessage, e)
            false
        } catch (e: Exception) {
            lastMessage = "Error licencia: ${e.message}"
            Log.e(TAG, lastMessage, e)
            false
        } catch (e: Throwable) {
            lastMessage = "Error nativo: ${e.message}"
            Log.e(TAG, lastMessage, e)
            false
        }
    }
}
