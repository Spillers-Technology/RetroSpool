package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Delivery of one capture to one destination. Tenant scope is inherited through the
 * capture (capture_id is only ever obtained via tenant-scoped queries). Exponential
 * backoff, max 5 attempts, then FAILED and surfaced (D-004).
 */
@Entity
@Table(name = "export_attempt")
public class ExportAttempt {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "capture_id", nullable = false)
    private UUID captureId;

    @Column(name = "export_destination_id", nullable = false)
    private UUID exportDestinationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExportStatus status = ExportStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ExportAttempt() {
    }

    public ExportAttempt(UUID captureId, UUID exportDestinationId) {
        this.captureId = captureId;
        this.exportDestinationId = exportDestinationId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCaptureId() {
        return captureId;
    }

    public UUID getExportDestinationId() {
        return exportDestinationId;
    }

    public ExportStatus getStatus() {
        return status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error;
    }

    public void markFailed() {
        this.status = ExportStatus.FAILED;
        this.completedAt = Instant.now();
    }

    public void markSucceeded() {
        this.attemptCount++;
        this.status = ExportStatus.SUCCESS;
        this.lastError = null;
        this.completedAt = Instant.now();
    }
}
