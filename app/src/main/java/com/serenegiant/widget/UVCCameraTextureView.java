package com.serenegiant.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import com.serenegiant.encoder.IVideoEncoder;
import com.serenegiant.encoder.MediaEncoder;
import com.serenegiant.encoder.MediaVideoEncoder;
import com.serenegiant.glutils.EGLBase;
import com.serenegiant.glutils.GLDrawer2D;
import com.serenegiant.glutils.es1.GLHelper;
import com.serenegiant.utils.FpsCounter;
import com.serenegiant.widget.CameraViewInterface;

/* loaded from: classes3.dex */
public class UVCCameraTextureView extends AspectRatioTextureView implements TextureView.SurfaceTextureListener, CameraViewInterface {
    private static final boolean DEBUG = true;
    private static final String TAG = "UVCCameraTextureView";
    private CameraViewInterface.Callback mCallback;
    private final Object mCaptureSync;
    private final FpsCounter mFpsCounter;
    private boolean mHasSurface;
    private Surface mPreviewSurface;
    private RenderHandler mRenderHandler;
    private boolean mReqesutCaptureStillImage;
    private Bitmap mTempBitmap;

    public UVCCameraTextureView(Context context) {
        this(context, null, 0);
    }

    public UVCCameraTextureView(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public UVCCameraTextureView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.mCaptureSync = new Object();
        this.mFpsCounter = new FpsCounter();
        setSurfaceTextureListener(this);
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public void onResume() {
        Log.v(TAG, "onResume:");
        // Do not create a second RenderHandler while one is live — that reconnects the
        // same SurfaceTexture to EGL and crashes with createWindowSurface / already connected.
        if (this.mHasSurface && this.mRenderHandler == null) {
            this.mRenderHandler = RenderHandler.createHandler(this.mFpsCounter, super.getSurfaceTexture(), getWidth(), getHeight());
        }
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public void onPause() {
        Log.v(TAG, "onPause:");
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler != null) {
            renderHandler.release();
            this.mRenderHandler = null;
        }
        Bitmap bitmap = this.mTempBitmap;
        if (bitmap != null) {
            bitmap.recycle();
            this.mTempBitmap = null;
        }
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i2) {
        Log.v(TAG, "onSurfaceTextureAvailable:" + surfaceTexture);
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler == null) {
            this.mRenderHandler = RenderHandler.createHandler(this.mFpsCounter, surfaceTexture, i, i2);
        } else {
            renderHandler.resize(i, i2);
        }
        this.mHasSurface = true;
        CameraViewInterface.Callback callback = this.mCallback;
        if (callback != null) {
            callback.onSurfaceCreated(this, getSurface());
        }
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i2) {
        Log.v(TAG, "onSurfaceTextureSizeChanged:" + surfaceTexture);
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler != null) {
            renderHandler.resize(i, i2);
        }
        CameraViewInterface.Callback callback = this.mCallback;
        if (callback != null) {
            callback.onSurfaceChanged(this, getSurface(), i, i2);
        }
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        Log.v(TAG, "onSurfaceTextureDestroyed:" + surfaceTexture);
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler != null) {
            renderHandler.release();
            this.mRenderHandler = null;
        }
        this.mHasSurface = false;
        CameraViewInterface.Callback callback = this.mCallback;
        if (callback != null) {
            try {
                callback.onSurfaceDestroy(this, getSurface());
            } catch (Exception e) {
                Log.w(TAG, "onSurfaceDestroy callback", e);
            }
        }
        Surface surface = this.mPreviewSurface;
        if (surface != null) {
            try {
                surface.release();
            } catch (Exception ignored) {
            }
            this.mPreviewSurface = null;
        }
        return true;
    }

    @Override // android.view.TextureView.SurfaceTextureListener
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        synchronized (this.mCaptureSync) {
            if (this.mReqesutCaptureStillImage) {
                this.mReqesutCaptureStillImage = false;
                if (this.mTempBitmap == null) {
                    this.mTempBitmap = getBitmap();
                } else {
                    getBitmap(this.mTempBitmap);
                }
                this.mCaptureSync.notifyAll();
            }
        }
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public boolean hasSurface() {
        return this.mHasSurface;
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public Bitmap captureStillImage() {
        Bitmap bitmap;
        synchronized (this.mCaptureSync) {
            this.mReqesutCaptureStillImage = true;
            try {
                this.mCaptureSync.wait();
            } catch (InterruptedException unused) {
            }
            bitmap = this.mTempBitmap;
        }
        return bitmap;
    }

    @Override // android.view.TextureView, com.serenegiant.widget.CameraViewInterface
    public SurfaceTexture getSurfaceTexture() {
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler != null) {
            return renderHandler.getPreviewTexture();
        }
        return null;
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public Surface getSurface() {
        SurfaceTexture surfaceTexture;
        Log.v(TAG, "getSurface:hasSurface=" + this.mHasSurface);
        if (this.mPreviewSurface == null && (surfaceTexture = getSurfaceTexture()) != null) {
            this.mPreviewSurface = new Surface(surfaceTexture);
        }
        return this.mPreviewSurface;
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public void setVideoEncoder(IVideoEncoder iVideoEncoder) {
        RenderHandler renderHandler = this.mRenderHandler;
        if (renderHandler != null) {
            renderHandler.setVideoEncoder(iVideoEncoder);
        }
    }

    @Override // com.serenegiant.widget.CameraViewInterface
    public void setCallback(CameraViewInterface.Callback callback) {
        this.mCallback = callback;
    }

    public void resetFps() {
        this.mFpsCounter.reset();
    }

    public void updateFps() {
        this.mFpsCounter.update();
    }

    public float getFps() {
        return this.mFpsCounter.getFps();
    }

    public float getTotalFps() {
        return this.mFpsCounter.getTotalFps();
    }

    private static final class RenderHandler extends Handler implements SurfaceTexture.OnFrameAvailableListener {
        private static final int MSG_CREATE_SURFACE = 3;
        private static final int MSG_REQUEST_RENDER = 1;
        private static final int MSG_RESIZE = 4;
        private static final int MSG_SET_ENCODER = 2;
        private static final int MSG_TERMINATE = 9;
        private final FpsCounter mFpsCounter;
        private boolean mIsActive;
        private RenderThread mThread;

        public static final RenderHandler createHandler(FpsCounter fpsCounter, SurfaceTexture surfaceTexture, int i, int i2) {
            RenderThread renderThread = new RenderThread(fpsCounter, surfaceTexture, i, i2);
            renderThread.start();
            return renderThread.getHandler();
        }

        private RenderHandler(FpsCounter fpsCounter, RenderThread renderThread) {
            this.mIsActive = true;
            this.mThread = renderThread;
            this.mFpsCounter = fpsCounter;
        }

        public final void setVideoEncoder(IVideoEncoder iVideoEncoder) {
            Log.v(UVCCameraTextureView.TAG, "setVideoEncoder:");
            if (this.mIsActive) {
                sendMessage(obtainMessage(2, iVideoEncoder));
            }
        }

        public final SurfaceTexture getPreviewTexture() {
            SurfaceTexture surfaceTexture;
            Log.v(UVCCameraTextureView.TAG, "getPreviewTexture:");
            if (!this.mIsActive) {
                return null;
            }
            synchronized (this.mThread.mSync) {
                sendEmptyMessage(3);
                try {
                    this.mThread.mSync.wait();
                } catch (InterruptedException unused) {
                }
                surfaceTexture = this.mThread.mPreviewSurface;
            }
            return surfaceTexture;
        }

        public void resize(int i, int i2) {
            Log.v(UVCCameraTextureView.TAG, "resize:");
            if (this.mIsActive) {
                synchronized (this.mThread.mSync) {
                    sendMessage(obtainMessage(4, i, i2));
                    try {
                        this.mThread.mSync.wait();
                    } catch (InterruptedException unused) {
                    }
                }
            }
        }

        public final void release() {
            Log.v(UVCCameraTextureView.TAG, "release:");
            if (this.mIsActive) {
                this.mIsActive = false;
                removeMessages(1);
                removeMessages(2);
                sendEmptyMessage(9);
            }
        }

        @Override // android.graphics.SurfaceTexture.OnFrameAvailableListener
        public final void onFrameAvailable(SurfaceTexture surfaceTexture) {
            if (this.mIsActive) {
                this.mFpsCounter.count();
                sendEmptyMessage(1);
            }
        }

        @Override // android.os.Handler
        public final void handleMessage(Message message) {
            if (this.mThread == null) {
                return;
            }
            int i = message.what;
            if (i != 9) {
                switch (i) {
                    case 1:
                        this.mThread.onDrawFrame();
                        break;
                    case 2:
                        this.mThread.setEncoder((MediaEncoder) message.obj);
                        break;
                    case 3:
                        this.mThread.updatePreviewSurface();
                        break;
                    case 4:
                        this.mThread.resize(message.arg1, message.arg2);
                        break;
                    default:
                        super.handleMessage(message);
                        break;
                }
                return;
            }
            Looper.myLooper().quit();
            this.mThread = null;
        }

        private static final class RenderThread extends Thread {
            private GLDrawer2D mDrawer;
            private EGLBase mEgl;
            private EGLBase.IEglSurface mEglSurface;
            private MediaEncoder mEncoder;
            private final FpsCounter mFpsCounter;
            private RenderHandler mHandler;
            private SurfaceTexture mPreviewSurface;
            private final SurfaceTexture mSurface;
            private int mViewHeight;
            private int mViewWidth;
            private final Object mSync = new Object();
            private int mTexId = -1;
            private final float[] mStMatrix = new float[16];

            public RenderThread(FpsCounter fpsCounter, SurfaceTexture surfaceTexture, int i, int i2) {
                this.mFpsCounter = fpsCounter;
                this.mSurface = surfaceTexture;
                this.mViewWidth = i;
                this.mViewHeight = i2;
                setName("RenderThread");
            }

            public final RenderHandler getHandler() {
                Log.v(UVCCameraTextureView.TAG, "RenderThread#getHandler:");
                synchronized (this.mSync) {
                    if (this.mHandler == null) {
                        try {
                            this.mSync.wait();
                        } catch (InterruptedException unused) {
                        }
                    }
                }
                return this.mHandler;
            }

            public void resize(int i, int i2) {
                if ((i > 0 && i != this.mViewWidth) || (i2 > 0 && i2 != this.mViewHeight)) {
                    this.mViewWidth = i;
                    this.mViewHeight = i2;
                    updatePreviewSurface();
                } else {
                    synchronized (this.mSync) {
                        this.mSync.notifyAll();
                    }
                }
            }

            public final void updatePreviewSurface() {
                Log.i(UVCCameraTextureView.TAG, "RenderThread#updatePreviewSurface:");
                synchronized (this.mSync) {
                    if (this.mPreviewSurface != null) {
                        Log.d(UVCCameraTextureView.TAG, "updatePreviewSurface:release mPreviewSurface");
                        this.mPreviewSurface.setOnFrameAvailableListener(null);
                        this.mPreviewSurface.release();
                        this.mPreviewSurface = null;
                    }
                    this.mEglSurface.makeCurrent();
                    if (this.mTexId >= 0) {
                        this.mDrawer.deleteTex(this.mTexId);
                    }
                    this.mTexId = this.mDrawer.initTex();
                    Log.v(UVCCameraTextureView.TAG, "updatePreviewSurface:tex_id=" + this.mTexId);
                    this.mPreviewSurface = new SurfaceTexture(this.mTexId);
                    this.mPreviewSurface.setDefaultBufferSize(this.mViewWidth, this.mViewHeight);
                    this.mPreviewSurface.setOnFrameAvailableListener(this.mHandler);
                    this.mSync.notifyAll();
                }
            }

            public final void setEncoder(MediaEncoder mediaEncoder) {
                Log.v(UVCCameraTextureView.TAG, "RenderThread#setEncoder:encoder=" + mediaEncoder);
                if (mediaEncoder != null && (mediaEncoder instanceof MediaVideoEncoder)) {
                    ((MediaVideoEncoder) mediaEncoder).setEglContext(this.mEglSurface.getContext(), this.mTexId);
                }
                this.mEncoder = mediaEncoder;
            }

            public final void onDrawFrame() {
                this.mEglSurface.makeCurrent();
                this.mPreviewSurface.updateTexImage();
                this.mPreviewSurface.getTransformMatrix(this.mStMatrix);
                MediaEncoder mediaEncoder = this.mEncoder;
                if (mediaEncoder != null) {
                    if (mediaEncoder instanceof MediaVideoEncoder) {
                        ((MediaVideoEncoder) mediaEncoder).frameAvailableSoon(this.mStMatrix);
                    } else {
                        mediaEncoder.frameAvailableSoon();
                    }
                }
                this.mDrawer.draw(this.mTexId, this.mStMatrix, 0);
                this.mEglSurface.swap();
            }

            @Override // java.lang.Thread, java.lang.Runnable
            public final void run() {
                Log.d(UVCCameraTextureView.TAG, getName() + " started");
                boolean ok = false;
                try {
                    ok = init();
                } catch (Throwable t) {
                    Log.e(UVCCameraTextureView.TAG, "RenderThread init crashed", t);
                    ok = false;
                }
                if (!ok) {
                    synchronized (this.mSync) {
                        this.mHandler = null;
                        this.mSync.notifyAll();
                    }
                    Log.e(UVCCameraTextureView.TAG, getName() + " aborted — EGL surface unavailable");
                    return;
                }
                Looper.prepare();
                synchronized (this.mSync) {
                    this.mHandler = new RenderHandler(this.mFpsCounter, this);
                    this.mSync.notify();
                }
                Looper.loop();
                Log.d(UVCCameraTextureView.TAG, getName() + " finishing");
                release();
                synchronized (this.mSync) {
                    this.mHandler = null;
                    this.mSync.notify();
                }
            }

            /** @return false if EGL window surface could not be created (do not crash the app). */
            private final boolean init() {
                Log.v(UVCCameraTextureView.TAG, "RenderThread#init:");
                try {
                    this.mEgl = EGLBase.createFrom(null, false, false);
                    this.mEglSurface = this.mEgl.createFromSurface(this.mSurface);
                    this.mEglSurface.makeCurrent();
                    this.mDrawer = new GLDrawer2D(true);
                    return true;
                } catch (Throwable t) {
                    Log.e(UVCCameraTextureView.TAG, "createWindowSurface / EGL init failed — not crashing", t);
                    release();
                    return false;
                }
            }

            private final void release() {
                Log.v(UVCCameraTextureView.TAG, "RenderThread#release:");
                GLDrawer2D gLDrawer2D = this.mDrawer;
                if (gLDrawer2D != null) {
                    gLDrawer2D.release();
                    this.mDrawer = null;
                }
                SurfaceTexture surfaceTexture = this.mPreviewSurface;
                if (surfaceTexture != null) {
                    surfaceTexture.release();
                    this.mPreviewSurface = null;
                }
                int i = this.mTexId;
                if (i >= 0) {
                    GLHelper.deleteTex(i);
                    this.mTexId = -1;
                }
                EGLBase.IEglSurface iEglSurface = this.mEglSurface;
                if (iEglSurface != null) {
                    iEglSurface.release();
                    this.mEglSurface = null;
                }
                EGLBase eGLBase = this.mEgl;
                if (eGLBase != null) {
                    eGLBase.release();
                    this.mEgl = null;
                }
            }
        }
    }
}
