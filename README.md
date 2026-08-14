# Dra María Laura Hernández Skin Analyzer Pro

App Android **personal (sin login)** pensada **exclusivamente para el hardware MJ-008 Maokin Miaojin** (tablet + cabina de luces del analizador de piel).

## Hardware objetivo: MJ-008

| Pieza | Detalle en MJ-008 |
|--------|-------------------|
| Modelo | Maokin Miaojin **MJ-008** |
| Luces | UART `/dev/ttyS4` @ **115200** (fallback legacy 9600 `AA 66 … 23`) |
| Comandos LED | `TCCCMD_W/N/P/UV/WS` + `%`, `TCCMD_OFF`, `TCCMD_PWM_SETL` |
| Cámara USB | PIDs familia Moji/MJ: **25441, 25443, 25456, 52243 (SXW)** |
| Espectros | White, XPL, PPL, Wood's, UV + mapas Blue / Brown / Red |
| Indicadores | 14 (superficial + profunda), niveles de cuidado 1–5 |

La app detecta al arrancar si el serial y la cámara USB del MJ-008 están presentes, y aplica la curva de brillo LED correspondiente al tipo de cámara Moji.

## Qué incluye

- Fichas de pacientes locales (Room), sin cuenta ni nube obligatoria
- Captura multiespectral en la cabina MJ-008
- Informe PDF + envío por **Email** y **WhatsApp Business**
- Branding MLH (logo monograma)

## Instalar en la tablet del MJ-008

1. Compilar el APK: `./gradlew :app:assembleDebug`
2. Copiar e instalar en la tablet del analizador
3. Abrir la app — no pide login
4. En inicio debe verse el estado **MJ-008** (LED + cámara)

### Permiso del puerto LED

En muchos firmwares MJ-008, `/dev/ttyS4` solo lo escribe una app de sistema. Si el estado indica “sin permiso”, la cámara sigue funcionando; para las luces hay que instalar como app de sistema o conceder acceso al puerto.

## Compilar

```bash
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Flujo

1. **Nuevo análisis** → ficha (edad obligatoria)
2. **Capturar** en el MJ-008 (mentón en soporte, ojos cerrados)
3. Revisar informe y compartir

## Nota clínica

Informe orientativo para consulta estética; **no sustituye diagnóstico médico**.
