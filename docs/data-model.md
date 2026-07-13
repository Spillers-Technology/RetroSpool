# Data Model

> Update this in the same change as any migration. See [CONTRIBUTING.md](../CONTRIBUTING.md).
> Source of truth is the Flyway migrations under `src/main/resources/db/migration`
> once Phase 2 lands; this file is the human-readable companion.

## Conventions

- All capture/export/audit rows are **tenant-scoped**. Every query filters by `tenant_id`.
- **Dedup is within-tenant only.** Identical bytes for two companies = two captures.
- Secrets are never stored inline. Columns ending in `_ref` hold an indirection,
  resolved by `SecretResolver`. Two ref shapes (D-023): an **env var name** (operator
  pre-provisioned, D-012) or **`db:<uuid>`** (a submitter-entered password encrypted in
  the `secret` table). Write-only at the API — never read back.

## Tables

### tenant
```sql
tenant (
  id uuid pk,
  name text,
  host text,
  port int,                       -- default 9476 (signon SSL); informational for spool ops
  use_ssl boolean,
  username text,
  secret_ref text,                -- IBM i password ref (Vault path or env var name)
  printer_device_name text,       -- LU/device from HOD; informational, not used at runtime
  ccsid int,
  library_list text[],
  retention_policy text,          -- HOLD | DELETE | LEAVE  (default for this tenant's queues)
  poll_interval_seconds int,
  created_at timestamptz,
  updated_at timestamptz
);
```

### tenant_output_queue
```sql
tenant_output_queue (
  id uuid pk,
  tenant_id uuid fk,
  library text,
  queue_name text,
  retention_policy text,          -- NULLABLE override of tenant.retention_policy (per-queue control)
  unique(tenant_id, library, queue_name)
);
```
Retention resolves per queue: `tenant_output_queue.retention_policy ?? tenant.retention_policy`.

### submission  (two-surface model: landing → admin review)
```sql
submission (
  id uuid pk,
  parsed_draft jsonb,             -- HodFileParser output (host, use_ssl, username, name, device, ...)
  ibmi_password_ref text,         -- collected at submission; write-only
  sftp_password_ref text,         -- collected at submission; write-only
  status text,                    -- PENDING | APPROVED | REJECTED
  submitted_at timestamptz,
  reviewed_by text,
  reviewed_at timestamptz,
  resulting_tenant_id uuid         -- set on approval
);
```
Approval copies the draft + secrets into `tenant` and `export_destination`: it creates the
tenant (connection fields incl. `port`/`ccsid`), carries the IBM i secret reference, and —
as of v0.2.0 (D-024) — creates an `export_destination` row with its SFTP secret when the
submission carried one. Nothing auto-activates; approval is the mandatory human-review gate.

### spool_watermark
```sql
spool_watermark (
  tenant_id uuid,
  output_queue_id uuid,
  last_seen_at timestamptz,
  last_seen_job_number text,
  primary key (tenant_id, output_queue_id)
);
```

### capture
```sql
capture (
  id uuid pk,
  tenant_id uuid fk,
  output_queue_id uuid fk,
  spool_file_name text,
  spool_file_number int,
  spool_job_name text,
  spool_job_user text,
  spool_job_number text,
  detected_format text,           -- PDF | PCL | TEXT | UNKNOWN
  logical_segment_index int,      -- 0 unless PCL was split
  sha256 text,
  byte_size bigint,
  storage_key text,               -- key in landing object store (original artifact; .pcl for PCL)
  rendered_storage_key text,      -- rendered PDF sibling (PCL only; NULL until rendered) — V2
  render_status text,             -- SKIPPED | SUCCESS | FAILED (SKIPPED for non-PCL) — V2
  render_error text,              -- last renderer error, surfaced in UI — V2
  created_at timestamptz,         -- when host created the spool file
  captured_at timestamptz,
  unique(tenant_id, sha256, logical_segment_index)   -- load-bearing idempotency / within-tenant dedup
);
```

Rendering (V2, D-018): PCL captures keep **both** artifacts — the original `.pcl`
(source of truth, re-renderable) and the sidecar-rendered `.pdf`. A render failure
never fails the capture; it lands with `render_status = FAILED` + `render_error`.
Dedup is computed on original bytes only.

### export_destination
```sql
export_destination (
  id uuid pk,
  tenant_id uuid fk,
  type text,                      -- S3 | SFTP | FTPS
  name text,
  config jsonb,                   -- S3: bucket/prefix/region/endpoint/pathStyleAccess
                                  -- SFTP: host/port/username/remote_path/host_key_fingerprint
                                  -- FTPS: host/port/username/remote_path/implicit_tls
  secret_ref text,                -- SFTP: password ref (password auth, per decision)
  enabled boolean
);
```

### export_attempt
```sql
export_attempt (
  id uuid pk,
  capture_id uuid fk,
  export_destination_id uuid fk,
  status text,                    -- PENDING | SUCCESS | FAILED
  attempt_count int,              -- exponential backoff, max 5 then FAILED
  last_error text,
  completed_at timestamptz
);
```

### audit_event
```sql
audit_event (
  id bigserial pk,
  tenant_id uuid,                 -- per-company auditable
  event_type text,
  payload jsonb,
  created_at timestamptz
);
```

### secret  (V3 — encrypted secret store, D-023)
```sql
secret (
  id uuid pk,
  material bytea,                 -- AES-256-GCM envelope: nonce(12) || ciphertext || tag(16)
  created_at timestamptz
);
```
Written by the pre-tenant submission surface (D-007) for submitter-entered passwords, so
**not tenant-scoped** (like `submission`). Referenced as `db:<id>`; key from
`gateway.secrets.encryption-key`. Meaningless without the key; never returned by any API.
The `submission.parsed_draft` may also carry an optional `sftpDestination` object
(config only, no password), which approval turns into an `export_destination` row.

## Deltas from the original handoff schema

- Added `tenant.printer_device_name` (HOD LU/device, informational).
- Added nullable `tenant_output_queue.retention_policy` override (per-queue control).
- Added `submission` table for the landing-vs-admin two-surface lifecycle, carrying
  write-only IBM i + SFTP password refs through approval.
- Clarified `export_destination.secret_ref` as SFTP **password** (not key) per decision.
- Added the `secret` table (V3) for encrypted, submitter-entered passwords (D-023),
  giving `_ref` columns a `db:<uuid>` shape alongside env-var names.
