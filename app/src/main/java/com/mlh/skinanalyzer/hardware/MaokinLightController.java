package com.mlh.skinanalyzer.hardware;

import android.hardware.usb.UsbDeviceConnection;
import android.util.Log;

/**
 * Referencia del protocolo XU (canales, delays, payload).
 *
 * En runtime las luces se envían con {@code UVCCameraHandler.controlLed}
 * sobre la misma sesión UVC (brief §2). No usar esta clase para abrir USB
 * ni claimInterface — provocaría doble conexión.
 */
public class MaokinLightController {
    private static final String TAG = "MaokinLight";

    public static final int LIGHT_WHITE = 0x10;
    public static final int LIGHT_XPL = 0x11;
    public static final int LIGHT_PPL = 0x12;
    public static final int LIGHT_WOODS = 0x13;
    public static final int LIGHT_UV = 0x14;
    public static final int LIGHT_BLUE = 0x15;
    public static final int LIGHT_BROWN = 0x16;
    public static final int LIGHT_RED = 0x17;

    public static final int[] CAPTURE_SEQUENCE = {
            LIGHT_WHITE, LIGHT_XPL, LIGHT_PPL, LIGHT_WOODS,
            LIGHT_UV, LIGHT_BLUE, LIGHT_BROWN, LIGHT_RED
    };

    public static String fileNameFor(int channel) {
        switch (channel) {
            case LIGHT_WHITE:
                return "white.jpg";
            case LIGHT_XPL:
                return "negative.jpg";
            case LIGHT_PPL:
                return "positive.jpg";
            case LIGHT_WOODS:
                return "wsg.jpg";
            case LIGHT_UV:
                return "uv.jpg";
            case LIGHT_BLUE:
                return "blue.jpg";
            case LIGHT_BROWN:
                return "orange.jpg";
            case LIGHT_RED:
                return "red.jpg";
            default:
                return "unknown.jpg";
        }
    }

    private static final int REQ_TYPE_OUT = 0x21;
    private static final int SET_CUR = 0x01;
    private static final int WVAL_CMD = 0x0A00;
    private static final int WVAL_DATA = 0x0B00;
    private static final int WINDEX_XU = 0x0400;
    private static final int PKT_LEN = 8;
    private static final int TIMEOUT_MS = 1000;
    private static final int OPCODE = 0x0082;
    private static final int LIGHT_ADDR = 0x00D816;

    /** OEM: white / first shot settle. */
    public static final long DELAY_FIRST_SHOT = 1500L;
    /** OEM: between light change and still (after first). */
    public static final long DELAY_BETWEEN = 3600L;
    /** OEM: after still before next light. */
    public static final long DELAY_AFTER_SHOT = 2000L;

    private final UsbDeviceConnection conn;

    public MaokinLightController(UsbDeviceConnection conn) {
        this.conn = conn;
    }

    public boolean turnOn(int channel) {
        return write(channel, 0xFF);
    }

    /** Apaga luces (equivale al code 1011 OEM: Woods + OFF). */
    public boolean turnOff() {
        return write(LIGHT_WOODS, 0x00);
    }

    public boolean write(int channel, int value) {
        byte[] payload = new byte[]{
                0x00,
                (byte) 0x78,
                (byte) channel,
                (byte) value
        };
        return xuWrite(OPCODE, LIGHT_ADDR, payload);
    }

    public boolean xuWrite(int opcode, int addr, byte[] payload) {
        if (conn == null) {
            Log.e(TAG, "UsbDeviceConnection nula");
            return false;
        }
        int len = payload.length;
        byte[] cmd = new byte[PKT_LEN];
        cmd[0] = (byte) ((opcode >> 8) & 0xFF);
        cmd[1] = (byte) (opcode & 0xFF);
        cmd[2] = (byte) (addr & 0xFF);
        cmd[3] = (byte) ((addr >> 8) & 0xFF);
        cmd[4] = (byte) (len & 0xFF);
        cmd[5] = (byte) ((len >> 8) & 0xFF);
        cmd[6] = (byte) ((addr >> 16) & 0xFF);
        cmd[7] = 0x00;

        int r = conn.controlTransfer(REQ_TYPE_OUT, SET_CUR, WVAL_CMD,
                WINDEX_XU, cmd, PKT_LEN, TIMEOUT_MS);
        if (r < 0) {
            Log.e(TAG, "Fallo en paquete de comando: " + r);
            return false;
        }
        int blocks = (len + PKT_LEN - 1) / PKT_LEN;
        for (int b = 0; b < blocks; b++) {
            byte[] chunk = new byte[PKT_LEN];
            int copy = Math.min(PKT_LEN, len - b * PKT_LEN);
            System.arraycopy(payload, b * PKT_LEN, chunk, 0, copy);
            r = conn.controlTransfer(REQ_TYPE_OUT, SET_CUR, WVAL_DATA,
                    WINDEX_XU, chunk, PKT_LEN, TIMEOUT_MS);
            if (r < 0) {
                Log.e(TAG, "Fallo en paquete de datos " + b + ": " + r);
                return false;
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
        Log.i(TAG, String.format(
                "XU OK ch=0x%02X val=0x%02X",
                payload.length > 2 ? (payload[2] & 0xFF) : -1,
                payload.length > 3 ? (payload[3] & 0xFF) : -1));
        return true;
    }
}
