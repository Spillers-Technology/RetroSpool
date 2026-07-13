package io.retrospool.secrets;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Envelope for one secret encrypted at rest (D-023). {@code material} is the raw
 * AES-256-GCM output ({@code nonce || ciphertext || tag}); it is meaningless without
 * the configured key. Addressed externally as a {@code db:<id>} secret_ref. Not
 * tenant-scoped — secrets are written by the pre-tenant submission surface (D-007).
 */
@Entity
@Table(name = "secret")
public class StoredSecret {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "material", nullable = false)
    private byte[] material;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StoredSecret() {
    }

    public StoredSecret(byte[] material) {
        this.material = material;
    }

    public UUID getId() {
        return id;
    }

    public byte[] getMaterial() {
        return material;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
