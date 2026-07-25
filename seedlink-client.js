/**
 * Cliente SeedLink (TCP) para streaming MiniSEED en tiempo real.
 * Soporta:
 *  - SeedLink clásico: cabecera 6 dígitos + espacio + 'D' + 512 MiniSEED
 *  - RingServer (IRIS): cabecera 'SL' + 6 hex + 512 MiniSEED
 *
 * Nota: IRIS RingServer con multi-estación a menudo solo emite 1 estación por
 * conexión. Por eso GlobalQuakeEngine abre una conexión por estación.
 */
const net = require("net");
const EventEmitter = require("events");

const PACKET_HEADER = 8;
const MSEED_SIZE = 512;
const DATA_PACKET = PACKET_HEADER + MSEED_SIZE;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

class SeedLinkClient extends EventEmitter {
  constructor(opts) {
    super();
    this.host = opts.host;
    this.port = opts.port || 18000;
    this.stations = opts.stations || [];
    this.label = opts.label || `${this.host}:${this.port}`;
    this.socket = null;
    this.buffer = Buffer.alloc(0);
    this.running = false;
    this.connected = false;
    this.reconnectMs = opts.reconnectMs || 8_000;
    this._reconnectTimer = null;
    this.handshakeDone = false;
    this._generation = 0;
  }

  start() {
    if (this.running) return;
    this.running = true;
    this._connect();
  }

  stop() {
    this.running = false;
    this._generation += 1;
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this.socket) {
      try {
        this.socket.removeAllListeners();
        this.socket.destroy();
      } catch {
        // ignore
      }
      this.socket = null;
    }
    this.connected = false;
  }

  _scheduleReconnect() {
    if (!this.running) return;
    if (this._reconnectTimer) return;
    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      this._connect();
    }, this.reconnectMs);
  }

  _connect() {
    if (!this.running) return;
    const generation = ++this._generation;

    if (this.socket) {
      try {
        this.socket.removeAllListeners();
        this.socket.destroy();
      } catch {
        // ignore
      }
      this.socket = null;
    }

    this.buffer = Buffer.alloc(0);
    this.connected = false;
    this.handshakeDone = false;

    const socket = net.createConnection({ host: this.host, port: this.port });
    this.socket = socket;
    socket.setKeepAlive(true, 30_000);
    socket.setTimeout(0);

    socket.on("connect", () => {
      if (generation !== this._generation) return;
      this._handshake(socket, generation).catch((err) => {
        this.emit("error", err);
        if (generation === this._generation) socket.destroy();
      });
    });

    socket.on("data", (chunk) => {
      if (generation !== this._generation) return;
      this.buffer = Buffer.concat([this.buffer, chunk]);
      if (this.handshakeDone) this._consumePackets();
    });

    socket.on("error", (err) => {
      if (generation !== this._generation) return;
      this.emit("error", err);
    });

    socket.on("close", () => {
      if (generation !== this._generation) return;
      this.connected = false;
      this.handshakeDone = false;
      this.emit("status", {
        connected: false,
        detail: "disconnected",
        source: this.label,
        updatedAt: Date.now(),
      });
      this._scheduleReconnect();
    });
  }

  async _handshake(socket, generation) {
    await this._send(socket, "HELLO");
    await sleep(250);
    if (generation !== this._generation || socket.destroyed) {
      throw new Error("socket closed during HELLO");
    }

    for (const st of this.stations) {
      if (generation !== this._generation || socket.destroyed) {
        throw new Error("socket closed during subscribe");
      }
      const channels = st.channels || ["BHZ.D"];
      await this._send(socket, `STATION ${st.station} ${st.network}`);
      for (const ch of channels) {
        await this._send(socket, `SELECT ${ch}`);
      }
      await this._send(socket, "END");
    }

    await sleep(100);
    if (generation !== this._generation || socket.destroyed) {
      throw new Error("socket closed before DATA");
    }

    await this._send(socket, "DATA");
    this.handshakeDone = true;
    this.connected = true;
    this._consumePackets();
    this.emit("status", {
      connected: true,
      detail: "streaming",
      source: this.label,
      stations: this.stations.length,
      updatedAt: Date.now(),
    });
  }

  _send(socket, cmd) {
    return new Promise((resolve, reject) => {
      if (!socket || socket.destroyed) {
        reject(new Error("socket closed"));
        return;
      }
      socket.write(`${cmd}\r\n`, (err) => (err ? reject(err) : resolve()));
    });
  }

  _consumePackets() {
    while (this.buffer.length >= DATA_PACKET) {
      const kind = this._detectHeader(this.buffer);
      if (!kind) {
        const next = this._findNextHeader(this.buffer);
        if (next < 0) {
          if (this.buffer.length > 16384) {
            this.buffer = this.buffer.slice(this.buffer.length - 32);
          }
          return;
        }
        this.buffer = this.buffer.slice(next);
        continue;
      }

      if (this.buffer.length < DATA_PACKET) return;
      const mseed = Buffer.from(this.buffer.slice(PACKET_HEADER, DATA_PACKET));
      this.buffer = this.buffer.slice(DATA_PACKET);
      this.emit("mseed", mseed);
    }
  }

  _detectHeader(buf) {
    if (buf[0] === 0x53 && buf[1] === 0x4c) {
      const seq = buf.slice(2, 8).toString("ascii");
      if (/^[0-9A-F]{6}$/i.test(seq)) return "ringserver";
    }
    const seq = buf.slice(0, 6).toString("ascii");
    if (/^[0-9A-F]{6}$/i.test(seq) && buf[6] === 0x20 && buf[7] === 0x44) {
      return "classic";
    }
    return null;
  }

  _findNextHeader(buf) {
    for (let i = 0; i < buf.length - 8; i++) {
      if (
        buf[i] === 0x53 &&
        buf[i + 1] === 0x4c &&
        /^[0-9A-F]{6}$/i.test(buf.slice(i + 2, i + 8).toString("ascii"))
      ) {
        return i;
      }
      if (
        /^[0-9A-F]{6}$/i.test(buf.slice(i, i + 6).toString("ascii")) &&
        buf[i + 6] === 0x20 &&
        buf[i + 7] === 0x44
      ) {
        return i;
      }
    }
    return -1;
  }
}

