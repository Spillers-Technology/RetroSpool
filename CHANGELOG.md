# Changelog

All notable changes to retrospool are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions follow
[SemVer](https://semver.org/) (pre-1.0: expect the ground to move).

> **A note on history:** this repository's git history was restarted when the
> project went public (see [docs/decisions.md](docs/decisions.md) D-019). The
> project's full evolution is preserved deliberately in two public artifacts:
> this changelog and the append-only decision log
> ([docs/decisions.md](docs/decisions.md), D-001 onward), which records every
> architectural decision — including the ones that were later superseded and why.

## [0.0.2] — 2026-07-02

### Added
- **Persistence layer** (Phase 2): JPA entities and tenant-scoped repositories over
  the V1 schema — `text[]` array and `jsonb` mappings, composite-key watermarks —
  plus `SecretResolver` with an env-var-backed implementation (write-only secrets,
  D-012).
- **Capture pipeline** (Phase 3): `FormatSniffer` (PDF/PCL/TEXT/UNKNOWN from the
  first 16 bytes), `PclSplitter` (ESC E boundaries guarded by form feed, trailing
  bare-reset merge), `TextToPdfConverter` (PDFBox, Courier, auto landscape),
  `CaptureService` orchestration with constraint-backed within-tenant dedup (D-008).
- **PCL→PDF rendering** (D-018, supersedes D-003): GhostPDL `gpcl6` built from a
  pinned release into a dedicated `render-sidecar/` container behind a stdlib-Python
  HTTP shim — AGPL stays off the JVM classpath, the MinIO precedent. Both artifacts
  kept per capture (`.pcl` original + rendered `.pdf`); a render failure never fails
  the capture. V2 migration adds `rendered_storage_key` / `render_status` /
  `render_error`.
- **Object storage**: `S3ObjectStore` (AWS SDK v2; MinIO-compatible endpoint +
  path-style), landing key scheme `{tenantId}/{yyyy}/{MM}/{dd}/{captureId}-{seg}.{ext}`
  (D-016).
- **CI gates**: `licenseGate` Gradle task (no Ghostscript/AGPL/Oracle artifacts on
  the runtime classpath, D-002) and a tenant-isolation reflection gate (every query
  on a tenant-scoped entity must take `tenantId`).
- **Integration test suite** (Testcontainers, `@Tag("integration")`, D-017): Flyway
  V1+V2 on real Postgres 16, entity round-trips, dedup constraint, MinIO round-trip,
  and a full end-to-end capture through the real gpcl6 sidecar.
- Portfolio README.

### Changed
- **Renamed to retrospool** (D-019): package `io.retrospool`, Gradle group/artifact,
  main class; all former employer/vendor branding and business-identifying language
  genericized across code, docs, fixtures, and test data.
- Testcontainers pinned to 1.21.3 with a versioned Docker API path — Docker
  Engine 29 rejects the legacy negotiation older docker-java performs (D-017).

## [0.0.1] — 2026-06-27

### Added
- Spring Boot 3 / Temurin 21 skeleton with a Docker-only toolchain — the dev machine
  needs no JDK (Phase 0, D-014).
- Full Postgres schema in Flyway `V1__init.sql`: tenants, output queues, submissions,
  watermarks, captures with the load-bearing
  `unique(tenant_id, sha256, logical_segment_index)`, export destinations/attempts,
  audit events.
- **JTOpen connection mechanism** (Phase 1): `As400Factory` (`SecureAS400` for TLS —
  D-013 corrected the handoff's nonexistent `setUseSSL` — `GuiAvailable=false`,
  `MustUseSockets=true`), `TruststoreLoader`, and a synchronous **Test Connection**
  endpoint returning the real signon outcome. Live-green gated on operator
  credentials by design (D-011): no practical IBM i emulator exists, so the first
  real signon *is* the milestone validation.
- Health endpoints; docs corpus (architecture, data model, phased implementation
  plan) and the append-only decision log (D-001…D-015).
