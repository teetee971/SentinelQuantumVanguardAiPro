import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);
const WG_KEY = /^[A-Za-z0-9+/]{43}=$/;
const IPV4_HOST = /^\d{1,3}(?:\.\d{1,3}){3}\/32$/;
const IPV6_HOST = /^[0-9A-Fa-f:]+\/128$/;

export class VpnGatewayPeerRuntime {
  #scriptPath;
  #interfaceName;
  #runner;

  constructor({
    scriptPath = new URL("./manage-peer.sh", import.meta.url).pathname,
    interfaceName = "sentinel0",
    runner = execFileAsync,
  } = {}) {
    if (typeof scriptPath !== "string" || scriptPath.length < 1 || scriptPath.length > 4096) {
      throw new Error("VPN_PEER_RUNTIME_SCRIPT_INVALID");
    }
    if (!/^[A-Za-z0-9_.-]{1,15}$/.test(interfaceName)) {
      throw new Error("VPN_PEER_RUNTIME_INTERFACE_INVALID");
    }
    if (typeof runner !== "function") throw new TypeError("runner must be a function");
    this.#scriptPath = scriptPath;
    this.#interfaceName = interfaceName;
    this.#runner = runner;
  }

  async apply({ devicePublicKey, clientAddresses }) {
    if (!WG_KEY.test(String(devicePublicKey || ""))) {
      return Object.freeze({ accepted: false, reason: "VPN_PEER_RUNTIME_KEY_INVALID" });
    }
    if (!Array.isArray(clientAddresses) || clientAddresses.length !== 2) {
      return Object.freeze({ accepted: false, reason: "VPN_PEER_RUNTIME_ADDRESSES_INVALID" });
    }
    const ipv4 = clientAddresses.find(value => IPV4_HOST.test(String(value)));
    const ipv6 = clientAddresses.find(value => IPV6_HOST.test(String(value)));
    if (!ipv4 || !ipv6) {
      return Object.freeze({ accepted: false, reason: "VPN_PEER_RUNTIME_ADDRESSES_INVALID" });
    }

    return this.#invoke(["add", devicePublicKey, ipv4, ipv6], "VPN_PEER_RUNTIME_APPLIED");
  }

  async remove(devicePublicKey) {
    if (!WG_KEY.test(String(devicePublicKey || ""))) {
      return Object.freeze({ accepted: false, reason: "VPN_PEER_RUNTIME_KEY_INVALID" });
    }
    return this.#invoke(["remove", devicePublicKey], "VPN_PEER_RUNTIME_REMOVED");
  }

  async #invoke(args, successReason) {
    try {
      await this.#runner(this.#scriptPath, args, {
        env: {
          PATH: process.env.PATH || "/usr/sbin:/usr/bin:/sbin:/bin",
          SENTINEL_WG_INTERFACE: this.#interfaceName,
        },
        timeout: 10_000,
        maxBuffer: 16 * 1024,
        windowsHide: true,
      });
      return Object.freeze({ accepted: true, reason: successReason });
    } catch {
      return Object.freeze({ accepted: false, reason: "VPN_PEER_RUNTIME_COMMAND_FAILED" });
    }
  }
}
