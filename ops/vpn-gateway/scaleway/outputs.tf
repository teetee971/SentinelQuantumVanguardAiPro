output "gateway_id" {
  description = "Scaleway Instance ID."
  value       = scaleway_instance_server.gateway.id
}

output "gateway_zone" {
  description = "Scaleway Paris zone used by the gateway."
  value       = scaleway_instance_server.gateway.zone
}

output "public_ipv4" {
  description = "Routed public IPv4 reserved for the gateway."
  value       = scaleway_instance_ip.ipv4.address
}

output "routed_ipv6_prefix" {
  description = "Routed public IPv6 /64 prefix reserved for the gateway. Feed this into Sentinel clientIpv6Prefix only after provider routing and external egress validation succeed."
  value       = scaleway_instance_ip.ipv6.prefix
}

output "wireguard_udp_port" {
  description = "WireGuard UDP port exposed by the security group."
  value       = var.wireguard_port
}
