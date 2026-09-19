# Sentinel VPN France — Scaleway IaC

This Terraform configuration prepares the infrastructure shell for the first Sentinel VPN France gateway in Scaleway Paris.

It intentionally does **not** run `terraform apply`, configure production secrets, mark a gateway `AVAILABLE`, or publish a catalog entry.

## What it creates

- one Instance in region `fr-par`;
- one routed public IPv4;
- one routed public IPv6 allocation;
- one stateful security group;
- SSH restricted to a caller-supplied administrative CIDR;
- WireGuard UDP exposed on the configured port;
- HTTPS exposed on TCP 443 for the provisioning endpoint.

The Scaleway Terraform provider currently exposes Instance IP resources with `routed_ipv4` and `routed_ipv6` types. The routed IPv6 allocation supplies a prefix that can be used as the source for Sentinel's client `/128` leases after routing has been independently verified.

## Credentials

Do not put Scaleway credentials in Terraform files or `*.tfvars` committed to Git.

Use the provider's supported environment/configuration mechanism outside the repository and provide only the project ID as an input.

## Plan only

A safe review flow is:

```bash
terraform init
terraform fmt -check
terraform validate
terraform plan \
  -var='project_id=<PROJECT_ID>' \
  -var='admin_ssh_cidr=<TRUSTED_IPV4>/32'
```

Do not run `terraform apply` until the expected recurring cost, region, instance type, IP allocations and administrative access path have been reviewed.

## After provisioning

Infrastructure creation is only the first gate. The server must still run the Sentinel gateway bootstrap and pass all production acceptance evidence before the signed catalog can advertise `fr-par-01` as `AVAILABLE`.

Required post-provisioning work includes:

1. install and start the Sentinel WireGuard gateway;
2. bind the routed IPv6 prefix to the WireGuard client-address plan;
3. deploy the provisioning HTTPS service;
4. configure durable lease-state secret storage;
5. configure monitoring and rate limiting;
6. run provisioning/revocation, IPv4/IPv6 egress, DNS-path, geolocation, reconnect and MTU tests;
7. pass the Sentinel gateway acceptance gate.
