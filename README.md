# Sismos - Alerta temprana web (Global + Venezuela)

App web de monitoreo sísmico en tiempo real inspirada en GlobalQuake:

- Mapa mundial con eventos recientes.
- Lista de sismos de la última hora (feed público USGS).
- Regla de alerta configurable para Venezuela:
  - magnitud mínima,
  - distancia máxima a Caracas,
  - alerta visual/sonora y notificación del navegador.

> Importante: esto **no reemplaza** un sistema oficial de alerta temprana por
> ondas P con red instrumental local. Es una alerta rápida basada en eventos
> ya detectados y publicados por fuentes abiertas.

## Ejecutar localmente

Como es una SPA estática, puedes levantarla con cualquier servidor HTTP.

### Opción rápida con Python

```bash
python3 -m http.server 8080
```

Luego abre:

```txt
http://localhost:8080
```

## Fuente de datos

- USGS GeoJSON feed (última hora):
  `https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson`

## Ideas para mejorar

- Integrar más fuentes (EMSC, redes regionales).
- Agregar backend con WebSocket para fan-out de alertas.
- Implementar geocercas por estado en Venezuela.
- Soporte PWA y notificaciones push.