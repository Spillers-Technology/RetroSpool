-- Retrospool — initial schema.
-- Human-readable companion: docs/data-model.md (keep in sync).
-- All capture/export/audit data is tenant-scoped; dedup is within-tenant only.

CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()

-- Active company configuration (promoted from an approved submission).
CREATE TABLE tenant (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 text NOT NULL,
    host                 text NOT NULL,
    port                 int  NOT NULL DEFAULT 9476,          -- informational for spool ops
    use_ssl              boolean NOT NULL DEFAULT true,
    username             text NOT NULL,
    secret_ref           text,                                -- IBM i password ref (write-only)
    printer_device_name  text,                                -- HOD LU/device; informational
    ccsid                int,
    library_list         text[] NOT NULL DEFAULT '{}',
    retention_policy     text NOT NULL DEFAULT 'HOLD'
                           CHECK (retention_policy IN ('HOLD','DELETE','LEAVE')),
    poll_interval_seconds int NOT NULL DEFAULT 60,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE tenant_output_queue (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    library          text NOT NULL,
    queue_name       text NOT NULL,
    retention_policy text CHECK (retention_policy IN ('HOLD','DELETE','LEAVE')), -- NULL = inherit tenant
    UNIQUE (tenant_id, library, queue_name)
);

-- Landing/low-trust submissions awaiting credentialed admin review (two-surface model).
CREATE TABLE submission (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    parsed_draft        jsonb NOT NULL,
    ibmi_password_ref   text,                                 -- write-only
    sftp_password_ref   text,                                 -- write-only
    status              text NOT NULL DEFAULT 'PENDING'
                          CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    submitted_at        timestamptz NOT NULL DEFAULT now(),
    reviewed_by         text,
    reviewed_at         timestamptz,
    resulting_tenant_id uuid REFERENCES tenant(id)
);

CREATE TABLE spool_watermark (
    tenant_id           uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    output_queue_id     uuid NOT NULL REFERENCES tenant_output_queue(id) ON DELETE CASCADE,
    last_seen_at        timestamptz,
    last_seen_job_number text,
    PRIMARY KEY (tenant_id, output_queue_id)
);

CREATE TABLE capture (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    output_queue_id      uuid NOT NULL REFERENCES tenant_output_queue(id) ON DELETE CASCADE,
    spool_file_name      text,
    spool_file_number    int,
    spool_job_name       text,
    spool_job_user       text,
    spool_job_number     text,
    detected_format      text NOT NULL
                           CHECK (detected_format IN ('PDF','PCL','TEXT','UNKNOWN')),
    logical_segment_index int NOT NULL DEFAULT 0,
    sha256               text NOT NULL,
    byte_size            bigint NOT NULL,
    storage_key          text NOT NULL,
    created_at           timestamptz,                         -- host spool-file creation time
    captured_at          timestamptz NOT NULL DEFAULT now(),
    -- load-bearing: idempotency across restarts + within-tenant dedup
    UNIQUE (tenant_id, sha256, logical_segment_index)
);

CREATE INDEX idx_capture_tenant_captured ON capture (tenant_id, captured_at DESC);

CREATE TABLE export_destination (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    type        text NOT NULL CHECK (type IN ('S3','SFTP','FTPS')),
    name        text NOT NULL,
    config      jsonb NOT NULL DEFAULT '{}',
    secret_ref  text,                                         -- write-only
    enabled     boolean NOT NULL DEFAULT true
);

CREATE TABLE export_attempt (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    capture_id            uuid NOT NULL REFERENCES capture(id) ON DELETE CASCADE,
    export_destination_id uuid NOT NULL REFERENCES export_destination(id) ON DELETE CASCADE,
    status                text NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING','SUCCESS','FAILED')),
    attempt_count         int NOT NULL DEFAULT 0,
    last_error            text,
    completed_at          timestamptz
);

CREATE TABLE audit_event (
    id         bigserial PRIMARY KEY,
    tenant_id  uuid,
    event_type text NOT NULL,
    payload    jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_tenant_created ON audit_event (tenant_id, created_at DESC);
