package com.zeze.faceDetection

/**
 * JNI bridge to OEM `libsalon.so` (Miaojing / MJ-008 skin analysis).
 * Returns JSON matching `FaceSkinDetectBean.ImageBean` layout.
 */
object FaceDetectionJni {
    init {
        System.loadLibrary("c++_shared")
        System.loadLibrary("salon")
    }

    @JvmStatic external fun skinCollagen(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinUVAcne(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinUVspot(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinPorphyrin(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinMoisture(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinWrinkle(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinPigmentation(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinSensitivity(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinBlackhead(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinPore(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinBlackeye(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinSpot(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinOilyGloss(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinAcneScar(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinFaceLandMark(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinAge(path: String, path2: String, x: FloatArray, y: FloatArray): String

    @JvmStatic external fun skinColor(path: String, path2: String, x: FloatArray, y: FloatArray): String

    const val LANDMARK_COUNT = 478
}
