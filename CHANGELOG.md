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

## [0.2.0] — 2026-07-12

### Added
- **Public submission intake** (D-024), the low-trust half of the two-surface model
  (D-007) that D-021 left planned:
  - `WsHodParser` — imports an IBM Personal Communications `.ws` (INI) or Host On-Demand
    session file (applet `<PARAM>`s or flat properties) into a normalized draft: host,
    port, TLS posture, user, LU/device, CCSID, session type. Tolerant by design — a
    partial or unrecognized file yields a draft with warnings, not an error.
  - Two anonymous, CSRF-exempt routes on the low-trust surface (same posture as Test
    Connection): `POST /api/submissions/parse` (upload → draft, nothing persisted) and
    `POST /api/submissions` (create a **pending** submission). Path-exact security so the
    admin approve/reject routes stay authenticated. Neither creates a tenant.
  - A **standalone** `/submit` React page: upload → review/edit → Test Connection →
    submit → confirmation, served outside the admin identity gate.
- **Encrypted secret store** (D-023): a `secret` table (V3 migration) holding AES-256-GCM
  envelopes, giving the submission surface a *write* path for submitter-entered passwords
  that the env-var resolver (D-012) lacked. `CompositeSecretResolver` routes `db:<uuid>`
  refs to the encrypted store and env-var refs to the existing resolver behind one
  `SecretResolver`. Key from `gateway.secrets.encryption-key` (base64 32-byte key or a
  passphrase); blank leaves the store disabled and the app still boots.
- **Approval now provisions destinations**: an approved submission that carried an SFTP
  destination creates the `export_destination` row with its write-only secret ref, and
  carries `port`/`ccsid` onto the tenant — closing the destination/secret-promotion item
  D-021 left open.
- Focused tests: `.ws`/HOD parsing across PComm and HOD shapes; GCM round-trip / tamper /
  wrong-key / passphrase-derivation; composite resolver routing; DB store write→resolve;
  intake draft-JSON + write-only secret handling; anonymous + CSRF-exempt intake routes;
  SFTP-destination promotion on approval.

### Changed
- Version advanced to `0.2.0`; quickstart compose image tag and render-sidecar git ref
  bumped to `v0.2.0`.
- `SubmissionApprovalService` gained an `ExportDestinationRepository` dependency to
  provision destinations on approval.

### Not yet included
- Scheduled IBM i queue polling (Phase 4) and S3/SFTP/FTPS export execution (Phase 5)
  remain planned and gated on live host credentials (D-011). 1.0.0 awaits that first real
  IBM i validation.

## [0.1.0] — 2026-07-12

### Added
- **Authenticated React admin console** (D-021): Vite + React 18 + TypeScript +
  TanStack Query + Tailwind, packaged into the Spring Boot application. The console
  includes a dashboard, submission review/approve/reject, tenant list and detail tabs,
  tenant-scoped capture browsing/downloads, and an ephemeral Test Connection form.
- **Admin REST API**: current-operator identity and headline stats; submission list,
  detail, approval, and rejection; tenant list/detail with queues, destinations,
  recent captures, and audit history; tenant-scoped original/rendered artifact download.
- **Audited approval service**: promotes an existing pending submission into a tenant,
  carries its IBM i secret reference, records the reviewer, and writes an audit event.
  A database row lock serializes competing approval/rejection decisions.
- **Authentik forward-auth integration** (D-022): stateless pre-authentication from
  Authentik identity headers, JSON `401` responses for unauthenticated API calls, and an
  explicit local-only `retrospool.admin.dev-user` escape hatch. Cookie-backed CSRF
  tokens protect admin mutations even though the application itself has no session.
- Focused tests for forwarded-header/dev authentication, submission approval/rejection,
  and tenant-scoped capture downloads.

### Changed
- Version advanced to `0.1.0`; container and release builds compile the frontend and
  package its static assets into the runnable application.
- The admin console and admin APIs now require authentication. Health endpoints remain
  open for direct container and Kubernetes probes.

### Not yet included
- Public HOD `.ws` import and submission creation, scheduled IBM i queue polling, and
  S3/SFTP/FTPS export fan-out remain planned. The v0.1.0 console can review submissions
  already present in the database, but it does not yet provide the low-trust intake flow.

## [0.0.3] — 2026-07-08

### Added
- **Container image on GHCR** (D-020): the release workflow now publishes
  `ghcr.io/spillers-technology/retrospool` on every tag, after unit tests and the
  `licenseGate` pass. App image only — the GhostPDL render sidecar is deliberately
  never published as a binary; end users build it from the tag's source.
- **Quickstart** ([QUICKSTART.md](QUICKSTART.md) + `quickstart/docker-compose.yml`):
  the whole stack from one downloaded file and one `docker compose up` — app from
  GHCR, internal Postgres/MinIO (no host-port collisions), render sidecar built
  from the pinned tag's git context. Jar path documented with Temurin install
  guidance and checksum verification.
- **Release assets**: each release ships `retrospool-x.y.z.zip` (runnable jar,
  compose file, render-sidecar source, docs) plus a `.sha256` checksum, built and
  attached by CI (also backfilled onto v0.0.2).

### Changed
- README leads with a producty "Get started" path; the containerized build
  instructions moved under "Build it yourself".

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
