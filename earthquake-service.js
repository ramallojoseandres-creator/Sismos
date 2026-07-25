const { VENEZUELA_STATE_GEOFENCES } = require("./venezuela-states");
const {
  CARACAS,
  VENEZUELA_BBOX,
  distanceKm,
  estimateCityEtas,
  estimateIntensity,
  FUNVISIS_STATIONS,
  ACTIVE_FAULTS,
  MAJOR_CITIES,
  WAVE_SPEEDS,
} = require("./venezuela-geo");

const DEFAULT_SOURCES = [
  { key: "usgs", enabled: true, role: "global-realtime" },
  { key: "usgs-ve", enabled: true, role: "venezuela-region" },
  { key: "emsc", enabled: true, role: "global-realtime" },
  { key: "gfz", enabled: true, role: "venezuela-region" },
  { key: "funvisis", enabled: true, role: "catalogo-nacional" },
  { key: "regional", enabled: Boolean(process.env.REGIONAL_FEED_URL), role: "opcional" },
];

const USGS_HOURLY_FEED =
  "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";
const EMSC_FEED =
  "https://www.seismicportal.eu/fdsnws/event/1/query?limit=120&format=json";
const EMSC_FAST_FEED =
  "https://www.seismicportal.eu/fdsnws/event/1/query?limit=50&format=json";
const FUNVISIS_CATALOG_CSV =
  process.env.FUNVISIS_CATALOG_URL ||
  "https://raw.githubusercontent.com/kyleedwardbradley/funvisis-catalog/main/funvisis_catalog.csv";
const FUNVISIS_META_URL =
  "https://raw.githubusercontent.com/kyleedwardbradley/funvisis-catalog/main/funvisis_catalog.meta.json";

function isoDaysAgo(days) {
  return new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString().slice(0, 19);
}

function usgsRegionUrl(days = 30, minMag = 2.5) {
  const { minLat, maxLat, minLon, maxLon } = VENEZUELA_BBOX;
  return (
    "https://earthquake.usgs.gov/fdsnws/event/1/query?" +
    new URLSearchParams({
      format: "geojson",
      minlatitude: String(minLat),
      maxlatitude: String(maxLat),
      minlongitude: String(minLon),
      maxlongitude: String(maxLon),
      starttime: isoDaysAgo(days),
      minmagnitude: String(minMag),
      orderby: "time",
      limit: "200",
    }).toString()
  );
}

function gfzRegionUrl(days = 30, minMag = 3.5) {
  const { minLat, maxLat, minLon, maxLon } = VENEZUELA_BBOX;
  return (
    "https://geofon.gfz-potsdam.de/fdsnws/event/1/query?" +
    new URLSearchParams({
      format: "text",
      minlat: String(minLat),
      maxlat: String(maxLat),
      minlon: String(minLon),
      maxlon: String(maxLon),
      starttime: isoDaysAgo(days),
      minmagnitude: String(minMag),
      limit: "100",
      nodata: "404",
    }).toString()
  );
}

function pointInPolygon(lat, lon, polygon) {
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, yi] = polygon[i];
    const [xj, yj] = polygon[j];
    const intersects =
      yi > lat !== yj > lat &&
      lon < ((xj - xi) * (lat - yi)) / (yj - yi + Number.EPSILON) + xi;
    if (intersects) inside = !inside;
  }
  return inside;
}

function locateVenezuelaState(lat, lon) {
  for (const state of VENEZUELA_STATE_GEOFENCES) {
    if (pointInPolygon(lat, lon, state.polygon)) {
      return state.name;
    }
  }
  return null;
}

function enrichQuake(base) {
  const cityEtas = estimateCityEtas(base.lat, base.lon, base.time);
  const nearest = cityEtas[0];
  const intensity = nearest
    ? estimateIntensity(base.mag, nearest.distanceKm)
    : null;
  return {
    ...base,
    nearestCity: nearest
      ? {
          name: nearest.city,
          distanceKm: nearest.distanceKm,
          pWaveEtaSec: nearest.pWaveEtaSec,
          sWaveEtaSec: nearest.sWaveEtaSec,
          warningWindowSec: nearest.warningWindowSec,
        }
      : null,
    cityEtas: cityEtas.slice(0, 8),
    intensity,
    wave: {
      pKmPerSec: WAVE_SPEEDS.pWaveKmPerSec,
      sKmPerSec: WAVE_SPEEDS.sWaveKmPerSec,
    },
  };
}

