# Implementation Plan

> Keep this current as scope/sequencing shifts. See [CONTRIBUTING.md](../CONTRIBUTING.md).

## Status

| Phase | Title | State |
|-------|-------|-------|
| 0 | Repo & toolchain | **done** (boot + Flyway verified) |
| 1 | JTOpen connection mechanism + Test Connection | **done** (mechanism wired; live-green pending creds) |
| 2 | Persistence model & migrations | **done** (V1+V2, JPA entities/repos, SecretResolver, isolation gate) |
| 3 | Capture pipeline (format → split → store → **render**) | **done** (incl. PCL→PDF via GhostPDL sidecar, D-018) |
| 4 | Poller wiring (first end-to-end) | not started |
| 5 | Export destinations | not started |
| 6 | Admin REST API + audit | not started |
| 6.5 | Submission flow (HOD import + approval) | not started |
| 7 | React frontend | not started |

**v0.0.1 status (2026-06-27):** verified end-to-end in Docker — app boots, connects to
Postgres 16, Flyway applies all tables; `/api/health` + `/actuator/health` UP;
`POST /api/connection/test` exercises the real JTOpen signon path (returns
`CONNECTIVITY_ERROR` against an unreachable host). See docs/decisions.md D-015.
Live `OK` awaits operator host/creds/truststore.

**v0.0.1 (MVP cut)** = Phase 0 + Phase 1: prove the connection mechanism and ship a
one-click **Test Connection**. See "v0.0.1 reality" below.

## Guiding principle

The one unknown that can sink the project is **JTOpen actually connecting to a real
production IBM i host with correct SSL/truststore settings**. Front-load it. Validate on day
one with one company, not day thirty with twenty.

## v0.0.1 reality — gated on operator inputs

There is no practical IBM i emulator to point JTOpen at, and the operator does **not yet
have** a host/creds/truststore to share. So a live signon cannot go green until those
arrive. v0.0.1 therefore ships:

- the full connection mechanism (`As400Factory` with the non-negotiable flags,
  `TruststoreLoader`),
- a **Test Connection** action wired end-to-end (submission page → backend signon →
  real success/failure + error string),
- everything unit-testable around it green.

The moment creds exist, Test Connection either lights up or hands us the exact
SSL/truststore error to fix — which *is* the milestone-one validation, just gated on
inputs outside our control. This also answers Open Questions #1/#2 (DEVTYPE / spool
bypass) automatically against a real host.

## Phases

### Phase 0 — Repo & toolchain
Boot a Spring Boot app that runs Flyway against Postgres and serves health.
- `build.gradle.kts` + `settings.gradle.kts`, Gradle wrapper, Temurin 21 toolchain (vendor ADOPTIUM).
- Single-module Spring Boot 3.x (the module layout in the handoff is a *package* layout, not Gradle subprojects).
- Deps: web, data-jpa, validation, actuator, security; `net.sf.jt400:jt400`; flyway-core + flyway-database-postgresql; postgresql; pdfbox 3.x; aws sdk v2 s3; `org.apache.sshd:sshd-sftp`; `commons-net`; test: spring-boot-starter-test, Testcontainers (postgres, minio/localstack).
- `docker-compose.yml`: Postgres 16 + MinIO. `application.yml` + `application-local.yml`. `Dockerfile` (Temurin base, non-root). `.gitignore`/`.dockerignore`.
- **Exit:** `./gradlew bootRun` boots, Flyway baseline applies, `/actuator/health` UP.

### Phase 1 — JTOpen connection mechanism + Test Connection
- `As400Factory` (`GuiAvailable=false`, `MustUseSockets=true`, conditional SSL).
- `TruststoreLoader` (shared JKS/PKCS12 first; per-tenant later).
- Synchronous **Test Connection** endpoint returning real signon result + error string.
- Unit tests for factory config; live signon gated on creds.
- **Exit (v0.0.1):** mechanism shipped, Test Connection wired. Live green when creds arrive.

