package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One watched output queue (library/queue) on a tenant's host. */
@Entity
@Table(name = "tenant_output_queue")
public class TenantOutputQueue {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "library", nullable = false)
    private String library;

    @Column(name = "queue_name", nullable = false)
    private String queueName;

    /** NULL = inherit the tenant default (D-009). */
    @Enumerated(EnumType.STRING)
    @Column(name = "retention_policy")
    private RetentionPolicy retentionPolicy;

    protected TenantOutputQueue() {
    }

    public TenantOutputQueue(UUID tenantId, String library, String queueName) {
        this.tenantId = tenantId;
        this.library = library;
        this.queueName = queueName;
    }

    /** Per-queue override with tenant fallback (D-009). */
    public RetentionPolicy effectiveRetention(RetentionPolicy tenantDefault) {
        return retentionPolicy != null ? retentionPolicy : tenantDefault;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getLibrary() {
        return library;
    }

    public String getQueueName() {
        return queueName;
    }

    public RetentionPolicy getRetentionPolicy() {
        return retentionPolicy;
    }

    public void setRetentionPolicy(RetentionPolicy retentionPolicy) {
        this.retentionPolicy = retentionPolicy;
    }
}
