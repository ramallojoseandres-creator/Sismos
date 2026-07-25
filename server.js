const http = require("http");
const fs = require("fs");
const path = require("path");
const { WebSocketServer } = require("ws");
const { EarthquakeService } = require("./earthquake-service");
const { RealtimeStream } = require("./realtime-stream");
const { GlobalQuakeEngine } = require("./gq-detector");
const { VENEZUELA_STATE_GEOFENCES } = require("./venezuela-states");

const PORT = Number(process.env.PORT || 8080);
const REFRESH_MS = Number(process.env.REFRESH_MS || 30_000);
const FAST_REFRESH_MS = Number(process.env.FAST_REFRESH_MS || 7_000);

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
};

const earthquakeService = new EarthquakeService();
const gqEngine = new GlobalQuakeEngine();

let realtimeStatus = {
  source: "emsc-rt",
  connected: false,
  updatedAt: null,
  detail: "idle",
};

let gqStatus = {
  source: "seedlink",
  connected: false,
  updatedAt: null,
  detail: "idle",
};

function writeJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
  });
  res.end(JSON.stringify(payload));
}

function serveFile(res, filePath) {
  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      res.end("Not found");
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, {
      "Content-Type": MIME_TYPES[ext] || "application/octet-stream",
    });
    res.end(content);
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (url.pathname === "/api/earthquakes") {
    writeJson(res, 200, {
      ...earthquakeService.snapshot,
      gq: gqEngine.getSnapshot(),
    });
    return;
  }

  if (url.pathname === "/api/gq") {
    writeJson(res, 200, gqEngine.getSnapshot());
    return;
  }

  if (url.pathname === "/api/geofences/venezuela") {
    writeJson(res, 200, { states: VENEZUELA_STATE_GEOFENCES });
    return;
  }

  if (url.pathname === "/api/layers") {
    writeJson(res, 200, {
      ...earthquakeService.getReferenceLayers(),
      gqStations: gqEngine.getStationActivity(),
    });
    return;
  }

  if (url.pathname === "/api/status") {
    writeJson(res, 200, {
      ok: true,
      refreshedAt: earthquakeService.snapshot.refreshedAt,
      stats: earthquakeService.snapshot.stats,
      sources: earthquakeService.snapshot.sources,
      sourceErrors: earthquakeService.snapshot.sourceErrors || [],
      realtime: realtimeStatus,
      fast: earthquakeService.fastState,
      funvisisMeta: earthquakeService.snapshot.funvisisMeta,
      globalquake: gqStatus,
    });
    return;
  }

  if (url.pathname === "/healthz") {
    writeJson(res, 200, {
      ok: true,
      refreshedAt: earthquakeService.snapshot.refreshedAt,
      gqConnected: Boolean(gqStatus.connected),
    });
    return;
  }

  const rootDir = path.resolve(process.cwd());
  const requestedPath = url.pathname === "/" ? "index.html" : url.pathname.slice(1);
  const safePath = path.normalize(requestedPath);
  const requested = path.resolve(rootDir, safePath);
  if (requested !== rootDir && !requested.startsWith(`${rootDir}${path.sep}`)) {
    res.writeHead(403, { "Content-Type": "text/plain; charset=utf-8" });
    res.end("Forbidden");
    return;
  }
  serveFile(res, requested);
});

const wss = new WebSocketServer({ server, path: "/ws" });

function broadcast(payload) {
  const msg = JSON.stringify(payload);
  for (const client of wss.clients) {
    if (client.readyState === 1) {
      client.send(msg);
    }
  }
}

wss.on("connection", (socket) => {
  socket.send(
    JSON.stringify({
      type: "snapshot",
      data: {
        ...earthquakeService.snapshot,
        gq: gqEngine.getSnapshot(),
      },
    })
  );
  socket.send(JSON.stringify({ type: "fast_status", data: earthquakeService.fastState }));
  socket.send(JSON.stringify({ type: "realtime_status", data: realtimeStatus }));
  socket.send(JSON.stringify({ type: "gq_status", data: gqStatus }));
  socket.send(JSON.stringify({ type: "gq_snapshot", data: gqEngine.getSnapshot() }));
});

