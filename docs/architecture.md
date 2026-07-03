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
A standalone page. No credentialed access required.

- Upload a HOD `.ws` file → `HodFileParser` produces a **draft** (host, SSL flag,
  LU/device name [informational], user id, session name).
- The submitter adds the **SFTP destination password** and the **IBM i password**
  (never present in HOD), and can run **Test Connection** (synchronous JTOpen signon).
- Submitting creates a **pending `submission`** — it does **not** create a tenant.

### 2. Admin surface (credentialed / OIDC)
The prod team's workspace.

- Reviews pending submissions and **approves** → promotion to an active **Tenant**
  (plus its `export_destination` rows and secrets). Nothing auto-activates; approval is
  the mandatory human-review gate.
- One page manages **many companies / many queues**, but every view (captures, audit,
  exports, errors) is **scoped and auditable per company**. Convenience in the UI; hard
  isolation in the data.

### Lifecycle

```
HOD upload ─▶ parsed draft ─▶ pending submission ─▶ admin approval ─▶ active tenant ─▶ polling
                              (+ SFTP/IBM i passwords as write-only secrets, carried through)
```

Secrets collected at submission (SFTP password, IBM i password) ride through approval
into the ingestion pipeline as **write-only** `secret_ref` values.

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
  ─▶ ExportDispatcher         (fan-out to every enabled destination on the tenant)
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

- `SpoolPoller` (per tenant, `@Scheduled` at `poll_interval_seconds`) lists
  `*READY *HELD` spool files on each `tenant_output_queue`, filters by watermark,
  reads bytes, runs the pipeline, advances the watermark.
- Idempotent across restarts via the dedup constraint.
- `PollOrchestrator` carries a leader-election seam (single-node always-leader now;
  real HA deferred).
- **Retention** after capture: `HOLD | DELETE | LEAVE`, defaulting to `HOLD` until the
  pipeline is trusted. Resolved **per output queue** with fallback to the tenant default.

## Export destinations

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

`As400Factory` enforces `setGuiAvailable(false)`, `setMustUseSockets(true)` (critical on
Linux), and `setUseSSL(true)` when configured. `TruststoreLoader` provides shared
JKS/PKCS12 (per-tenant later). Connections pooled by `tenantId`, idle timeout 15–30 min,
invalidated on credential change.

## Frontend scope

Submission page (HOD upload → draft → SFTP/IBM i password → Test Connection → submit) and
the credentialed admin: tenants list, tenant detail tabs (Connection / Output Queues /
Export Destinations / Recent Captures / Audit), submission review/approval, captures list
with download, export-destination editor with conditional fields + test. OIDC auth;
credentials write-only (`•••• (set)` + Replace).

## Out of scope

TN5250E emulation, native ACS/IBM client install, multi-region HA, print-to-IBM-i
(this is read-only off the host). *(PCL rendering was out of scope under D-003; it is
now in scope via the GhostPDL sidecar — D-018.)*
