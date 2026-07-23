const { WebSocket } = require("ws");

const EMSC_REALTIME_WS_URL =
  process.env.EMSC_REALTIME_WS_URL ||
  "wss://www.seismicportal.eu/standing_order/websocket";

function parseRealtimeMessage(rawText) {
  let parsed;
  try {
    parsed = JSON.parse(rawText);
  } catch {
    return null;
  }

  const feature =
    parsed?.data?.geometry && parsed?.data?.properties
      ? parsed.data
      : parsed?.feature?.geometry && parsed?.feature?.properties
        ? parsed.feature
        : null;

  if (feature) {
    const coords = feature.geometry.coordinates || [];
    const props = feature.properties || {};
    return {
      id: feature.id || props.unid || props.eventid,
      lon: coords[0],
      lat: coords[1],
      depthKm: coords[2],
      mag: props.mag ?? props.magnitude,
      time: props.time || props.lastupdate || props.updated,
      place: props.flynn_region || props.title || props.place,
    };
  }

  // Fallback for lighter websocket payloads.
  if (parsed && typeof parsed === "object") {
    return {
      id: parsed.id || parsed.unid || parsed.eventid,
      lon: parsed.lon ?? parsed.longitude,
      lat: parsed.lat ?? parsed.latitude,
      depthKm: parsed.depth,
      mag: parsed.mag ?? parsed.magnitude,
      time: parsed.time || parsed.lastupdate || parsed.updated,
      place: parsed.flynn_region || parsed.title || parsed.region || parsed.place,
    };
  }

  return null;
}

class RealtimeStream {
  constructor({ onStatus, onEvent, onError }) {
    this.onStatus = onStatus || (() => {});
    this.onEvent = onEvent || (() => {});
    this.onError = onError || (() => {});
    this.ws = null;
    this.reconnectTimer = null;
  }

  start() {
    this.connect();
  }

  connect() {
    this.onStatus({
      connected: false,
      source: "emsc-rt",
      updatedAt: Date.now(),
      detail: "connecting",
    });

    this.ws = new WebSocket(EMSC_REALTIME_WS_URL, { handshakeTimeout: 12_000 });

    this.ws.on("open", () => {
      this.onStatus({
        connected: true,
        source: "emsc-rt",
        updatedAt: Date.now(),
        detail: "connected",
      });
    });

    this.ws.on("message", (payload) => {
      const parsed = parseRealtimeMessage(payload.toString());
      if (!parsed) return;
      this.onEvent(parsed);
    });

    this.ws.on("close", () => {
      this.onStatus({
        connected: false,
        source: "emsc-rt",
        updatedAt: Date.now(),
        detail: "reconnecting",
      });
      this.scheduleReconnect();
    });

    this.ws.on("error", (error) => {
      this.onError(error);
      this.onStatus({
        connected: false,
        source: "emsc-rt",
        updatedAt: Date.now(),
        detail: "error",
      });
    });
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 5000);
  }
}

module.exports = { RealtimeStream };
