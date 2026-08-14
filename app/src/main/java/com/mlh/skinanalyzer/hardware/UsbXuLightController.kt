package com.mlh.skinanalyzer.hardware

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MJ-008 LED control via the OEM USB protocol (reverse-engineered from Miaojing APK
 * `libUVCCamera.so` → `UVCCamera::xu_write`).
 *
 * Not classic UVC XU SET_CUR. The firmware expects two class-interface transfers:
 *
 * 1) SETUP  bmRequestType=0x21 request=0x01 wValue=0x0A00 wIndex=0x0400
 *    data[8] = unit_hi, unit_lo, addr_lo, addr_hi, len_lo, len_hi, addr_b16, 0
 * 2) DATA   bmRequestType=0x21 request=0x01 wValue=0x0B00 wIndex=0x0400
 *    data[8] = payload (4 bytes) zero-padded
 *
 * Unit=130 (0x82), address=55318 (0xD816), payload=`00 78 cmd arg`.
 */
class UsbXuLightController(
    context: Context,
) : LightController {
    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var claimedInterface: UsbInterface? = null

    override var isOpen: Boolean = false
        private set
    override var lastError: String? = null
        private set
    override val backendLabel: String = "USB-XU"

    override fun open(): Boolean {
        close()
        return try {
            val cam = findMj008Camera()
            if (cam == null) {
                lastError = "MJ-008: cámara USB no detectada (USB3.0 / USB Camera)"
                Log.w(TAG, lastError!!)
                return false
            }
            if (!usbManager.hasPermission(cam)) {
                val granted = requestPermissionBlocking(cam)
                if (!granted) {
                    lastError = "MJ-008: permiso USB denegado para ${describe(cam)}"
                    Log.w(TAG, lastError!!)
                    return false
                }
            }
            val conn = usbManager.openDevice(cam)
            if (conn == null) {
                lastError = "MJ-008: no se pudo abrir ${describe(cam)}"
                return false
            }
            val intf = pickControlInterface(cam)
            if (intf != null) {
                // Force claim so controlTransfer reaches the LED MCU even if another
                // driver briefly holds the interface on some MJ firmwares.
                runCatching { conn.claimInterface(intf, true) }
                claimedInterface = intf
            }
            device = cam
            connection = conn
            isOpen = true
            lastError = null
            Log.i(TAG, "Opened USB LED path on ${describe(cam)}")
            // Warm white so operator sees hardware is alive
            applyRaw(CMD_WHITE, ARG_ON)
            true
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "USB XU open failed", e)
            close()
            false
        }
    }

    override fun close() {
        try {
            if (isOpen) turnOff()
        } catch (_: Exception) {
        }
        try {
            claimedInterface?.let { connection?.releaseInterface(it) }
        } catch (_: Exception) {
        }
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        claimedInterface = null
        connection = null
        device = null
        isOpen = false
    }

    override fun turnOff() {
        applyRaw(CMD_WOODS, ARG_OFF) // OEM close = 00 78 13 00
    }

    override fun applyLightMode(mode: LightMode) {
        val cmd = mode.usbCmd ?: return
        try {
            applyRaw(cmd, ARG_ON)
            Thread.sleep(SETTLE_MS)
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "applyLightMode failed", e)
        }
    }

    fun applyRaw(cmd: Int, arg: Int): Boolean {
        val conn = connection
        if (conn == null) {
            lastError = "USB XU no abierto"
            return false
        }
        val payload = byteArrayOf(0x00, 0x78, cmd.toByte(), arg.toByte())
        return xuWrite(conn, UNIT_ID, LIGHT_ADDR, payload)
    }

    private fun xuWrite(
        conn: UsbDeviceConnection,
        unit: Int,
        address: Int,
        payload: ByteArray,
    ): Boolean {
        val length = payload.size
        val header = ByteArray(8).also { h ->
            h[0] = ((unit ushr 8) and 0xFF).toByte()
            h[1] = (unit and 0xFF).toByte()
            h[2] = (address and 0xFF).toByte()
            h[3] = ((address ushr 8) and 0xFF).toByte()
            h[4] = (length and 0xFF).toByte()
            h[5] = ((length ushr 8) and 0xFF).toByte()
            h[6] = ((address ushr 16) and 0xFF).toByte()
            h[7] = 0
        }
        val setup = controlOut(conn, WVALUE_SETUP, header)
        if (setup < 0) {
            lastError = "USB XU SETUP falló ($setup)"
            Log.e(TAG, lastError!!)
            return false
        }
        // OEM pads each data chunk to 8 bytes
        var offset = 0
        while (offset < length) {
            val chunk = ByteArray(8)
            val n = minOf(8, length - offset)
            System.arraycopy(payload, offset, chunk, 0, n)
            val wrote = controlOut(conn, WVALUE_DATA, chunk)
            if (wrote < 0) {
                lastError = "USB XU DATA falló ($wrote) @off=$offset"
                Log.e(TAG, lastError!!)
                return false
            }
            offset += n
            if (offset < length) Thread.sleep(1)
        }
        Log.d(
            TAG,
            "XU OK unit=$unit addr=0x${Integer.toHexString(address)} " +
                "payload=${payload.joinToString(" ") { "%02X".format(it) }}",
        )
        return true
    }

    private fun controlOut(conn: UsbDeviceConnection, wValue: Int, data: ByteArray): Int {
        return conn.controlTransfer(
            /* requestType */ 0x21, // Host→device | Class | Interface
            /* request */ 0x01, // SET_CUR
            /* value */ wValue,
            /* index */ WINDEX,
            /* buffer */ data,
            /* length */ data.size,
            /* timeout */ TIMEOUT_MS,
        )
    }

    private fun findMj008Camera(): UsbDevice? {
        val devices = usbManager.deviceList.values.toList()
        val preferred = devices.firstOrNull { isMj008Camera(it) }
        if (preferred != null) return preferred
        // Fallback: first video-class device
        return devices.firstOrNull { hasVideoInterface(it) }
    }

    private fun pickControlInterface(device: UsbDevice): UsbInterface? {
        // Prefer video control (class 14, subclass 1) else first interface
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_VIDEO) return intf
        }
        return if (device.interfaceCount > 0) device.getInterface(0) else null
    }

    private fun requestPermissionBlocking(device: UsbDevice): Boolean {
        val latch = CountDownLatch(1)
        val granted = AtomicBoolean(false)
        val action = ACTION_USB_PERMISSION
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != action) return
                granted.set(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                latch.countDown()
            }
        }
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val pi = PendingIntent.getBroadcast(appContext, 0, Intent(action), flags)
        usbManager.requestPermission(device, pi)
        val ok = latch.await(20, TimeUnit.SECONDS) && granted.get()
        runCatching { appContext.unregisterReceiver(receiver) }
        return ok || usbManager.hasPermission(device)
    }

    companion object {
        private const val TAG = "Mj008UsbXu"
        private const val ACTION_USB_PERMISSION = "com.mlh.skinanalyzer.USB_PERMISSION"
        private const val UNIT_ID = 130 // 0x82
        private const val LIGHT_ADDR = 55318 // 0xD816
        private const val WVALUE_SETUP = 0x0A00
        private const val WINDEX = 0x0400
        private const val WVALUE_DATA = 0x0B00
        private const val TIMEOUT_MS = 1000
        private const val SETTLE_MS = 180L

        const val CMD_WHITE = 0x10
        const val CMD_NEGATIVE = 0x11
        const val CMD_POSITIVE = 0x12
        const val CMD_WOODS = 0x13
        const val CMD_UV = 0x14
        const val CMD_BLUE = 0x15
        const val CMD_ORANGE = 0x16
        const val CMD_RED = 0x17
        const val ARG_ON = 0xFF
        const val ARG_OFF = 0x00

        fun isMj008Camera(device: UsbDevice): Boolean {
            val serial = runCatching { device.serialNumber }.getOrNull().orEmpty()
            val product = runCatching { device.productName }.getOrNull().orEmpty()
            if (serial.equals("USB3.0", ignoreCase = true)) return true
            if (product.equals("USB3.0", ignoreCase = true)) return true
            if (product.equals("USB Camera", ignoreCase = true)) return true
            if (device.productId in Mj008Hardware.knownCameraProductIds) return true
            return false
        }

        fun hasVideoInterface(device: UsbDevice): Boolean {
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                    return true
                }
            }
            return false
        }

        fun describe(device: UsbDevice): String {
            val name = runCatching { device.productName }.getOrNull() ?: "USB"
            return "$name vid=${device.vendorId} pid=${device.productId}"
        }

        /** Builds the 4-byte OEM light payload for tests / diagnostics. */
        fun lightPayload(cmd: Int, arg: Int = ARG_ON): ByteArray =
            byteArrayOf(0x00, 0x78, cmd.toByte(), arg.toByte())

        fun setupHeader(unit: Int = UNIT_ID, address: Int = LIGHT_ADDR, length: Int = 4): ByteArray =
            byteArrayOf(
                ((unit ushr 8) and 0xFF).toByte(),
                (unit and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                ((address ushr 8) and 0xFF).toByte(),
                (length and 0xFF).toByte(),
                ((length ushr 8) and 0xFF).toByte(),
                ((address ushr 16) and 0xFF).toByte(),
                0,
            )
    }
}
