package com.mlh.skinanalyzer.hardware

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
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
 *
 * USB open / UVC open run on a dedicated [usbIo] thread — never on the UI thread —
 * to avoid ANR while the MJ-008 firmware enumerates the camera.
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
    private val opening = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val usbThread = HandlerThread("mj008-uvc-io").also { it.start() }
    private val usbIo = Handler(usbThread.looper)
    private val openedDevice = AtomicReference<UsbDevice?>(null)
    @Volatile private var lastOpenAttemptMs: Long = 0L

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
            // createHandler loads R.raw.camera_click (0x7f0e0000) via SoundPool —
            // that raw file + public.xml ID pin must exist or this throws Resources.NotFoundException.
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
            patchUsbPermissionIntent(usbMonitor!!)
        }
        previewView?.onResume()
        usbMonitor?.register()
        mainHandler.postDelayed({ probeAttachedDevices() }, 200)
        mainHandler.postDelayed({ probeAttachedDevices() }, 1000)
        mainHandler.postDelayed({ probeAttachedDevices() }, 2500)
        mainHandler.postDelayed({ probeAttachedDevices() }, 5000)
    }

    /**
     * OEM USBMonitor.register() builds PendingIntent with flags=0 — illegal on API 31+.
     * Pre-install a mutable PendingIntent so register() skips creation.
     */
    private fun patchUsbPermissionIntent(monitor: USBMonitor) {
        try {
            val actionField = USBMonitor::class.java.getDeclaredField("ACTION_USB_PERMISSION")
            actionField.isAccessible = true
            val action = actionField.get(monitor) as String
            val flags = if (android.os.Build.VERSION.SDK_INT >= 31) {
                android.app.PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val pi = android.app.PendingIntent.getBroadcast(
                activity,
                0,
                android.content.Intent(action),
                flags,
            )
            val piField = USBMonitor::class.java.getDeclaredField("mPermissionIntent")
            piField.isAccessible = true
            piField.set(monitor, pi)
            Log.i(TAG, "USBMonitor PendingIntent patched for API ${android.os.Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not patch USBMonitor PendingIntent", e)
        }
    }

    fun retryConnect() {
        ready = CompletableDeferred()
        opening.set(false)
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
     * MJ-008 / Miaojing OEM (`CameraSamplingActPresenter`):
     * onAttach → filter USB3.0 / USB Camera → [USBMonitor.requestPermission] only.
     * onConnect → open + startPreview → delay 1s → white light (`controlLed`).
     *
     * We mirror requestPermission-first; if already granted and onConnect never
     * fires (common on this tablet), fall back to [openDevice] on [usbIo].
     */
    private fun openOrRequest(device: UsbDevice) {
        val monitor = usbMonitor ?: return
        if (isReady) return
        val now = SystemClock.elapsedRealtime()
        if (opening.get() && now - lastOpenAttemptMs < 8_000L) {
            Log.d(TAG, "openOrRequest skipped — already opening")
            return
        }
        if (!opening.compareAndSet(false, true)) return
        lastOpenAttemptMs = now
        val desc = UsbXuLightController.describe(device)
        try {
            if (ready.isCompleted && !isReady) {
                ready = CompletableDeferred()
            }
            val granted = runCatching { monitor.hasPermission(device) }.getOrDefault(false) ||
                systemHasPermission(device)
            lastStatus = if (granted) {
                "UVC: permiso OK — requestPermission($desc) como OEM…"
            } else {
                "UVC: pidiendo permiso USB ($desc). Si no sale diálogo, revise Ajustes→Apps→MLH→USB"
            }
            Log.i(TAG, lastStatus)
            // OEM path: always requestPermission; USBMonitor opens via onConnect.
            runCatching { monitor.requestPermission(device) }

            // Fallback if onConnect never arrives (already-granted / no dialog).
            usbIo.postDelayed({
                if (isReady || cameraHandler?.isOpened == true) {
                    opening.set(false)
                    return@postDelayed
                }
                if (!systemHasPermission(device) &&
                    runCatching { monitor.hasPermission(device) }.getOrDefault(false).not()
                ) {
                    opening.set(false)
                    lastStatus = "UVC: sin permiso USB para $desc"
                    return@postDelayed
                }
                lastStatus = "UVC: sin onConnect — openDevice($desc) en hilo USB…"
                Log.w(TAG, lastStatus)
                val ctrl = try {
                    monitor.openDevice(device)
                } catch (e: Exception) {
                    Log.e(TAG, "openDevice blocked/failed", e)
                    null
                }
                if (ctrl == null) {
                    opening.set(false)
                    lastStatus = "UVC: openDevice null — pulse Reintentar ($desc)"
                } else {
                    openPreviewOnUsbThread(device, ctrl)
                }
            }, 2_200L)

            usbIo.postDelayed({
                if (opening.get() && !isReady) {
                    Log.w(TAG, "open watchdog — still not ready after 12s")
                    opening.set(false)
                    lastStatus = "UVC: timeout abriendo $desc — pulse Reintentar"
                }
            }, 12_000L)
        } catch (e: Exception) {
            opening.set(false)
            Log.e(TAG, "openOrRequest", e)
            lastStatus = "UVC open: ${e.message}"
        }
    }

    private fun openPreview(
        device: UsbDevice?,
        ctrlBlock: USBMonitor.UsbControlBlock?,
    ) {
        // onConnect may arrive on USBMonitor's thread — route to usbIo.
        if (Looper.myLooper() == usbThread.looper) {
            openPreviewOnUsbThread(device, ctrlBlock)
        } else {
            usbIo.post { openPreviewOnUsbThread(device, ctrlBlock) }
        }
    }

    private fun openPreviewOnUsbThread(
        device: UsbDevice?,
        ctrlBlock: USBMonitor.UsbControlBlock?,
    ) {
        val handler = cameraHandler ?: run {
            opening.set(false)
            lastStatus = "UVC: sin handler (vista no lista)"
            return
        }
        val view = previewView ?: run {
            opening.set(false)
            lastStatus = "UVC: sin TextureView"
            return
        }
        if (ctrlBlock == null || device == null) {
            opening.set(false)
            return
        }
        if (!Mj008UsbDevices.isAnalyzerCamera(device)) {
            lastStatus = "UVC: rechazada secundaria ${UsbXuLightController.describe(device)}"
            Log.w(TAG, lastStatus)
            runCatching { ctrlBlock.close() }
            opening.set(false)
            return
        }
        try {
            lastStatus = "UVC: open() nativo ${UsbXuLightController.describe(device)}…"
            if (handler.isOpened) {
                runCatching { handler.close() }
            }
            handler.open(ctrlBlock)
            openedDevice.set(device)
            fun startSurface() {
                if (!started.get()) return
                val h = cameraHandler ?: return
                val tex = view.surfaceTexture ?: return
                previewSurface?.release()
                previewSurface = Surface(tex)
                h.startPreview(previewSurface)
            }
            mainHandler.post {
                if (!started.get()) return@post
                if (view.surfaceTexture != null) {
                    startSurface()
                } else {
                    mainHandler.postDelayed({ startSurface() }, 300)
                    mainHandler.postDelayed({ startSurface() }, 800)
                }
                lastStatus = "UVC frontal OK: ${UsbXuLightController.describe(device)}"
                Log.i(TAG, lastStatus)
                opening.set(false)
                completeReady(true)
                // OEM: sendEmptyMessageDelayed(1008, 1000) — “下发了白光指令”
                mainHandler.postDelayed({
                    if (started.get()) runCatching { applyWhiteLight() }
                }, LightMode.WHITE_LIGHT_DELAY_MS)
            }
        } catch (e: Exception) {
            opening.set(false)
            lastStatus = "UVC error: ${e.message}"
            Log.e(TAG, "UVC connect failed", e)
            completeReady(false)
        }
    }

    private fun systemUsbDevices(): List<UsbDevice> {
        return try {
            val mgr = activity.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return emptyList()
            mgr.deviceList.values.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun systemHasPermission(device: UsbDevice): Boolean {
        return try {
            val mgr = activity.getSystemService(Context.USB_SERVICE) as? UsbManager
                ?: return false
            mgr.hasPermission(device)
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { turnOff() }
        runCatching { usbMonitor?.unregister() }
        previewView?.onPause()
        mainHandler.removeCallbacksAndMessages(null)
    }

    /** Prefer live [isReady]; a prior failed complete must not block a later successful open. */
    suspend fun awaitReady(timeoutMs: Long = 25_000): Boolean {
        if (isReady) return true
        return try {
            withTimeout(timeoutMs) { ready.await() }
            isReady
        } catch (_: Exception) {
            isReady
        }
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
        opening.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { cameraHandler?.release() }
        runCatching { usbMonitor?.destroy() }
        previewSurface?.release()
        previewSurface = null
        cameraHandler = null
        usbMonitor = null
        previewView = null
        openedDevice.set(null)
        started.set(false)
        runCatching {
            usbIo.removeCallbacksAndMessages(null)
            usbThread.quitSafely()
        }
    }

    private fun completeReady(ok: Boolean) {
        // Always allow a later success after an earlier false complete.
        if (!ready.isCompleted) {
            ready.complete(ok)
        } else if (ok && isReady) {
            // Already completed false earlier — replace deferred for future awaiters.
            ready = CompletableDeferred(true)
        }
    }

    companion object {
        private const val TAG = "Mj008Uvc"
    }
}