async function refreshAndBroadcast() {
  try {
    const { snapshot, newQuakes, alerts } = await earthquakeService.refresh();
    broadcast({
      type: "snapshot",
      data: { ...snapshot, gq: gqEngine.getSnapshot() },
    });
    if (newQuakes.length > 0) {
      broadcast({ type: "new_quakes", data: newQuakes });
    }
    for (const quake of alerts) {
      broadcast({ type: "alert", data: quake });
    }
  } catch (err) {
    broadcast({
      type: "error",
      data: { message: err.message || String(err) },
    });
    // eslint-disable-next-line no-console
    console.error("refresh error:", err);
  }
}

async function refreshFastAndBroadcast() {
  try {
    const { fastPreAlerts, fastState } = await earthquakeService.refreshFastLane();
    broadcast({ type: "fast_status", data: fastState });
    if (fastPreAlerts.length > 0) {
      broadcast({ type: "fast_prealert", data: fastPreAlerts });
    }
  } catch (err) {
    broadcast({
      type: "error",
      data: { message: err.message || String(err) },
    });
  }
}

server.listen(PORT, "0.0.0.0", () => {
  const realtimeStream = new RealtimeStream({
    onStatus: (status) => {
      realtimeStatus = status;
      broadcast({ type: "realtime_status", data: status });
    },
    onEvent: (candidate) => {
      const quake = earthquakeService.ingestRealtimeCandidate(candidate);
      if (!quake) return;
      broadcast({ type: "global_realtime", data: quake });
      broadcast({ type: "new_quakes", data: [quake] });
      if (earthquakeService.shouldTriggerAlert(quake)) {
        broadcast({ type: "alert", data: quake });
      }
    },
    onError: (error) => {
      broadcast({
        type: "error",
        data: { message: error.message || String(error) },
      });
    },
  });

  gqEngine.on("status", (status) => {
    gqStatus = status;
    broadcast({ type: "gq_status", data: status });
  });

  gqEngine.on("station_trigger", (pick) => {
    broadcast({ type: "gq_station_trigger", data: pick });
    // Refresco ligero de actividad de estaciones
    broadcast({
      type: "gq_stations",
      data: gqEngine.getStationActivity(),
    });
  });

  gqEngine.on("detection", (detection) => {
    // Insertar detección GQ al frente del snapshot en memoria
    const merged = earthquakeService.ingestRealtimeCandidate({
      id: detection.id,
      lat: detection.lat,
      lon: detection.lon,
      depth: detection.depthKm,
      mag: detection.mag,
      time: detection.time,
      place: detection.place,
      magType: detection.magType,
    });
    // Preferir el objeto enriquecido del motor GQ
    broadcast({ type: "gq_detection", data: detection });
    broadcast({ type: "new_quakes", data: [detection] });
    if (
      detection.mag >= Number(process.env.ALERT_MIN_MAG || 4.0) &&
      detection.distanceToCaracasKm <= Number(process.env.ALERT_MAX_DISTANCE_KM || 1500)
    ) {
      broadcast({ type: "alert", data: detection });
    }
    if (merged) {
      // no-op: ya emitimos detection
    }
  });

  gqEngine.on("error", (err) => {
    // eslint-disable-next-line no-console
    console.error("GQ seedlink error:", err.message || err);
  });

  // eslint-disable-next-line no-console
  console.log(`SISMÓS VE (GlobalQuake mode) en http://localhost:${PORT}`);
  gqEngine.start();
  refreshAndBroadcast();
  refreshFastAndBroadcast();
  realtimeStream.start();
  setInterval(refreshAndBroadcast, REFRESH_MS);
  setInterval(refreshFastAndBroadcast, FAST_REFRESH_MS);
  // Push periódico de estado GQ / estaciones
  setInterval(() => {
    broadcast({ type: "gq_snapshot", data: gqEngine.getSnapshot() });
  }, 5_000);
});
