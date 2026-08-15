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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * OEM UVC camera session for MJ-008 (1600×1200 @ 0° front) using Miaojing serenegiant stack.
 *
 * Critical on this tablet: never call into [UVCCameraHandler.isOpened] / [stopPreview] / [close]
 * from the UI thread. Those take CameraThread locks or wait forever while native USB is stalled
 * ([LIBUSB_TRANSFER_STALL]), which is the Captura ANR (“abriendo sesión…”).
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
    private val released = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val usbThread = HandlerThread("mj008-uvc-io").also { it.start() }
    private val usbIo = Handler(usbThread.looper)
    private val openedDevice = AtomicReference<UsbDevice?>(null)
    /** Same UsbDeviceConnection as UVC — never a second openDevice for LEDs. */
    private val maokinLights = AtomicReference<MaokinLightController?>(null)
    private val ctrlBlockRef = AtomicReference<USBMonitor.UsbControlBlock?>(null)

    /** Set only from our code — never derived from handler.isOpened (that can deadlock). */
    private val previewReady = AtomicBoolean(false)
    private val cameraOpenFlag = AtomicBoolean(false)

    @Volatile private var lastOpenAttemptMs: Long = 0L

    @Volatile var lastStatus: String = "UVC: inactivo"
        private set

    @Volatile var lightsOn: Boolean = false
        private set

    val isReady: Boolean
        get() = previewReady.get() && !released.get()

    val statusLabel: String
        get() {
            if (released.get()) return "UVC: liberada"
            val d = openedDevice.get()
            val name = d?.let { UsbXuLightController.describe(it) } ?: "analizador"
            return when {
                previewReady.get() && lightsOn -> "Cámara + luces ON · $name"
                previewReady.get() -> "Cámara frontal · $name · encendiendo luces…"
                cameraOpenFlag.get() -> "UVC abierta · iniciando preview…"
                else -> lastStatus
            }
        }

    private val deviceListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            if (device == null || released.get()) return
            if (!Mj008UsbDevices.isAnalyzerCamera(device)) {
                Log.d(TAG, "Ignorando USB (no analizador): ${UsbXuLightController.describe(device)}")
                return
            }
            usbIo.post { openOrRequest(device) }
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean,
        ) {
            if (released.get()) return
            usbIo.post { openPreviewOnUsbThread(device, ctrlBlock) }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            // Do NOT call stopPreview/close here — they can wait forever on stalled USB.
            previewReady.set(false)
            cameraOpenFlag.set(false)
            openedDevice.set(null)
            lightsOn = false
            lastStatus = "UVC: desconectada"
            completeReady(false)
        }

        override fun onDetach(device: UsbDevice?) {
            previewReady.set(false)
            cameraOpenFlag.set(false)
            openedDevice.set(null)
            lightsOn = false
            lastStatus = "UVC: detach"
            completeReady(false)
        }

        override fun onCancel(device: UsbDevice?) {
            lastStatus = "UVC: permiso USB denegado en el sistema"
            Log.w(TAG, lastStatus)
            opening.set(false)
            completeReady(false)
        }
    }

    /**
     * Create UVC handler off the UI thread. [UVCCameraHandler.createHandler] starts a
     * CameraThread and [getHandler] waits — keep that wait off Main.
     */
    fun bindPreview(view: UVCCameraTextureView): Boolean {
        if (released.get()) return false
        previewView = view
        if (cameraHandler != null) return true
        val latch = CountDownLatch(1)
        val holder = AtomicReference<UVCCameraHandler?>()
        val error = AtomicReference<String?>()
        Thread({
            try {
                holder.set(
                    UVCCameraHandler.createHandler(
                        activity,
                        view,
                        2,
                        Mj008Hardware.PREVIEW_WIDTH,
                        Mj008Hardware.PREVIEW_HEIGHT,
                        1,
                        Mj008Hardware.PREVIEW_ORIENTATION,
                    ),
                )
            } catch (e: Exception) {
                Log.e(TAG, "createHandler failed", e)
                error.set(e.message ?: e.javaClass.simpleName)
            } finally {
                latch.countDown()
            }
        }, "mj008-uvc-handler").apply { isDaemon = true; start() }

        val ok = latch.await(4, TimeUnit.SECONDS)
        if (!ok) {
            lastStatus = "UVC: timeout creando handler (4s)"
            Log.e(TAG, lastStatus)
            return false
        }
        val handler = holder.get()
        if (handler == null) {
            lastStatus = "UVC: handler null — ${error.get() ?: "error"}"
            return false
        }
        cameraHandler = handler
        return true
    }

    fun start() {
        if (released.get()) return
        if (!started.compareAndSet(false, true)) {
            usbIo.post { probeAttachedDevices() }
            return
        }
        ready = CompletableDeferred()
        previewReady.set(false)
        cameraOpenFlag.set(false)
        lastStatus = "UVC: buscando cámara frontal USB3.0…"
        // Entire USBMonitor lifecycle on usbIo — register() is synchronized and can
        // block behind a hung destroy() of a previous session on another monitor,
        // but at least Main stays alive for Demo / Reintentar.
        usbIo.post {
            if (released.get() || !started.get()) return@post
            try {
                if (usbMonitor == null) {
                    usbMonitor = USBMonitor(activity, deviceListener)
                    patchUsbPermissionIntent(usbMonitor!!)
                }
                mainHandler.post { runCatching { previewView?.onResume() } }
                usbMonitor?.register()
                lastStatus = "UVC: USBMonitor registrado — sondeando…"
                probeAttachedDevices()
                usbIo.postDelayed({ if (!released.get()) probeAttachedDevices() }, 800)
                usbIo.postDelayed({ if (!released.get()) probeAttachedDevices() }, 2_000)
                usbIo.postDelayed({ if (!released.get()) probeAttachedDevices() }, 4_500)
            } catch (e: Exception) {
                Log.e(TAG, "start/register", e)
                lastStatus = "UVC register: ${e.message}"
                opening.set(false)
            }
        }
    }

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
        if (released.get()) return
        ready = CompletableDeferred()
        opening.set(false)
        previewReady.set(false)
        cameraOpenFlag.set(false)
        lastStatus = "UVC: reintentando cámara frontal…"
        // Skip handler.close() — it can block forever (stopPreview wait).
        openedDevice.set(null)
        usbIo.post { probeAttachedDevices() }
    }

    private fun probeAttachedDevices() {
        if (released.get()) return
        val monitor = usbMonitor ?: return
        try {
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

    private fun openOrRequest(device: UsbDevice) {
        if (released.get()) return
        val monitor = usbMonitor ?: return
        if (previewReady.get()) return
        val now = SystemClock.elapsedRealtime()
        if (opening.get() && now - lastOpenAttemptMs < 8_000L) {
            Log.d(TAG, "openOrRequest skipped — already opening")
            return
        }
        if (!opening.compareAndSet(false, true)) return
        lastOpenAttemptMs = now
        val desc = UsbXuLightController.describe(device)
        try {
            if (ready.isCompleted && !previewReady.get()) {
                ready = CompletableDeferred()
            }
            val granted = runCatching { monitor.hasPermission(device) }.getOrDefault(false) ||
                systemHasPermission(device)
            lastStatus = if (granted) {
                "UVC: permiso OK — requestPermission($desc)…"
            } else {
                "UVC: pidiendo permiso USB ($desc)"
            }
            Log.i(TAG, lastStatus)
            runCatching { monitor.requestPermission(device) }

            usbIo.postDelayed({
                if (released.get() || previewReady.get() || cameraOpenFlag.get()) {
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
                lastStatus = "UVC: sin onConnect — openDevice($desc)…"
                Log.w(TAG, lastStatus)
                val ctrl = try {
                    monitor.openDevice(device)
                } catch (e: Exception) {
                    Log.e(TAG, "openDevice blocked/failed", e)
                    null
                }
                if (ctrl == null) {
                    opening.set(false)
                    lastStatus = "UVC: openDevice null — Reintentar ($desc)"
                } else {
                    openPreviewOnUsbThread(device, ctrl)
                }
            }, 2_200L)

            usbIo.postDelayed({
                if (opening.get() && !previewReady.get()) {
                    Log.w(TAG, "open watchdog — still not ready after 12s")
                    opening.set(false)
                    lastStatus = "UVC: timeout abriendo $desc — Reintentar o Demo"
                }
            }, 12_000L)
        } catch (e: Exception) {
            opening.set(false)
            Log.e(TAG, "openOrRequest", e)
            lastStatus = "UVC open: ${e.message}"
        }
    }

    private fun openPreviewOnUsbThread(
        device: UsbDevice?,
        ctrlBlock: USBMonitor.UsbControlBlock?,
    ) {
        if (released.get()) return
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
            // Never handler.close() here — stopPreview waits forever on stalled USB.
            // Orden OEM: primero cámara (open + preview), luces solo después.
            handler.open(ctrlBlock)
            cameraOpenFlag.set(true)
            openedDevice.set(device)
            ctrlBlockRef.set(ctrlBlock)

            fun startSurface() {
                if (!started.get() || released.get()) return
                val h = cameraHandler ?: return
                val tex = view.surfaceTexture ?: return
                previewSurface?.release()
                previewSurface = Surface(tex)
                h.startPreview(previewSurface)
                previewReady.set(true)
                opening.set(false)
                // Luces DESPUÉS de preview, misma UsbDeviceConnection (nunca openDevice 2º).
                bindMaokinLights(ctrlBlock)
                lastStatus = "UVC frontal OK: ${UsbXuLightController.describe(device)}"
                Log.i(TAG, lastStatus)
                completeReady(true)
                usbIo.postDelayed({
                    if (started.get() && !released.get() && previewReady.get()) {
                        runCatching { applyLightMode(LightMode.WHITE) }
                        lightsOn = true
                        lastStatus =
                            "UVC + luces blancas ON · ${UsbXuLightController.describe(device)}"
                        Log.i(TAG, lastStatus)
                    }
                }, LightMode.WHITE_LIGHT_DELAY_MS)
            }

            mainHandler.post {
                if (!started.get() || released.get()) return@post
                if (view.surfaceTexture != null) {
                    usbIo.post { startSurface() }
                } else {
                    mainHandler.postDelayed({
                        usbIo.post { startSurface() }
                    }, 300)
                    mainHandler.postDelayed({
                        usbIo.post { startSurface() }
                    }, 800)
                }
            }
        } catch (e: Exception) {
            opening.set(false)
            cameraOpenFlag.set(false)
            maokinLights.set(null)
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
        previewReady.set(false)
        // Skip turnOff — controlLed can hang. Skip blocking close.
        mainHandler.removeCallbacksAndMessages(null)
        usbIo.post {
            runCatching { usbMonitor?.unregister() }
            mainHandler.post { runCatching { previewView?.onPause() } }
        }
    }

    suspend fun awaitReady(timeoutMs: Long = 25_000): Boolean {
        if (isReady) return true
        return try {
            withTimeout(timeoutMs) { ready.await() }
            isReady
        } catch (_: Exception) {
            isReady
        }
    }

    private fun bindMaokinLights(ctrlBlock: USBMonitor.UsbControlBlock) {
        val conn = runCatching { ctrlBlock.connection }.getOrNull()
        if (conn == null) {
            Log.w(TAG, "MaokinLight: sin UsbDeviceConnection en ctrlBlock")
            maokinLights.set(null)
            return
        }
        // Same connection UVC already opened — do NOT openDevice again.
        // Avoid force claimInterface here (can STALL while preview is live);
        // native UVC open already claimed VideoControl on this connection.
        maokinLights.set(MaokinLightController(conn))
        Log.i(TAG, "MaokinLight bound to same UVC UsbDeviceConnection")
    }

    fun applyWhiteLight() {
        usbIo.post {
            applyLightMode(LightMode.WHITE)
            lightsOn = true
        }
    }

    /**
     * Drive LEDs on the UVC connection only ([MaokinLightController]).
     * Never open a second USB handle here.
     */
    fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        // Never before previewReady (onConnect → open → startPreview).
        if (!previewReady.get() || released.get()) {
            Log.w(TAG, "applyLightMode skipped — preview not ready")
            return
        }
        val lights = maokinLights.get()
        if (lights != null) {
            val ok = lights.turnOn(cmd)
            lightsOn = ok
            if (ok) {
                Log.i(TAG, "LED ${mode.shortName} via MaokinLight (same USB as UVC)")
            } else {
                lastStatus = "LED Maokin falló (${mode.shortName})"
                // Fallback: native xu_write on same UVC fd (still one process).
                fallbackControlLed(cmd)
            }
            return
        }
        fallbackControlLed(cmd)
    }

    private fun fallbackControlLed(cmd: Int) {
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(cmd, UsbXuLightController.ARG_ON)
        try {
            handler.controlLed(130, 55318, payload)
            lightsOn = true
            Log.i(TAG, "LED cmd=0x${Integer.toHexString(cmd)} via controlLed fallback")
        } catch (e: Exception) {
            Log.e(TAG, "controlLed failed", e)
            lastStatus = "LED error: ${e.message}"
        }
    }

    fun turnOff() {
        if (!previewReady.get() || released.get()) return
        usbIo.post {
            val lights = maokinLights.get()
            if (lights != null) {
                runCatching { lights.turnOff() }
            } else {
                runCatching {
                    cameraHandler?.controlLed(
                        130,
                        55318,
                        UsbXuLightController.lightPayload(
                            UsbXuLightController.CMD_WOODS,
                            UsbXuLightController.ARG_OFF,
                        ),
                    )
                }
            }
            lightsOn = false
        }
    }

    suspend fun captureStill(target: File): Bitmap? {
        val handler = cameraHandler ?: return null
        if (!previewReady.get()) return null
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

    /**
     * Abandon USB without waiting. [UVCCameraHandler.release]/[close]/[stopPreview] and
     * [USBMonitor.destroy] can block forever on MJ-008; joining them ANRs Captura.
     */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        started.set(false)
        opening.set(false)
        previewReady.set(false)
        cameraOpenFlag.set(false)
        lightsOn = false
        maokinLights.set(null)
        ctrlBlockRef.set(null)
        mainHandler.removeCallbacksAndMessages(null)
        val monitor = usbMonitor
        val handler = cameraHandler
        usbMonitor = null
        cameraHandler = null
        previewView = null
        openedDevice.set(null)
        previewSurface?.release()
        previewSurface = null
        // Fire-and-forget teardown on a daemon — never join.
        Thread({
            runCatching { monitor?.unregister() }
            // Skip monitor.destroy() / handler.release() — known to hang on this firmware.
            runCatching { usbIo.removeCallbacksAndMessages(null) }
            runCatching { usbThread.quitSafely() }
            Log.i(TAG, "release abandoned monitor/handler without blocking close")
        }, "mj008-uvc-abandon").apply { isDaemon = true; start() }
        // Silence unused
        Log.d(TAG, "release: dropped handler=${handler != null}")
    }

    private fun completeReady(ok: Boolean) {
        if (!ready.isCompleted) {
            ready.complete(ok)
        } else if (ok && previewReady.get()) {
            ready = CompletableDeferred(true)
        }
    }

    companion object {
        private const val TAG = "Mj008Uvc"
    }
}
