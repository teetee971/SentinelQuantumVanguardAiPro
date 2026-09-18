#!/usr/bin/env bash
set -euo pipefail
umask 077

# Installs the DNS filtering component for a Sentinel WireGuard gateway.
# This is intentionally a gateway-side resolver so Android keeps a single VpnService/WireGuard path.

: "${SENTINEL_WG_IPV4_ADDRESS:?Set the WireGuard gateway IPv4 interface address, e.g. 10.73.0.1/24}"
: "${SENTINEL_WG_IPV6_ADDRESS:?Set the WireGuard gateway IPv6 interface address}"
: "${SENTINEL_WG_IPV4_SUBNET:?Set the WireGuard IPv4 subnet}"
: "${SENTINEL_WG_IPV6_SUBNET:?Set the WireGuard IPv6 subnet}"
: "${SENTINEL_DNS_UPSTREAM_IPV4_1:?Set the first DNS-over-TLS upstream IPv4 address}"
: "${SENTINEL_DNS_UPSTREAM_TLS_NAME_1:?Set the TLS authentication name for the first upstream}"

SENTINEL_DNS_UPSTREAM_IPV4_2="${SENTINEL_DNS_UPSTREAM_IPV4_2:-}"
SENTINEL_DNS_UPSTREAM_TLS_NAME_2="${SENTINEL_DNS_UPSTREAM_TLS_NAME_2:-}"
SENTINEL_BLOCKLIST_FILE="${SENTINEL_BLOCKLIST_FILE:-/etc/sentinel-vpn/blocklist.txt}"
SENTINEL_ALLOWLIST_FILE="${SENTINEL_ALLOWLIST_FILE:-/etc/sentinel-vpn/allowlist.txt}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

