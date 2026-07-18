const API_SNAPSHOT = "/api/earthquakes";
const API_GEOFENCES = "/api/geofences/venezuela";
const WS_PATH = "/ws";

const CARACAS = { lat: 10.4806, lon: -66.9036 };

const els = {
  statusDot: document.getElementById("status-dot"),
  statusText: document.getElementById("status-text"),
  quakeList: document.getElementById("quake-list"),
  liveFeed: document.getElementById("live-feed"),
  itemTemplate: document.getElementById("quake-item-template"),
  metricGlobal: document.getElementById("metric-global"),
  metricVenezuela: document.getElementById("metric-venezuela"),
  metricHigh: document.getElementById("metric-high"),
  lastEventText: document.getElementById("last-event-text"),
  streamRateText: document.getElementById("stream-rate-text"),
  fastCountText: document.getElementById("fast-count-text"),
  autofocusToggle: document.getElementById("autofocus-toggle"),
  forceRefresh: document.getElementById("force-refresh"),
  magnitudeThreshold: document.getElementById("magnitude-threshold"),
  distanceThreshold: document.getElementById("distance-threshold"),
  stateFilter: document.getElementById("state-filter"),
  sourcesText: document.getElementById("sources-text"),
  alertSound: document.getElementById("alert-sound"),
};

const state = {
  markers: new Map(),
  latestSnapshot: null,
  statePolygons: [],
  hasReceivedInitialSnapshot: false,
  alertedQuakeIds: new Set(),
  recentEventTimes: [],
  fastSeenIds: new Set(),
};

const map = L.map("map", {
  worldCopyJump: true,
  zoomControl: true,
}).setView([15, -30], 2);

L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 8,
  attribution: "&copy; OpenStreetMap contributors",
}).addTo(map);

L.marker([CARACAS.lat, CARACAS.lon])
  .addTo(map)
  .bindPopup("Caracas (referencia de distancia)");

const pulseLayer = L.layerGroup().addTo(map);

function formatAgo(timeMs) {
  const minutes = Math.max(0, Math.floor((Date.now() - timeMs) / 60_000));
  if (minutes < 1) return "ahora";
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return `hace ${hours} h`;
}

function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

function getMarkerStyle(mag) {
  if (mag >= 6) return { color: "#ef4444", radius: 18 };
  if (mag >= 5) return { color: "#f97316", radius: 14 };
  if (mag >= 4) return { color: "#facc15", radius: 10 };
  return { color: "#60a5fa", radius: 7 };
}

function setStatus(text, color) {
  els.statusText.textContent = text;
  els.statusDot.style.backgroundColor = color;
  els.statusDot.style.boxShadow = `0 0 8px ${color}`;
}

function shouldTriggerAlert(quake) {
  const minMag = Number(els.magnitudeThreshold.value || 4);
  const maxDistance = Number(els.distanceThreshold.value || 1200);
  const stateFilter = els.stateFilter.value;

  const matchesState =
    !stateFilter || quake.venezuelaState === stateFilter || quake.venezuelaState === null;

  return (
    quake.mag >= minMag &&
    matchesState &&
    (Boolean(quake.venezuelaState) || quake.distanceToCaracasKm <= maxDistance)
  );
}

function updateMonitorHeader(lastQuake) {
  const now = Date.now();
  state.recentEventTimes = state.recentEventTimes.filter((t) => now - t <= 60_000);
  els.streamRateText.textContent = `${state.recentEventTimes.length} eventos/min`;

  if (!lastQuake) {
    els.lastEventText.textContent = "Esperando datos…";
    return;
  }
  els.lastEventText.textContent =
    `M ${lastQuake.mag.toFixed(1)} · ${lastQuake.place} (${formatAgo(lastQuake.time)})`;
}

function appendLiveFeed(text) {
  const li = document.createElement("li");
  li.textContent = text;
  els.liveFeed.prepend(li);
  while (els.liveFeed.children.length > 14) {
    els.liveFeed.removeChild(els.liveFeed.lastChild);
  }
}

function appendFastFeed(quake) {
  const li = document.createElement("li");
  li.className = "fast-item";
  li.textContent =
    `⚡ PRE-ALERTA ${new Date(quake.time).toLocaleTimeString()} · ` +
    `M ${quake.mag.toFixed(1)} · ${quake.place}`;
  els.liveFeed.prepend(li);
  while (els.liveFeed.children.length > 14) {
    els.liveFeed.removeChild(els.liveFeed.lastChild);
  }
}

