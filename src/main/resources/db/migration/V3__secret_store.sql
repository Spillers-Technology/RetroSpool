-- Encrypted secret store (docs/decisions.md D-023).
-- The env-var-backed SecretResolver (D-012) can only *read* pre-provisioned secrets;
-- the public submission surface (D-007) needs to *write* a submitter-entered IBM i /
-- SFTP password at intake time. This table holds those secrets encrypted at rest
-- (AES-256-GCM; key from gateway.secrets.encryption-key), addressed by a `db:<uuid>`
-- secret_ref. Values are write-only at the API boundary (D-012) — never returned.
CREATE TABLE secret (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    material   bytea NOT NULL,          -- 12-byte GCM nonce || ciphertext || 16-byte tag
    created_at timestamptz NOT NULL DEFAULT now()
);