[[ "${EUID}" -eq 0 ]] || fail "Run as root."

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends unbound dnsutils ca-certificates python3-minimal
apt-get clean
rm -rf /var/lib/apt/lists/*

install -d -m 0750 /etc/sentinel-vpn
touch "${SENTINEL_BLOCKLIST_FILE}" "${SENTINEL_ALLOWLIST_FILE}"
chmod 0640 "${SENTINEL_BLOCKLIST_FILE}" "${SENTINEL_ALLOWLIST_FILE}"

VALIDATED="$(
python3 - \
  "${SENTINEL_WG_IPV4_ADDRESS}" \
  "${SENTINEL_WG_IPV6_ADDRESS}" \
  "${SENTINEL_WG_IPV4_SUBNET}" \
  "${SENTINEL_WG_IPV6_SUBNET}" \
  "${SENTINEL_DNS_UPSTREAM_IPV4_1}" \
  "${SENTINEL_DNS_UPSTREAM_TLS_NAME_1}" \
  "${SENTINEL_DNS_UPSTREAM_IPV4_2}" \
  "${SENTINEL_DNS_UPSTREAM_TLS_NAME_2}" <<'PY'
import ipaddress
import re
import sys

v4_addr = ipaddress.ip_interface(sys.argv[1])
v6_addr = ipaddress.ip_interface(sys.argv[2])
v4_net = ipaddress.ip_network(sys.argv[3], strict=False)
v6_net = ipaddress.ip_network(sys.argv[4], strict=False)
upstream1 = ipaddress.ip_address(sys.argv[5])
tls1 = sys.argv[6].strip().lower()
upstream2_raw = sys.argv[7].strip()
tls2 = sys.argv[8].strip().lower()

def valid_tls_name(value: str) -> bool:
    if len(value) > 253 or not value:
        return False
    labels = value.rstrip(".").split(".")
    return all(
        0 < len(label) <= 63
        and not label.startswith("-")
        and not label.endswith("-")
        and re.fullmatch(r"[a-z0-9-]+", label)
        for label in labels
    )

if v4_addr.version != 4 or v4_net.version != 4 or v4_addr.ip not in v4_net:
    raise SystemExit("Invalid WireGuard IPv4 address/subnet")
if v6_addr.version != 6 or v6_net.version != 6 or v6_addr.ip not in v6_net:
    raise SystemExit("Invalid WireGuard IPv6 address/subnet")
if upstream1.version != 4:
    raise SystemExit("First DNS upstream must currently be IPv4")
if not valid_tls_name(tls1):
    raise SystemExit("Invalid TLS authentication name for first DNS upstream")

if bool(upstream2_raw) != bool(tls2):
    raise SystemExit("Second DNS upstream IP and TLS name must be configured together")
if upstream2_raw:
    upstream2 = ipaddress.ip_address(upstream2_raw)
    if upstream2.version != 4:
        raise SystemExit("Second DNS upstream must currently be IPv4")
    if not valid_tls_name(tls2):
        raise SystemExit("Invalid TLS authentication name for second DNS upstream")

print(v4_addr.ip)
print(v6_addr.ip)
print(v4_net)
print(v6_net)
PY
)" || fail "DNS gateway network validation failed."

mapfile -t NETWORK_VALUES <<<"${VALIDATED}"
DNS_LISTEN_IPV4="${NETWORK_VALUES[0]}"
DNS_LISTEN_IPV6="${NETWORK_VALUES[1]}"
WG_IPV4_SUBNET="${NETWORK_VALUES[2]}"
WG_IPV6_SUBNET="${NETWORK_VALUES[3]}"

BLOCK_CONF="$(mktemp)"
trap 'rm -f "${BLOCK_CONF}"' EXIT

python3 - \
  "${SENTINEL_BLOCKLIST_FILE}" \
  "${SENTINEL_ALLOWLIST_FILE}" \
  "${BLOCK_CONF}" <<'PY'
from pathlib import Path
import re
import sys

block_path = Path(sys.argv[1])
allow_path = Path(sys.argv[2])
output_path = Path(sys.argv[3])

MAX_BYTES = 8 * 1024 * 1024
MAX_LINES = 400_000
MAX_BLOCK_RULES = 250_000
MAX_ALLOW_RULES = 10_000

def normalize(raw: str):
    value = raw.split("#", 1)[0].strip()
    if not value:
        return None
    parts = value.split()
    if len(parts) == 2 and parts[0] in {"0.0.0.0", "127.0.0.1"}:
        value = parts[1]
    elif len(parts) != 1:
        return None
    value = value.strip().rstrip(".")
    try:
        value = value.encode("idna").decode("ascii").lower()
    except UnicodeError:
        return None
    if len(value) > 253 or "." not in value:
        return None
    labels = value.split(".")
    if any(
        not label
        or len(label) > 63
        or label.startswith("-")
        or label.endswith("-")
        or re.fullmatch(r"[a-z0-9-]+", label) is None
        for label in labels
    ):
        return None
    return value

def load(path: Path, max_rules: int):
    raw = path.read_bytes()
    if len(raw) > MAX_BYTES:
        raise SystemExit(f"{path} exceeds {MAX_BYTES} bytes")
    text = raw.decode("utf-8", errors="strict")
    values = set()
    for index, line in enumerate(text.splitlines()):
        if index >= MAX_LINES:
            raise SystemExit(f"{path} exceeds {MAX_LINES} lines")
        normalized = normalize(line)
        if normalized:
            values.add(normalized)
            if len(values) > max_rules:
                raise SystemExit(f"{path} exceeds {max_rules} accepted rules")
    return values

blocked = load(block_path, MAX_BLOCK_RULES)
allowed = load(allow_path, MAX_ALLOW_RULES)
blocked.difference_update(allowed)

with output_path.open("w", encoding="utf-8") as handle:
    handle.write("# Generated by Sentinel. Do not edit manually.\n")
    for domain in sorted(blocked):
        handle.write(f'local-zone: "{domain}." always_nxdomain\n')
    for domain in sorted(allowed):
        handle.write(f'local-zone: "{domain}." always_transparent\n')

print(f"Generated {len(blocked)} blocked and {len(allowed)} allowed domains")
PY

install -m 0644 "${BLOCK_CONF}" /etc/unbound/unbound.conf.d/sentinel-blocklist.conf

cat >/etc/unbound/unbound.conf.d/sentinel-vpn.conf <<EOF
server:
    interface: ${DNS_LISTEN_IPV4}
    interface: ${DNS_LISTEN_IPV6}
    port: 53
    access-control: ${WG_IPV4_SUBNET} allow
    access-control: ${WG_IPV6_SUBNET} allow
    do-ip4: yes
    do-ip6: yes
    do-udp: yes
    do-tcp: yes
    hide-identity: yes
    hide-version: yes
    qname-minimisation: yes
    harden-glue: yes
    harden-dnssec-stripped: yes
    prefetch: yes
    verbosity: 1
    log-queries: no
    log-replies: no
    tls-cert-bundle: "/etc/ssl/certs/ca-certificates.crt"

forward-zone:
    name: "."
    forward-tls-upstream: yes
    forward-first: no
    forward-addr: ${SENTINEL_DNS_UPSTREAM_IPV4_1}@853#${SENTINEL_DNS_UPSTREAM_TLS_NAME_1}
EOF

if [[ -n "${SENTINEL_DNS_UPSTREAM_IPV4_2}" ]]; then
  printf '    forward-addr: %s@853#%s\n' \
    "${SENTINEL_DNS_UPSTREAM_IPV4_2}" \
    "${SENTINEL_DNS_UPSTREAM_TLS_NAME_2}" \
    >> /etc/unbound/unbound.conf.d/sentinel-vpn.conf
fi

unbound-checkconf
systemctl enable unbound
systemctl restart unbound

if ! systemctl is-active --quiet unbound; then
  fail "Unbound did not start."
fi

if ! dig +time=3 +tries=1 @"${DNS_LISTEN_IPV4}" example.com A >/dev/null; then
  fail "DNS resolver smoke test failed."
fi

FIRST_BLOCKED="$(
python3 - "${SENTINEL_BLOCKLIST_FILE}" <<'PY'
from pathlib import Path
import re
import sys

for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines():
    value = line.split("#", 1)[0].strip()
    if not value:
        continue
    parts = value.split()
    if len(parts) == 2 and parts[0] in {"0.0.0.0", "127.0.0.1"}:
        value = parts[1]
    elif len(parts) != 1:
        continue
    try:
        value = value.strip().rstrip(".").encode("idna").decode("ascii").lower()
    except UnicodeError:
        continue
    if "." in value and re.fullmatch(r"[a-z0-9.-]+", value):
        print(value)
        break
PY
)"

if [[ -n "${FIRST_BLOCKED}" ]]; then
  if ! dig +time=3 +tries=1 +noall +comments @"${DNS_LISTEN_IPV4}" "${FIRST_BLOCKED}" A |
      grep -q 'status: NXDOMAIN'; then
    fail "Blocklist smoke test did not return NXDOMAIN for the first blocked domain."
  fi
fi

echo "Sentinel filtering DNS is active on the WireGuard gateway addresses."
echo "Configure provisioned WireGuard clients to use ${DNS_LISTEN_IPV4} (and optionally ${DNS_LISTEN_IPV6}) as DNS."
echo "Plain DNS sent by clients to external port 53 should be blocked by the gateway firewall."
echo "DoH inside HTTPS cannot be generically blocked without additional policy and must not be claimed as covered."
