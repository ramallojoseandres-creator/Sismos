/**
 * Motor de detección estilo GlobalQuake:
 * SeedLink → MiniSEED → STA/LTA → asociación multiestación → hipocentro estimado.
 * Emite detecciones propias SIN esperar catálogos USGS/EMSC.
 */
const EventEmitter = require("events");
const miniseed = require("seisplotjs-miniseed");
const { SeedLinkClient, SeedLinkPool } = require("./seedlink-client");
const {
  VENEZUELA_BBOX,
  WAVE_SPEEDS,
  distanceKm,
  estimateCityEtas,
  estimateIntensity,
  CARACAS,
} = require("./venezuela-geo");

/** Estaciones SeedLink públicas que emiten BHZ en IRIS cerca de Venezuela/Caribe. */
const GQ_STATIONS = [
  { network: "IU", station: "SDV", lat: 8.8839, lon: -70.634, name: "Santo Domingo, Venezuela" },
  { network: "IU", station: "SJG", lat: 18.1091, lon: -66.15, name: "San Juan, Puerto Rico" },
  { network: "CU", station: "BBGH", lat: 13.1434, lon: -59.5588, name: "Barbados" },
  { network: "CU", station: "GRGR", lat: 12.1324, lon: -61.6541, name: "Grenada" },
  { network: "CU", station: "ANWB", lat: 17.6684, lon: -61.7856, name: "Barbuda" },
  { network: "WI", station: "ABD", lat: 16.4744, lon: -61.4881, name: "Guadeloupe" },
  // Colombia norte (a veces BHZ/HHZ según disponibilidad en el ring)
  { network: "CM", station: "URI", lat: 11.702, lon: -71.993, name: "Uribia, Colombia" },
];

const STATION_BY_KEY = new Map(
  GQ_STATIONS.map((s) => [`${s.network}.${s.station}`, s])
);

const DEFAULT_SEEDLINK = {
  host: process.env.SEEDLINK_HOST || "rtserve.iris.washington.edu",
  port: Number(process.env.SEEDLINK_PORT || 18000),
};

function decodeSamples(mseedBuf) {
  try {
    const ab = mseedBuf.buffer.slice(
      mseedBuf.byteOffset,
      mseedBuf.byteOffset + mseedBuf.byteLength
    );
    const records = miniseed.parseDataRecords(ab);
    if (!records || !records.length) return null;
    const record = records[0];
    const header = record.header || {};
    let samples = null;
    try {
      samples = record.decompress();
    } catch (err) {
      return { error: err.message || String(err) };
    }
    if (!samples || !samples.length) return null;

    const start = header.start;
    let startMs = Date.now();
    if (start) {
      if (typeof start.valueOf === "function") startMs = Number(start.valueOf());
      else startMs = Number(new Date(start).valueOf());
    }

    return {
      network: String(header.netCode || "").trim(),
      station: String(header.staCode || "").trim(),
      channel: String(header.chanCode || "").trim(),
      location: String(header.locCode || "").trim(),
      startMs: Number.isFinite(startMs) ? startMs : Date.now(),
      sampleRate: Number(header.sampleRate) || 20,
      samples: Array.from(samples),
    };
  } catch (err) {
    return { error: err.message || String(err) };
  }
}

class StaLtaDetector {
  constructor({
    staSec = 1.0,
    ltaSec = 30.0,
    threshold = 3.2,
    dedupeSec = 25,
  } = {}) {
    this.staSec = staSec;
    this.ltaSec = ltaSec;
    this.threshold = threshold;
    this.dedupeSec = dedupeSec;
    this.state = new Map(); // key -> { absBuf, times, lastTriggerMs, peak }
  }

  /**
   * @returns {{triggered:boolean, ratio:number, peak:number, timeMs:number}|null}
   */
  push(key, startMs, sampleRate, samples) {
    if (!samples?.length || !sampleRate) return null;
    let st = this.state.get(key);
    if (!st) {
      st = {
        values: [],
        lastTriggerMs: 0,
        peak: 0,
      };
      this.state.set(key, st);
    }

    const dt = 1000 / sampleRate;
    let best = null;
    for (let i = 0; i < samples.length; i++) {
      const v = Math.abs(Number(samples[i]) || 0);
      const t = startMs + i * dt;
      st.values.push({ t, v });
      // Mantener ~LTA*2 de historia
      const cutoff = t - this.ltaSec * 2000;
      while (st.values.length > 10 && st.values[0].t < cutoff) {
        st.values.shift();
      }

      const staN = Math.max(1, Math.floor(this.staSec * sampleRate));
      const ltaN = Math.max(staN + 1, Math.floor(this.ltaSec * sampleRate));
      if (st.values.length < ltaN) continue;

      let staSum = 0;
      let ltaSum = 0;
      const n = st.values.length;
      for (let j = n - staN; j < n; j++) staSum += st.values[j].v;
      for (let j = n - ltaN; j < n; j++) ltaSum += st.values[j].v;
      const sta = staSum / staN;
      const lta = Math.max(1e-9, ltaSum / ltaN);
      const ratio = sta / lta;

      if (ratio >= this.threshold && t - st.lastTriggerMs >= this.dedupeSec * 1000) {
        st.lastTriggerMs = t;
        st.peak = Math.max(st.peak, v);
        best = { triggered: true, ratio, peak: v, timeMs: t, sta, lta };
      } else {
        st.peak = Math.max(st.peak * 0.995, v);
      }
    }
    return best;
  }

