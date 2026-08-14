package com.mlh.skinanalyzer.hardware

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usbcameracommon.UVCCameraHandler
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * OEM UVC camera session for MJ-008 (1600×1200 @ 90°) using Miaojing serenegiant stack.
 * Drives preview, still capture, and LED over the same USB connection as the OEM app.
 */
class Mj008UvcSession(
    private val activity: Activity,
) {
    private var usbMonitor: USBMonitor? = null
    private var cameraHandler: UVCCameraHandler? = null
    private var previewView: UVCCameraTextureView? = null
    private var previewSurface: Surface? = null
    private val ready = CompletableDeferred<Boolean>()
    private val started = AtomicBoolean(false)

    val isReady: Boolean
        get() = cameraHandler?.isOpened == true && cameraHandler?.isPreviewing == true

    val statusLabel: String
        get() = when {
            isReady -> "UVC ${Mj008Hardware.PREVIEW_WIDTH}×${Mj008Hardware.PREVIEW_HEIGHT} @ ${Mj008Hardware.PREVIEW_ORIENTATION}°"
            ready.isCompleted && runCatching { ready.getCompleted() }.getOrNull() == false ->
                "UVC: cámara MJ-008 no conectada"
            else -> "UVC: conectando…"
        }

    private val deviceListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            if (device == null || !UsbXuLightController.isMj008Camera(device)) return
            usbMonitor?.requestPermission(device)
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean,
        ) {
            val handler = cameraHandler ?: return
            val view = previewView ?: return
            if (ctrlBlock == null) return
            try {
                handler.open(ctrlBlock)
                val tex = view.surfaceTexture
                if (tex != null) {
                    previewSurface?.release()
                    previewSurface = Surface(tex)
                    handler.startPreview(previewSurface)
                }
                if (!ready.isCompleted) ready.complete(true)
                Log.i(TAG, "UVC preview started on ${UsbXuLightController.describe(device!!)}")
            } catch (e: Exception) {
                Log.e(TAG, "UVC connect failed", e)
                if (!ready.isCompleted) ready.complete(false)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            cameraHandler?.stopPreview()
            if (!ready.isCompleted) ready.complete(false)
        }

        override fun onDetach(device: UsbDevice?) {
            cameraHandler?.close()
            if (!ready.isCompleted) ready.complete(false)
        }

        override fun onCancel(device: UsbDevice?) {
            if (!ready.isCompleted) ready.complete(false)
        }
    }

    fun bindPreview(view: UVCCameraTextureView) {
        previewView = view
        if (cameraHandler == null) {
            cameraHandler = UVCCameraHandler.createHandler(
                activity,
                view,
                2,
                Mj008Hardware.PREVIEW_WIDTH,
                Mj008Hardware.PREVIEW_HEIGHT,
                1,
                Mj008Hardware.PREVIEW_ORIENTATION,
            )
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(activity, deviceListener)
        }
        previewView?.onResume()
        usbMonitor?.register()
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { turnOff() }
        usbMonitor?.unregister()
        previewView?.onPause()
    }

    suspend fun awaitReady(timeoutMs: Long = 20_000): Boolean = try {
        withTimeout(timeoutMs) { ready.await() }
    } catch (_: Exception) {
        false
    }

    fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(cmd, UsbXuLightController.ARG_ON)
        handler.controlLed(130, 55318, payload)
    }

    fun turnOff() {
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(UsbXuLightController.CMD_WOODS, UsbXuLightController.ARG_OFF)
        handler.controlLed(130, 55318, payload)
    }

    suspend fun captureStill(target: File): Bitmap? {
        val handler = cameraHandler ?: return null
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        handler.captureStill(target.absolutePath)
        repeat(30) {
            delay(100)
            if (target.exists() && target.length() > 10_000) {
                return BitmapFactory.decodeFile(target.absolutePath)
            }
        }
        return if (target.exists()) BitmapFactory.decodeFile(target.absolutePath) else null
    }

    fun release() {
        stop()
        runCatching { cameraHandler?.release() }
        runCatching { usbMonitor?.destroy() }
        previewSurface?.release()
        previewSurface = null
        cameraHandler = null
        usbMonitor = null
        previewView = null
    }

    companion object {
        private const val TAG = "Mj008Uvc"

        fun isSupported(activity: Activity): Boolean {
            return runCatching {
                Mj008Hardware.detect(activity).usbXuCameraPresent
            }.getOrDefault(false)
        }
    }
}