function normalizeGeoJsonFeature(feature, sourceKey) {
  const [lon, lat, depthKm] = feature.geometry?.coordinates || [];
  if (typeof lat !== "number" || typeof lon !== "number") return null;

  const magRaw = feature.properties?.mag ?? feature.properties?.magnitude ?? 0;
  const timeRaw =
    feature.properties?.time ??
    feature.properties?.lastupdate ??
    feature.properties?.updated;
  const time = Number(new Date(timeRaw).valueOf());
  if (!Number.isFinite(time)) return null;

  const mag = Number(magRaw || 0);
  const place =
    feature.properties?.place ||
    feature.properties?.flynn_region ||
    feature.properties?.title ||
    "Ubicación no disponible";

  const depthAbs = Number(depthKm);
  const depth = Number.isFinite(depthAbs) ? Math.abs(depthAbs) : 0;

  return enrichQuake({
    id: `${sourceKey}:${feature.id || `${time}:${lat}:${lon}`}`,
    source: sourceKey,
    mag: Number.isFinite(mag) ? mag : 0,
    magType: feature.properties?.magType || feature.properties?.magtype || null,
    place,
    time,
    lat,
    lon,
    depthKm: depth,
    distanceToCaracasKm: distanceKm(lat, lon, CARACAS.lat, CARACAS.lon),
    venezuelaState: locateVenezuelaState(lat, lon),
    url: feature.properties?.url || null,
  });
}

function parseFdsnText(text, sourceKey) {
  const lines = String(text || "")
    .split("\n")
    .map((l) => l.trim())
    .filter((l) => l && !l.startsWith("#"));

  return lines
    .map((line) => {
      const parts = line.split("|");
      if (parts.length < 13) return null;
      const [
        eventId,
        timeIso,
        latStr,
        lonStr,
        depthStr,
        ,
        ,
        ,
        ,
        magType,
        magStr,
        ,
        place,
      ] = parts;
      const lat = Number(latStr);
      const lon = Number(lonStr);
      const mag = Number(magStr);
      const depthKm = Number(depthStr);
      const time = Number(new Date(timeIso).valueOf());
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !Number.isFinite(time)) {
        return null;
      }
      return enrichQuake({
        id: `${sourceKey}:${eventId || `${time}:${lat}:${lon}`}`,
        source: sourceKey,
        mag: Number.isFinite(mag) ? mag : 0,
        magType: magType || null,
        place: place || "Ubicación no disponible",
        time,
        lat,
        lon,
        depthKm: Number.isFinite(depthKm) ? Math.abs(depthKm) : 0,
        distanceToCaracasKm: distanceKm(lat, lon, CARACAS.lat, CARACAS.lon),
        venezuelaState: locateVenezuelaState(lat, lon),
        url: null,
      });
    })
    .filter(Boolean);
}

function parseFunvisisCsv(csvText, maxRows = 250) {
  const lines = String(csvText || "")
    .trim()
    .split("\n")
    .filter(Boolean);
  if (lines.length < 2) return [];

  const header = lines[0].split(",");
  const idx = Object.fromEntries(header.map((h, i) => [h.trim(), i]));
  const rows = lines.slice(1);
  const recent = rows.slice(Math.max(0, rows.length - maxRows)).reverse();

  return recent
    .map((row) => {
      // CSV simple (sin comillas anidadas complejas en este catálogo)
      const cols = row.split(",");
      const lat = Number(cols[idx.latitude]);
      const lon = Number(cols[idx.longitude]);
      const mag = Number(cols[idx.magnitude]);
      const depthKm = Number(cols[idx.depth_km]);
      const time = Number(new Date(cols[idx.time]).valueOf());
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || !Number.isFinite(time)) {
        return null;
      }
      const place = cols[idx.place] || "FUNVISIS";
      const id = cols[idx.id] || `FUNVISIS:${time}`;
      return enrichQuake({
        id: `funvisis:${id}`,
        source: "funvisis",
        mag: Number.isFinite(mag) ? mag : 0,
        magType: cols[idx.mag_type] || "Mw",
        place,
        time,
        lat,
        lon,
        depthKm: Number.isFinite(depthKm) ? depthKm : 0,
        distanceToCaracasKm: distanceKm(lat, lon, CARACAS.lat, CARACAS.lon),
        venezuelaState: locateVenezuelaState(lat, lon),
        url: null,
        author: cols[idx.author] || "FUNVISIS",
      });
    })
    .filter(Boolean);
}

