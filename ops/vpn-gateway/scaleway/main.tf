terraform {
  required_version = ">= 1.6.0"

  required_providers {
    scaleway = {
      source  = "scaleway/scaleway"
      version = "~> 2.81"
    }
  }
}

provider "scaleway" {
  project_id = var.project_id
  zone       = var.zone
  region     = var.region
}

resource "scaleway_instance_ip" "ipv4" {
  project_id = var.project_id
  zone       = var.zone
  type       = "routed_ipv4"
  tags       = ["sentinel-vpn", "france", "gateway"]
}

resource "scaleway_instance_ip" "ipv6" {
  project_id = var.project_id
  zone       = var.zone
  type       = "routed_ipv6"
  tags       = ["sentinel-vpn", "france", "gateway"]
}

resource "scaleway_instance_security_group" "gateway" {
  project_id              = var.project_id
  zone                    = var.zone
  name                    = "sentinel-vpn-fr-gateway"
  description             = "Minimal ingress for Sentinel WireGuard gateway and provisioning endpoint."
  inbound_default_policy  = "drop"
  outbound_default_policy = "accept"
  stateful                = true
  enable_default_security = true
  tags                    = ["sentinel-vpn", "france", "gateway"]

  inbound_rule {
    action   = "accept"
    protocol = "TCP"
    port     = var.ssh_port
    ip_range = var.admin_ssh_cidr
  }

  inbound_rule {
    action   = "accept"
    protocol = "UDP"
    port     = var.wireguard_port
    ip_range = "0.0.0.0/0"
  }

  inbound_rule {
    action   = "accept"
    protocol = "UDP"
    port     = var.wireguard_port
    ip_range = "::/0"
  }

  inbound_rule {
    action   = "accept"
    protocol = "TCP"
    port     = 443
    ip_range = "0.0.0.0/0"
  }

  inbound_rule {
    action   = "accept"
    protocol = "TCP"
    port     = 443
    ip_range = "::/0"
  }
}

resource "scaleway_instance_server" "gateway" {
  project_id        = var.project_id
  zone              = var.zone
  name              = var.instance_name
  type              = var.instance_type
  image             = var.image
  state             = "started"
  security_group_id = scaleway_instance_security_group.gateway.id
  ip_ids = [
    scaleway_instance_ip.ipv4.id,
    scaleway_instance_ip.ipv6.id,
  ]

  tags = [
    "sentinel-vpn",
    "france",
    "gateway",
    "preproduction",
  ]

  root_volume {
    size_in_gb = var.root_volume_gb
  }
}
