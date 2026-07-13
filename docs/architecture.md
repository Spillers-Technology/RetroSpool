# Architecture

> Keep this current. See [CONTRIBUTING.md](../CONTRIBUTING.md) for the doc-maintenance rule.

## Problem and approach

Today, business reports are written to named IBM i **output queues**. A Windows laptop
running IBM ACS with HOD-configured printer sessions "claims" each device and pulls
spool files as they appear. Crucially, **the host queues spool files in `*READY`
regardless of whether any device is online** — confirmed in operations. The printer
session is therefore not required to capture the data.

This service replaces that pattern by **polling output queues directly via JTOpen**.
No TN5250E negotiation, no device-name juggling, no port 992, no live SCS stream.
One HOD `.ws` file == one tenant (one company).

## Output format reality

Spool files are typically `DEVTYPE(*USERASCII)` opaque byte streams:

- Usually **PCL** intended for block printers, sometimes multiple reports concatenated.
- Increasingly **PDF** emitted directly.
- Occasionally plain **text**.

This service **detects** format, **splits** concatenated PCL on logical boundaries,
**stores** with the correct extension, and **renders PCL to PDF** via the GhostPDL
sidecar (D-018, superseding D-003) so every report is pullable as a PDF. The original
`.pcl` is always kept alongside the rendered `.pdf`.

## Two-surface model (submission vs admin)

The companies are internal customers; each queue holds a different company's data, so
isolation is intentional. The system exposes two distinct surfaces:

### 1. Submission surface (landing / low-trust)
A standalone page at `/submit`. No credentialed access required. **Delivered in v0.2.0**
(D-024); switched in by path before the admin identity gate runs.

- Upload a PComm `.ws` or HOD session file → `WsHodParser` produces a **draft** (host,
  port, SSL posture, LU/device name [informational], user id, session name, CCSID,
  session type) with warnings rather than errors.
- The submitter reviews/edits it, adds the **IBM i password** and an optional **SFTP
  destination + password** (never present in a session file), and can run **Test
  Connection** (synchronous JTOpen signon, ephemeral).
- Submitting creates a **pending `submission`** via `POST /api/submissions` — it does
  **not** create a tenant. `POST /api/submissions/parse` and `POST /api/submissions` are
  anonymous and CSRF-exempt, matched path-exact so admin approve/reject stay gated.
- Submitter passwords are persisted **write-only** through the encrypted secret store
  (D-023), referenced from the submission and never echoed back.

### 2. Admin surface (credentialed / OIDC)
The prod team's workspace. **Delivered in v0.1.0** as a React SPA backed by
authenticated Spring REST endpoints.

- Reviews existing pending submissions and **approves** → promotion to an active
  **Tenant**. Nothing auto-activates; approval is the mandatory human-review gate.
  A pessimistic lock on the submission row serializes competing decisions.
  Destination-row creation and SFTP-secret promotion arrive with the public submission
  flow; v0.1.0 carries the IBM i secret reference onto the tenant.
- One page manages **many companies / many queues**, but every view (captures, audit,
  configured destinations) is **scoped and auditable per company**. Convenience in the
  UI; hard isolation in the data.

### Lifecycle

```
WS/HOD upload ─▶ parsed draft ─▶ pending submission ─▶ admin approval ─▶ active tenant ─▶ polling
   [v0.2.0 public intake]          [v0.2.0]              [v0.1.0]          + SFTP dest    [planned]
```

Both submission credentials are carried as **write-only** `secret_ref` values (D-012,
D-023). Approval promotes the draft's connection fields (including `port`/`ccsid`) and the
IBM i secret onto the tenant, and — new in v0.2.0 — creates the `export_destination` row
with its SFTP secret when the submission carried one.

## Admin authentication boundary (D-022)

Production OIDC terminates at an **Authentik forward-auth outpost**, not inside the
Spring application. After Authentik authenticates and authorizes a request, the outpost
injects `X-authentik-username`, `X-authentik-email`, `X-authentik-name`, and
`X-authentik-groups`. Retrospool turns that asserted identity into its stateless admin
principal. It does not issue a second redirect or maintain an application session.

The app permits health probes and the low-trust `POST /api/connection/test` route
anonymously; every admin API requires authentication. Static SPA files are permitted by
the app because the ingress/outpost is the outer gate. Consequently, the deployment
boundary is load-bearing: clients must not be able to reach the service directly, and
the proxy must remove client-supplied identity headers before writing trusted values.
Authentik policy decides who may enter; the application treats every admitted forwarded
identity as an admin.

The application is stateless, but the upstream Authentik flow may still use a browser
session cookie. Spring therefore issues a readable `XSRF-TOKEN` cookie on safe admin
requests; the SPA echoes it as `X-XSRF-TOKEN` on mutations. Only the anonymous
`POST /api/connection/test` contract is excluded from CSRF enforcement.

For local development only, `retrospool.admin.dev-user` supplies a synthetic identity
when Authentik headers are absent. Production must leave it blank.

## Tenant isolation (cross-cutting, mandatory)

- Every query scoped by `tenant_id`; no cross-company reads anywhere.
- Capture **dedup is within-tenant only** via `unique(tenant_id, sha256, logical_segment_index)`.
  Identical bytes for two different companies are two distinct captures — never merged.
- Audit is per-company-auditable.
- Secrets are per-tenant.
- Enforced as a test/CI gate, not just convention.

## Capture pipeline

