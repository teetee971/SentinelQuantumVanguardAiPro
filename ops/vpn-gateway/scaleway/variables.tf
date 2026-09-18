variable "project_id" {
  type        = string
  description = "Scaleway project ID. Supply externally; never commit credentials or project secrets."

  validation {
    condition     = length(trimspace(var.project_id)) > 0
    error_message = "project_id must not be empty."
  }
}

variable "region" {
  type        = string
  description = "Scaleway region for the France gateway."
  default     = "fr-par"

  validation {
    condition     = var.region == "fr-par"
    error_message = "The first Sentinel France gateway must stay in the fr-par region."
  }
}

variable "zone" {
  type        = string
  description = "Scaleway Paris availability zone."
  default     = "fr-par-1"

  validation {
    condition     = contains(["fr-par-1", "fr-par-2", "fr-par-3"], var.zone)
    error_message = "zone must be a Scaleway Paris zone."
  }
}

variable "instance_name" {
  type        = string
  description = "Gateway instance name."
  default     = "sentinel-vpn-fr-par-01"
}

variable "instance_type" {
  type        = string
  description = "Scaleway Instance commercial type. Confirm current availability before apply."
  default     = "DEV1-S"
}

variable "image" {
  type        = string
  description = "Scaleway OS image."
  default     = "ubuntu_jammy"
}

variable "root_volume_gb" {
  type        = number
  description = "Root volume size."
  default     = 20

  validation {
    condition     = var.root_volume_gb >= 10 && var.root_volume_gb <= 100
    error_message = "root_volume_gb must be between 10 and 100."
  }
}

variable "admin_ssh_cidr" {
  type        = string
  description = "Single trusted IPv4 CIDR allowed to reach SSH, for example x.x.x.x/32."

  validation {
    condition     = can(cidrnetmask(var.admin_ssh_cidr)) && !can(regex("(^0\\.0\\.0\\.0/0$)", var.admin_ssh_cidr))
    error_message = "admin_ssh_cidr must be a specific IPv4 CIDR and must not be 0.0.0.0/0."
  }
}

variable "ssh_port" {
  type        = number
  description = "Administrative SSH port."
  default     = 22

  validation {
    condition     = var.ssh_port >= 1 && var.ssh_port <= 65535
    error_message = "ssh_port must be in 1..65535."
  }
}

variable "wireguard_port" {
  type        = number
  description = "WireGuard UDP listen port."
  default     = 51820

  validation {
    condition     = var.wireguard_port >= 1 && var.wireguard_port <= 65535
    error_message = "wireguard_port must be in 1..65535."
  }
}