function drawPulse(quake) {
  const style = getMarkerStyle(quake.mag);
  const pulse = L.circleMarker([quake.lat, quake.lon], {
    radius: style.radius + 2,
    color: style.color,
    fillColor: style.color,
    fillOpacity: 0.3,
    className: "quake-pulse",
    weight: 1.1,
  }).addTo(pulseLayer);

  setTimeout(() => {
    pulseLayer.removeLayer(pulse);
  }, 1800);
}

function renderList(quakes) {
  els.quakeList.textContent = "";

  for (const quake of quakes) {
    const li = els.itemTemplate.content.firstElementChild.cloneNode(true);
    li.querySelector(".quake-mag").textContent = `M ${quake.mag.toFixed(1)}`;
    li.querySelector(".quake-time").textContent = formatAgo(quake.time);
    li.querySelector(".quake-place").textContent = quake.place;
    li.querySelector(".quake-meta").textContent =
      `${quake.depthKm.toFixed(1)} km de profundidad · ` +
      `${quake.distanceToCaracasKm.toFixed(0)} km de Caracas · ` +
      `Fuente: ${quake.source.toUpperCase()} · ` +
      `Estado: ${quake.venezuelaState || "Fuera de Venezuela"}`;
    els.quakeList.appendChild(li);
  }
}

function updateMarkers(quakes) {
  const incomingIds = new Set(quakes.map((q) => q.id));

  for (const [id, marker] of state.markers.entries()) {
    if (!incomingIds.has(id)) {
      map.removeLayer(marker);
      state.markers.delete(id);
    }
  }

  for (const quake of quakes) {
    if (state.markers.has(quake.id)) continue;
    const style = getMarkerStyle(quake.mag);
    const marker = L.circleMarker([quake.lat, quake.lon], {
      color: style.color,
      radius: style.radius,
      weight: 1.4,
      fillOpacity: 0.45,
    }).addTo(map);

    marker.bindPopup(
      `<strong>M ${quake.mag.toFixed(1)}</strong> — ${quake.place}<br/>` +
        `Profundidad: ${quake.depthKm.toFixed(1)} km<br/>` +
        `Distancia a Caracas: ${quake.distanceToCaracasKm.toFixed(0)} km<br/>` +
        `Fuente: ${quake.source.toUpperCase()}<br/>` +
        `Estado: ${quake.venezuelaState || "Fuera de Venezuela"}`
    );
    state.markers.set(quake.id, marker);
  }
}

function triggerAlert(quake) {
  const msg =
    `ALERTA VENEZUELA\nM ${quake.mag.toFixed(1)} · ${quake.place}\n` +
    `Estado: ${quake.venezuelaState || "No clasificado"}\n` +
    `Distancia a Caracas: ${quake.distanceToCaracasKm.toFixed(0)} km`;
  alert(msg);

  els.alertSound.currentTime = 0;
  els.alertSound.play().catch(() => {
    // Algunos navegadores bloquean autoplay sin interacción previa.
  });

  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("Alerta sísmica Venezuela", {
      body:
        `M ${quake.mag.toFixed(1)} — ${quake.place}` +
        ` (${quake.venezuelaState || "fuera de geocerca"})`,
    });
  }
}

function populateStateFilter(states) {
  const current = els.stateFilter.value;
  const unique = [...new Set(states.map((s) => s.name))].sort();
  els.stateFilter.innerHTML = '<option value="">Todos</option>';
  for (const name of unique) {
    const option = document.createElement("option");
    option.value = name;
    option.textContent = name;
    els.stateFilter.appendChild(option);
  }
  els.stateFilter.value = current;
}

async function loadGeofences() {
  const response = await fetch(API_GEOFENCES, { cache: "no-store" });
  if (!response.ok) return;
  const payload = await response.json();
  const states = payload.states || [];

  populateStateFilter(states);
  for (const geo of state.statePolygons) {
    map.removeLayer(geo);
  }
  state.statePolygons = [];

  for (const stateFence of states) {
    const points = stateFence.polygon.map(([lon, lat]) => [lat, lon]);
    const polygon = L.polygon(points, {
      color: "#164e63",
      weight: 1,
      fillColor: "#0e7490",
      fillOpacity: 0.04,
    }).addTo(map);
    polygon.bindTooltip(stateFence.name);
    state.statePolygons.push(polygon);
  }
}

