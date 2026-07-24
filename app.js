const API_SNAPSHOT = "/api/earthquakes";
const API_GEOFENCES = "/api/geofences/venezuela";
const API_LAYERS = "/api/layers";
const WS_PATH = "/ws";

const CARACAS = { lat: 10.4806, lon: -66.9036 };
const P_WAVE_KMS = 6.0;
const S_WAVE_KMS = 3.5;

const els = {
  statusDot: document.getElementById("status-dot"),
  statusText: document.getElementById("status-text"),
  quakeList: document.getElementById("quake-list"),
  liveFeed: document.getElementById("live-feed"),
  etaList: document.getElementById("eta-list"),
  etaContext: document.getElementById("eta-context"),
  itemTemplate: document.getElementById("quake-item-template"),
  metricGlobal: document.getElementById("metric-global"),
  metricVenezuela: document.getElementById("metric-venezuela"),
  metricHigh: document.getElementById("metric-high"),
  metric24h: document.getElementById("metric-24h"),
  lastEventText: document.getElementById("last-event-text"),
  streamRateText: document.getElementById("stream-rate-text"),
  fastCountText: document.getElementById("fast-count-text"),
  autofocusToggle: document.getElementById("autofocus-toggle"),
  wavesToggle: document.getElementById("waves-toggle"),
  stationsToggle: document.getElementById("stations-toggle"),
  faultsToggle: document.getElementById("faults-toggle"),
  forceRefresh: document.getElementById("force-refresh"),
  magnitudeThreshold: document.getElementById("magnitude-threshold"),
  distanceThreshold: document.getElementById("distance-threshold"),
  stateFilter: document.getElementById("state-filter"),
  sourcesText: document.getElementById("sources-text"),
  realtimeText: document.getElementById("realtime-text"),
  gqPill: document.getElementById("gq-pill"),
  gqStatusText: document.getElementById("gq-status-text"),
  gqTriggerText: document.getElementById("gq-trigger-text"),
  gqDetectionList: document.getElementById("gq-detection-list"),
  alertSound: document.getElementById("alert-sound"),
  alertBanner: document.getElementById("alert-banner"),
  alertTitle: document.getElementById("alert-title"),
  alertBody: document.getElementById("alert-body"),
  alertDismiss: document.getElementById("alert-dismiss"),
};

const state = {
  markers: new Map(),
  latestSnapshot: null,
  statePolygons: [],
  stationLayer: L.layerGroup(),
  gqStationLayer: L.layerGroup(),
  faultLayer: L.layerGroup(),
  cityLayer: L.layerGroup(),
  waveLayer: L.layerGroup(),
  pulseLayer: L.layerGroup(),
  hasReceivedInitialSnapshot: false,
  alertedQuakeIds: new Set(),
  recentEventTimes: [],
  fastSeenIds: new Set(),
  selectedQuakeId: null,
  waveAnimation: null,
  layersReady: false,
  gqStationMarkers: new Map(),
  gqTriggerCount: 0,
  gqDetectionsSeen: new Set(),
};

const map = L.map("map", {
  worldCopyJump: false,
  zoomControl: true,
  preferCanvas: true,
}).setView([8.5, -66.2], 6);

L.tileLayer("https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png", {
  maxZoom: 12,
  attribution: "&copy; OpenStreetMap &copy; CARTO",
}).addTo(map);

state.faultLayer.addTo(map);
state.stationLayer.addTo(map);
state.gqStationLayer.addTo(map);
state.cityLayer.addTo(map);
state.pulseLayer.addTo(map);
state.waveLayer.addTo(map);

L.circleMarker([CARACAS.lat, CARACAS.lon], {
  radius: 5,
  color: "#2dd4bf",
  weight: 2,
  fillColor: "#2dd4bf",
  fillOpacity: 0.35,
})
  .addTo(map)
  .bindPopup("<strong>Caracas</strong><br/>Referencia de distancia / alerta");

function formatAgo(timeMs) {
  const minutes = Math.max(0, Math.floor((Date.now() - timeMs) / 60_000));
  if (minutes < 1) return "ahora";
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.floor(minutes / 60);
  if (hours < 48) return `hace ${hours} h`;
  return `hace ${Math.floor(hours / 24)} d`;
}