async function fetchJson(url) {
  const response = await fetch(url, {
    cache: "no-store",
    headers: { "User-Agent": "SismosVE/1.0 (alerta-temprana)" },
  });
  if (!response.ok) {
    throw new Error(`Error ${response.status} al consultar ${url}`);
  }
  return response.json();
}

async function fetchText(url) {
  const response = await fetch(url, {
    cache: "no-store",
    headers: { "User-Agent": "SismosVE/1.0 (alerta-temprana)" },
  });
  if (!response.ok) {
    throw new Error(`Error ${response.status} al consultar ${url}`);
  }
  return response.text();
}

async function fetchUsgs() {
  const payload = await fetchJson(USGS_HOURLY_FEED);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "usgs"))
    .filter(Boolean);
}

async function fetchUsgsVenezuela() {
  const payload = await fetchJson(usgsRegionUrl(30, 2.5));
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "usgs-ve"))
    .filter(Boolean);
}

async function fetchEmsc() {
  const payload = await fetchJson(EMSC_FEED);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "emsc"))
    .filter(Boolean);
}

async function fetchEmscFast() {
  const payload = await fetchJson(EMSC_FAST_FEED);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "emsc-fast"))
    .filter(Boolean);
}

async function fetchGfz() {
  const text = await fetchText(gfzRegionUrl(30, 3.5));
  return parseFdsnText(text, "gfz");
}

async function fetchFunvisis() {
  const csv = await fetchText(FUNVISIS_CATALOG_CSV);
  return parseFunvisisCsv(csv, 300);
}

async function fetchFunvisisMeta() {
  try {
    return await fetchJson(FUNVISIS_META_URL);
  } catch {
    return null;
  }
}

async function fetchRegional() {
  if (!process.env.REGIONAL_FEED_URL) return [];
  const payload = await fetchJson(process.env.REGIONAL_FEED_URL);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "regional"))
    .filter(Boolean);
}

function dedupeQuakes(quakes) {
  const byKey = new Map();
  for (const quake of quakes) {
    const key = `${Math.round(quake.time / 10000)}:${quake.lat.toFixed(2)}:${quake.lon.toFixed(2)}:${quake.mag.toFixed(1)}`;
    const existing = byKey.get(key);
    if (!existing) {
      byKey.set(key, quake);
      continue;
    }

    if (quake.place.length > existing.place.length) {
      existing.place = quake.place;
    }
    if (quake.mag > existing.mag) {
      existing.mag = quake.mag;
    }
    const sources = new Set(
      String(existing.source)
        .split("+")
        .concat(String(quake.source).split("+"))
    );
    existing.source = [...sources].join("+");
    if (!existing.url && quake.url) existing.url = quake.url;
    if (!existing.author && quake.author) existing.author = quake.author;
    if (!existing.cityEtas && quake.cityEtas) {
      existing.cityEtas = quake.cityEtas;
      existing.nearestCity = quake.nearestCity;
      existing.intensity = quake.intensity;
      existing.wave = quake.wave;
    }
  }
  return [...byKey.values()].sort((a, b) => b.time - a.time);
}

