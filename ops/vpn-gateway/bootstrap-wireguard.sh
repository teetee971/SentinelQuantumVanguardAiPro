#!/usr/bin/env bash
set -euo pipefail
umask 077

# Sentinel VPN gateway bootstrap for a dedicated Ubuntu/Debian VPS.
# This script intentionally requires explicit network values and never stores client keys.
# Run only on a disposable/dedicated gateway host you control.

: "${SENTINEL_WG_IPV4_ADDRESS:?Set SENTINEL_WG_IPV4_ADDRESS, e.g. 10.73.0.1/24}"
: "${SENTINEL_WG_IPV6_ADDRESS:?Set SENTINEL_WG_IPV6_ADDRESS, e.g. fd73:1::1/64}"
: "${SENTINEL_WG_IPV4_SUBNET:?Set SENTINEL_WG_IPV4_SUBNET, e.g. 10.73.0.0/24}"
: "${SENTINEL_WG_IPV6_SUBNET:?Set SENTINEL_WG_IPV6_SUBNET, e.g. fd73:1::/64}"
: "${SENTINEL_PUBLIC_IPV6_PREFIX:?Set provider-routed IPv6 prefix; NAT66 is not used}"

SENTINEL_WG_PORT="${SENTINEL_WG_PORT:-51820}"
SENTINEL_WG_INTERFACE="${SENTINEL_WG_INTERFACE:-sentinel0}"
SENTINEL_EGRESS_INTERFACE="${SENTINEL_EGRESS_INTERFACE:-}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root." >&2
  exit 1
fi

if [[ ! "${SENTINEL_WG_PORT}" =~ ^[0-9]{1,5}$ ]] || (( SENTINEL_WG_PORT < 1 || SENTINEL_WG_PORT > 65535 )); then
  echo "Invalid SENTINEL_WG_PORT." >&2
  exit 1
fi

if [[ ! "${SENTINEL_WG_INTERFACE}" =~ ^[a-zA-Z0-9_.-]{1,15}$ ]]; then
  echo "Invalid SENTINEL_WG_INTERFACE." >&2
  exit 1
fi

if [[ -z "${SENTINEL_EGRESS_INTERFACE}" ]]; then
  SENTINEL_EGRESS_INTERFACE="$(ip -4 route show default | awk 'NR==1 {print $5}')"
fi
if [[ -z "${SENTINEL_EGRESS_INTERFACE}" || ! -d "/sys/class/net/${SENTINEL_EGRESS_INTERFACE}" ]]; then
  echo "Could not determine a valid egress interface." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends wireguard-tools nftables ca-certificates
apt-get clean
rm -rf /var/lib/apt/lists/*

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

cat >/etc/nftables.conf <<EOF
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
    tcp dport 22 accept
  }

  chain forward {
    type filter hook forward priority 0; policy drop;
    ct state established,related accept
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

systemctl enable --now nftables
systemctl enable "wg-quick@${SENTINEL_WG_INTERFACE}"
systemctl restart "wg-quick@${SENTINEL_WG_INTERFACE}"

if ! wg show "${SENTINEL_WG_INTERFACE}" >/dev/null 2>&1; then
  echo "WireGuard interface failed to start." >&2
  exit 1
fi

if ! nft list table inet sentinel_filter >/dev/null 2>&1; then
  echo "nftables filter rules are not active." >&2
  exit 1
fi

PUBLIC_KEY="$(cat "${PUBLIC_KEY_FILE}")"
echo "Sentinel gateway bootstrap complete."
echo "Interface: ${SENTINEL_WG_INTERFACE}"
echo "UDP port: ${SENTINEL_WG_PORT}"
echo "Public key: ${PUBLIC_KEY}"
echo "IPv6 routed prefix declared: ${SENTINEL_PUBLIC_IPV6_PREFIX}"
echo "Private key remains only at ${PRIVATE_KEY_FILE}."
echo "No client peer has been provisioned. Do not mark this gateway AVAILABLE yet."