function formatSec(sec) {
  if (sec < 60) return `${sec.toFixed(0)} s`;
  const m = Math.floor(sec / 60);
  const s = Math.round(sec % 60);
  return `${m}m ${s}s`;
}

function getMarkerStyle(mag) {
  if (mag >= 6) return { color: "#ef4444", radius: 14 };
  if (mag >= 5) return { color: "#f97316", radius: 11 };
  if (mag >= 4) return { color: "#facc15", radius: 9 };
  if (mag >= 3) return { color: "#38bdf8", radius: 7 };
  return { color: "#64748b", radius: 5 };
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
  els.streamRateText.textContent = `${state.recentEventTimes.length} / min`;

  if (!lastQuake) {
    els.lastEventText.textContent = "Esperando datos…";
    return;
  }
  els.lastEventText.textContent = `M ${lastQuake.mag.toFixed(1)} · ${lastQuake.place} (${formatAgo(lastQuake.time)})`;
}

function appendLiveFeed(text, className = "") {
  const li = document.createElement("li");
  if (className) li.className = className;
  li.textContent = text;
  els.liveFeed.prepend(li);
  while (els.liveFeed.children.length > 18) {
    els.liveFeed.removeChild(els.liveFeed.lastChild);
  }
}

function stopWaveAnimation() {
  if (state.waveAnimation) {
    cancelAnimationFrame(state.waveAnimation.raf);
    state.waveAnimation = null;
  }
  state.waveLayer.clearLayers();
}

function startWaveAnimation(quake, { replay = false } = {}) {
  stopWaveAnimation();
  if (!els.wavesToggle.checked) return;

  const originMs = replay ? Date.now() : quake.time;
  const pSpeed = quake.wave?.pKmPerSec || P_WAVE_KMS;
  const sSpeed = quake.wave?.sKmPerSec || S_WAVE_KMS;

  const pCircle = L.circle([quake.lat, quake.lon], {
    radius: 1,
    color: "#38bdf8",
    weight: 2,
    fillColor: "#38bdf8",
    fillOpacity: 0.06,
    className: "wave-ring-p",
    interactive: false,
  }).addTo(state.waveLayer);

  const sCircle = L.circle([quake.lat, quake.lon], {
    radius: 1,
    color: "#f0a202",
    weight: 2,
    fillColor: "#f0a202",
    fillOpacity: 0.05,
    className: "wave-ring-s",
    interactive: false,
  }).addTo(state.waveLayer);

  const epicenter = L.circleMarker([quake.lat, quake.lon], {
    radius: 6,
    color: "#e85d4c",
    fillColor: "#e85d4c",
    fillOpacity: 0.9,
    weight: 2,
  })
    .addTo(state.waveLayer)
    .bindPopup(
      `<strong>M ${quake.mag.toFixed(1)}</strong> — ${quake.place}<br/>Simulación ondas P/S`
    );

  const maxRadiusM = 1_800_000;
  const durationMs = (maxRadiusM / 1000 / sSpeed) * 1000;

  function frame(now) {
    const elapsedSec = Math.max(0, (now - originMs) / 1000);
    const pRadius = Math.min(maxRadiusM, elapsedSec * pSpeed * 1000);
    const sRadius = Math.min(maxRadiusM, elapsedSec * sSpeed * 1000);
    pCircle.setRadius(Math.max(1, pRadius));
    sCircle.setRadius(Math.max(1, sRadius));

    if (elapsedSec * 1000 < durationMs + 2000) {
      state.waveAnimation.raf = requestAnimationFrame(frame);
    }
  }

  state.waveAnimation = { raf: requestAnimationFrame(frame), epicenter };
}

