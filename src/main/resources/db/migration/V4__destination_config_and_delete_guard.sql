-- Keep the persisted export config contract aligned with docs/data-model.md.
-- Public submission DTOs use browser-friendly camelCase, but export_destination.config
-- is the exporter-facing JSONB boundary and uses the canonical keys below.
UPDATE export_destination
SET config = jsonb_strip_nulls(
        (config - 'remotePath' - 'hostKeyFingerprint')
        || jsonb_build_object(
            'remote_path', COALESCE(config -> 'remote_path', config -> 'remotePath'),
            'host_key_fingerprint',
                COALESCE(config -> 'host_key_fingerprint', config -> 'hostKeyFingerprint')
        )
    )
WHERE type = 'SFTP'
  AND (config ? 'remotePath' OR config ? 'hostKeyFingerprint');

UPDATE export_destination
SET config = jsonb_strip_nulls(
        (config - 'remotePath' - 'implicitTls')
        || jsonb_build_object(
            'remote_path', COALESCE(config -> 'remote_path', config -> 'remotePath'),
            'implicit_tls', COALESCE(config -> 'implicit_tls', config -> 'implicitTls')
        )
    )
WHERE type = 'FTPS'
  AND (config ? 'remotePath' OR config ? 'implicitTls');

-- Delivery history is operational evidence. The CRUD service blocks deletes after an
-- attempt exists; RESTRICT makes that guarantee race-safe and protects direct SQL too.
ALTER TABLE export_attempt
    DROP CONSTRAINT export_attempt_export_destination_id_fkey;

ALTER TABLE export_attempt
    ADD CONSTRAINT export_attempt_export_destination_id_fkey
        FOREIGN KEY (export_destination_id)
        REFERENCES export_destination(id)
        ON DELETE RESTRICT;
