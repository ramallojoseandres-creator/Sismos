package com.mlh.skinanalyzer.hardware

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * MJ-008 / A6 light controller via UART.
 *
 * Protocol reverse-engineered from Moji AI Skin Tester (Bitmoji A6):
 * - Device: /dev/ttyS4
 * - Baud: 115200 (v2 text protocol) or 9600 (legacy AA66 binary)
 * - Text commands are ASCII/GB18030, terminated with "\n\r"
 *
 * Channel prefixes (Left / Center / Right):
 *   TCLCMD_ / TCCCMD_ / TCRCMD_  + W | N | P | UV | WS + percent (e.g. "49%")
 * Close all: TCCMD_OFF
 * Multi mode: TCCMD_PWM_SETL
 */
class SerialLightController(
    private val devicePath: String = DEFAULT_DEVICE,
    private val baudRate: Int = DEFAULT_BAUD,
) {
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var fdHolder: Any? = null
    var isOpen: Boolean = false
        private set
    var lastError: String? = null
        private set

    fun open(): Boolean {
        close()
        return try {
            val file = File(devicePath)
            if (!file.exists()) {
                lastError = "Puerto serial no encontrado: $devicePath"
                Log.w(TAG, lastError!!)
                return false
            }
            // Prefer native SerialPort via reflection if available; otherwise File streams.
            val opened = openWithNative(file) || openWithStreams(file)
            isOpen = opened
            if (opened) {
                lastError = null
                sendRawText(CMD_MULTI_MODE)
                sendHeartbeat()
            }
            opened
        } catch (e: Exception) {
            lastError = e.message
            Log.e(TAG, "open failed", e)
            false
        }
    }

    private fun openWithNative(file: File): Boolean {
        return try {
            // android.serialport.SerialPort or kongqw equivalent if present on device image
            val clazz = Class.forName("android.serialport.SerialPort")
            val ctor = clazz.getConstructor(File::class.java, Int::class.javaPrimitiveType)
            val port = ctor.newInstance(file, baudRate)
            fdHolder = port
            val getOut = clazz.getMethod("getOutputStream")
            val getIn = clazz.getMethod("getInputStream")
            outputStream = getOut.invoke(port) as OutputStream
            inputStream = getIn.invoke(port) as InputStream
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
            // sendBytes path only — store manager for send
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun openWithStreams(file: File): Boolean {
        return try {
            // Requires root / system UID on most MJ tablets for /dev/ttyS4
            outputStream = FileOutputStream(file)
            inputStream = FileInputStream(file)
            true
        } catch (e: Exception) {
            lastError = "Sin permiso para $devicePath (${e.message}). Ejecutar como app de sistema o root."
            Log.e(TAG, lastError!!)
            false
        }
    }

    fun close() {
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
        sendRawText(CMD_HEART)
    }

    fun turnOff() {
        sendRawText(CMD_OFF)
    }

    fun setMultiMode() {
        sendRawText(CMD_MULTI_MODE)
    }

    /**
     * Set center-channel intensity for a hardware spectrum group.
     * @param channel 1=W, 2=N(XPL), 3=P(PPL), 4=WS, 5=UV
     */
    fun setCenterChannel(channel: Int, percent: Int) {
        val p = percent.coerceIn(0, 100)
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

    fun applyLightMode(mode: LightMode) {
        if (mode.hardwareChannel == null) return
        turnOff()
        Thread.sleep(80)
        when (mode) {
            LightMode.WHITE -> setCenterChannel(1, mode.centerPercent)
            LightMode.XPL -> {
                // XPL: mostly negative polar + tiny white fill (matches OEM presets)
                setCenterChannel(1, 3)
                setCenterChannel(2, mode.centerPercent)
            }
            LightMode.PPL -> {
                setCenterChannel(2, 10)
                setCenterChannel(3, mode.centerPercent)
            }
            LightMode.WOODS -> setCenterChannel(4, mode.centerPercent)
            LightMode.UV -> setCenterChannel(5, mode.centerPercent)
            else -> Unit
        }
        Thread.sleep(120)
    }

    /** Legacy binary protocol (9600 baud devices): AA 66 cmd arg 23 */
    fun sendLegacyBinary(cmd: Byte, arg: Byte) {
        val packet = byteArrayOf(0xAA.toByte(), 0x66, cmd, arg, 0x23)
        writeBytes(packet)
    }

    private fun sendRawText(command: String) {
        val payload = (command + "\n\r").toByteArray(CHARSET)
        writeBytes(payload)
        Log.d(TAG, "TX: $command")
    }

    private fun writeBytes(data: ByteArray) {
        val holder = fdHolder
        if (holder != null) {
            try {
                val m = holder.javaClass.getMethod("sendBytes", ByteArray::class.java)
                m.invoke(holder, data)
                return
            } catch (_: Exception) {
            }
        }
        outputStream?.write(data)
        outputStream?.flush()
    }

    companion object {
        private const val TAG = "SerialLightController"
        const val DEFAULT_DEVICE = "/dev/ttyS4"
        const val DEFAULT_BAUD = 115200
        private val CHARSET: Charset = Charset.forName("GB18030")

        const val CMD_OFF = "TCCMD_OFF"
        const val CMD_MULTI_MODE = "TCCMD_PWM_SETL"
        const val CMD_SINGLE_MODE = "TCCMD_PWM_SETH"
        const val CMD_HEART = "TC_HEART"
        const val CMD_VER = "VER_QUERY"
    }
}
