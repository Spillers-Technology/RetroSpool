-- Keep the persisted export config contract aligned with docs/data-model.md.
-- Public submission DTOs use browser-friendly camelCase, but export_destination.config
-- is the exporter-facing JSONB boundary and uses the canonical keys below.
--
-- NULLIF(..., 'null'::jsonb) matters here: the `->` operator returns a real SQL NULL
-- for an absent key, but a key present with an explicit JSON `null` value (e.g. an old
-- row shaped {"remote_path": null, "remotePath": "/legit/path"}) comes back as the JSONB
-- scalar `null`, which COALESCE treats as a non-null value and would pick over a valid
-- legacy fallback. NULLIF converts that JSON null into a real SQL NULL first so COALESCE
-- falls through correctly. jsonb_strip_nulls is applied only to the small object being
-- merged in (not the whole `config` document), so unrelated null-valued keys elsewhere
-- in a row's config are left alone rather than stripped as a side effect.
UPDATE export_destination
SET config = (config - 'remotePath' - 'hostKeyFingerprint')
        || jsonb_strip_nulls(jsonb_build_object(
            'remote_path', COALESCE(
                NULLIF(config -> 'remote_path', 'null'::jsonb),
                NULLIF(config -> 'remotePath', 'null'::jsonb)
            ),
            'host_key_fingerprint', COALESCE(
                NULLIF(config -> 'host_key_fingerprint', 'null'::jsonb),
                NULLIF(config -> 'hostKeyFingerprint', 'null'::jsonb)
            )
        ))
WHERE type = 'SFTP'
  AND (config ? 'remotePath' OR config ? 'hostKeyFingerprint');

UPDATE export_destination
SET config = (config - 'remotePath' - 'implicitTls')
        || jsonb_strip_nulls(jsonb_build_object(
            'remote_path', COALESCE(
                NULLIF(config -> 'remote_path', 'null'::jsonb),
                NULLIF(config -> 'remotePath', 'null'::jsonb)
            ),
            'implicit_tls', COALESCE(
                NULLIF(config -> 'implicit_tls', 'null'::jsonb),
                NULLIF(config -> 'implicitTls', 'null'::jsonb)
            )
        ))
WHERE type = 'FTPS'
  AND (config ? 'remotePath' OR config ? 'implicitTls');

-- Postgres never auto-indexes a referencing FK column. Both the delete-guard's own
-- existsByExportDestinationId check and every FK check below scan this
-- column; without an index each is a full table scan against delivery history, which
-- only grows. Created before the FK churn so the ALTERs below aren't scanning unindexed.
CREATE INDEX IF NOT EXISTS idx_export_attempt_destination
    ON export_attempt (export_destination_id);

-- Delivery history is operational evidence. The CRUD service blocks deletes after an
-- attempt exists; the FK makes that guarantee race-safe and protects direct SQL too.
-- NO ACTION, not RESTRICT: RESTRICT is checked row by row mid-cascade, so deleting a
-- tenant (which cascades to both its destinations and, via capture, their attempts)
-- could fail. NO ACTION checks at the end of the statement, so the cascade completes,
-- while deleting a destination that still has history fails exactly as before.
ALTER TABLE export_attempt
    DROP CONSTRAINT export_attempt_export_destination_id_fkey;

ALTER TABLE export_attempt
    ADD CONSTRAINT export_attempt_export_destination_id_fkey
        FOREIGN KEY (export_destination_id)
        REFERENCES export_destination(id)
        ON DELETE NO ACTION;
