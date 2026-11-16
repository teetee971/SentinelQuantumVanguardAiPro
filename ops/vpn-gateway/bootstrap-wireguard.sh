#!/usr/bin/env bash
set -euo pipefail
umask 077

# Sentinel VPN gateway bootstrap for a dedicated Ubuntu/Debian VPS.
# Pre-production only: this script does NOT mark a gateway AVAILABLE.
# It intentionally generates server key material on-host and never stores client keys in Git.

: "${SENTINEL_WG_IPV4_ADDRESS:?Set SENTINEL_WG_IPV4_ADDRESS, e.g. 10.73.0.1/24}"
: "${SENTINEL_WG_IPV6_ADDRESS:?Set SENTINEL_WG_IPV6_ADDRESS, e.g. fd73:1::1/64}"
: "${SENTINEL_WG_IPV4_SUBNET:?Set SENTINEL_WG_IPV4_SUBNET, e.g. 10.73.0.0/24}"
: "${SENTINEL_WG_IPV6_SUBNET:?Set SENTINEL_WG_IPV6_SUBNET, e.g. fd73:1::/64}"
: "${SENTINEL_ADMIN_SSH_CIDR:?Set the administrative SSH source CIDR, e.g. 203.0.113.10/32}"
: "${SENTINEL_CONFIRM_FIREWALL_RESET:?Set SENTINEL_CONFIRM_FIREWALL_RESET=YES after reviewing nftables impact}"

SENTINEL_WG_PORT="${SENTINEL_WG_PORT:-51820}"
SENTINEL_WG_INTERFACE="${SENTINEL_WG_INTERFACE:-sentinel0}"
SENTINEL_EGRESS_INTERFACE="${SENTINEL_EGRESS_INTERFACE:-}"
SENTINEL_SSH_PORT="${SENTINEL_SSH_PORT:-22}"
SENTINEL_IPV6_EGRESS_READY="${SENTINEL_IPV6_EGRESS_READY:-NO}"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

if [[ "${EUID}" -ne 0 ]]; then
  fail "Run as root."
fi

[[ "${SENTINEL_CONFIRM_FIREWALL_RESET}" == "YES" ]] ||
  fail "Firewall replacement not acknowledged. Review this script and set SENTINEL_CONFIRM_FIREWALL_RESET=YES."

for port_name in SENTINEL_WG_PORT SENTINEL_SSH_PORT; do
  value="${!port_name}"
  if [[ ! "${value}" =~ ^[0-9]{1,5}$ ]] || (( value < 1 || value > 65535 )); then
    fail "Invalid ${port_name}."
  fi
done

if [[ ! "${SENTINEL_WG_INTERFACE}" =~ ^[a-zA-Z0-9_.-]{1,15}$ ]]; then
  fail "Invalid SENTINEL_WG_INTERFACE."
fi

case "${SENTINEL_IPV6_EGRESS_READY}" in
  YES|NO) ;;
  *) fail "SENTINEL_IPV6_EGRESS_READY must be YES or NO." ;;
esac

if [[ -z "${SENTINEL_EGRESS_INTERFACE}" ]]; then
  SENTINEL_EGRESS_INTERFACE="$(ip -4 route show default | awk 'NR==1 {print $5}')"
fi
if [[ -z "${SENTINEL_EGRESS_INTERFACE}" ||
      ! "${SENTINEL_EGRESS_INTERFACE}" =~ ^[a-zA-Z0-9_.-]{1,15}$ ||
      ! -d "/sys/class/net/${SENTINEL_EGRESS_INTERFACE}" ]]; then
  fail "Could not determine a valid egress interface."
fi