/**
 * Pool: una conexión SeedLink por estación (mejor cobertura en IRIS RingServer).
 */
class SeedLinkPool extends EventEmitter {
  constructor({ host, port, stations, reconnectMs = 10_000 }) {
    super();
    this.host = host;
    this.port = port || 18000;
    this.stations = stations || [];
    this.reconnectMs = reconnectMs;
    this.clients = [];
    this.connectedCount = 0;
  }

  start() {
    this.stop();
    this.connectedCount = 0;
    for (const st of this.stations) {
      const label = `${st.network}.${st.station}`;
      const client = new SeedLinkClient({
        host: this.host,
        port: this.port,
        stations: [
          {
            network: st.network,
            station: st.station,
            channels: st.channels || ["BHZ.D"],
          },
        ],
        label,
        reconnectMs: this.reconnectMs,
      });
      client.on("mseed", (buf) => this.emit("mseed", buf, label));
      client.on("error", (err) => this.emit("error", err, label));
      client.on("status", (status) => {
        // recalcular conectados
        this.connectedCount = this.clients.filter((c) => c.connected).length;
        this.emit("status", {
          connected: this.connectedCount > 0,
          detail: `${this.connectedCount}/${this.clients.length} enlaces`,
          source: "iris-seedlink-pool",
          stations: this.connectedCount,
          updatedAt: Date.now(),
          link: status,
        });
      });
      this.clients.push(client);
      // escalonar arranque para no saturar
      setTimeout(() => client.start(), 150 * this.clients.length);
    }
  }

  stop() {
    for (const c of this.clients) c.stop();
    this.clients = [];
    this.connectedCount = 0;
  }
}

module.exports = { SeedLinkClient, SeedLinkPool };