```
SpooledFile bytes
  ─▶ FormatSniffer            (PDF / PCL / TEXT / UNKNOWN from first 16 bytes)
  ─▶ PclSplitter              (split concatenated PCL on ESC E boundaries; guarded)
  ─▶ ConverterDispatcher      (TEXT→PDF via PDFBox; PDF/PCL/UNKNOWN passthrough)
  ─▶ ObjectStore (MinIO/S3)   (landing zone; key scheme per D-016)
  ─▶ PclRenderClient          (PCL only: → GhostPDL sidecar → .pdf sibling; D-018)
  ─▶ CaptureRepository        (metadata + dedup + render status)
  ─▶ ExportDispatcher         (planned: fan-out to enabled tenant destinations)
```

### Format sniffing (first 16 bytes, after stripping leading nulls)

| Signature            | Format |
|----------------------|--------|
| `%PDF-`              | PDF    |
| `0x1B 0x45` (ESC E)  | PCL    |
| `0x1B 0x25` (ESC %)  | PCL XL / PJL |
| `0x1B 0x26` (ESC &)  | PCL    |
| All printable ASCII + common whitespace | TEXT |
| otherwise            | UNKNOWN (`.bin`) |

### PCL splitting
Split on `ESC E` (`0x1B 0x45`) **only when preceded by form feed `0x0C` or at offset 0**,
to avoid false positives inside binary sections. Each segment becomes its own capture
with an incrementing `logical_segment_index`.

### Converter dispatcher + PCL rendering (D-018)

| Format  | Primary artifact    | Extension | Rendered sibling |
|---------|---------------------|-----------|------------------|
| PDF     | passthrough         | `pdf`     | — (`SKIPPED`)    |
| TEXT    | rendered via PDFBox | `pdf`     | — (`SKIPPED`)    |
| PCL     | **store as-is**     | `pcl`     | `.pdf` via GhostPDL sidecar |
| UNKNOWN | passthrough         | `bin`     | — (`SKIPPED`)    |

**PCL rendering** (supersedes D-003's no-op stub): `PclRenderClient` POSTs the
original segment bytes to the **`render-sidecar/`** container — unmodified AGPL
`gpcl6` (built from a pinned GhostPDL release) behind a single-file Python HTTP shim.
GhostPDL never touches the JVM classpath (the MinIO precedent; enforced by the
`licenseGate` Gradle task). The rendered PDF is stored at `{originalKey}.pdf` and
recorded in `capture.rendered_storage_key`. A render failure **never fails the
capture** — the `.pcl` lands with `render_status = FAILED` + `render_error`,
retryable later. Dedup/sha256 are always over the original bytes.

Text→PDF: PDFBox, Courier (standard-14), US Letter, landscape if max line
length > 80 chars else portrait, hard-wrap at 132 columns with a `+ ` continuation
marker.

## Polling

**Planned; not delivered in v0.1.0.**

- `SpoolPoller` (per tenant, `@Scheduled` at `poll_interval_seconds`) lists
  `*READY *HELD` spool files on each `tenant_output_queue`, filters by watermark,
  reads bytes, runs the pipeline, advances the watermark.
- Idempotent across restarts via the dedup constraint.
- `PollOrchestrator` carries a leader-election seam (single-node always-leader now;
  real HA deferred).
- **Retention** after capture: `HOLD | DELETE | LEAVE`, defaulting to `HOLD` until the
  pipeline is trusted. Resolved **per output queue** with fallback to the tenant default.

## Export destinations

**Execution is planned; not delivered in v0.1.0.** The schema and admin read views
exist, but destination editing, dispatch, and retry workers do not.

Per tenant, multiple allowed (e.g. S3 archive + SFTP downstream). Each capture is queued
to every enabled destination. Retry with exponential backoff in `export_attempt`,
max 5 attempts, then `FAILED` and surfaced in the UI.

- **S3** (AWS SDK v2): `bucket`, `prefix`, `region`, optional `endpoint`,
  optional `pathStyleAccess` (for MinIO/Backblaze).
  Key: `{prefix}/{tenant_name}/{yyyy}/{MM}/{dd}/{capture_id}.{ext}`.
- **SFTP** (Apache MINA SSHD): `host`, `port`, `username`, `remote_path`, pinned
  `host_key_fingerprint` (no auto-accept). **Password auth** (entered at submission).
- **FTPS** (Apache Commons Net): `host`, `port`, `username`, `remote_path`,
  `implicit_tls` (990 implicit vs AUTH TLS explicit on 21); validate cert against truststore.

## Connection layer

`As400Factory` enforces `setGuiAvailable(false)` and `setMustUseSockets(true)` (critical
on Linux), and constructs `SecureAS400` rather than plain `AS400` when SSL is configured
(D-013). `TruststoreLoader` provides shared JKS/PKCS12 (per-tenant later). Tenant-keyed
pooling, idle expiry, and invalidation on credential change arrive with the poller.

## Frontend scope

The v0.1.0 credentialed admin SPA delivers dashboard statistics; submission
list/filter/detail with approve/reject actions; tenant list and detail tabs (Connection /
Output Queues / Export Destinations / Recent Captures / Audit); tenant-scoped capture
downloads; and ephemeral Test Connection. It is a same-origin Vite/React bundle served
by Spring Boot and authenticated at the Authentik boundary described above.

Delivered in v0.2.0 (D-024): the standalone public `/submit` page — WS/HOD upload → draft
review → Test Connection → submit — outside the admin identity gate.

Still planned: the admin export-destination editor with conditional fields and write-only
credential replacement UX (submission intake can now create SFTP destinations on approval,
but there is no in-console destination editor yet).

## Out of scope

TN5250E emulation, native ACS/IBM client install, multi-region HA, print-to-IBM-i
(this is read-only off the host). *(PCL rendering was out of scope under D-003; it is
now in scope via the GhostPDL sidecar — D-018.)*