function renderEtas(quake) {
  if (!quake) {
    els.etaContext.textContent = "Selecciona un sismo para simular llegada P/S.";
    els.etaList.textContent = "";
    return;
  }

  const etas = quake.cityEtas || [];
  els.etaContext.textContent =
    `M ${quake.mag.toFixed(1)} · ${quake.place}` +
    (quake.intensity ? ` · Intensidad est. ${quake.intensity.label}` : "");

  els.etaList.textContent = "";
  for (const eta of etas.slice(0, 8)) {
    const li = document.createElement("li");
    li.innerHTML =
      `<span class="city">${eta.city}</span>` +
      `<span>${eta.distanceKm.toFixed(0)} km</span>` +
      `<span class="waves"><span class="p">P ${formatSec(eta.pWaveEtaSec)}</span> · ` +
      `<span class="s">S ${formatSec(eta.sWaveEtaSec)}</span> · ` +
      `ventana ${formatSec(eta.warningWindowSec)}</span>`;
    els.etaList.appendChild(li);
  }
}

function drawPulse(quake) {
  const style = getMarkerStyle(quake.mag);
  const pulse = L.circleMarker([quake.lat, quake.lon], {
    radius: style.radius + 4,
    color: style.color,
    fillColor: style.color,
    fillOpacity: 0.25,
    className: "quake-pulse",
    weight: 1,
  }).addTo(state.pulseLayer);

  setTimeout(() => {
    state.pulseLayer.removeLayer(pulse);
  }, 1800);
}

function selectQuake(quake, { fly = true, replayWaves = true } = {}) {
  state.selectedQuakeId = quake.id;
  renderEtas(quake);
  if (replayWaves) {
    startWaveAnimation(quake, { replay: true });
  }
  if (fly) {
    map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 7), { duration: 1.1 });
  }
  for (const item of els.quakeList.querySelectorAll(".quake-item")) {
    item.classList.toggle("active", item.dataset.id === quake.id);
  }
  const marker = state.markers.get(quake.id);
  if (marker) marker.openPopup();
}

function renderList(quakes) {
  els.quakeList.textContent = "";

  for (const quake of quakes) {
    const li = els.itemTemplate.content.firstElementChild.cloneNode(true);
    li.dataset.id = quake.id;
    if (quake.id === state.selectedQuakeId) li.classList.add("active");
    li.querySelector(".quake-mag").textContent = `M ${quake.mag.toFixed(1)}`;
    li.querySelector(".quake-time").textContent = formatAgo(quake.time);
    li.querySelector(".quake-place").textContent = quake.place;
    const nearest = quake.nearestCity
      ? ` · cerca de ${quake.nearestCity.name} (${quake.nearestCity.distanceKm.toFixed(0)} km)`
      : "";
    li.querySelector(".quake-meta").textContent =
      `${quake.depthKm.toFixed(0)} km prof. · ` +
      `${quake.distanceToCaracasKm.toFixed(0)} km Caracas · ` +
      `${String(quake.source).toUpperCase()} · ` +
      `${quake.venezuelaState || "fuera VE"}` +
      nearest;
    li.addEventListener("click", () => selectQuake(quake));
    li.addEventListener("keydown", (ev) => {
      if (ev.key === "Enter" || ev.key === " ") {
        ev.preventDefault();
        selectQuake(quake);
      }
    });
    els.quakeList.appendChild(li);
  }
}

function updateMarkers(quakes) {
  const focus =
    quakes.filter(
      (q) =>
        q.venezuelaState ||
        (q.lat >= 0.5 && q.lat <= 13.5 && q.lon >= -73.5 && q.lon <= -59.5)
    ) || [];
  const display = focus.length ? focus : quakes.slice(0, 80);
  const incomingIds = new Set(display.map((q) => q.id));

  for (const [id, marker] of state.markers.entries()) {
    if (!incomingIds.has(id)) {
      map.removeLayer(marker);
      state.markers.delete(id);
    }
  }

  for (const quake of display) {
    if (state.markers.has(quake.id)) continue;
    const style = getMarkerStyle(quake.mag);
    const marker = L.circleMarker([quake.lat, quake.lon], {
      color: style.color,
      radius: style.radius,
      weight: 1.4,
      fillOpacity: 0.5,
    }).addTo(map);

    const etaHint = quake.nearestCity
      ? `<br/>P a ${quake.nearestCity.name}: ${formatSec(quake.nearestCity.pWaveEtaSec)}` +
        ` · S: ${formatSec(quake.nearestCity.sWaveEtaSec)}`
      : "";

    marker.bindPopup(
      `<strong>M ${quake.mag.toFixed(1)}</strong> — ${quake.place}<br/>` +
        `Profundidad: ${quake.depthKm.toFixed(1)} km<br/>` +
        `Caracas: ${quake.distanceToCaracasKm.toFixed(0)} km<br/>` +
        `Fuente: ${String(quake.source).toUpperCase()}<br/>` +
        `Estado: ${quake.venezuelaState || "Fuera de Venezuela"}` +
        etaHint +
        `<br/><em>Clic en lista para animar ondas</em>`
    );
    marker.on("click", () => selectQuake(quake, { fly: false }));
    state.markers.set(quake.id, marker);
  }
}