### Phase 2 — Persistence model & migrations
- Flyway `V1__init.sql` for the schema in [data-model.md](data-model.md).
- `library_list text[]` via array mapping; `config jsonb` via JSON mapping.
- Load-bearing `unique(tenant_id, sha256, logical_segment_index)`.
- `tenant_output_queue.retention_policy` nullable override over tenant default.
- `submission` table (pending → approved/rejected) per the two-surface model.
- `SecretResolver` interface, **env-var-backed impl** first (Vault deferred).
- **Exit:** Testcontainers Postgres, migrations apply, repository round-trips pass.
  **Met** (2026-07-02) — plus a tenant-isolation reflection gate: every declared query
  method on a repository whose entity carries `tenant_id` must scope by tenant.

### Phase 3 — Capture pipeline (scope expanded 2026-07-02: rendering IN, D-018)
- `FormatSniffer`, `PclSplitter` (ESC E guarded by leading `0x0C`/offset 0; trailing
  bare-reset merged), `ConverterDispatcher`, `TextToPdfConverter` (PDFBox).
- **PCL→PDF rendering** via `render-sidecar/` (GhostPDL `gpcl6` + HTTP shim,
  AGPL-as-external-service; supersedes the D-003 no-op stub) + `PclRenderClient`
  (never fails the capture; `render_status`/`render_error` on the row, V2 migration).
- `ObjectStore` (S3 client → MinIO; key scheme D-016) + `CaptureService` orchestration.
- `licenseGate` Gradle task asserting **no Ghostscript/Oracle/AGPL-linked** artifacts
  on the runtime classpath, wired into `check`.
- **Exit:** fixtures (PDF/PCL/text/concatenated-PCL/binary) produce expected captures
  incl. rendered `.pdf` siblings; dedup blocks re-insert. **Met** — unit +
  Testcontainers e2e (Postgres/MinIO/sidecar) green; see D-017 for how to run.

### Phase 4 — Poller wiring
- `SpoolWatermarkRepository`, `SpoolPoller` (the handoff `pollQueue` loop),
  `PollOrchestrator` (single-node always-leader seam), `@Scheduled` cadence.
- Retention switch defaulting to HOLD; one manually-inserted tenant + queue.
- **Exit:** poller captures real spool files to MinIO, watermark advances, restart idempotent. (Gated on creds.)

### Phase 5 — Export destinations
- `Exporter` interface + `ExportDispatcher` (per-tenant fan-out).
- `S3Exporter` (endpoint + pathStyleAccess), `SftpExporter` (MINA SSHD, pinned host key,
  **password auth**), `FtpsExporter` (Commons Net, implicit/explicit TLS, cert validation).
- `export_attempt` retry: exponential backoff, max 5, then FAILED + surfaced.
- **Exit:** capture flows to S3 then SFTP; forced failure retries then FAILED.

### Phase 6 — Admin REST API + audit
- Controllers: Tenant, ExportDestination, SpoolCapture, Health.
- `AuditLogger` → `audit_event`. OIDC resource-server `SecurityConfig`.
- Write-only secret handling at the boundary.
- Tenant-scoping enforced as a test gate.
- **Exit:** credentialed admin API scoped per tenant, audit recorded.

### Phase 6.5 — Submission flow
- `HodFileParser` (ACS workstation XML → draft; port + device name informational only).
- Public submission controller + `submission` table; collects SFTP + IBM i passwords.
- `POST /api/tenants/import-hod` returns a **draft** (never auto-creates).
- Approval promotes submission → tenant + export_destination + secrets.
- **Exit:** end-to-end submission → review → approval → active tenant.

### Phase 7 — React frontend
Vite + TS + TanStack Query + Tailwind. Submission page; admin tenants list; tenant detail
tabs; submission review/approval; captures list + download; export-destination editor
with conditional fields + test. OIDC; write-only credential UX.

## Cross-cutting (threaded, not a phase)
- Dependency/architecture tests = licensing + tenant-isolation CI gates.
- Structured logging with per-tenant MDC.
- CI: Gradle build + Testcontainers.

## Open questions
1. **DEVTYPE `*USERASCII`? / spool bypass?** — answered by Phase 1 against a real host. **Blocked on operator creds.**
2. **Retention per-tenant vs per-queue** — resolved: per-queue nullable override over tenant default.
3. **SFTP key vs password** — resolved: **password**, collected at submission, used by pipeline.
4. **Cross-tenant dedup** — resolved: **within-tenant only**; never cross company data.
