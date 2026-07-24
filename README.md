# SISMÓS VE — Monitor sísmico Venezuela

Web de monitoreo sísmico en tiempo real **centrada en Venezuela**, con mapa animado, estimación de ondas P/S, alertas tempranas y consolidación de fuentes abiertas.

Inspirada en monitores tipo [LIVE 3D Globe Earthquake Monitor](https://www.youtube.com/live/U8x51eUfFTI), pero enfocada en el territorio venezolano y su margen Caribe.

## Qué incluye

- Mapa oscuro centrado en Venezuela (Leaflet + Carto).
- Animación de **ondas P (~6 km/s)** y **S (~3.5 km/s)** desde el epicentro.
- **ETA a ciudades** (Caracas, Maracaibo, Valencia, etc.) y ventana de alerta P→S.
- Capas de **estaciones FUNVISIS / IU** y **fallas activas** (Boconó, El Pilar, San Sebastián, Oca–Ancón…).
- Geocercas por estado para clasificar eventos nacionales.
- Pre-alerta rápida EMSC + stream WebSocket EMSC Standing Order.
- Banner, sonido y notificaciones del navegador según reglas configurables.

## Fuentes de datos

| Fuente | Uso |
| --- | --- |
| **USGS** feed horario + FDSN regional | Tiempo real global y catálogo zona VE |
| **EMSC / SeismicPortal** FDSN + WS | Tiempo real y pre-alerta |
| **GFZ GEOFON** FDSN | Eventos regionales |
| **FUNVISIS catalog** (GitHub / ISC + reportes) | Catálogo nacional (~23k eventos) |
| **Regional** (`REGIONAL_FEED_URL`) | Feed GeoJSON opcional |

> **Importante:** esto **no reemplaza** un sistema oficial de alerta temprana por sensores de onda P (FUNVISIS / protección civil). Las ondas animadas estiman tiempos teóricos a partir de orígenes ya publicados por redes públicas.

## Ejecutar

```bash
npm install
npm start
```

Abre `http://localhost:8080`.

### Variables opcionales

```bash
export PORT=8080
export REFRESH_MS=30000
export FAST_REFRESH_MS=7000
export ALERT_MIN_MAG=4.0
export ALERT_MAX_DISTANCE_KM=1200
export PREALERT_MIN_MAG=3.8
export PREALERT_MAX_DISTANCE_KM=1700
export REGIONAL_FEED_URL="https://tu-feed/earthquakes.geojson"
export EMSC_REALTIME_WS_URL="wss://www.seismicportal.eu/standing_order/websocket"
export FUNVISIS_CATALOG_URL="https://raw.githubusercontent.com/kyleedwardbradley/funvisis-catalog/main/funvisis_catalog.csv"
```

## API

- `GET /api/earthquakes` — snapshot consolidado + capas geo
- `GET /api/layers` — estaciones, fallas, ciudades, bbox
- `GET /api/geofences/venezuela` — estados
- `GET /api/status` — salud de fuentes / realtime
- `GET /healthz`
- `WS /ws` — `snapshot`, `new_quakes`, `alert`, `fast_prealert`, `global_realtime`, `realtime_status`

## Arquitectura

- `server.js` — HTTP + WebSocket
- `earthquake-service.js` — agregación, dedupe, alertas, ETA
- `realtime-stream.js` — EMSC Standing Order
- `venezuela-geo.js` — ciudades, estaciones, fallas, física de ondas
- `venezuela-states.js` — geocercas
- `app.js` / `index.html` / `style.css` — UI

## Créditos de datos

USGS, EMSC, GFZ, FUNVISIS (vía catálogo público ISC/reportes), IRIS/FDSN (estación IU SDV y metadatos).