# Require iproute2 parsing to succeed before touching the firewall.
ip -4 route get 1.1.1.1 >/dev/null 2>&1 || fail "IPv4 routing is not healthy."
ip -4 route get "${SENTINEL_ADMIN_SSH_CIDR%/*}" >/dev/null 2>&1 ||
  fail "Administrative SSH source is not routable from this host."

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends wireguard-tools nftables ca-certificates python3-minimal
apt-get clean
rm -rf /var/lib/apt/lists/*

python3 - \
  "${SENTINEL_WG_IPV4_ADDRESS}" \
  "${SENTINEL_WG_IPV6_ADDRESS}" \
  "${SENTINEL_WG_IPV4_SUBNET}" \
  "${SENTINEL_WG_IPV6_SUBNET}" \
  "${SENTINEL_ADMIN_SSH_CIDR}" \
  "${SENTINEL_IPV6_EGRESS_READY}" <<'PY'
import ipaddress
import sys

raw_values = sys.argv[1:6]
if any(any(ch in value for ch in ("\n", "\r", "\x00")) for value in raw_values):
    raise SystemExit("Network values must not contain control characters")

v4_addr = ipaddress.ip_interface(sys.argv[1])
v6_addr = ipaddress.ip_interface(sys.argv[2])
v4_net = ipaddress.ip_network(sys.argv[3], strict=False)
v6_net = ipaddress.ip_network(sys.argv[4], strict=False)
admin = ipaddress.ip_network(sys.argv[5], strict=False)
ipv6_ready = sys.argv[6] == "YES"

if v4_addr.version != 4 or v4_net.version != 4:
    raise SystemExit("IPv4 WireGuard address/subnet is invalid")
if v6_addr.version != 6 or v6_net.version != 6:
    raise SystemExit("IPv6 WireGuard address/subnet is invalid")
if admin.version != 4:
    raise SystemExit("SENTINEL_ADMIN_SSH_CIDR must currently be IPv4")
if v4_addr.ip not in v4_net:
    raise SystemExit("WireGuard IPv4 address is outside SENTINEL_WG_IPV4_SUBNET")
if v6_addr.ip not in v6_net:
    raise SystemExit("WireGuard IPv6 address is outside SENTINEL_WG_IPV6_SUBNET")
if ipv6_ready and not v6_addr.ip.is_global:
    raise SystemExit("IPv6 egress cannot be marked ready with a non-global WireGuard IPv6 address")
if ipv6_ready and not v6_net.network_address.is_global:
    raise SystemExit("IPv6 egress cannot be marked ready with a non-global WireGuard IPv6 subnet")
PY

install -d -m 0700 /etc/wireguard
PRIVATE_KEY_FILE="/etc/wireguard/${SENTINEL_WG_INTERFACE}.key"
PUBLIC_KEY_FILE="/etc/wireguard/${SENTINEL_WG_INTERFACE}.pub"

if [[ ! -s "${PRIVATE_KEY_FILE}" ]]; then
  wg genkey > "${PRIVATE_KEY_FILE}"
  chmod 0600 "${PRIVATE_KEY_FILE}"
fi
wg pubkey < "${PRIVATE_KEY_FILE}" > "${PUBLIC_KEY_FILE}"
chmod 0644 "${PUBLIC_KEY_FILE}"

cat >/etc/sysctl.d/90-sentinel-vpn.conf <<'EOF'
net.ipv4.ip_forward=1
net.ipv6.conf.all.forwarding=1
net.ipv4.conf.all.rp_filter=1
net.ipv4.conf.default.rp_filter=1
EOF
sysctl --system >/dev/null

cat >"/etc/wireguard/${SENTINEL_WG_INTERFACE}.conf" <<EOF
[Interface]
Address = ${SENTINEL_WG_IPV4_ADDRESS}, ${SENTINEL_WG_IPV6_ADDRESS}
ListenPort = ${SENTINEL_WG_PORT}
PrivateKey = $(cat "${PRIVATE_KEY_FILE}")
SaveConfig = false
EOF
chmod 0600 "/etc/wireguard/${SENTINEL_WG_INTERFACE}.conf"

NFT_TMP="$(mktemp)"
trap 'rm -f "${NFT_TMP}"' EXIT
cat >"${NFT_TMP}" <<EOF
#!/usr/sbin/nft -f
flush ruleset

table inet sentinel_filter {
  chain input {
    type filter hook input priority 0; policy drop;
    ct state established,related accept
    iifname "lo" accept
    ip protocol icmp accept
    ip6 nexthdr ipv6-icmp accept
    udp dport ${SENTINEL_WG_PORT} accept
    iifname "${SENTINEL_WG_INTERFACE}" udp dport 53 accept
    iifname "${SENTINEL_WG_INTERFACE}" tcp dport 53 accept
    ip saddr ${SENTINEL_ADMIN_SSH_CIDR} tcp dport ${SENTINEL_SSH_PORT} accept
  }

  chain forward {
    type filter hook forward priority 0; policy drop;
    ct state established,related accept
    iifname "${SENTINEL_WG_INTERFACE}" udp dport 53 drop
    iifname "${SENTINEL_WG_INTERFACE}" tcp dport 53 drop
    iifname "${SENTINEL_WG_INTERFACE}" oifname "${SENTINEL_EGRESS_INTERFACE}" accept
    iifname "${SENTINEL_EGRESS_INTERFACE}" oifname "${SENTINEL_WG_INTERFACE}" ct state established,related accept
  }

  chain output {
    type filter hook output priority 0; policy accept;
  }
}

table ip sentinel_nat {
  chain postrouting {
    type nat hook postrouting priority 100; policy accept;
    ip saddr ${SENTINEL_WG_IPV4_SUBNET} oifname "${SENTINEL_EGRESS_INTERFACE}" masquerade
  }
}
EOF

# Parse the future ruleset before replacing the current firewall.
nft -c -f "${NFT_TMP}" || fail "Generated nftables rules failed validation."
install -m 0600 "${NFT_TMP}" /etc/nftables.conf

systemctl enable --now nftables
systemctl enable "wg-quick@${SENTINEL_WG_INTERFACE}"
systemctl restart "wg-quick@${SENTINEL_WG_INTERFACE}"

wg show "${SENTINEL_WG_INTERFACE}" >/dev/null 2>&1 ||
  fail "WireGuard interface failed to start."
nft list table inet sentinel_filter >/dev/null 2>&1 ||
  fail "nftables filter rules are not active."

PUBLIC_KEY="$(cat "${PUBLIC_KEY_FILE}")"
echo "Sentinel gateway bootstrap complete."
echo "Interface: ${SENTINEL_WG_INTERFACE}"
echo "UDP port: ${SENTINEL_WG_PORT}"
echo "Public key: ${PUBLIC_KEY}"
echo "Private key remains only at ${PRIVATE_KEY_FILE}."
echo "IPv4 egress NAT: configured."
if [[ "${SENTINEL_IPV6_EGRESS_READY}" == "YES" ]]; then
  echo "IPv6 egress: operator declared ready; it must still pass an external probe."
else
  echo "IPv6 egress: NOT READY. Configure provider-routed IPv6 before production acceptance."
fi
echo "No client peer has been provisioned."
echo "DO NOT mark this gateway AVAILABLE until external IPv4/IPv6, DNS-leak, geolocation and reconnect tests pass."
