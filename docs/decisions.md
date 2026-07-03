# Decision Log

> Append-only. To change a decision, add a new entry that **supersedes** the old one
> (note it in both). Don't rewrite history. See [CONTRIBUTING.md](../CONTRIBUTING.md).

Format: `### D-NNN — Title` · **Status** · **Date** · context / decision / why.

---

### D-001 — No printer-session / TN5250E emulation
**Accepted** · 2026-06-27
The host queues spool files in `*READY` regardless of whether a device/session is online
(confirmed in operations). JTOpen reading `OutputQueue` directly does the same job with
no Telnet negotiation, device-name juggling, port 992, or live SCS decoding.

### D-002 — No Oracle JDK / GraalVM Enterprise / AGPL-linked libraries
**Accepted** · 2026-06-27
Production licenses limited to Apache-2.0, MIT, BSD, EPL, MPL, GPL-with-Classpath-Exception,
plus IBM Public License (JTOpen) and PostgreSQL License. JDK = **Eclipse Temurin 21 LTS**.
MinIO is used only as an external service over the S3 API (AGPL, never linked).
Enforced as a dependency/CI test gate, not just prose.

### D-003 — No PCL rendering in this service
**Superseded by D-018** · 2026-06-27
No Ghostscript / GhostPDL / PCL library. PCL is detected, split on logical boundaries, and
stored as `.pcl`. `PclConverter` is a deliberate **no-op stub** marking the seam.
PCL→PDF is handled downstream by the operator.
*(2026-07-02: operator brought rendering in scope — "pull down spooled PDFs" is the
product. See D-018 for how it's done without violating D-002.)*

### D-004 — S3 and (S)FTP export destinations, per tenant, multiple allowed
**Accepted** · 2026-06-27
Each capture is queued to every enabled destination on its tenant. Retry with exponential
backoff in `export_attempt`, max 5 attempts then FAILED + surfaced in UI.

### D-005 — Stack
**Accepted** · 2026-06-27
Spring Boot 3.x + JTOpen (jt400) backend; React 18 + Vite + TypeScript + TanStack Query +
Tailwind frontend; PostgreSQL 16; PDFBox 3.x for text→PDF; AWS SDK v2 for S3; Apache MINA
SSHD for SFTP (preferred over JSch); Apache Commons Net for FTPS.

### D-006 — Build tool: Gradle (Kotlin DSL)
**Accepted** · 2026-06-27
`build.gradle.kts`. Single-module Spring Boot app (the handoff "module layout" is a
package layout, not Gradle subprojects).

### D-007 — Two-surface model: submission (landing) vs admin (credentialed)
**Accepted** · 2026-06-27
The HOD importer is a **standalone, low-trust submission page** (no credentialed access):
upload `.ws` → draft → add SFTP + IBM i passwords → Test Connection → submit. Submission
creates a **pending `submission`**, not a tenant. The **credentialed (OIDC) admin** reviews
and **approves**, promoting to an active tenant. Nothing auto-activates — approval is the
mandatory human-review gate.
*Why:* internal companies with isolated data; prod team needs one place to manage many
companies while keeping submission intake separate from privileged management.

### D-008 — Tenant isolation is mandatory and within-tenant dedup only
**Accepted** · 2026-06-27 · supersedes the implicit "within-tenant" note in the handoff
Each queue holds a different company's data. Every read/write is scoped by `tenant_id`;
no cross-company reads. Capture dedup is **within-tenant only** via
`unique(tenant_id, sha256, logical_segment_index)` — identical bytes for two companies are
two distinct captures. Audit and secrets are per-tenant. Enforced as a test/CI gate.
*Why:* "don't cross the streams" — company data must not commingle.

### D-009 — Retention resolved per output queue with tenant fallback
**Accepted** · 2026-06-27
`tenant_output_queue.retention_policy` is a nullable override of `tenant.retention_policy`.
Default `HOLD` until the pipeline is trusted. Gives per-company/per-queue control while
keeping a sane tenant default.

### D-010 — SFTP uses password auth, collected at submission
**Accepted** · 2026-06-27
SFTP destinations authenticate with a **password** entered by the submitter during HOD
submission, associated at the same time, and carried through approval into the ingestion
pipeline as a write-only `secret_ref`. (Host key fingerprint still pinned; no auto-accept.)

### D-011 — v0.0.1 = connection mechanism + Test Connection, live-green gated on creds
**Accepted** · 2026-06-27
No practical IBM i emulator exists and the operator has no host/creds/truststore yet. v0.0.1
ships the full connection mechanism and a one-click Test Connection wired end-to-end; the
live signon goes green (or yields the exact SSL/truststore error) the moment creds arrive —
which is the milestone-one validation, gated on inputs outside our control.

### D-012 — Secrets are write-only at the boundary
**Accepted** · 2026-06-27
The UI shows `•••• (set)` with a Replace action and never reads secret values back.
`SecretResolver` is env-var-backed first; Vault (MPL) deferred as an additive impl.

### D-013 — SSL signon uses SecureAS400, not AS400.setUseSSL
**Accepted** · 2026-06-27 · corrects the handoff snippet
The handoff pseudo-code called `system.setUseSSL(true)`. That method does **not exist** in
jt400 21.0.6 (verified via `javap`). SSL is requested by instantiating the `SecureAS400`
subclass, which negotiates TLS through the standard JSSE truststore. `As400Factory` therefore
returns `SecureAS400` when `useSsl` is true and plain `AS400` otherwise; both still get
`GuiAvailable=false` and `MustUseSockets=true`. The shared truststore is applied by
`TruststoreLoader` via `javax.net.ssl.*` system properties.

### D-014 — Build/test/run via Docker (no host JDK)
**Accepted** · 2026-06-27
The dev workstation has no JDK and the target is Linux anyway, so the build runs in the
`gradle:8.10.2-jdk21` (Temurin) image and the app runs on `eclipse-temurin:21-jre`.
Pinned versions: Spring Boot 3.4.1, jt400 21.0.6, AWS SDK v2 BOM 2.29.45, PDFBox 3.0.3,
Apache MINA SSHD 2.14.0, Commons Net 3.11.1. See CONTRIBUTING.md "Build & run".

### D-015 — v0.0.1 verified end-to-end (mechanism, host-green still pending creds)
**Accepted** · 2026-06-27
Smoke test passed: app boots, connects to Postgres 16, Flyway applies all tables;
`/api/health` + `/actuator/health` UP; `POST /api/connection/test` against an unreachable
host returns `CONNECTIVITY_ERROR` (the JTOpen signon path executes). A real `OK` still awaits
operator host/creds/truststore (per D-011), which is the only thing gating live validation.

### D-016 — Landing object-store key scheme
**Accepted** · 2026-07-02
`{tenantId}/{yyyy}/{MM}/{dd}/{captureId}-{segmentIndex}.{ext}` (UTC). Tenant-first prefix
keeps isolation visible at the storage layer; date partitions match the export key scheme's
shape. The rendered PDF sibling (D-018) lives at `{originalKey}.pdf`.

### D-017 — Integration tests are tagged; Docker socket mounted for the containerized build
**Accepted** · 2026-07-02
Testcontainers-backed tests carry JUnit `@Tag("integration")` and run via the separate
Gradle `integrationTest` task, so plain `gradle build` stays green without a Docker socket
(the D-014 build runs inside a container). The verified invocation (Docker Desktop, engine 29):
```bash
docker run --rm \
  -e DOCKER_HOST=unix:///var/run/docker.sock \
  -e TESTCONTAINERS_RYUK_DISABLED=true \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "$PWD:/app" -w /app -v spool-gradle-cache:/home/gradle/.gradle \
  -v /var/run/docker.sock:/var/run/docker.sock \
  gradle:8.10.2-jdk21 gradle integrationTest --no-daemon --console=plain
```
Gotchas encoded in the build, learned the hard way:
- **Docker Engine 29+ rejects the legacy API requests** from the docker-java that
  Spring Boot 3.4.1's managed Testcontainers (1.20.4) ships. Fixed two ways in
  `build.gradle.kts`: `extra["testcontainers.version"] = "1.21.3"` (a `platform()` BOM
  does NOT win over Spring's dependency management) and
  `systemProperty("api.version", "1.44")` on the `integrationTest` task.
- Ryuk can't be reached from inside the build container → disabled for these runs;
  Testcontainers' JVM shutdown hook still stops the containers.
- `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` is required for the test JVM to
  reach published container ports from inside the build container.
Testcontainers modules are MIT; the Postgres/MinIO images they run are external services
(consistent with D-002).

### D-018 — PCL→PDF rendering via a GhostPDL sidecar (supersedes D-003)
**Accepted** · 2026-07-02 · supersedes D-003
Rendering is in scope: the product is "ACS print session as a web service — spooled reports
show up as PDFs you can pull down." The only serious open-source PCL renderer is GhostPDL
(`gpcl6`), which is AGPL — banned from *linking* by D-002. Resolution mirrors the MinIO
precedent exactly: `render-sidecar/` builds unmodified upstream `gpcl6` from a pinned
release tarball behind a tiny stdlib-Python HTTP shim, deployed as a **separate container**
called over HTTP. GhostPDL never appears on the JVM classpath (enforced by the `licenseGate`
build task).
- Both artifacts kept per PCL capture: original `.pcl` (source of truth, re-renderable) and
  rendered `.pdf` at `{originalKey}.pdf` (`capture.rendered_storage_key`, V2 migration).
- A render failure **never** fails the capture: the `.pcl` lands with
  `render_status = FAILED` + `render_error`, retryable later (re-render endpoint deferred to
  the Phase 6 admin API). Dedup stays on original bytes only.

### D-019 — Renamed to Retrospool; employer/vendor branding purged for public release
**Accepted** · 2026-07-02
The project is going public on GitHub (with a Pages site) as a portfolio piece. Renamed
to **retrospool**: Java package `io.retrospool`, Gradle group `io.retrospool`, root
project/artifact `retrospool`, main class `RetrospoolApplication`. All former
employer/vendor branding and business-identifying terminology removed from
code, docs, fixtures, and test data — the domain is described generically (IBM i host,
company, business reports). Note: the V1 migration's header comment changed, so existing
local dev databases need `docker compose down -v` (Flyway checksum); Testcontainers DBs
are unaffected.
Git history was **restarted** for the public debut (pre-public commits contained the old
branding throughout). The evolution is preserved on purpose in two public artifacts:
[CHANGELOG.md](../CHANGELOG.md) and this decision log — append-only, including superseded
decisions.
