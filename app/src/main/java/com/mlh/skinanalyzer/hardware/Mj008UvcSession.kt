package com.mlh.skinanalyzer.hardware

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
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
import com.serenegiant.widget.CameraViewInterface
import com.serenegiant.widget.UVCCameraTextureView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
            if (released.get()) return "Sesión cerrada"
            return when {
                previewReady.get() && lightsOn -> "Listo para capturar"
                previewReady.get() -> "Preparando luces…"
                cameraOpenFlag.get() -> "Iniciando cámara…"
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
        view.setCallback(object : CameraViewInterface.Callback {
            override fun onSurfaceCreated(view: CameraViewInterface, surface: Surface) {
                Log.d(TAG, "onSurfaceCreated")
            }

            override fun onSurfaceChanged(view: CameraViewInterface, surface: Surface, width: Int, height: Int) {
                Log.d(TAG, "onSurfaceChanged ${width}x$height")
            }

            override fun onSurfaceDestroy(view: CameraViewInterface, surface: Surface) {
                Log.i(TAG, "onSurfaceDestroy — releasing preview surface")
                previewReady.set(false)
                lightsOn = false
                runCatching { previewSurface?.release() }
                previewSurface = null
            }
        })
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
                        /* encoderType */ 1,
                        Mj008Hardware.PREVIEW_WIDTH,
                        Mj008Hardware.PREVIEW_HEIGHT,
                        /* pixelFormat */ 1,
                        Mj008Hardware.PREVIEW_ORIENTATION, // 90 — same as on-screen preview
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
            // Prefer system UsbManager — avoid wedged USBMonitor.hasPermission().
            val granted = systemHasPermission(device)

            if (granted) {
                // Do NOT call requestPermission when already granted: processConnect →
                // UsbControlBlock.updateDeviceInfo often stalls and never hits onConnect
                // (UI stuck at "requestPermission…" — the v23 symptom).
                lastStatus = "UVC: permiso OK — openDevice directo ($desc)…"
                Log.i(TAG, lastStatus)
                openDeviceDirect(device)
            } else {
                lastStatus = "UVC: pidiendo permiso USB ($desc)"
                Log.i(TAG, lastStatus)
                runCatching { monitor.requestPermission(device) }
                mainHandler.postDelayed({
                    if (released.get() || previewReady.get()) return@postDelayed
                    if (systemHasPermission(device)) {
                        lastStatus = "UVC: permiso concedido — openDevice ($desc)…"
                        openDeviceDirect(device)
                    }
                }, 3_000L)
            }

            // Main watchdog — does not depend on usbIo (may be blocked in native).
            mainHandler.postDelayed({
                if (released.get() || previewReady.get()) return@postDelayed
                opening.set(false)
                lastStatus =
                    "UVC: timeout 10s ($desc). ¿App china abierta? Reintentar o Demo."
                Log.w(TAG, lastStatus)
                completeReady(false)
            }, 10_000L)
        } catch (e: Exception) {
            opening.set(false)
            Log.e(TAG, "openOrRequest", e)
            lastStatus = "UVC open: ${e.message}"
        }
    }

    /**
     * openDevice on a throwaway thread with timeout so a STALL cannot leave the
     * UI frozen on the previous status line forever.
     */
    private fun openDeviceDirect(device: UsbDevice) {
        val monitor = usbMonitor ?: return
        val desc = UsbXuLightController.describe(device)
        Thread({
            if (released.get() || previewReady.get() || cameraOpenFlag.get()) return@Thread
            lastStatus = "UVC: openDevice($desc)…"
            Log.i(TAG, lastStatus)
            val ctrl = try {
                val future = java.util.concurrent.FutureTask {
                    try {
                        monitor.openDevice(device)
                    } catch (e: Exception) {
                        Log.e(TAG, "openDevice failed", e)
                        null
                    }
                }
                Thread(future, "mj008-openDevice").apply { isDaemon = true; start() }
                future.get(6, java.util.concurrent.TimeUnit.SECONDS)
            } catch (_: java.util.concurrent.TimeoutException) {
                Log.e(TAG, "openDevice timeout 6s")
                lastStatus = "UVC: openDevice timeout — USB ocupado/STALL. Demo o Reintentar."
                opening.set(false)
                null
            } catch (e: Exception) {
                Log.e(TAG, "openDeviceDirect", e)
                lastStatus = "UVC: openDevice error ${e.message}"
                opening.set(false)
                null
            }
            if (ctrl == null) {
                if (opening.get() && !lastStatus.contains("timeout") && !lastStatus.contains("error")) {
                    opening.set(false)
                    lastStatus = "UVC: openDevice null — Reintentar ($desc)"
                }
                return@Thread
            }
            usbIo.post {
                if (released.get() || previewReady.get()) return@post
                openPreviewOnUsbThread(device, ctrl)
            }
        }, "mj008-open-direct").apply { isDaemon = true; start() }
    }

    private fun openPreviewOnUsbThread(
        device: UsbDevice?,
        ctrlBlock: USBMonitor.UsbControlBlock?,
    ) {
        if (released.get()) return
        if (previewReady.get()) return
        if (cameraOpenFlag.get()) {
            Log.d(TAG, "openPreview skipped — already opening camera")
            return
        }
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
                if (previewReady.get()) {
                    Log.d(TAG, "startSurface skipped — preview already ready")
                    return
                }
                val h = cameraHandler ?: return
                val tex = view.surfaceTexture ?: run {
                    Log.w(TAG, "startSurface: surfaceTexture null")
                    return
                }
                try {
                    previewSurface?.release()
                    previewSurface = Surface(tex)
                    h.startPreview(previewSurface)
                    previewReady.set(true)
                    opening.set(false)
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
                } catch (e: Exception) {
                    Log.e(TAG, "startSurface / preview failed (EGL?)", e)
                    runCatching { previewSurface?.release() }
                    previewSurface = null
                    previewReady.set(false)
                    opening.set(false)
                    lastStatus = "UVC: error de superficie — Reintentar (${e.message})"
                    completeReady(false)
                }
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

    fun applyWhiteLight() {
        usbIo.post {
            applyLightMode(LightMode.WHITE)
            lightsOn = true
        }
    }

    /**
     * Brief §2: luces reutilizan el handle UVC vía [UVCCameraHandler.controlLed]
     * (native xu_write). Nunca [UsbManager.openDevice] ni claimInterface aparte.
     * Canales/payload: [MaokinLightController] / [UsbXuLightController.lightPayload].
     */
    fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        if (!previewReady.get() || released.get()) {
            Log.w(TAG, "applyLightMode skipped — preview not ready")
            return
        }
        val handler = cameraHandler ?: return
        val payload = UsbXuLightController.lightPayload(cmd, UsbXuLightController.ARG_ON)
        try {
            handler.controlLed(UsbXuLightController.UNIT_ID, UsbXuLightController.LIGHT_ADDR, payload)
            lightsOn = true
            lastStatus = "LED ${mode.shortName} ON (controlLed)"
            Log.i(TAG, "LED ${mode.shortName} via controlLed unit=130 addr=0xD816")
        } catch (e: Exception) {
            Log.e(TAG, "controlLed failed", e)
            lastStatus = "LED error: ${e.message}"
        }
    }

    /**
     * Apaga LEDs (canal 0x13 / Woods off). Best-effort con tope para no colgar
     * la UI al salir de Captura.
     */
    fun turnOff() {
        if (released.get()) {
            lightsOn = false
            return
        }
        val handler = cameraHandler
        if (handler == null) {
            lightsOn = false
            return
        }
        val latch = CountDownLatch(1)
        usbIo.post {
            try {
                runCatching {
                    handler.controlLed(
                        UsbXuLightController.UNIT_ID,
                        UsbXuLightController.LIGHT_ADDR,
                        UsbXuLightController.lightPayload(
                            UsbXuLightController.CMD_WOODS,
                            UsbXuLightController.ARG_OFF,
                        ),
                    )
                }.onFailure { Log.w(TAG, "turnOff controlLed: ${it.message}") }
                lightsOn = false
                lastStatus = "LED OFF (controlLed)"
                Log.i(TAG, "LED OFF via controlLed")
            } finally {
                latch.countDown()
            }
        }
        if (!latch.await(1_500, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "turnOff timed out (1.5s) — luces pueden seguir encendidas")
            lightsOn = false
        }
    }

    /** True while our session still considers preview usable. */
    fun isCameraAlive(): Boolean = previewReady.get() && !released.get() && cameraHandler != null

    /**
     * Captura still: frame fresco → rotación en píxeles → JPEG.
     * Verifica dimensiones leyendo el archivo de disco (DISCO=).
     */
    suspend fun captureStill(target: File): Bitmap? {
        if (!isCameraAlive()) {
            Log.e(TAG, "captureStill aborted — camera not alive")
            return null
        }
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()

        val raw = grabFreshFrame()
        if (raw == null) {
            Log.e(TAG, "captureStill: no fresh frame")
            return null
        }

        // Autocalibrar una sola vez sobre el frame crudo (antes de guardar).
        if (!CapturePrefs.isRotationCalibrated(activity)) {
            val detected = withContext(Dispatchers.Default) {
                com.mlh.skinanalyzer.analysis.oem.OemFaceLandmarks.detectBestRotation(activity, raw)
            }
            CapturePrefs.setCaptureRotationDeg(activity, detected)
        }

        val deg = CapturePrefs.captureRotationDeg(activity)
        val mirror = CapturePrefs.MIRROR_HORIZONTAL
        // Sensor es 1600×1200 apaisado → rotar a 1200×1600. Si ya viene portrait, no girar de más.
        val effectiveDeg = when {
            raw.width > raw.height -> deg
            raw.height > raw.width && (deg == 90 || deg == 270) -> {
                Log.i(TAG, "frame ya portrait ${raw.width}x${raw.height} — sin rotación extra")
                0
            }
            else -> deg
        }

        val oriented = CapturePrefs.transformBitmap(raw, effectiveDeg, mirror)
        if (oriented !== raw) {
            runCatching { raw.recycle() }
        }

        withContext(Dispatchers.IO) {
            java.io.FileOutputStream(target).use { fos ->
                // IMPORTANTE: comprimir oriented, NUNCA raw
                oriented.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, fos)
                fos.flush()
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(target.absolutePath, bounds)
            Log.i(
                TAG,
                "guardado ${target.name} rot=$effectiveDeg mirror=$mirror " +
                    "memoria=${oriented.width}x${oriented.height} " +
                    "DISCO=${bounds.outWidth}x${bounds.outHeight} " +
                    "${target.length()} bytes",
            )
            if (bounds.outWidth > bounds.outHeight) {
                Log.e(TAG, "ARCHIVO APAISADO EN DISCO — la rotación no llegó al fichero")
            }
        }
        return oriented
    }

    /**
     * Descarta frames de la luz anterior y toma uno nuevo (copia independiente).
     */
    private suspend fun grabFreshFrame(): Bitmap? {
        val view = previewView ?: return null
        // Descartar frames en vuelo del pipeline.
        repeat(3) {
            withContext(Dispatchers.Main) {
                runCatching { view.bitmap }
            }
            delay(50)
        }
        return withContext(Dispatchers.Main) {
            val frame = runCatching { view.captureStillImage() }.getOrNull()
                ?.takeIf { !it.isRecycled && it.width > 0 }
                ?: runCatching { view.bitmap }.getOrNull()
                    ?.takeIf { !it.isRecycled && it.width > 0 }
            frame?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        }
    }

    /**
     * Abandon USB without waiting. [UVCCameraHandler.release]/[close]/[stopPreview] and
     * [USBMonitor.destroy] can block forever on MJ-008; joining them ANRs Captura.
     */
    fun release() {
        if (released.get()) return
        // Apagar LEDs mientras el handler aún existe.
        runCatching { turnOff() }
        if (!released.compareAndSet(false, true)) return
        started.set(false)
        opening.set(false)
        previewReady.set(false)
        cameraOpenFlag.set(false)
        lightsOn = false
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