function buildStats(quakes) {
  const withinVenezuela = quakes.filter((q) => Boolean(q.venezuelaState));
  const nearCaracas = quakes.filter((q) => q.distanceToCaracasKm <= 1200);
  const high = quakes.filter((q) => q.mag >= 5);
  const last24h = quakes.filter((q) => Date.now() - q.time <= 24 * 60 * 60 * 1000);
  return {
    total: quakes.length,
    venezuela: withinVenezuela.length,
    nearCaracas: nearCaracas.length,
    high: high.length,
    last24h: last24h.length,
    bySource: quakes.reduce((acc, quake) => {
      for (const key of String(quake.source).split("+")) {
        acc[key] = (acc[key] || 0) + 1;
      }
      return acc;
    }, {}),
  };
}

function shouldTriggerServerAlert(quake) {
  const minMag = Number(process.env.ALERT_MIN_MAG || 4.0);
  const maxDistance = Number(process.env.ALERT_MAX_DISTANCE_KM || 1200);
  return (
    quake.mag >= minMag &&
    (Boolean(quake.venezuelaState) || quake.distanceToCaracasKm <= maxDistance)
  );
}

function inVenezuelaFocus(quake) {
  const { minLat, maxLat, minLon, maxLon } = VENEZUELA_BBOX;
  return (
    quake.lat >= minLat &&
    quake.lat <= maxLat &&
    quake.lon >= minLon &&
    quake.lon <= maxLon
  );
}

class EarthquakeService {
  constructor() {
    this.snapshot = {
      refreshedAt: null,
      quakes: [],
      stats: {},
      sources: DEFAULT_SOURCES,
      geo: {
        stations: FUNVISIS_STATIONS,
        faults: ACTIVE_FAULTS,
        cities: MAJOR_CITIES,
        bbox: VENEZUELA_BBOX,
        waveSpeeds: WAVE_SPEEDS,
      },
      funvisisMeta: null,
    };
    this.lastIds = new Set();
    this.realtimeSeenIds = new Set();
    this.fastSeenIds = new Set();
    this.fastState = { updatedAt: null, prealertsLastMinute: 0, sourceErrors: [] };
  }

