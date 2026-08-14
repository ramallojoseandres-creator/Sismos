package com.mlh.skinanalyzer.analysis.oem

import com.google.gson.annotations.SerializedName

/** Gson models for `libsalon.so` JSON (matches OEM `FaceSkinDetectBean.ImageBean`). */
data class OemImageEnvelope(
    @SerializedName("result") val result: OemImageBean?,
)

data class OemAcneScarEnvelope(
    @SerializedName("result") val result: OemImageBean?,
)

data class OemImageBean(
    @SerializedName("type") val type: String? = null,
    @SerializedName("score") val score: Int = 0,
    @SerializedName("level") val level: String? = null,
    @SerializedName("urls") val urls: String? = null,
    @SerializedName("black_url") val blackUrl: String? = null,
    @SerializedName("age") val age: Int = 0,
    @SerializedName("result") val nested: OemImageBean? = null,
    @SerializedName("result_two") val nestedTwo: OemImageBean? = null,
    @SerializedName("value") val value: OemValueBean? = null,
)

data class OemValueBean(
    @SerializedName("all_count") val allCount: Double = 0.0,
    @SerializedName("center_count") val centerCount: Int = 0,
)

data class OemFaceProportionEnvelope(
    @SerializedName("three_parts") val threeParts: OemThreeParts? = null,
    @SerializedName("five_eyes") val fiveEyes: OemFiveEyes? = null,
)

data class OemThreeParts(
    @SerializedName("upper") val upper: Float = 0f,
    @SerializedName("middle") val middle: Float = 0f,
    @SerializedName("lower") val lower: Float = 0f,
)

data class OemFiveEyes(
    @SerializedName("eye_width") val eyeWidth: Float = 0f,
    @SerializedName("face_width") val faceWidth: Float = 0f,
)

data class OemIndicatorResult(
    val key: String,
    val oemType: String,
    val displayName: String,
    val layer: String,
    val score: Int,
    val levelLabel: String,
    val overlayPath: String?,
    val blackOverlayPath: String? = null,
)

data class OemAnalysisBundle(
    val indicators: List<OemIndicatorResult>,
    val skinAge: Int,
    val overview: String,
    val facialRatioJson: String,
    val sessionDir: String,
)
