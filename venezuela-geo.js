// Datos geográficos de referencia para monitoreo sísmico en Venezuela.
// Coordenadas aproximadas de estaciones FUNVISIS (Red Sismológica Satelital Nacional)
// y fallas activas principales. Fuentes: FUNVISIS / FDSN red VE / literatura tectónica.

const CARACAS = { name: "Caracas", lat: 10.4806, lon: -66.9036, population: 2_900_000 };

const MAJOR_CITIES = [
  CARACAS,
  { name: "Maracaibo", lat: 10.6427, lon: -71.6125, population: 1_600_000 },
  { name: "Valencia", lat: 10.1621, lon: -68.0077, population: 1_400_000 },
  { name: "Barquisimeto", lat: 10.0647, lon: -69.357, population: 1_000_000 },
  { name: "Maracay", lat: 10.2354, lon: -67.5911, population: 1_000_000 },
  { name: "Ciudad Guayana", lat: 8.3114, lon: -62.7186, population: 900_000 },
  { name: "Barcelona", lat: 10.1363, lon: -64.6862, population: 450_000 },
  { name: "Maturín", lat: 9.7457, lon: -63.1832, population: 500_000 },
  { name: "Cumaná", lat: 10.463, lon: -64.1775, population: 400_000 },
  { name: "San Cristóbal", lat: 7.7669, lon: -72.225, population: 400_000 },
  { name: "Mérida", lat: 8.5897, lon: -71.1561, population: 300_000 },
  { name: "Puerto La Cruz", lat: 10.2167, lon: -64.6167, population: 350_000 },
  { name: "Coro", lat: 11.4045, lon: -69.6734, population: 250_000 },
  { name: "Punto Fijo", lat: 11.6956, lon: -70.1992, population: 280_000 },
  { name: "Guatire", lat: 10.474, lon: -66.5426, population: 270_000 },
  { name: "Los Teques", lat: 10.3443, lon: -67.0432, population: 250_000 },
  { name: "La Guaira", lat: 10.599, lon: -66.9346, population: 200_000 },
  { name: "Porlamar", lat: 10.9577, lon: -63.8497, population: 150_000 },
  { name: "Güiria", lat: 10.5778, lon: -62.2986, population: 40_000 },
  { name: "El Tocuyo", lat: 9.787, lon: -69.793, population: 60_000 },
];

const FUNVISIS_STATIONS = [
  { code: "BAUV", name: "El Baúl", locality: "Cojedes", lat: 8.9433, lon: -68.0415 },
  { code: "BENV", name: "Belén", locality: "Carabobo", lat: 9.9623, lon: -67.5988 },
  { code: "BIRV", name: "Birongo", locality: "Miranda", lat: 10.4757, lon: -66.2692 },
  { code: "CACV", name: "Caicara del Orinoco", locality: "Bolívar", lat: 7.5011, lon: -65.9926 },
  { code: "CAPV", name: "Capacho", locality: "Táchira", lat: 7.825, lon: -72.325 },
  { code: "CARV", name: "Caracas", locality: "Distrito Capital", lat: 10.506, lon: -66.915 },
  { code: "CRUV", name: "Carúpano", locality: "Sucre", lat: 10.667, lon: -63.25 },
  { code: "CUPV", name: "Cúpira", locality: "Miranda", lat: 10.15, lon: -65.7 },
  { code: "CURV", name: "Curarigua", locality: "Lara", lat: 10.0128, lon: -69.9611 },
  { code: "DABV", name: "Dabajuro", locality: "Falcón", lat: 11.02, lon: -70.68 },
  { code: "ELOV", name: "Elorza", locality: "Apure", lat: 7.06, lon: -69.5 },
  { code: "FUNV", name: "El Llanito (FUNVISIS)", locality: "Distrito Capital", lat: 10.4693, lon: -66.8102 },
  { code: "GUIV", name: "Güiria", locality: "Sucre", lat: 10.6377, lon: -62.2082 },
  { code: "GUNV", name: "Guanoco", locality: "Sucre", lat: 10.15, lon: -62.9 },
  { code: "GURV", name: "El Guri", locality: "Bolívar", lat: 7.77, lon: -63.0 },
  { code: "JACV", name: "Jacura", locality: "Falcón", lat: 11.0873, lon: -68.8299 },
  { code: "MCQV", name: "Machiques", locality: "Zulia", lat: 10.05, lon: -72.5349 },
  { code: "PCRV", name: "Puerto Cabello", locality: "Carabobo", lat: 10.47, lon: -68.01 },
  { code: "SDV", name: "Santo Domingo (IU)", locality: "Mérida", lat: 8.8839, lon: -70.634 },
  { code: "TACV", name: "Tacata", locality: "Miranda", lat: 10.1381, lon: -67.0267 },
  { code: "TERV", name: "El Tocuyo", locality: "Lara", lat: 9.9638, lon: -69.1918 },
  { code: "TURV", name: "Turiamo", locality: "Aragua", lat: 10.4495, lon: -67.8395 },
];

