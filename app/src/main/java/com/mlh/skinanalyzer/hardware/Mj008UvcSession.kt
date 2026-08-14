package com.mlh.skinanalyzer.hardware

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
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
 * Opens analyzer USB3.0 cam; if USB permission was already granted (no dialog), opens directly.
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
            openOrRequest(device)
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean,
        ) {
            openPreview(device, ctrlBlock)
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
            lastStatus = "UVC: permiso USB denegado en el sistema"
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
        mainHandler.postDelayed({ probeAttachedDevices() }, 200)
        mainHandler.postDelayed({ probeAttachedDevices() }, 1000)
        mainHandler.postDelayed({ probeAttachedDevices() }, 2500)
        mainHandler.postDelayed({ probeAttachedDevices() }, 5000)
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
            // Merge USBMonitor list + Android UsbManager (some MJ firmwares only fill one).
            val fromMonitor = runCatching { monitor.deviceList }.getOrDefault(emptyList())
            val fromSystem = systemUsbDevices()
            val merged = LinkedHashMap<String, UsbDevice>()
            (fromMonitor + fromSystem).forEach { d ->
                merged["${d.vendorId}:${d.productId}:${d.deviceName}"] = d
            }
            val list = merged.values.toList()
            val ranked = list.map { Mj008UsbDevices.rankAnalyzerCamera(it) }
                .sortedByDescending { it.score }
            Log.i(
                TAG,
                "USB monitor=${fromMonitor.size} system=${fromSystem.size} → ${ranked.joinToString {
                    "${UsbXuLightController.describe(it.device)} score=${it.score} (${it.reason})"
                }}",
            )
            if (list.isEmpty()) {
                lastStatus = "UVC: 0 dispositivos USB — cable interno / Dual USB camera"
                return
            }
            val pick = Mj008UsbDevices.pickAnalyzerCamera(list)
            if (pick == null) {
                lastStatus = "UVC: ${list.size} USB pero ninguno es analizador. " +
                    ranked.take(3).joinToString { "${it.device.productName}:s=${it.score}" }
                return
            }
            openOrRequest(pick.device)
        } catch (e: Exception) {
            Log.e(TAG, "probeAttachedDevices", e)
            lastStatus = "UVC probe: ${e.message}"
        }
    }

    /**
     * MJ-008 often never shows a USB dialog: permission was already granted once.
     * In that case [USBMonitor.requestPermission] is a no-op UI-wise — we must [openDevice].
     */
    private fun openOrRequest(device: UsbDevice) {
        val monitor = usbMonitor ?: return
        val desc = UsbXuLightController.describe(device)
        try {
            val granted = runCatching { monitor.hasPermission(device) }.getOrDefault(false) ||
                systemHasPermission(device)
            if (granted) {
                lastStatus = "UVC: permiso USB ya OK — abriendo $desc"
                Log.i(TAG, lastStatus)
                val ctrl = runCatching { monitor.openDevice(device) }.getOrNull()
                if (ctrl != null) {
                    openPreview(device, ctrl)
                } else {
                    // Fallback: requestPermission often triggers onConnect when already granted.
                    lastStatus = "UVC: openDevice null — requestPermission($desc)"
                    monitor.requestPermission(device)
                }
            } else {
                lastStatus = "UVC: pidiendo permiso USB ($desc). Si no sale diálogo, revise Ajustes→Apps→MLH→USB"
                Log.i(TAG, lastStatus)
                monitor.requestPermission(device)
            }
        } catch (e: Exception) {
            Log.e(TAG, "openOrRequest", e)
            lastStatus = "UVC open: ${e.message}"
        }
    }

    private fun openPreview(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
        val handler = cameraHandler ?: run {
            lastStatus = "UVC: sin handler (vista no lista)"
            return
        }
        val view = previewView ?: run {
            lastStatus = "UVC: sin TextureView"
            return
        }
        if (ctrlBlock == null || device == null) return
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
            fun startSurface() {
                val tex = view.surfaceTexture ?: return
                previewSurface?.release()
                previewSurface = Surface(tex)
                handler.startPreview(previewSurface)
            }
            if (view.surfaceTexture != null) {
                startSurface()
            } else {
                mainHandler.postDelayed({ startSurface() }, 300)
                mainHandler.postDelayed({ startSurface() }, 800)
            }
            lastStatus = "UVC frontal OK: ${UsbXuLightController.describe(device)}"
            Log.i(TAG, lastStatus)
            mainHandler.postDelayed({ runCatching { applyWhiteLight() } }, 250)
            completeReady(true)
        } catch (e: Exception) {
            lastStatus = "UVC error: ${e.message}"
            Log.e(TAG, "UVC connect failed", e)
            completeReady(false)
        }
    }

    private fun systemUsbDevices(): List<UsbDevice> = try {
        val mgr = activity.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return emptyList()
        mgr.deviceList.values.toList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun systemHasPermission(device: UsbDevice): Boolean = try {
        val mgr = activity.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return false
        mgr.hasPermission(device)
    } catch (_: Exception) {
        false
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
