# Dra María Laura Hernández Skin Analyzer Pro

App Android personal (sin login) para la tablet del analizador de piel **MJ-008 / Maokin Miaojin (Moji AI / Bitmoji A6)**.

## Qué incluye

- Fichas de pacientes locales (Room), sin cuenta ni nube obligatoria
- Captura multiespectral (8 modos de informe: White, XPL, PPL, Wood's, UV + mapas Blue/Brown/Red)
- Control de LEDs por puerto serial `/dev/ttyS4` @ 115200 (protocolo A6: `TCCCMD_W49%`, `TCCMD_OFF`, etc.)
- Análisis offline de 14 indicadores con niveles de cuidado 1–5
- Informe en PDF + envío por **Email** y **WhatsApp Business**
- Branding MLH (logo monograma)

## Instalar en la tablet MJ-008

1. Compilar el APK (debug o release) desde este proyecto
2. Copiar el APK a la tablet (USB / archivo)
3. Permitir instalación de orígenes desconocidos
4. Abrir la app — no pide login
5. Conectar Wi‑Fi solo si quieres enviar informes

### Luces LED

En el equipo original las luces se mandan por UART:

| Puerto | Baud | Comandos |
|--------|------|----------|
| `/dev/ttyS4` | 115200 | `TCCCMD_W` / `N` / `P` / `UV` / `WS` + porcentaje, `TCCMD_OFF`, `TCCMD_PWM_SETL` |

Si la app no tiene permiso de escritura en `/dev/ttyS4` (app no-sistema), la captura de cámara sigue funcionando y el estado de hardware lo indica en la pantalla de inicio. En ese caso instala como app de sistema o concede permiso root al puerto, según el firmware de la tablet.

### Cámara

Se usa CameraX (cámara USB/UVC del equipo aparece como cámara del sistema en la mayoría de tablets MJ). Guía de línea media en pantalla; el paciente debe cerrar los ojos durante el flash de luces.

## Compilar

```bash
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Flujo de uso

1. **Nuevo análisis** → ficha (nombre, sexo, edad obligatoria, teléfono/email)
2. **Capturar** → 5 tomas con LEDs + 3 mapas derivados
3. Revisar informe (superficial / profunda / resumen)
4. Compartir por Email o WhatsApp Business

## Nota clínica

El análisis local es orientativo para consulta estética y **no sustituye diagnóstico médico**.
