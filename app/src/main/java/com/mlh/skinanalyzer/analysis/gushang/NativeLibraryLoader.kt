package com.mlh.skinanalyzer.analysis.gushang

import android.util.Log

/**
 * Precarga todas las nativas OEM que SkinDetect / Paddle / salon pueden exigir.
 * Orden: C++ → HiAI → Paddle → OpenCV ↔ xfeatures → SkinDetect → Native.
 */
object NativeLibraryLoader {
    private const val TAG = "NativeLibs"

    @Volatile
    private var loaded = false

    @JvmStatic
    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        // Best-effort preload — missing optional libs must not abort SkinDetect.
        loadOptional("c++_shared")
        loadOptional("hiai")
        loadOptional("hiai_ir")
        loadOptional("hiai_ir_build")
        loadOptional("paddle_light_api_shared")
        loadOptional("opencv_java3")
        loadOptional("xfeatures2d")
        // Required for clinical engine
        System.loadLibrary("SkinDetect")
        loadOptional("Native")
        loadOptional("salon")
        loaded = true
        Log.i(TAG, "native preload complete")
    }

    private fun loadOptional(name: String) {
        try {
            System.loadLibrary(name)
            Log.i(TAG, "loaded $name")
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "optional $name not loaded: ${e.message}")
        }
    }
}
