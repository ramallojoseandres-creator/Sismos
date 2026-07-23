# Sismos - Alerta temprana web (Global + Venezuela)

App web de monitoreo sísmico en tiempo real inspirada en GlobalQuake:

- Mapa mundial con eventos recientes.
- Lista de sismos consolidada desde múltiples fuentes:
  - USGS,
  - EMSC (SeismicPortal),
  - feed regional opcional configurable.
- Backend Node.js con WebSocket para fan-out de alertas en tiempo real.
- Geocercas por estado de Venezuela para clasificar eventos nacionales.
- Modo monitor en vivo estilo sala operativa:
  - feed en tiempo real,
  - resaltado/pulso de eventos nuevos en mapa,
  - autoenfoque opcional en eventos relevantes.
- Carril rápido de pre-alerta:
  - sondeo rápido EMSC (`emsc-fast`) para avisos preliminares,
  - emisión WebSocket `fast_prealert` y estado `fast_status`.
- Stream global en vivo (estilo monitor):
  - conexión WebSocket a EMSC Standing Order,
  - ingestión inmediata de eventos `global_realtime`,
  - estado de enlace realtime (`realtime_status`).
- Regla de alerta configurable para Venezuela:
  - magnitud mínima,
  - distancia máxima a Caracas,
  - estado prioritario,
  - alerta visual/sonora y notificación del navegador.

> Importante: esto **no reemplaza** un sistema oficial de alerta temprana por
> ondas P con red instrumental local. Es una alerta rápida basada en eventos
> ya detectados y publicados por fuentes abiertas.

## Ejecutar localmente

### 1) Instalar dependencias

```bash
npm install
```

### 2) (Opcional) configurar feed regional y umbrales de alerta del backend

```bash
export REGIONAL_FEED_URL="https://tu-feed-regional/earthquakes.geojson"
export ALERT_MIN_MAG=4.0
export ALERT_MAX_DISTANCE_KM=1200
export REFRESH_MS=30000
export FAST_REFRESH_MS=7000
export PREALERT_MIN_MAG=3.8
export PREALERT_MAX_DISTANCE_KM=1700
export EMSC_REALTIME_WS_URL="wss://www.seismicportal.eu/standing_order/websocket"
```

### 3) Iniciar servidor

```bash
npm start
```

Abre:

```txt
http://localhost:8080
```

## Arquitectura rápida

- `server.js`
  - sirve frontend estático,
  - expone `/api/earthquakes` y `/api/geofences/venezuela`,
  - publica eventos por WebSocket (`/ws`).
- `earthquake-service.js`
  - consulta fuentes sísmicas,
  - normaliza y deduplica eventos,
  - calcula estado de Venezuela por geocerca,
  - decide alertas de backend para fan-out.
- `venezuela-states.js`
  - geocercas simplificadas por estado.

## Fuentes

- USGS GeoJSON (última hora):
  `https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson`
- EMSC / SeismicPortal:
  `https://www.seismicportal.eu/fdsnws/event/1/query?limit=100&format=json`
- Regional (opcional):
  `REGIONAL_FEED_URL` (formato GeoJSON compatible).

## Próximas mejoras

- Soporte PWA y notificaciones push.