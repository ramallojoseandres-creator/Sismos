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
import java.util.concurrent.atomic.AtomicReference

/**
 * OEM UVC camera session for MJ-008 (1600×1200 @ 0° front) using Miaojing serenegiant stack.
 * Opens only the analyzer USB3.0 cam and drives LEDs via controlLed on the same connection.
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
    private val openedDevice = AtomicReference<UsbDevice?>(null)

    @Volatile var lastStatus: String = "UVC: inactivo"
        private set

    val isReady: Boolean
        get() = cameraHandler?.isOpened == true && cameraHandler?.isPreviewing == true

    val statusLabel: String
        get() = when {
            isReady -> {
                val d = openedDevice.get()
                val name = d?.let { UsbXuLightController.describe(it) } ?: "analizador"
                "Cámara frontal analizador · $name · ${Mj008Hardware.PREVIEW_WIDTH}×${Mj008Hardware.PREVIEW_HEIGHT}"
            }
            else -> lastStatus
        }

    private val deviceListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            if (device == null) return
            if (!Mj008UsbDevices.isAnalyzerCamera(device)) {
                Log.d(TAG, "Ignorando USB (no analizador): ${UsbXuLightController.describe(device)}")
                return
            }
            lastStatus = "UVC: permiso USB ${UsbXuLightController.describe(device)}"
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
            // Dual USB: never open the secondary cam even if it somehow got permission.
            if (!Mj008UsbDevices.isAnalyzerCamera(device)) {
                lastStatus = "UVC: rechazada secundaria ${UsbXuLightController.describe(device)}"
                Log.w(TAG, lastStatus)
                runCatching { ctrlBlock.close() }
                return
            }
            try {
                if (handler.isOpened) {
                    runCatching { handler.close() }
                }
                handler.open(ctrlBlock)
                openedDevice.set(device)
                val tex = view.surfaceTexture
                if (tex != null) {
                    previewSurface?.release()
                    previewSurface = Surface(tex)
                    handler.startPreview(previewSurface)
                } else {
                    mainHandler.postDelayed({
                        runCatching {
                            val t = view.surfaceTexture ?: return@runCatching
                            previewSurface?.release()
                            previewSurface = Surface(t)
                            handler.startPreview(previewSurface)
                        }
                    }, 400)
                }
                lastStatus = "UVC frontal OK: ${UsbXuLightController.describe(device)}"
                Log.i(TAG, lastStatus)
                // OEM turns white LEDs on as soon as the analyzer cam opens.
                mainHandler.postDelayed({
                    runCatching { applyWhiteLight() }
                }, 200)
                completeReady(true)
            } catch (e: Exception) {
                lastStatus = "UVC error: ${e.message}"
                Log.e(TAG, "UVC connect failed", e)
                completeReady(false)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            cameraHandler?.stopPreview()
            openedDevice.set(null)
            lastStatus = "UVC: desconectada"
            completeReady(false)
        }

        override fun onDetach(device: UsbDevice?) {
            cameraHandler?.close()
            openedDevice.set(null)
            lastStatus = "UVC: detach"
            completeReady(false)
        }

        override fun onCancel(device: UsbDevice?) {
            lastStatus = "UVC: permiso USB denegado — acepte el diálogo"
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
            probeAttachedDevices()
            return
        }
        ready = CompletableDeferred()
        lastStatus = "UVC: buscando cámara frontal USB3.0…"
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(activity, deviceListener)
        }
        previewView?.onResume()
        usbMonitor?.register()
        mainHandler.postDelayed({ probeAttachedDevices() }, 300)
        mainHandler.postDelayed({ probeAttachedDevices() }, 1500)
        mainHandler.postDelayed({ probeAttachedDevices() }, 3500)
    }

    fun retryConnect() {
        ready = CompletableDeferred()
        lastStatus = "UVC: reintentando cámara frontal…"
        runCatching { cameraHandler?.close() }
        openedDevice.set(null)
        probeAttachedDevices()
    }

    private fun probeAttachedDevices() {
        val monitor = usbMonitor ?: return
        try {
            val list = monitor.deviceList
            val ranked = list.map { Mj008UsbDevices.rankAnalyzerCamera(it) }
                .sortedByDescending { it.score }
            Log.i(
                TAG,
                "USB detectados=${list.size} → ${ranked.joinToString {
                    "${UsbXuLightController.describe(it.device)} score=${it.score} (${it.reason})"
                }}",
            )
            if (list.isEmpty()) {
                lastStatus = "UVC: sin USB — revise cable interno del analizador"
                return
            }
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            if (pick != null) {
                lastStatus = "UVC: abriendo frontal ${UsbXuLightController.describe(pick.device)}"
                monitor.requestPermission(pick.device)
                return
            }
            lastStatus = "UVC: no hay USB3.0 analizador (hay ${list.size} USB, ninguno score≥100). " +
                "Revise Dual USB camera / Force USB front camera."
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

    fun applyWhiteLight() {
        applyLightMode(LightMode.WHITE)
    }

    fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        val handler = cameraHandler ?: return
        if (handler.isOpened != true) {
            Log.w(TAG, "applyLightMode skipped — camera not open")
            return
        }
        val payload = UsbXuLightController.lightPayload(cmd, UsbXuLightController.ARG_ON)
        try {
            handler.controlLed(130, 55318, payload)
            Log.i(TAG, "LED ${mode.shortName} via UVC XU")
        } catch (e: Exception) {
            Log.e(TAG, "controlLed failed", e)
            lastStatus = "LED error: ${e.message}"
        }
    }

    fun turnOff() {
        val handler = cameraHandler ?: return
        if (handler.isOpened != true) return
        val payload = UsbXuLightController.lightPayload(
            UsbXuLightController.CMD_WOODS,
            UsbXuLightController.ARG_OFF,
        )
        runCatching { handler.controlLed(130, 55318, payload) }
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
        openedDevice.set(null)
        started.set(false)
    }

    private fun completeReady(ok: Boolean) {
        if (!ready.isCompleted) ready.complete(ok)
    }

    companion object {
        private const val TAG = "Mj008Uvc"
    }
}
