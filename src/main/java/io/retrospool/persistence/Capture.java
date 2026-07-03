package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One captured artifact (a whole spool file, or one logical segment of split PCL).
 * Tenant-scoped; unique(tenant_id, sha256, logical_segment_index) is the load-bearing
 * idempotency + within-tenant dedup constraint (D-008).
 */
@Entity
@Table(name = "capture")
public class Capture {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "output_queue_id", nullable = false)
    private UUID outputQueueId;

    @Column(name = "spool_file_name")
    private String spoolFileName;

    @Column(name = "spool_file_number")
    private Integer spoolFileNumber;

    @Column(name = "spool_job_name")
    private String spoolJobName;

    @Column(name = "spool_job_user")
    private String spoolJobUser;

    @Column(name = "spool_job_number")
    private String spoolJobNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_format", nullable = false)
    private DetectedFormat detectedFormat;

    /** 0 unless PCL was split into multiple logical reports. */
    @Column(name = "logical_segment_index", nullable = false)
    private int logicalSegmentIndex;

    /** SHA-256 of the original segment bytes (pre-conversion, pre-render). */
    @Column(name = "sha256", nullable = false)
    private String sha256;

    @Column(name = "byte_size", nullable = false)
    private long byteSize;

    /** Landing object store key of the original/primary artifact (.pcl for PCL). */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    /** Sidecar-rendered PDF sibling (PCL only; NULL until rendered) — D-018. */
    @Column(name = "rendered_storage_key")
    private String renderedStorageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "render_status", nullable = false)
    private RenderStatus renderStatus = RenderStatus.SKIPPED;

    @Column(name = "render_error")
    private String renderError;

    /** When the host created the spool file (unknown until Phase 4 wires the poller). */
    @Column(name = "created_at")
    private Instant createdAt;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private Instant capturedAt;

    protected Capture() {
    }

    public Capture(UUID tenantId, UUID outputQueueId, DetectedFormat detectedFormat,
                   int logicalSegmentIndex, String sha256, long byteSize) {
        this.tenantId = tenantId;
        this.outputQueueId = outputQueueId;
        this.detectedFormat = detectedFormat;
        this.logicalSegmentIndex = logicalSegmentIndex;
        this.sha256 = sha256;
        this.byteSize = byteSize;
    }

    /** Set once the landing key is derived from this capture's own id (D-016). */
    public void assignStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getOutputQueueId() {
        return outputQueueId;
    }

    public String getSpoolFileName() {
        return spoolFileName;
    }

    public void setSpoolFileName(String spoolFileName) {
        this.spoolFileName = spoolFileName;
    }

    public Integer getSpoolFileNumber() {
        return spoolFileNumber;
    }

    public void setSpoolFileNumber(Integer spoolFileNumber) {
        this.spoolFileNumber = spoolFileNumber;
    }

    public String getSpoolJobName() {
        return spoolJobName;
    }

    public void setSpoolJobName(String spoolJobName) {
        this.spoolJobName = spoolJobName;
    }

    public String getSpoolJobUser() {
        return spoolJobUser;
    }

    public void setSpoolJobUser(String spoolJobUser) {
        this.spoolJobUser = spoolJobUser;
    }

    public String getSpoolJobNumber() {
        return spoolJobNumber;
    }

    public void setSpoolJobNumber(String spoolJobNumber) {
        this.spoolJobNumber = spoolJobNumber;
    }

    public DetectedFormat getDetectedFormat() {
        return detectedFormat;
    }

    public int getLogicalSegmentIndex() {
        return logicalSegmentIndex;
    }

    public String getSha256() {
        return sha256;
    }

    public long getByteSize() {
        return byteSize;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getRenderedStorageKey() {
        return renderedStorageKey;
    }

    public RenderStatus getRenderStatus() {
        return renderStatus;
    }

    public String getRenderError() {
        return renderError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void renderSucceeded(String renderedStorageKey) {
        this.renderedStorageKey = renderedStorageKey;
        this.renderStatus = RenderStatus.SUCCESS;
        this.renderError = null;
    }

    public void renderFailed(String error) {
        this.renderStatus = RenderStatus.FAILED;
        this.renderError = error;
    }
}
