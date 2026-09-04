package com.gushang.skindetect;

import android.util.Log;

/* loaded from: classes2.dex — load order: OpenCV deps before SkinDetect */
public class JniInterface {
    private static String TAG;

    public static native int register(String str);

    public static native float skinAcne(String str, String str2, byte[] bArr, int i, int i2);

    /** Variante Profundo (presente en libSkinDetect.so). */
    public static native float skinAcneLEC(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinAcneCuticle(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinAcneInflammation(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinBlackheads(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinCuticle(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinElasticity(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinElasticityLEC(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinExudates(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinHair(String str, String str2, byte[] bArr, int i, int i2);

    public static native int skinHeatMap(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinHeavyMetal(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinOilContent(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinOilContentLEC(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinScars(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinSensitivity(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinSensitivityLEC(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinSplotColor(String str, String str2, byte[] bArr, int i, int i2);

    public static native int skinThreeDImage(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWaterContent(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWaterContentOld(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWhiteness(String str, String str2, byte[] bArr, int i, int i2);

    static {
        // Only SkinDetect here — deps must already be preloaded by NativeLibraryLoader.
        // Do not call ensureLoaded in a way that loads SkinDetect twice.
        try {
            com.mlh.skinanalyzer.analysis.gushang.NativeLibraryLoader.preloadDeps();
        } catch (Throwable t) {
            Log.e("gushang", "preloadDeps before SkinDetect", t);
        }
        System.loadLibrary("SkinDetect");
        TAG = "gushang";
    }

    public static float skinTest(int i, String str, String str2) {
        byte[] bArr = new byte[0];
        if (i != 2001) {
            return 0.0f;
        }
        return skinWaterContentOld(str, str2, bArr, 0, 0);
    }

    public static int registerResult(String str) {
        int register = register(str);
        Log.e(TAG, "激活状态：" + register + "===" + str);
        if (register == 0) {
            Log.e(TAG, "激活成功!");
        } else {
            Log.e(TAG, "激活失败!");
        }
        return register;
    }
}