  getActivity(key) {
    const st = this.state.get(key);
    if (!st || !st.values.length) return { ratio: 0, active: false, ageMs: null };
    const last = st.values[st.values.length - 1];
    const sampleRateGuess = 20;
    const staN = Math.max(1, Math.floor(this.staSec * sampleRateGuess));
    const ltaN = Math.max(staN + 1, Math.floor(this.ltaSec * sampleRateGuess));
    if (st.values.length < ltaN) return { ratio: 0, active: false, ageMs: null };
    let staSum = 0;
    let ltaSum = 0;
    const n = st.values.length;
    for (let j = n - staN; j < n; j++) staSum += st.values[j].v;
    for (let j = n - ltaN; j < n; j++) ltaSum += st.values[j].v;
    const ratio = staSum / staN / Math.max(1e-9, ltaSum / ltaN);
    return {
      ratio,
      active: Date.now() - st.lastTriggerMs < 60_000,
      lastTriggerMs: st.lastTriggerMs || null,
      ageMs: Date.now() - last.t,
    };
  }
}

function locateFromPicks(picks) {
  if (picks.length < 2) return null;
  const { minLat, maxLat, minLon, maxLon } = VENEZUELA_BBOX;
  // Ampliar un poco el grid al Caribe occidental / norte Colombia
  const lat0 = minLat - 1;
  const lat1 = maxLat + 4;
  const lon0 = minLon - 2;
  const lon1 = maxLon + 2;
  const step = 0.4;
  const vp = WAVE_SPEEDS.pWaveKmPerSec;

  let best = null;
  for (let lat = lat0; lat <= lat1; lat += step) {
    for (let lon = lon0; lon <= lon1; lon += step) {
      // Origin time: promedio de (pick - travel)
      const originCandidates = picks.map(
        (p) => p.timeMs - (distanceKm(lat, lon, p.lat, p.lon) / vp) * 1000
      );
      originCandidates.sort((a, b) => a - b);
      const originMs = originCandidates[Math.floor(originCandidates.length / 2)];

      let rss = 0;
      for (const p of picks) {
        const pred = originMs + (distanceKm(lat, lon, p.lat, p.lon) / vp) * 1000;
        const dt = (p.timeMs - pred) / 1000;
        rss += dt * dt;
      }
      const rms = Math.sqrt(rss / picks.length);
      if (!best || rms < best.rms) {
        best = { lat, lon, originMs, rms, depthKm: 10 };
      }
    }
  }

  if (!best || best.rms > 18) return null; // rechazo si residuales grandes
  return best;
}

function estimateMagnitude(picks, hyp) {
  // Estimación empírica tipo logA + logDist (muy aproximada, estilo EEW experimental).
  const vals = [];
  for (const p of picks) {
    const dist = Math.max(10, distanceKm(hyp.lat, hyp.lon, p.lat, p.lon));
    const amp = Math.max(1, p.peak || 1);
    const mag = Math.log10(amp) + 1.2 * Math.log10(dist) - 2.8;
    vals.push(mag);
  }
  vals.sort((a, b) => a - b);
  const mid = vals[Math.floor(vals.length / 2)];
  return Math.max(2.0, Math.min(8.5, Number(mid.toFixed(1))));
}

class GlobalQuakeEngine extends EventEmitter {
  constructor(opts = {}) {
    super();
    this.detector = new StaLtaDetector({
      staSec: Number(opts.staSec || 1.0),
      ltaSec: Number(opts.ltaSec || 25.0),
      threshold: Number(opts.threshold || process.env.GQ_STA_LTA || 3.0),
    });
    this.recentPicks = []; // {key,timeMs,lat,lon,peak,ratio,network,station}
    this.detections = [];
    this.seenDetectionKeys = new Set();
    this.packetCount = 0;
    this.decodeErrors = 0;
    this.status = {
      connected: false,
      source: "seedlink",
      detail: "idle",
      updatedAt: null,
      stationsLive: 0,
      packets: 0,
    };
    this.client = null;
    this._assocTimer = null;
  }

