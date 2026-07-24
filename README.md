# SISMÓS VE — Monitor sísmico Venezuela

Web de monitoreo sísmico en tiempo real **centrada en Venezuela**, con mapa animado, estimación de ondas P/S, alertas tempranas y consolidación de fuentes abiertas.

Inspirada en monitores tipo [LIVE 3D Globe Earthquake Monitor](https://www.youtube.com/live/U8x51eUfFTI), pero enfocada en el territorio venezolano y su margen Caribe.

## Qué incluye

- **Motor GlobalQuake (propio):** SeedLink IRIS → STA/LTA → triangulación multiestación.
  Emite alertas **antes** de que existan boletines USGS/EMSC/FUNVISIS.
- Mapa oscuro centrado en Venezuela (Leaflet + Carto).
- Animación de **ondas P (~6 km/s)** y **S (~3.5 km/s)** desde el epicentro estimado.
- Estaciones SeedLink en vivo (se ponen verdes al detectar onda P).
- **ETA a ciudades** y ventana de alerta P→S.
- Capas de fallas activas y geocercas por estado.
- Catálogo de apoyo (USGS/EMSC/GFZ/FUNVISIS) como verificación secundaria.

## Fuentes de datos

| Fuente | Uso |
| --- | --- |
| **IRIS SeedLink** (`rtserve.iris.washington.edu`) | Formas de onda en vivo → detección GQ |
| **USGS** feed + FDSN regional | Catálogo de apoyo |
| **EMSC / SeismicPortal** FDSN + WS | Catálogo / stream de apoyo |
| **GFZ GEOFON** FDSN | Catálogo regional |
| **FUNVISIS catalog** | Histórico nacional |
| **Regional** (`REGIONAL_FEED_URL`) | Feed GeoJSON opcional |

Estaciones SeedLink usadas (Caribe / norte Sudamérica): IU.SDV, IU.SJG, IU.OTAV, CU.BBGH/GRGR/ANWB/GTBY/SDDR, CM.URI/CRJC/SMAR/OCA/RUS, PR.ACPR, WI.ABD, G.MPG.

> **Importante:** el motor GQ es **experimental**. Puede haber falsos positivos.
> No reemplaza un sistema oficial FUNVISIS / protección civil.

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
export SEEDLINK_HOST="rtserve.iris.washington.edu"
export SEEDLINK_PORT=18000
export GQ_STA_LTA=3.0
export FUNVISIS_CATALOG_URL="https://raw.githubusercontent.com/kyleedwardbradley/funvisis-catalog/main/funvisis_catalog.csv"
```

## API

- `GET /api/earthquakes` — snapshot consolidado + capas geo
- `GET /api/gq` — estado motor GlobalQuake, estaciones y detecciones
- `GET /api/layers` — estaciones, fallas, ciudades, bbox
- `GET /api/geofences/venezuela` — estados
- `GET /api/status` — salud de fuentes / realtime / seedlink
- `GET /healthz`
- `WS /ws` — `gq_detection`, `gq_station_trigger`, `gq_snapshot`, `snapshot`, `alert`, …

## Arquitectura

- `server.js` — HTTP + WebSocket
- `gq-detector.js` — motor GlobalQuake (STA/LTA + localización)
- `seedlink-client.js` — cliente SeedLink TCP
- `earthquake-service.js` — catálogos de apoyo, dedupe, alertas
- `realtime-stream.js` — EMSC Standing Order
- `venezuela-geo.js` — ciudades, estaciones, fallas, física de ondas
- `venezuela-states.js` — geocercas
- `app.js` / `index.html` / `style.css` — UI

## Créditos de datos

USGS, EMSC, GFZ, FUNVISIS (vía catálogo público ISC/reportes), IRIS/FDSN (estación IU SDV y metadatos).
