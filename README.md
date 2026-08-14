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

## Flujo

1. Ajustes → datos del consultorio (opcional)
2. Nuevo análisis → paciente → captura 8 luces
3. Informe (resumen / capas / 3-5 ojos / cuidado) → Email o WhatsApp
4. Historial → comparar dos sesiones

## Nota clínica

Informe orientativo para consulta estética; **no sustituye diagnóstico médico**.
