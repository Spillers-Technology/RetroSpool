package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Composite key: one watermark per (tenant, output queue). */
@Embeddable
public class SpoolWatermarkId implements Serializable {

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "output_queue_id", nullable = false)
    private UUID outputQueueId;

    protected SpoolWatermarkId() {
    }

    public SpoolWatermarkId(UUID tenantId, UUID outputQueueId) {
        this.tenantId = tenantId;
        this.outputQueueId = outputQueueId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOutputQueueId() {
        return outputQueueId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpoolWatermarkId that)) {
            return false;
        }
        return Objects.equals(tenantId, that.tenantId)
                && Objects.equals(outputQueueId, that.outputQueueId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, outputQueueId);
    }
}
