package com.gushang.skindetect;

import android.graphics.PointF;
import android.graphics.Rect;
import java.util.Arrays;

/** OEM Gushang data bean — keep package name for JNI compatibility. */
public class DataBean {
    public byte[] mData;
    public int[][] mDoubleDimenArray;
    public int mID;
    public Inner mInner;
    public PointF[] mPoints;
    public Rect mRect;
    public float mScore;

    public static class Inner {
        public String mMessage;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DataBean{mRect=");
        sb.append(mRect != null ? mRect.toShortString() : "null");
        sb.append("\nmPoints=");
        sb.append(Arrays.toString(mPoints));
        sb.append("\nmInner=");
        sb.append(mInner != null ? mInner.mMessage : "null");
        sb.append("\nmID=");
        sb.append(mID);
        sb.append("\nmScore=");
        sb.append(mScore);
        sb.append("\nmData=");
        sb.append(Arrays.toString(mData));
        sb.append('}');
        return sb.toString();
    }
}