function showAlertBanner(quake) {
  const nearest = quake.nearestCity;
  els.alertTitle.textContent = `ALERTA M ${quake.mag.toFixed(1)}`;
  els.alertBody.textContent =
    `${quake.place}` +
    (quake.venezuelaState ? ` · ${quake.venezuelaState}` : "") +
    (nearest
      ? ` · S en ${nearest.name} ~${formatSec(nearest.sWaveEtaSec)} (ventana ${formatSec(nearest.warningWindowSec)})`
      : ` · ${quake.distanceToCaracasKm.toFixed(0)} km de Caracas`);
  els.alertBanner.hidden = false;
}

function triggerAlert(quake) {
  showAlertBanner(quake);
  selectQuake(quake, { fly: true, replayWaves: true });
  appendLiveFeed(
    `ALERTA M ${quake.mag.toFixed(1)} · ${quake.place}`,
    "alert-item"
  );

  els.alertSound.currentTime = 0;
  els.alertSound.play().catch(() => {});

  if ("Notification" in window && Notification.permission === "granted") {
    new Notification("SISMÓS VE — Alerta", {
      body: `M ${quake.mag.toFixed(1)} — ${quake.place}`,
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

function renderStations(stations) {
  state.stationLayer.clearLayers();
  for (const st of stations || []) {
    if (st.key) continue;
    const icon = L.divIcon({
      className: "station-icon",
      html: `<div style="width:8px;height:8px;border:1.5px solid #2dd4bf;background:rgba(45,212,191,.25);transform:rotate(45deg);"></div>`,
      iconSize: [8, 8],
      iconAnchor: [4, 4],
    });
    L.marker([st.lat, st.lon], { icon })
      .addTo(state.stationLayer)
      .bindTooltip(`${st.code || st.station} · ${st.name}`);
  }
}

function renderGqStations(stations) {
  const incoming = new Set();
  for (const st of stations || []) {
    const key = st.key || `${st.network}.${st.station}`;
    incoming.add(key);
    let marker = state.gqStationMarkers.get(key);
    const active = Boolean(st.active) || Number(st.ratio) >= 2.5;
    const html = `<div class="station-dot${active ? " active" : ""}" title="${key}"></div>`;
    const icon = L.divIcon({
      className: "station-icon",
      html,
      iconSize: [12, 12],
      iconAnchor: [6, 6],
    });
    if (!marker) {
      marker = L.marker([st.lat, st.lon], { icon }).addTo(state.gqStationLayer);
      state.gqStationMarkers.set(key, marker);
    } else {
      marker.setIcon(icon);
    }
    marker.bindTooltip(
      `${key} · ${st.name || ""}` +
        (st.ratio ? `<br/>STA/LTA ${Number(st.ratio).toFixed(2)}` : "") +
        (active ? "<br/><b>TRIGGER P</b>" : "")
    );
  }
  for (const [key, marker] of state.gqStationMarkers.entries()) {
    if (!incoming.has(key)) {
      state.gqStationLayer.removeLayer(marker);
      state.gqStationMarkers.delete(key);
    }
  }
}

function prependGqDetection(detection) {
  if (!els.gqDetectionList) return;
  if (state.gqDetectionsSeen.has(detection.id)) return;
  state.gqDetectionsSeen.add(detection.id);
  const li = document.createElement("li");
  li.innerHTML =
    `<strong>GQ Mest ${Number(detection.mag).toFixed(1)}</strong> · ` +
    `${detection.place}<br/>` +
    `<span class="muted">${detection.pickCount || 0} estaciones · ` +
    `RMS ${detection.rmsSec ?? "?"}s · ${detection.confidence || "preliminar"}</span>`;
  li.addEventListener("click", () => selectQuake(detection, { fly: true, replayWaves: true }));
  els.gqDetectionList.prepend(li);
  while (els.gqDetectionList.children.length > 8) {
    els.gqDetectionList.removeChild(els.gqDetectionList.lastChild);
  }
}

function applyGqSnapshot(gq) {
  if (!gq) return;
  if (gq.status) {
    const on = Boolean(gq.status.connected);
    if (els.gqPill) {
      els.gqPill.textContent = on
        ? `SeedLink ON · ${gq.status.stations || gq.stations?.length || 0} est.`
        : "SeedLink OFF";
      els.gqPill.classList.toggle("off", !on);
    }
    if (els.gqStatusText) {
      els.gqStatusText.textContent = on
        ? `ON · ${gq.status.packets || 0} pkts`
        : gq.status.detail || "OFF";
    }
  }
  if (gq.stations) renderGqStations(gq.stations);
  if (gq.detections?.length) {
    for (const det of [...gq.detections].reverse()) {
      prependGqDetection(det);
    }
  }
}

function handleGqDetection(detection) {
  if (!detection) return;
  prependGqDetection(detection);
  appendLiveFeed(
    `GQ EEW Mest ${Number(detection.mag).toFixed(1)} · ${detection.place} · ${detection.pickCount} est.`,
    "alert-item"
  );
  drawPulse(detection);
  if (els.wavesToggle.checked) {
    startWaveAnimation(detection, { replay: true });
  }
  renderEtas(detection);
  if (els.autofocusToggle.checked) {
    map.flyTo([detection.lat, detection.lon], Math.max(map.getZoom(), 6), { duration: 1.0 });
  }
  if (state.alertedQuakeIds.has(detection.id)) return;
  if (shouldTriggerAlert(detection) || detection.mag >= Number(els.magnitudeThreshold.value || 4)) {
    triggerAlert(detection);
    state.alertedQuakeIds.add(detection.id);
  }
}

function renderFaults(faults) {
  state.faultLayer.clearLayers();
  for (const fault of faults || []) {
    const latlngs = fault.coordinates.map(([lon, lat]) => [lat, lon]);
    L.polyline(latlngs, {
      color: fault.color || "#e85d4c",
      weight: 2.2,
      opacity: 0.75,
      dashArray: "6 4",
    })
      .addTo(state.faultLayer)
      .bindTooltip(fault.name);
  }
}

function renderCities(cities) {
  state.cityLayer.clearLayers();
  for (const city of cities || []) {
    if (city.name === "Caracas") continue;
    L.circleMarker([city.lat, city.lon], {
      radius: 3,
      color: "#8aa8b0",
      weight: 1,
      fillOpacity: 0.5,
    })
      .addTo(state.cityLayer)
      .bindTooltip(city.name);
  }
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
      color: "#1a4a55",
      weight: 0.8,
      fillColor: "#0e7490",
      fillOpacity: 0.03,
    }).addTo(map);
    polygon.bindTooltip(stateFence.name);
    state.statePolygons.push(polygon);
  }
}

async function loadLayers() {
  const response = await fetch(API_LAYERS, { cache: "no-store" });
  if (!response.ok) return;
  const layers = await response.json();
  renderStations(layers.stations);
  renderFaults(layers.faults);
  renderCities(layers.cities);
  state.layersReady = true;
  syncLayerVisibility();
}

function syncLayerVisibility() {
  if (els.stationsToggle.checked) {
    if (!map.hasLayer(state.stationLayer)) state.stationLayer.addTo(map);
    if (!map.hasLayer(state.gqStationLayer)) state.gqStationLayer.addTo(map);
  } else {
    if (map.hasLayer(state.stationLayer)) map.removeLayer(state.stationLayer);
    if (map.hasLayer(state.gqStationLayer)) map.removeLayer(state.gqStationLayer);
  }

  if (els.faultsToggle.checked) {
    if (!map.hasLayer(state.faultLayer)) state.faultLayer.addTo(map);
  } else if (map.hasLayer(state.faultLayer)) {
    map.removeLayer(state.faultLayer);
  }

  if (!els.wavesToggle.checked) {
    stopWaveAnimation();
  }
}

function applySnapshot(snapshot) {
  state.latestSnapshot = snapshot;
  const quakes = (snapshot.quakes || []).sort((a, b) => b.time - a.time);

  els.metricGlobal.textContent = String(snapshot.stats?.total || quakes.length);
  els.metricVenezuela.textContent = String(snapshot.stats?.venezuela || 0);
  els.metricHigh.textContent = String(snapshot.stats?.high || 0);
  els.metric24h.textContent = String(snapshot.stats?.last24h || 0);

  const sourceBits = (snapshot.sources || [])
    .filter((source) => source.enabled)
    .map((source) => source.key.toUpperCase());
  const meta = snapshot.funvisisMeta;
  els.sourcesText.textContent =
    sourceBits.join(", ") +
    (meta?.rows
      ? ` · FUNVISIS catálogo ${meta.rows.toLocaleString("es")} eventos (${meta.time_range?.min?.slice(0, 4)}–${meta.time_range?.max?.slice(0, 10)})`
      : "");

  if (snapshot.geo && !state.layersReady) {
    renderStations(snapshot.geo.stations);
    renderFaults(snapshot.geo.faults);
    renderCities(snapshot.geo.cities);
    state.layersReady = true;
    syncLayerVisibility();
  }

  if (snapshot.gq) applyGqSnapshot(snapshot.gq);

  const focusList =
    snapshot.focus?.venezuelaQuakes?.length
      ? snapshot.focus.venezuelaQuakes
      : quakes.filter(
          (q) =>
            q.venezuelaState ||
            (q.lat >= 0.5 && q.lat <= 13.5 && q.lon >= -73.5 && q.lon <= -59.5)
        );

  renderList((focusList.length ? focusList : quakes).slice(0, 40));
  updateMarkers(quakes);
  updateMonitorHeader(quakes[0]);
  setStatus("En línea", "#34d399");

  if (!state.selectedQuakeId && focusList[0]) {
    renderEtas(focusList[0]);
  }
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

    const inFocus =
      quake.venezuelaState ||
      (quake.lat >= 0.5 && quake.lat <= 13.5 && quake.lon >= -73.5 && quake.lon <= -59.5);

    if (els.autofocusToggle.checked && (quake.mag >= 4.5 || inFocus)) {
      map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 6), { duration: 1.2 });
    }

    if (inFocus && quake.mag >= 3.5 && els.wavesToggle.checked) {
      startWaveAnimation(quake, { replay: false });
      renderEtas(quake);
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
    appendLiveFeed(
      `PRE-ALERTA ${new Date(quake.time).toLocaleTimeString()} · M ${quake.mag.toFixed(1)} · ${quake.place}`,
      "fast-item"
    );
    drawPulse(quake);
    if (els.wavesToggle.checked) {
      startWaveAnimation(quake, { replay: true });
      renderEtas(quake);
    }
    if (els.autofocusToggle.checked) {
      map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 6), { duration: 1.0 });
    }
  }
}