function applySnapshot(snapshot) {
  state.latestSnapshot = snapshot;
  const quakes = (snapshot.quakes || []).sort((a, b) => b.time - a.time);

  els.metricGlobal.textContent = String(snapshot.stats?.total || quakes.length);
  els.metricVenezuela.textContent = String(snapshot.stats?.venezuela || 0);
  els.metricHigh.textContent = String(snapshot.stats?.high || 0);
  els.sourcesText.textContent = (snapshot.sources || [])
    .filter((source) => source.enabled)
    .map((source) => source.key.toUpperCase())
    .join(", ");

  renderList(quakes.slice(0, 30));
  updateMarkers(quakes);
  updateMonitorHeader(quakes[0]);
  setStatus("En línea (WS)", "#34d399");
}

function onNewQuakes(newQuakes) {
  if (!state.hasReceivedInitialSnapshot) return;
  for (const quake of newQuakes) {
    state.recentEventTimes.push(Date.now());
    updateMonitorHeader(quake);
    appendLiveFeed(
      `${new Date(quake.time).toLocaleTimeString()} · M ${quake.mag.toFixed(1)} · ${quake.place}`
    );
    drawPulse(quake);

    if (els.autofocusToggle.checked && (quake.mag >= 4.8 || quake.venezuelaState)) {
      map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 4), {
        duration: 1.4,
      });
    }

    if (state.alertedQuakeIds.has(quake.id)) continue;
    if (shouldTriggerAlert(quake)) {
      triggerAlert(quake);
      state.alertedQuakeIds.add(quake.id);
    }
  }
}

function onFastPreAlerts(prealerts) {
  for (const quake of prealerts) {
    if (state.fastSeenIds.has(quake.id)) continue;
    state.fastSeenIds.add(quake.id);
    appendFastFeed(quake);
    drawPulse(quake);
    if (els.autofocusToggle.checked) {
      map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 5), {
        duration: 1.1,
      });
    }
  }
}

async function fetchSnapshot() {
  setStatus("Sincronizando snapshot…", "#fbbf24");
  const response = await fetch(API_SNAPSHOT, { cache: "no-store" });
  if (!response.ok) throw new Error(`API respondió ${response.status}`);
  const snapshot = await response.json();
  applySnapshot(snapshot);
}

function connectWebSocket() {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${location.host}${WS_PATH}`);

  socket.addEventListener("open", () => {
    setStatus("Conectado al backend", "#34d399");
  });

  socket.addEventListener("message", (event) => {
    try {
      const message = JSON.parse(event.data);
      if (message.type === "snapshot") {
        applySnapshot(message.data);
        state.hasReceivedInitialSnapshot = true;
      } else if (message.type === "new_quakes") {
        onNewQuakes(message.data || []);
        if (state.latestSnapshot && message.data?.length) {
          const merged = [...message.data, ...(state.latestSnapshot.quakes || [])];
          const unique = [];
          const seen = new Set();
          for (const quake of merged) {
            if (seen.has(quake.id)) continue;
            seen.add(quake.id);
            unique.push(quake);
          }
          state.latestSnapshot.quakes = unique;
          applySnapshot(state.latestSnapshot);
        }
      } else if (message.type === "alert") {
        const quake = message.data;
        if (!quake || state.alertedQuakeIds.has(quake.id)) return;
        if (state.hasReceivedInitialSnapshot && shouldTriggerAlert(quake)) {
          triggerAlert(message.data);
          state.alertedQuakeIds.add(quake.id);
        }
      } else if (message.type === "fast_status") {
        const count = Number(message.data?.prealertsLastMinute || 0);
        els.fastCountText.textContent = `${count} activas`;
      } else if (message.type === "fast_prealert") {
        onFastPreAlerts(message.data || []);
      } else if (message.type === "error") {
        setStatus("Error en fuentes sísmicas", "#fb7185");
      }
    } catch (err) {
      console.error("Error parseando WS:", err);
    }
  });

  socket.addEventListener("close", () => {
    setStatus("WS desconectado, reintentando…", "#fbbf24");
    setTimeout(connectWebSocket, 2500);
  });

  socket.addEventListener("error", () => {
    setStatus("Error de WebSocket", "#fb7185");
    socket.close();
  });
}

els.forceRefresh.addEventListener("click", async () => {
  try {
    await fetchSnapshot();
  } catch (err) {
    setStatus("No se pudo actualizar", "#fb7185");
  }
});

els.stateFilter.addEventListener("change", () => {
  if (!state.latestSnapshot) return;
  applySnapshot(state.latestSnapshot);
});

if ("Notification" in window && Notification.permission === "default") {
  Notification.requestPermission().catch(() => {});
}

loadGeofences().catch((err) => {
  console.error("Geofences error:", err);
});
fetchSnapshot().catch((err) => {
  console.error("Snapshot error:", err);
  setStatus("Esperando backend…", "#fbbf24");
});
connectWebSocket();
