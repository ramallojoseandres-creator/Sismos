const USGS_HOURLY_FEED =
  "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";
const REFRESH_MS = 30_000;

const CARACAS = { lat: 10.4806, lon: -66.9036 };
const VENEZUELA_BOUNDS = {
  minLat: 0.5,
  maxLat: 13.7,
  minLon: -73.7,
  maxLon: -58.5,
};

const state = {
  markers: new Map(),
  lastSeenIds: new Set(),
  lastRefreshAt: null,
};

const els = {
  statusDot: document.getElementById("status-dot"),
  statusText: document.getElementById("status-text"),
  quakeList: document.getElementById("quake-list"),
  itemTemplate: document.getElementById("quake-item-template"),
  metricGlobal: document.getElementById("metric-global"),
  metricVenezuela: document.getElementById("metric-venezuela"),
  metricHigh: document.getElementById("metric-high"),
  forceRefresh: document.getElementById("force-refresh"),
  magnitudeThreshold: document.getElementById("magnitude-threshold"),
  distanceThreshold: document.getElementById("distance-threshold"),
  alertSound: document.getElementById("alert-sound"),
};

const map = L.map("map", {
  worldCopyJump: true,
  zoomControl: true,
}).setView([15, -30], 2);

L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
  maxZoom: 8,
  attribution: "&copy; OpenStreetMap contributors",
}).addTo(map);

L.rectangle(
  [
    [VENEZUELA_BOUNDS.minLat, VENEZUELA_BOUNDS.minLon],
    [VENEZUELA_BOUNDS.maxLat, VENEZUELA_BOUNDS.maxLon],
  ],
  {
    color: "#22d3ee",
    weight: 1.2,
    fillColor: "#22d3ee",
    fillOpacity: 0.05,
  }
).addTo(map);

L.marker([CARACAS.lat, CARACAS.lon])
  .addTo(map)
  .bindPopup("Caracas (referencia de distancia)");

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

function isInVenezuelaBox(lat, lon) {
  return (
    lat >= VENEZUELA_BOUNDS.minLat &&
    lat <= VENEZUELA_BOUNDS.maxLat &&
    lon >= VENEZUELA_BOUNDS.minLon &&
    lon <= VENEZUELA_BOUNDS.maxLon
  );
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

  return (
    quake.mag >= minMag &&
    (quake.inVenezuelaBox || quake.distanceToCaracasKm <= maxDistance)
  );
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
      `${quake.distanceToCaracasKm.toFixed(0)} km de Caracas`;
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
        `Distancia a Caracas: ${quake.distanceToCaracasKm.toFixed(0)} km`
    );
    state.markers.set(quake.id, marker);
  }
}

function triggerAlert(quake) {
  const msg =
    `ALERTA VENEZUELA\nM ${quake.mag.toFixed(1)} · ${quake.place}\n` +
    `Distancia a Caracas: ${quake.distanceToCaracasKm.toFixed(0)} km`;
  alert(msg);

  els.alertSound.currentTime = 0;
  els.alertSound.play().catch(() => {
    // Algunos navegadores bloquean autoplay sin interacción previa.
  });

  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("Alerta sísmica Venezuela", {
      body: `M ${quake.mag.toFixed(1)} — ${quake.place}`,
    });
  }
}

async function fetchQuakes() {
  setStatus("Actualizando datos…", "#fbbf24");
  const response = await fetch(USGS_HOURLY_FEED, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`USGS respondió ${response.status}`);
  }
  const data = await response.json();

  const quakes = data.features
    .map((f) => {
      const mag = Number(f.properties.mag || 0);
      const [lon, lat, depthKm] = f.geometry.coordinates;
      const distanceToCaracasKm = distanceKm(lat, lon, CARACAS.lat, CARACAS.lon);
      return {
        id: f.id,
        mag,
        place: f.properties.place || "Ubicación no disponible",
        time: Number(f.properties.time),
        lat,
        lon,
        depthKm: Number(depthKm || 0),
        distanceToCaracasKm,
        inVenezuelaBox: isInVenezuelaBox(lat, lon),
      };
    })
    .sort((a, b) => b.time - a.time);

  const venezuelaCount = quakes.filter(
    (q) => q.inVenezuelaBox || q.distanceToCaracasKm <= 1200
  ).length;
  const highCount = quakes.filter((q) => q.mag >= 5).length;

  els.metricGlobal.textContent = String(quakes.length);
  els.metricVenezuela.textContent = String(venezuelaCount);
  els.metricHigh.textContent = String(highCount);

  renderList(quakes.slice(0, 30));
  updateMarkers(quakes);

  for (const quake of quakes) {
    const isNew = !state.lastSeenIds.has(quake.id);
    if (isNew && shouldTriggerAlert(quake)) {
      triggerAlert(quake);
    }
  }

  state.lastSeenIds = new Set(quakes.map((q) => q.id));
  state.lastRefreshAt = Date.now();
  setStatus("En línea", "#34d399");
}

async function refreshLoop() {
  try {
    await fetchQuakes();
  } catch (err) {
    console.error(err);
    setStatus("Error de conexión al feed", "#fb7185");
  }
}

if ("Notification" in window && Notification.permission === "default") {
  Notification.requestPermission().catch(() => {
    // Ignorar en navegadores sin soporte total.
  });
}

els.forceRefresh.addEventListener("click", () => {
  refreshLoop();
});

setInterval(() => {
  refreshLoop();
}, REFRESH_MS);

refreshLoop();
