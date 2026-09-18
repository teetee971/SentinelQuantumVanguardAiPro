import { X509SvidVerifier } from "./x509-svid-verifier.js";
import { SpiffeIdentityPolicy } from "./spiffe-identity.js";
import { ScimReadClient } from "./scim-read-client.js";
import { OidcProvider } from "./oidc-provider.js";
import { OidcIdTokenVerifier } from "./oidc-id-token-verifier.js";

export const INTEGRATION_PROTOCOLS = Object.freeze({
  HUMAN_IDENTITY: ["OIDC", "SAML2", "SCIM2"],
  WORKLOAD_IDENTITY: ["SPIFFE", "SPIRE"],
  NETWORK: ["WIREGUARD"],
  AUTOMATION: ["WEBHOOK", "REST", "OIDC_WORKLOAD_FEDERATION"]
});

export const FOUNDATION_INTEGRATIONS = Object.freeze([
  { id: "generic-oidc", category: "identity", protocol: "OIDC", status: "foundation" },
  { id: "generic-saml2", category: "identity", protocol: "SAML2", status: "planned" },
  { id: "generic-scim2", category: "provisioning", protocol: "SCIM2", status: "foundation" },
  { id: "spiffe", category: "workload-identity", protocol: "SPIFFE", status: "foundation" },
  { id: "spire", category: "workload-identity", protocol: "SPIRE", status: "planned" },
  { id: "kubernetes", category: "orchestration", protocol: "SPIFFE", status: "planned" },
  { id: "aws", category: "cloud", protocol: "OIDC_WORKLOAD_FEDERATION", status: "planned" },
  { id: "azure", category: "cloud", protocol: "OIDC_WORKLOAD_FEDERATION", status: "planned" },
  { id: "gcp", category: "cloud", protocol: "OIDC_WORKLOAD_FEDERATION", status: "planned" },
  { id: "github-actions", category: "ci-cd", protocol: "OIDC_WORKLOAD_FEDERATION", status: "planned" },
  { id: "gitlab-ci", category: "ci-cd", protocol: "OIDC_WORKLOAD_FEDERATION", status: "planned" },
  { id: "jenkins", category: "ci-cd", protocol: "REST", status: "planned" },
  { id: "terraform", category: "infrastructure", protocol: "REST", status: "planned" },
  { id: "openwrt", category: "edge", protocol: "WIREGUARD", status: "planned" },
  { id: "home-assistant", category: "home", protocol: "WIREGUARD", status: "planned" },
  { id: "mqtt", category: "iot", protocol: "WIREGUARD", status: "planned" },
  { id: "ssh", category: "pam", protocol: "WIREGUARD", status: "planned" },
  { id: "rdp", category: "pam", protocol: "WIREGUARD", status: "planned" },
  { id: "proxmox", category: "virtualization", protocol: "WIREGUARD", status: "planned" },
  { id: "vmware", category: "virtualization", protocol: "WIREGUARD", status: "planned" }
]);

export function validateIntegrationManifest(manifest) {
  if (!manifest || typeof manifest !== "object") throw new TypeError("manifest required");
  const id = String(manifest.id || "").trim();
  if (!/^[a-z0-9][a-z0-9-]{1,63}$/.test(id)) throw new Error("invalid integration id");
  const protocols = Object.values(INTEGRATION_PROTOCOLS).flat();
  if (!protocols.includes(manifest.protocol)) throw new Error("unsupported protocol");
  if (!["foundation","planned","validated"].includes(manifest.status)) throw new Error("invalid status");
  return true;
}


export function createOidcProvider(config) {
  return new OidcProvider(config);
}


export function createOidcIdTokenVerifier(config) {
  return new OidcIdTokenVerifier(config);
}


export function createScimReadClient(config) {
  return new ScimReadClient(config);
}


export function createSpiffeIdentityPolicy(config) {
  return new SpiffeIdentityPolicy(config);
}


export function createX509SvidVerifier(config) {
  return new X509SvidVerifier(config);
}