function mergeRealtimeIntoSnapshot(quake) {
  if (!state.latestSnapshot) return;
  const merged = [quake, ...(state.latestSnapshot.quakes || [])];
  const unique = [];
  const seen = new Set();
  for (const item of merged) {
    if (seen.has(item.id)) continue;
    seen.add(item.id);
    unique.push(item);
  }
  state.latestSnapshot.quakes = unique.slice(0, 400);
  applySnapshot(state.latestSnapshot);
}

async function fetchSnapshot() {
  setStatus("Sincronizando…", "#f0a202");
  const response = await fetch(API_SNAPSHOT, { cache: "no-store" });
  if (!response.ok) throw new Error(`API respondió ${response.status}`);
  const snapshot = await response.json();
  applySnapshot(snapshot);
}

function connectWebSocket() {
  const protocol = location.protocol === "https:" ? "wss:" : "ws:";
  const socket = new WebSocket(`${protocol}//${location.host}${WS_PATH}`);

  socket.addEventListener("open", () => {
    setStatus("Conectado", "#34d399");
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
          triggerAlert(quake);
          state.alertedQuakeIds.add(quake.id);
        }
      } else if (message.type === "fast_status") {
        const count = Number(message.data?.prealertsLastMinute || 0);
        els.fastCountText.textContent = String(count);
      } else if (message.type === "realtime_status") {
        const connected = Boolean(message.data?.connected);
        els.realtimeText.textContent = connected ? "ON" : "OFF";
      } else if (message.type === "global_realtime") {
        const quake = message.data;
        if (!quake) return;
        state.recentEventTimes.push(Date.now());
        updateMonitorHeader(quake);
        appendLiveFeed(
          `RT ${new Date(quake.time).toLocaleTimeString()} · M ${quake.mag.toFixed(1)} · ${quake.place}`
        );
        drawPulse(quake);
        const inFocus =
          quake.venezuelaState ||
          (quake.lat >= 0.5 && quake.lat <= 13.5 && quake.lon >= -73.5 && quake.lon <= -59.5);
        if (els.autofocusToggle.checked && (quake.mag >= 4.5 || inFocus)) {
          map.flyTo([quake.lat, quake.lon], Math.max(map.getZoom(), 6), {
            duration: 1.0,
          });
        }
        if (inFocus && els.wavesToggle.checked) {
          startWaveAnimation(quake, { replay: false });
          renderEtas(quake);
        }
        mergeRealtimeIntoSnapshot(quake);
      } else if (message.type === "fast_prealert") {
        onFastPreAlerts(message.data || []);
      } else if (message.type === "gq_status") {
        const on = Boolean(message.data?.connected);
        if (els.gqPill) {
          els.gqPill.textContent = on ? "SeedLink ON" : "SeedLink OFF";
          els.gqPill.classList.toggle("off", !on);
        }
        if (els.gqStatusText) {
          els.gqStatusText.textContent = on
            ? `ON · ${message.data?.packets || 0} pkts`
            : message.data?.detail || "OFF";
        }
      } else if (message.type === "gq_snapshot") {
        applyGqSnapshot(message.data);
      } else if (message.type === "gq_stations") {
        renderGqStations(message.data || []);
      } else if (message.type === "gq_station_trigger") {
        state.gqTriggerCount += 1;
        if (els.gqTriggerText) {
          els.gqTriggerText.textContent = String(state.gqTriggerCount);
        }
        const pick = message.data;
        if (pick) {
          appendLiveFeed(
            `P-TRIGGER ${pick.network}.${pick.station} · ratio ${pick.ratio}`,
            "fast-item"
          );
        }
      } else if (message.type === "gq_detection") {
        handleGqDetection(message.data);
      } else if (message.type === "error") {
        setStatus("Error en fuentes", "#e85d4c");
      }
    } catch (err) {
      console.error("Error parseando WS:", err);
    }
  });

  socket.addEventListener("close", () => {
    setStatus("WS desconectado…", "#f0a202");
    setTimeout(connectWebSocket, 2500);
  });

  socket.addEventListener("error", () => {
    setStatus("Error WebSocket", "#e85d4c");
    socket.close();
  });
}

els.forceRefresh.addEventListener("click", async () => {
  try {
    await fetchSnapshot();
  } catch {
    setStatus("No se pudo actualizar", "#e85d4c");
  }
});

els.stateFilter.addEventListener("change", () => {
  if (!state.latestSnapshot) return;
  applySnapshot(state.latestSnapshot);
});

els.stationsToggle.addEventListener("change", syncLayerVisibility);
els.faultsToggle.addEventListener("change", syncLayerVisibility);
els.wavesToggle.addEventListener("change", syncLayerVisibility);

els.alertDismiss.addEventListener("click", () => {
  els.alertBanner.hidden = true;
});

if ("Notification" in window && Notification.permission === "default") {
  Notification.requestPermission().catch(() => {});
}

loadGeofences().catch((err) => console.error("Geofences error:", err));
loadLayers().catch((err) => console.error("Layers error:", err));
fetchSnapshot().catch((err) => {
  console.error("Snapshot error:", err);
  setStatus("Esperando backend…", "#f0a202");
});
connectWebSocket();
