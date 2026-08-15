package com.mlh.skinanalyzer.analysis.gushang

import android.util.Log

/**
 * Precarga nativas OEM. No carga SkinDetect aquí — eso lo hace [JniInterface]
 * una sola vez. Si alguna dependencia crítica falla, no tocar JniInterface
 * (Android marca la clase como fallida permanentemente en el proceso).
 */
object NativeLibraryLoader {
    private const val TAG = "NativeLibs"

    @Volatile
    private var depsAttempted = false

    @Volatile
    var depsReady: Boolean = false
        private set

    @Volatile
    var lastError: String = ""
        private set

    @JvmStatic
    @Synchronized
    fun preloadDeps(): Boolean {
        if (depsAttempted) return depsReady
        depsAttempted = true
        val missing = mutableListOf<String>()
        fun load(name: String, required: Boolean) {
            try {
                System.loadLibrary(name)
                Log.i(TAG, "loaded $name")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "load $name failed: ${e.message}")
                if (required) missing += name
            }
        }
        load("c++_shared", required = false)
        load("hiai", required = false)
        load("hiai_ir", required = false)
        load("hiai_ir_build", required = false)
        load("paddle_light_api_shared", required = false)
        load("opencv_java3", required = true)
        load("xfeatures2d", required = true)
        load("Native", required = false)
        load("salon", required = false)
        depsReady = missing.isEmpty()
        lastError = if (depsReady) "" else "Faltan nativas: ${missing.joinToString()}"
        Log.i(TAG, "native preload complete depsReady=$depsReady ${lastError}")
        return depsReady
    }

    /** Alias for older call sites. */
    @JvmStatic
    @Synchronized
    fun ensureLoaded(): Boolean = preloadDeps()
}