// Trazas simplificadas de fallas activas principales (lon, lat).
const ACTIVE_FAULTS = [
  {
    name: "Sistema Boconó",
    color: "#e85d4c",
    coordinates: [
      [-72.4, 7.5],
      [-71.8, 8.0],
      [-71.2, 8.5],
      [-70.6, 9.0],
      [-70.0, 9.4],
      [-69.4, 9.7],
      [-68.8, 10.0],
      [-68.2, 10.2],
    ],
  },
  {
    name: "Falla de San Sebastián",
    color: "#f0a202",
    coordinates: [
      [-67.6, 10.55],
      [-67.1, 10.55],
      [-66.7, 10.52],
      [-66.3, 10.5],
      [-65.9, 10.48],
      [-65.4, 10.45],
    ],
  },
  {
    name: "Falla de El Pilar",
    color: "#f97316",
    coordinates: [
      [-64.2, 10.55],
      [-63.6, 10.6],
      [-63.0, 10.65],
      [-62.5, 10.68],
      [-62.0, 10.7],
      [-61.6, 10.72],
    ],
  },
  {
    name: "Falla de Oca–Ancón",
    color: "#38bdf8",
    coordinates: [
      [-72.5, 11.2],
      [-71.8, 11.15],
      [-71.0, 11.1],
      [-70.3, 11.05],
      [-69.6, 11.0],
      [-68.9, 10.95],
    ],
  },
  {
    name: "Falla de Valera",
    color: "#a78bfa",
    coordinates: [
      [-70.7, 8.6],
      [-70.65, 9.0],
      [-70.6, 9.4],
      [-70.55, 9.8],
      [-70.5, 10.2],
    ],
  },
  {
    name: "Falla de La Victoria",
    color: "#34d399",
    coordinates: [
      [-67.6, 10.15],
      [-67.35, 10.2],
      [-67.1, 10.25],
      [-66.85, 10.3],
    ],
  },
];

// Bounding box operativo (Venezuela + margen Caribe / Colombia oriental).
const VENEZUELA_BBOX = {
  minLat: 0.5,
  maxLat: 13.5,
  minLon: -73.5,
  maxLon: -59.5,
};

const WAVE_SPEEDS = {
  pWaveKmPerSec: 6.0,
  sWaveKmPerSec: 3.5,
};

function distanceKm(lat1, lon1, lat2, lon2) {
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLon = toRad(lon2 - lon1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * (2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
}

function estimateCityEtas(lat, lon, originTimeMs = Date.now()) {
  return MAJOR_CITIES.map((city) => {
    const dist = distanceKm(lat, lon, city.lat, city.lon);
    const pSec = dist / WAVE_SPEEDS.pWaveKmPerSec;
    const sSec = dist / WAVE_SPEEDS.sWaveKmPerSec;
    return {
      city: city.name,
      lat: city.lat,
      lon: city.lon,
      population: city.population,
      distanceKm: Number(dist.toFixed(1)),
      pWaveEtaSec: Number(pSec.toFixed(1)),
      sWaveEtaSec: Number(sSec.toFixed(1)),
      warningWindowSec: Number(Math.max(0, sSec - pSec).toFixed(1)),
      pArrivalAt: originTimeMs + pSec * 1000,
      sArrivalAt: originTimeMs + sSec * 1000,
    };
  }).sort((a, b) => a.distanceKm - b.distanceKm);
}

function estimateIntensity(mag, distanceKm) {
  // Aproximación simplificada tipo MMI a partir de magnitud y distancia.
  if (!Number.isFinite(mag) || !Number.isFinite(distanceKm)) return null;
  const hypo = Math.max(1, distanceKm);
  const raw = 1.5 * mag - 2.2 * Math.log10(hypo) - 0.5;
  const mmi = Math.max(1, Math.min(10, Math.round(raw)));
  const labels = [
    "",
    "I Imperceptible",
    "II Muy débil",
    "III Débil",
    "IV Ligero",
    "V Moderado",
    "VI Fuerte",
    "VII Muy fuerte",
    "VIII Severo",
    "IX Violento",
    "X Extremo",
  ];
  return { mmi, label: labels[mmi] };
}

module.exports = {
  CARACAS,
  MAJOR_CITIES,
  FUNVISIS_STATIONS,
  ACTIVE_FAULTS,
  VENEZUELA_BBOX,
  WAVE_SPEEDS,
  distanceKm,
  estimateCityEtas,
  estimateIntensity,
};
