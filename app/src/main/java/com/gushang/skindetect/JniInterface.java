package com.gushang.skindetect;

import android.util.Log;

/* loaded from: classes2.dex — load order: OpenCV deps before SkinDetect */
public class JniInterface {
    private static String TAG;

    public static native int register(String str);

    public static native float skinAcne(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinAcneCuticle(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinAcneInflammation(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinBlackheads(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinCuticle(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinElasticity(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinExudates(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinHair(String str, String str2, byte[] bArr, int i, int i2);

    public static native int skinHeatMap(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinHeavyMetal(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinOilContent(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinScars(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinSensitivity(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinSplotColor(String str, String str2, byte[] bArr, int i, int i2);

    public static native int skinThreeDImage(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWaterContent(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWaterContentOld(String str, String str2, byte[] bArr, int i, int i2);

    public static native float skinWhiteness(String str, String str2, byte[] bArr, int i, int i2);

    static {
        // Circular NEEDED between opencv_java3 ↔ xfeatures2d; load both before SkinDetect.
        try {
            System.loadLibrary("opencv_java3");
        } catch (UnsatisfiedLinkError e) {
            Log.e("gushang", "opencv_java3 preload: " + e.getMessage());
        }
        try {
            System.loadLibrary("xfeatures2d");
        } catch (UnsatisfiedLinkError e) {
            Log.e("gushang", "xfeatures2d preload: " + e.getMessage());
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
