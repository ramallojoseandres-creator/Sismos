const { VENEZUELA_STATE_GEOFENCES } = require("./venezuela-states");

const CARACAS = { lat: 10.4806, lon: -66.9036 };

const DEFAULT_SOURCES = [
  { key: "usgs", enabled: true },
  { key: "emsc", enabled: true },
  { key: "regional", enabled: Boolean(process.env.REGIONAL_FEED_URL) },
];

const USGS_HOURLY_FEED =
  "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson";
const EMSC_FEED =
  "https://www.seismicportal.eu/fdsnws/event/1/query?limit=100&format=json";

function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
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

function normalizeGeoJsonFeature(feature, sourceKey) {
  const [lon, lat, depthKm] = feature.geometry?.coordinates || [];
  if (typeof lat !== "number" || typeof lon !== "number") {
    return null;
  }

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

  const distanceToCaracasKm = distanceKm(lat, lon, CARACAS.lat, CARACAS.lon);
  const stateName = locateVenezuelaState(lat, lon);

  return {
    id: `${sourceKey}:${feature.id || `${time}:${lat}:${lon}`}`,
    source: sourceKey,
    mag: Number.isFinite(mag) ? mag : 0,
    place,
    time,
    lat,
    lon,
    depthKm: Number.isFinite(Number(depthKm)) ? Number(depthKm) : 0,
    distanceToCaracasKm,
    venezuelaState: stateName,
  };
}

async function fetchJson(url) {
  const response = await fetch(url, { cache: "no-store" });
  if (!response.ok) {
    throw new Error(`Error ${response.status} al consultar ${url}`);
  }
  return response.json();
}

async function fetchUsgs() {
  const payload = await fetchJson(USGS_HOURLY_FEED);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "usgs"))
    .filter(Boolean);
}

async function fetchEmsc() {
  const payload = await fetchJson(EMSC_FEED);
  return (payload.features || [])
    .map((feature) => normalizeGeoJsonFeature(feature, "emsc"))
    .filter(Boolean);
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

    // Priorizamos la ubicación más descriptiva y magnitud mayor.
    if (quake.place.length > existing.place.length) {
      existing.place = quake.place;
    }
    if (quake.mag > existing.mag) {
      existing.mag = quake.mag;
    }
    existing.source = `${existing.source}+${quake.source}`;
  }
  return [...byKey.values()].sort((a, b) => b.time - a.time);
}

function buildStats(quakes) {
  const withinVenezuela = quakes.filter((q) => Boolean(q.venezuelaState));
  const nearCaracas = quakes.filter((q) => q.distanceToCaracasKm <= 1200);
  const high = quakes.filter((q) => q.mag >= 5);
  return {
    total: quakes.length,
    venezuela: withinVenezuela.length,
    nearCaracas: nearCaracas.length,
    high: high.length,
    bySource: quakes.reduce((acc, quake) => {
      const key = quake.source;
      acc[key] = (acc[key] || 0) + 1;
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

class EarthquakeService {
  constructor() {
    this.snapshot = {
      refreshedAt: null,
      quakes: [],
      stats: {},
      sources: DEFAULT_SOURCES,
    };
    this.lastIds = new Set();
  }

  async refresh() {
    const tasks = [];
    if (DEFAULT_SOURCES.find((s) => s.key === "usgs" && s.enabled)) tasks.push(fetchUsgs());
    if (DEFAULT_SOURCES.find((s) => s.key === "emsc" && s.enabled)) tasks.push(fetchEmsc());
    if (DEFAULT_SOURCES.find((s) => s.key === "regional" && s.enabled)) tasks.push(fetchRegional());

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

    const quakes = dedupeQuakes(all).slice(0, 250);
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
    };

    return { snapshot: this.snapshot, newQuakes, alerts };
  }
}

module.exports = { EarthquakeService };
