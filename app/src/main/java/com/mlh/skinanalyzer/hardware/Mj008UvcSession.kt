package com.mlh.skinanalyzer.hardware

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usbcameracommon.UVCCameraHandler
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
    private var ready = CompletableDeferred<Boolean>()
    private val started = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var lastStatus: String = "UVC: inactivo"
        private set

    val isReady: Boolean
        get() = cameraHandler?.isOpened == true && cameraHandler?.isPreviewing == true

    val statusLabel: String
        get() = when {
            isReady -> "UVC ${Mj008Hardware.PREVIEW_WIDTH}×${Mj008Hardware.PREVIEW_HEIGHT} @ ${Mj008Hardware.PREVIEW_ORIENTATION}°"
            else -> lastStatus
        }

    private val deviceListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            if (device == null) return
            if (!Mj008UsbDevices.isLikelyAnalyzerCamera(device)) {
                Log.d(TAG, "Ignorando USB secundario: ${UsbXuLightController.describe(device)}")
                return
            }
            lastStatus = "UVC: solicitando permiso ${UsbXuLightController.describe(device)}"
            Log.i(TAG, lastStatus)
            usbMonitor?.requestPermission(device)
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean,
        ) {
            val handler = cameraHandler ?: return
            val view = previewView ?: return
            if (ctrlBlock == null || device == null) return
            try {
                handler.open(ctrlBlock)
                val tex = view.surfaceTexture
                if (tex != null) {
                    previewSurface?.release()
                    previewSurface = Surface(tex)
                    handler.startPreview(previewSurface)
                } else {
                    // Surface may arrive a moment later — retry briefly.
                    mainHandler.postDelayed({
                        runCatching {
                            val t = view.surfaceTexture ?: return@runCatching
                            previewSurface?.release()
                            previewSurface = Surface(t)
                            handler.startPreview(previewSurface)
                        }
                    }, 400)
                }
                lastStatus = "UVC conectada: ${UsbXuLightController.describe(device)}"
                Log.i(TAG, lastStatus)
                completeReady(true)
            } catch (e: Exception) {
                lastStatus = "UVC error: ${e.message}"
                Log.e(TAG, "UVC connect failed", e)
                completeReady(false)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            cameraHandler?.stopPreview()
            lastStatus = "UVC: desconectada"
            completeReady(false)
        }

        override fun onDetach(device: UsbDevice?) {
            cameraHandler?.close()
            lastStatus = "UVC: detach"
            completeReady(false)
        }

        override fun onCancel(device: UsbDevice?) {
            lastStatus = "UVC: permiso USB denegado"
            Log.w(TAG, lastStatus)
            completeReady(false)
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
        if (!started.compareAndSet(false, true)) {
            // Already started — still re-probe devices (hotplug / late permission).
            probeAttachedDevices()
            return
        }
        ready = CompletableDeferred()
        lastStatus = "UVC: registrando USBMonitor…"
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(activity, deviceListener)
        }
        previewView?.onResume()
        usbMonitor?.register()
        // Devices already plugged in may not fire onAttach until we ask.
        mainHandler.postDelayed({ probeAttachedDevices() }, 500)
        mainHandler.postDelayed({ probeAttachedDevices() }, 2000)
    }

    private fun probeAttachedDevices() {
        val monitor = usbMonitor ?: return
        try {
            val list = monitor.deviceList
            lastStatus = "UVC: USB detectados=${list.size}"
            val ranked = list.map { Mj008UsbDevices.rankAnalyzerCamera(it) }
                .sortedByDescending { it.score }
            Log.i(
                TAG,
                "$lastStatus → ${ranked.joinToString { "${UsbXuLightController.describe(it.device)} score=${it.score}" }}",
            )
            if (list.isEmpty()) {
                lastStatus = "UVC: sin USB (Auxiliary: Dual USB camera ON — revise cable interno)"
                return
            }
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            if (pick != null) {
                lastStatus = "UVC: eligiendo ${UsbXuLightController.describe(pick.device)} (${pick.reason})"
                monitor.requestPermission(pick.device)
                return
            }
            lastStatus = "UVC: ninguna cámara analizador reconocida entre ${list.size} USB"
        } catch (e: Exception) {
            Log.e(TAG, "probeAttachedDevices", e)
            lastStatus = "UVC probe: ${e.message}"
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { turnOff() }
        runCatching { usbMonitor?.unregister() }
        previewView?.onPause()
    }

    suspend fun awaitReady(timeoutMs: Long = 25_000): Boolean = try {
        withTimeout(timeoutMs) { ready.await() }
    } catch (_: Exception) {
        isReady
    }

    fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(cmd, UsbXuLightController.ARG_ON)
        handler.controlLed(130, 55318, payload)
    }

    fun turnOff() {
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(
            UsbXuLightController.CMD_WOODS,
            UsbXuLightController.ARG_OFF,
        )
        handler.controlLed(130, 55318, payload)
    }

    suspend fun captureStill(target: File): Bitmap? {
        val handler = cameraHandler ?: return null
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        handler.captureStill(target.absolutePath)
        repeat(40) {
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
        started.set(false)
    }

    private fun completeReady(ok: Boolean) {
        if (!ready.isCompleted) ready.complete(ok)
    }

    companion object {
        private const val TAG = "Mj008Uvc"

        /** Always try UVC on MJ-008; USB list can be empty until USBMonitor registers. */
        fun shouldPreferUvc(activity: Activity): Boolean = true
    }
}
