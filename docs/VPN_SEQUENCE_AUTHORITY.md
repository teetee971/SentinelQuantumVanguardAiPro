# VPN lease sequence authority

`PostgresVpnLeaseSequenceAuthority` supplies the durable adapter for the existing fail-closed lease persistence/restore coordinators. The base class is an interface boundary and deliberately refuses operation without an implementation.

The injected `execute({text, values})` must run parameterized statements against an independently provisioned PostgreSQL database and return PostgreSQL rows. The adapter opens no connection, reads no credential and executes no host command. Apply `ops/vpn-gateway/lease-sequence-schema.sql` using the database migration identity. The migration is transactional; the UPDATE trigger rejects a decreasing sequence, gateway renaming, substitution of the snapshot digest at the same sequence, and revalidation of an exact snapshot that has been invalidated.

A missing gateway has no trustworthy floor: reads fail closed. A first successful commit establishes its positive sequence. Atomic upsert rejects decreasing commits, accepts an idempotent equal commit only for the same non-invalidated snapshot SHA-256 digest, and preserves the greatest sequence across concurrent writers. Results must confirm the exact requested sequence; malformed results and unavailable storage are rejected without exposing database details.

Trusted restore rejects snapshots both below and above the confirmed sequence. It rereads the authority before changing the provisioning core, then verifies the exact snapshot digest against the committed database record. An `invalidated=true` record is never accepted by restore even if sequence and digest still match. In particular, writing a signed snapshot and then failing to commit its sequence cannot authorize that newer snapshot on restart, and a runtime revocation whose durable rewrite fails can invalidate the formerly committed active snapshot so it cannot resurrect after restart. Recovery from an invalidated record requires a strictly newer committed sequence; the same sequence cannot be revalidated.

`ops/vpn-gateway/provisioning-server.js` must never persist lease state directly. When persistence is enabled, `VpnLeaseStateStore` and `VpnLeaseSequenceAuthority` are a required pair, and writes flow through `persistVpnLeaseState`. If a peer has been removed or a lease revoked but the replacement state cannot be durably committed, the server either proves the local snapshot can no longer satisfy exact authority verification or invalidates the exact stale authority record before returning the failure.

## Deployment boundary

The adapter and real PostgreSQL integration tests are implemented. No production database, executor, service identity or gateway is installed by these files. `configured`, `deployed`, `runtime_verified` and `customer_available` remain false in the product contract.

The database must be independent of the gateway snapshot volume and its rollback/backup domain. Runtime credentials must not own the table, mutate the schema, disable triggers, or delete/truncate sequence records. They require only the narrowly scoped SELECT/INSERT/UPDATE operations needed by the adapter, including setting `invalidated=true`; they must not be able to set an invalidated record back to false. Trusted operator recovery must preserve the greatest committed floor and invalidation state. PostgreSQL administrators can bypass these protections; custody and deployment evidence remain necessary.

The sequence and snapshot digest prevent reuse of a committed revision with different state. They are not an ownership fence. Runtime wiring must enforce one exclusive writer per gateway and prohibit multiple local state-store instances from independently allocating the same sequence. Equal sequence acknowledgments require the same digest; a conflicting writer fails closed. This adapter alone does not establish multi-writer snapshot consistency, host integrity or production VPN availability.

## Verification

`npm run test:vpn-gateway` exercises input validation, unavailable storage, paired persistence configuration, provisioning/revocation persistence and restore rejection. `npm run test:security-execution` additionally requires a disposable PostgreSQL test database via `DATABASE_URL`; its CI PostgreSQL job runs the real upsert, concurrent commits, trigger rejection, exact snapshot invalidation, revalidation rejection, adapter restart and signed-snapshot substitution tests. Local test database owners delete only the generated VPN test identities.