  normalizeRealtimeCandidate(candidate, source = "emsc-rt") {
    const lat = Number(candidate.lat);
    const lon = Number(candidate.lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;

    const time = Number(new Date(candidate.time || candidate.updated || Date.now()).valueOf());
    if (!Number.isFinite(time)) return null;

    const mag = Number(candidate.mag || candidate.magnitude || 0);
    const depthKm = Number(candidate.depthKm ?? candidate.depth ?? 0);
    const place = candidate.place || candidate.region || candidate.title || "Ubicación no disponible";
    const id =
      candidate.id ||
      `${source}:${Math.round(time / 1000)}:${lat.toFixed(3)}:${lon.toFixed(3)}:${Number.isFinite(mag) ? mag.toFixed(1) : "0.0"}`;

    return enrichQuake({
      id: String(id),
      source,
      mag: Number.isFinite(mag) ? mag : 0,
      magType: candidate.magType || candidate.magtype || null,
      place,
      time,
      lat,
      lon,
      depthKm: Number.isFinite(depthKm) ? Math.abs(depthKm) : 0,
      distanceToCaracasKm: distanceKm(lat, lon, CARACAS.lat, CARACAS.lon),
      venezuelaState: locateVenezuelaState(lat, lon),
      url: candidate.url || null,
    });
  }

  shouldTriggerAlert(quake) {
    return shouldTriggerServerAlert(quake);
  }

  getReferenceLayers() {
    return {
      stations: FUNVISIS_STATIONS,
      faults: ACTIVE_FAULTS,
      cities: MAJOR_CITIES,
      bbox: VENEZUELA_BBOX,
      waveSpeeds: WAVE_SPEEDS,
      states: VENEZUELA_STATE_GEOFENCES,
    };
  }

  ingestRealtimeCandidate(candidate) {
    const normalized = this.normalizeRealtimeCandidate(candidate);
    if (!normalized) return null;
    if (this.realtimeSeenIds.has(normalized.id) || this.lastIds.has(normalized.id)) {
      return null;
    }

    this.realtimeSeenIds.add(normalized.id);
    if (this.realtimeSeenIds.size > 5000) {
      this.realtimeSeenIds = new Set(Array.from(this.realtimeSeenIds).slice(-3000));
    }

    const merged = dedupeQuakes([normalized, ...(this.snapshot.quakes || [])]).slice(0, 400);
    this.snapshot = {
      ...this.snapshot,
      refreshedAt: Date.now(),
      quakes: merged,
      stats: buildStats(merged),
    };
    this.lastIds = new Set(merged.map((q) => q.id));
    return normalized;
  }

  async refresh() {
    const tasks = [];
    if (DEFAULT_SOURCES.find((s) => s.key === "usgs" && s.enabled)) tasks.push(fetchUsgs());
    if (DEFAULT_SOURCES.find((s) => s.key === "usgs-ve" && s.enabled)) {
      tasks.push(fetchUsgsVenezuela());
    }
    if (DEFAULT_SOURCES.find((s) => s.key === "emsc" && s.enabled)) tasks.push(fetchEmsc());
    if (DEFAULT_SOURCES.find((s) => s.key === "gfz" && s.enabled)) tasks.push(fetchGfz());
    if (DEFAULT_SOURCES.find((s) => s.key === "funvisis" && s.enabled)) {
      tasks.push(fetchFunvisis());
    }
    if (DEFAULT_SOURCES.find((s) => s.key === "regional" && s.enabled)) {
      tasks.push(fetchRegional());
    }

    const settled = await Promise.allSettled(tasks);
    const all = [];
    const sourceErrors = [];

    for (const result of settled) {
      if (result.status === "fulfilled") {
        all.push(...result.value);
      } else {
        sourceErrors.push(result.reason?.message || String(result.reason));
      }
    }

    const funvisisMeta = await fetchFunvisisMeta();
    const quakes = dedupeQuakes(all).slice(0, 400);
    const stats = buildStats(quakes);

    const newQuakes = quakes.filter((q) => !this.lastIds.has(q.id));
    const alerts = newQuakes.filter(shouldTriggerServerAlert);

    this.lastIds = new Set(quakes.map((q) => q.id));
    this.snapshot = {
      refreshedAt: Date.now(),
      quakes,
      stats,
      sources: DEFAULT_SOURCES,
      sourceErrors,
      geo: this.getReferenceLayers(),
      funvisisMeta,
      focus: {
        venezuelaQuakes: quakes.filter(inVenezuelaFocus).slice(0, 120),
      },
    };

    return { snapshot: this.snapshot, newQuakes, alerts };
  }

  async refreshFastLane() {
    const now = Date.now();
    const minMag = Number(process.env.PREALERT_MIN_MAG || 3.8);
    const maxDistance = Number(process.env.PREALERT_MAX_DISTANCE_KM || 1700);
    try {
      const fast = await fetchEmscFast();
      const recent = fast.filter((q) => now - q.time <= 20 * 60_000);
      const candidates = recent.filter(
        (q) =>
          q.mag >= minMag &&
          (Boolean(q.venezuelaState) ||
            q.distanceToCaracasKm <= maxDistance ||
            inVenezuelaFocus(q))
      );
      const newPreAlerts = candidates
        .filter((q) => !this.fastSeenIds.has(q.id))
        .map((q) => ({
          ...q,
          source: "emsc-fast",
          confidence: "preliminar",
          channel: "fast-prealert",
        }));

      for (const quake of candidates) {
        this.fastSeenIds.add(quake.id);
      }
      if (this.fastSeenIds.size > 2000) {
        this.fastSeenIds = new Set(Array.from(this.fastSeenIds).slice(-1200));
      }

      this.fastState = {
        updatedAt: now,
        prealertsLastMinute: candidates.filter((q) => now - q.time <= 60_000).length,
        sourceErrors: [],
      };
      return { fastPreAlerts: newPreAlerts, fastState: this.fastState };
    } catch (error) {
      this.fastState = {
        updatedAt: now,
        prealertsLastMinute: 0,
        sourceErrors: [error.message || String(error)],
      };
      return { fastPreAlerts: [], fastState: this.fastState };
    }
  }
}

module.exports = { EarthquakeService };
