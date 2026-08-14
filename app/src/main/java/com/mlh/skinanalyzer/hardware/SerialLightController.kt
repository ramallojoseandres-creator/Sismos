package com.mlh.skinanalyzer.hardware

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * MJ-008 light board controller via UART `/dev/ttyS4`.
 *
 * Text protocol (115200, GB18030 + "\n\r"):
 *   TCCCMD_W / N / P / UV / WS + percent   (center channel)
 *   TCLCMD_* / TCRCMD_*                    (left / right)
 *   TCCMD_OFF, TCCMD_PWM_SETL, TC_HEART
 *
 * Legacy binary (9600): AA 66 cmd arg 23 — kept as fallback for older MJ boards.
 */
class SerialLightController(
    private val devicePath: String = Mj008Hardware.SERIAL_DEVICE,
    private var baudRate: Int = Mj008Hardware.SERIAL_BAUD_PRIMARY,
) : LightController {
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var fdHolder: Any? = null
    override var isOpen: Boolean = false
        private set
    override var lastError: String? = null
        private set
    override val backendLabel: String
        get() = if (usingLegacyBinary) "UART-legacy-9600" else "UART-ttyS4"
    var activePreset: Mj008Hardware.LightPreset =
        Mj008Hardware.presetFor(Mj008Hardware.CameraVariant.MOJI_25443)
        private set
    var usingLegacyBinary: Boolean = false
        private set

    override fun setCameraVariant(variant: Mj008Hardware.CameraVariant) {
        activePreset = Mj008Hardware.presetFor(variant)
    }

    override fun open(): Boolean {
        close()
        return try {
            val file = File(devicePath)
            if (!file.exists()) {
                lastError = "MJ-008: puerto LED no encontrado ($devicePath)"
                Log.w(TAG, lastError!!)
                return false
            }
            baudRate = Mj008Hardware.SERIAL_BAUD_PRIMARY
            usingLegacyBinary = false
            var opened = openWithNative(file) || openWithStreams(file)
            if (!opened) {
                // Some MJ-008 boards expose the older 9600 AA66 path
                baudRate = Mj008Hardware.SERIAL_BAUD_LEGACY
                usingLegacyBinary = true
                opened = openWithNative(file) || openWithStreams(file)
            }
            isOpen = opened
            if (opened) {
                lastError = null
                if (!usingLegacyBinary) {
                    sendRawText(CMD_MULTI_MODE)
                    sendHeartbeat()
                }
            }
            opened
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "MJ-008 open failed", e)
            false
        }
    }

    private fun openWithNative(file: File): Boolean {
        return try {
            val clazz = Class.forName("android.serialport.SerialPort")
            val ctor = clazz.getConstructor(File::class.java, Int::class.javaPrimitiveType)
            val port = ctor.newInstance(file, baudRate)
            fdHolder = port
            outputStream = clazz.getMethod("getOutputStream").invoke(port) as OutputStream
            inputStream = clazz.getMethod("getInputStream").invoke(port) as InputStream
            true
        } catch (_: ClassNotFoundException) {
            tryOpenKongqw(file)
        } catch (e: Exception) {
            Log.w(TAG, "native SerialPort open failed: ${e.message}")
            false
        }
    }

    private fun tryOpenKongqw(file: File): Boolean {
        return try {
            val managerClass = Class.forName("com.kongqw.serialportlibrary.SerialPortManager")
            val manager = managerClass.getDeclaredConstructor().newInstance()
            val open = managerClass.getMethod("openSerialPort", File::class.java, Int::class.javaPrimitiveType)
            val ok = open.invoke(manager, file, baudRate) as Boolean
            if (!ok) return false
            fdHolder = manager
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openWithStreams(file: File): Boolean {
        return try {
            outputStream = FileOutputStream(file)
            inputStream = FileInputStream(file)
            true
        } catch (e: Exception) {
            lastError = "MJ-008: sin permiso en $devicePath (${e.message}). " +
                "En la tablet del analizador la app suele necesitar ser app de sistema."
            Log.e(TAG, lastError!!)
            false
        }
    }

    override fun close() {
        try {
            turnOff()
        } catch (_: Exception) {
        }
        try {
            outputStream?.close()
            inputStream?.close()
            fdHolder?.let { holder ->
                try {
                    holder.javaClass.getMethod("close").invoke(holder)
                } catch (_: Exception) {
                    try {
                        holder.javaClass.getMethod("closeSerialPort").invoke(holder)
                    } catch (_: Exception) {
                    }
                    try {
                        holder.javaClass.getMethod("tryClose").invoke(holder)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
        outputStream = null
        inputStream = null
        fdHolder = null
        isOpen = false
    }

    fun sendHeartbeat() {
        if (!usingLegacyBinary) sendRawText(CMD_HEART)
    }

    override fun turnOff() {
        try {
            if (usingLegacyBinary) {
                sendLegacyBinary(0x10, 0x00)
            } else {
                sendRawText(CMD_OFF)
            }
        } catch (e: Exception) {
            Log.e(TAG, "turnOff failed", e)
        }
    }

    override fun setMultiMode() {
        if (!usingLegacyBinary) sendRawText(CMD_MULTI_MODE)
    }

    fun setCenterChannel(channel: Int, percent: Int) {
        val p = percent.coerceIn(0, 100)
        if (usingLegacyBinary) {
            // Legacy: channel index in cmd, intensity in arg (approx scale)
            val cmd = (0x10 + channel.coerceIn(1, 5)).toByte()
            sendLegacyBinary(cmd, (p * 2.55f).toInt().coerceIn(0, 255).toByte())
            return
        }
        val prefix = when (channel) {
            1 -> "TCCCMD_W"
            2 -> "TCCCMD_N"
            3 -> "TCCCMD_P"
            4 -> "TCCCMD_WS"
            5 -> "TCCCMD_UV"
            else -> return
        }
        sendRawText("$prefix$p%")
    }

    override fun applyLightMode(mode: LightMode) {
        if (mode.hardwareChannel == null) return
        try {
            val p = activePreset
            turnOff()
            Thread.sleep(80)
            when (mode) {
                LightMode.WHITE -> setCenterChannel(1, p.whiteCenter)
                LightMode.XPL -> {
                    setCenterChannel(1, p.xplWhiteFill)
                    setCenterChannel(2, p.xplCenter)
                }
                LightMode.PPL -> {
                    setCenterChannel(2, p.pplNegFill)
                    setCenterChannel(3, p.pplCenter)
                }
                LightMode.WOODS -> setCenterChannel(4, p.woodsCenter)
                LightMode.UV -> setCenterChannel(5, p.uvCenter)
                else -> Unit
            }
            Thread.sleep(140)
        } catch (e: Exception) {
            Log.e(TAG, "applyLightMode failed", e)
            lastError = e.message
        }
    }

    fun sendLegacyBinary(cmd: Byte, arg: Byte) {
        writeBytes(byteArrayOf(0xAA.toByte(), 0x66, cmd, arg, 0x23))
    }

    private fun sendRawText(command: String) {
        writeBytes((command + "\n\r").toByteArray(CHARSET))
        Log.d(TAG, "MJ-008 TX: $command")
    }

    private fun writeBytes(data: ByteArray) {
        try {
            val holder = fdHolder
            if (holder != null) {
                try {
                    holder.javaClass.getMethod("sendBytes", ByteArray::class.java).invoke(holder, data)
                    return
                } catch (_: Exception) {
                }
            }
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "writeBytes failed", e)
            lastError = e.message
        }
    }

    companion object {
        private const val TAG = "Mj008Lights"
        const val DEFAULT_DEVICE = Mj008Hardware.SERIAL_DEVICE
        const val DEFAULT_BAUD = Mj008Hardware.SERIAL_BAUD_PRIMARY
        private val CHARSET: Charset = Charset.forName("GB18030")

        const val CMD_OFF = "TCCMD_OFF"
        const val CMD_MULTI_MODE = "TCCMD_PWM_SETL"
        const val CMD_SINGLE_MODE = "TCCMD_PWM_SETH"
        const val CMD_HEART = "TC_HEART"
        const val CMD_VER = "VER_QUERY"
    }
}
