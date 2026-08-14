# Dra María Laura Hernández Skin Analyzer Pro

App Android **personal, 100% offline** para el hardware **MJ-008 Maokin Miaojin**.  
Reemplaza el software OEM chino (Miaojing / `com.ym.smart.skins`) **sin depender de** `ai.aiskin.vip`, `device.aiskin.vip`, Aliyun OSS, login ni licencia en la nube.

## Qué sustituye del OEM

| Función OEM (nube china) | En esta app (local) |
|--------------------------|---------------------|
| Login / registro / tienda | Sin login · perfil de consultorio en Room |
| Miembros en servidor | Pacientes en SQLite (Room) |
| Historial en nube | Sesiones locales + borrar |
| Comparación de historial | Pantalla Comparar (antes/después) |
| AI `landmark-lai/skin_detection` | Análisis multiespectral offline |
| `three_five_eyes` | Proporciones 3/5 ojos offline |
| Artículos / productos API | Catálogo local (guías + productos) |
| Indicadores configurables | Ajustes → interruptores |
| Aliyun OSS / uploads | Archivos solo en la tablet |
| PDF + WeChat | PDF + Email / WhatsApp Business |

## Hardware MJ-008

- Luces: **USB-XU** (protocolo OEM `00 78 cmd FF`); UART `/dev/ttyS4` solo como respaldo
- Cámara: USB UVC (`USB3.0` / `USB Camera`) · 8 luces físicas
- 14 indicadores (superficial + profunda)

## Base de datos propia (Room)

`mlh_skin_analyzer.db` en el dispositivo:

- `patients` · `analysis_sessions`
- `clinic_profile` · `indicator_prefs`
- `care_guides` · `products` (semilla local)

## Instalar

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions publica el artefacto `MLH-Skin-Analyzer-Pro-MJ008-debug`.

## Probar sin la tablet (emulador / teléfono)

El **emulador de Android Studio** (o un teléfono) sirve para UI, pacientes, captura e informe.

1. Instale el APK debug en el emulador (`./gradlew :app:assembleDebug` o el artefacto de Actions).
2. Abra **Ajustes → Modo Demo / Simulación** (en emulador se activa solo la primera vez).
3. Cree un paciente → **Analizar** → **Iniciar análisis (Demo)**.

**Limitación:** el emulador **no** reproduce la cámara USB3.0 ni las luces USB-XU del MJ-008. Eso solo se valida en la tablet real con Demo **apagado**.

## Flujo

1. Ajustes → datos del consultorio (opcional) · Demo si prueba fuera de la tablet
2. Nuevo análisis → paciente → captura 8 luces
3. Informe (resumen / capas / 3-5 ojos / cuidado) → Email o WhatsApp
4. Historial → comparar dos sesiones

## Nota clínica

Informe orientativo para consulta estética; **no sustituye diagnóstico médico**.