  start() {
    const stations = GQ_STATIONS.map((s) => ({
      network: s.network,
      station: s.station,
      channels: ["BHZ.D"],
    }));

    // Una conexión por estación: RingServer multi-estación suele emitir solo 1 stream.
    this.client = new SeedLinkPool({
      host: DEFAULT_SEEDLINK.host,
      port: DEFAULT_SEEDLINK.port,
      stations,
      reconnectMs: 10_000,
    });

    this.client.on("status", (st) => {
      this.status = {
        ...this.status,
        ...st,
        packets: this.packetCount,
        stationsConfigured: GQ_STATIONS.length,
        decodeErrors: this.decodeErrors,
      };
      this.emit("status", this.status);
    });

    this.client.on("error", (err) => {
      this.emit("error", err);
    });

    this.client.on("mseed", (buf) => this._onMseed(buf));
    this.client.start();

    this._assocTimer = setInterval(() => {
      this._associate();
      if (this.status.connected) {
        this.status.packets = this.packetCount;
        this.emit("status", { ...this.status, updatedAt: Date.now() });
      }
    }, 2000);
  }

  stop() {
    if (this._assocTimer) clearInterval(this._assocTimer);
    if (this.client) this.client.stop();
  }

  getStationActivity() {
    return GQ_STATIONS.map((s) => {
      const key = `${s.network}.${s.station}`;
      const act = this.detector.getActivity(key);
      return {
        ...s,
        key,
        ratio: Number((act.ratio || 0).toFixed(2)),
        active: Boolean(act.active),
        lastTriggerMs: act.lastTriggerMs,
      };
    });
  }

  getSnapshot() {
    return {
      status: this.status,
      stations: this.getStationActivity(),
      recentPicks: this.recentPicks.slice(-40),
      detections: this.detections.slice(0, 30),
    };
  }

  _onMseed(buf) {
    this.packetCount += 1;
    const decoded = decodeSamples(buf);
    if (!decoded || decoded.error || !decoded.samples) {
      this.decodeErrors += 1;
      return;
    }

    const net = (decoded.network || "").trim();
    const sta = (decoded.station || "").trim();
    const key = `${net}.${sta}`;
    const meta = STATION_BY_KEY.get(key);
    if (!meta) return;

    // Solo canal Z
    if (decoded.channel && !/Z$/i.test(decoded.channel)) return;

    const hit = this.detector.push(key, decoded.startMs, decoded.sampleRate, decoded.samples);
    if (!hit?.triggered) return;

    const pick = {
      key,
      network: net,
      station: sta,
      lat: meta.lat,
      lon: meta.lon,
      name: meta.name,
      timeMs: hit.timeMs,
      peak: hit.peak,
      ratio: Number(hit.ratio.toFixed(2)),
    };
    this.recentPicks.push(pick);
    if (this.recentPicks.length > 200) {
      this.recentPicks = this.recentPicks.slice(-150);
    }

    this.emit("station_trigger", pick);
    this._associate();
  }

  _associate() {
    const now = Date.now();
    // Picks recientes (90 s)
    const windowPicks = this.recentPicks.filter((p) => now - p.timeMs <= 90_000);
    if (windowPicks.length < 3) return;

    // Dedup por estación (último pick)
    const bySta = new Map();
    for (const p of windowPicks) {
      const prev = bySta.get(p.key);
      if (!prev || p.timeMs > prev.timeMs) bySta.set(p.key, p);
    }
    const picks = [...bySta.values()];
    if (picks.length < 3) return;

    const hyp = locateFromPicks(picks);
    if (!hyp) return;

    const mag = estimateMagnitude(picks, hyp);
    const detKey = `${Math.round(hyp.originMs / 5000)}:${hyp.lat.toFixed(1)}:${hyp.lon.toFixed(1)}`;
    if (this.seenDetectionKeys.has(detKey)) return;
    this.seenDetectionKeys.add(detKey);
    if (this.seenDetectionKeys.size > 500) {
      this.seenDetectionKeys = new Set([...this.seenDetectionKeys].slice(-300));
    }

    const cityEtas = estimateCityEtas(hyp.lat, hyp.lon, hyp.originMs);
    const nearest = cityEtas[0];
    const intensity = nearest ? estimateIntensity(mag, nearest.distanceKm) : null;

    const detection = {
      id: `gq:${detKey}`,
      source: "globalquake-engine",
      mode: "seedlink-stalta",
      confidence: picks.length >= 5 ? "alta" : picks.length >= 4 ? "media" : "preliminar",
      mag,
      magType: "Mest",
      place: `Detección automática cerca de ${nearest?.city || "Venezuela"}`,
      time: hyp.originMs,
      lat: Number(hyp.lat.toFixed(3)),
      lon: Number(hyp.lon.toFixed(3)),
      depthKm: hyp.depthKm,
      rmsSec: Number(hyp.rms.toFixed(2)),
      pickCount: picks.length,
      picks: picks.map((p) => ({
        station: p.station,
        network: p.network,
        timeMs: p.timeMs,
        ratio: p.ratio,
      })),
      distanceToCaracasKm: distanceKm(hyp.lat, hyp.lon, CARACAS.lat, CARACAS.lon),
      venezuelaState: null,
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
      channel: "gq-eew",
    };

    this.detections.unshift(detection);
    this.detections = this.detections.slice(0, 50);
    this.emit("detection", detection);
  }
}

module.exports = {
  GlobalQuakeEngine,
  GQ_STATIONS,
  StaLtaDetector,
  decodeSamples,
};
