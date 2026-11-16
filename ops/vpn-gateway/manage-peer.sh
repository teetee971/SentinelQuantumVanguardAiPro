#!/usr/bin/env bash
set -euo pipefail
umask 077

ACTION="${1:-}"
PUBLIC_KEY="${2:-}"
IPV4_CIDR="${3:-}"
IPV6_CIDR="${4:-}"
WG_INTERFACE="${SENTINEL_WG_INTERFACE:-sentinel0}"
LOCK_FILE="${SENTINEL_WG_PEER_LOCK:-/run/lock/sentinel-vpn-peer.lock}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ "${EUID}" -eq 0 ]] || fail "Run as root."
[[ "${ACTION}" == "add" || "${ACTION}" == "remove" ]] || fail "Usage: $0 add|remove <public-key> [ipv4/32] [ipv6/128]."
[[ "${WG_INTERFACE}" =~ ^[a-zA-Z0-9_.-]{1,15}$ ]] || fail "Invalid WireGuard interface."
[[ "${PUBLIC_KEY}" =~ ^[A-Za-z0-9+/]{43}=$ ]] || fail "Invalid WireGuard public key format."

command -v wg >/dev/null 2>&1 || fail "wg command not found."
wg show "${WG_INTERFACE}" >/dev/null 2>&1 || fail "WireGuard interface is not active."

install -d -m 0755 "$(dirname "${LOCK_FILE}")"
exec 9>"${LOCK_FILE}"
flock -x 9

if [[ "${ACTION}" == "remove" ]]; then
  [[ -z "${IPV4_CIDR}" && -z "${IPV6_CIDR}" ]] ||
    fail "remove accepts only the public key."
  wg set "${WG_INTERFACE}" peer "${PUBLIC_KEY}" remove
  echo "Peer removed from ${WG_INTERFACE}."
  exit 0
fi

[[ -n "${IPV4_CIDR}" && -n "${IPV6_CIDR}" ]] ||
  fail "add requires both IPv4 /32 and IPv6 /128 addresses."

python3 - "${IPV4_CIDR}" "${IPV6_CIDR}" <<'PY'
import ipaddress
import sys

if len(sys.argv) != 3:
    raise SystemExit("expected IPv4 and IPv6 CIDRs")

v4 = ipaddress.ip_interface(sys.argv[1])
v6 = ipaddress.ip_interface(sys.argv[2])

if v4.version != 4 or v4.network.prefixlen != 32:
    raise SystemExit("IPv4 peer address must be a host /32")
if v6.version != 6 or v6.network.prefixlen != 128:
    raise SystemExit("IPv6 peer address must be a host /128")
if v4.ip.is_unspecified or v4.ip.is_multicast or v4.ip.is_loopback:
    raise SystemExit("IPv4 peer address is not eligible")
if v6.ip.is_unspecified or v6.ip.is_multicast or v6.ip.is_loopback:
    raise SystemExit("IPv6 peer address is not eligible")
PY

ALLOWED_IPS="${IPV4_CIDR},${IPV6_CIDR}"

# Refuse duplicate address ownership on another peer. Exact peer replacement is allowed.
while IFS=$'\t' read -r existing_key existing_allowed; do
  [[ -n "${existing_key}" ]] || continue
  if [[ "${existing_key}" != "${PUBLIC_KEY}" ]]; then
    IFS=',' read -ra existing_ranges <<< "${existing_allowed}"
    for range in "${existing_ranges[@]}"; do
      range="${range//[[:space:]]/}"
      if [[ "${range}" == "${IPV4_CIDR}" || "${range}" == "${IPV6_CIDR}" ]]; then
        fail "Peer address is already assigned to another public key."
      fi
    done
  fi
done < <(wg show "${WG_INTERFACE}" allowed-ips)

wg set "${WG_INTERFACE}" peer "${PUBLIC_KEY}" allowed-ips "${ALLOWED_IPS}"

# Verify kernel/runtime state after mutation.
APPLIED="$(wg show "${WG_INTERFACE}" allowed-ips | awk -v key="${PUBLIC_KEY}" '$1 == key {print $2}')"
[[ -n "${APPLIED}" ]] || fail "Peer was not visible after apply."

echo "Peer applied to ${WG_INTERFACE} with bounded dual-stack AllowedIPs."
