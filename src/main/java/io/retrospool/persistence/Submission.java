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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Landing-surface submission awaiting admin review (D-007). Not tenant-scoped —
 * it exists before a tenant does; approval promotes it and sets resultingTenantId.
 */
@Entity
@Table(name = "submission")
public class Submission {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    /** HodFileParser output (host, use_ssl, username, name, device, ...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parsed_draft", nullable = false, columnDefinition = "jsonb")
    private String parsedDraft;

    @Column(name = "ibmi_password_ref")
    private String ibmiPasswordRef;

    @Column(name = "sftp_password_ref")
    private String sftpPasswordRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resulting_tenant_id")
    private UUID resultingTenantId;

    protected Submission() {
    }

    public Submission(String parsedDraft) {
        this.parsedDraft = parsedDraft;
    }

    public UUID getId() {
        return id;
    }

    public String getParsedDraft() {
        return parsedDraft;
    }

    public String getIbmiPasswordRef() {
        return ibmiPasswordRef;
    }

    public void setIbmiPasswordRef(String ibmiPasswordRef) {
        this.ibmiPasswordRef = ibmiPasswordRef;
    }

    public String getSftpPasswordRef() {
        return sftpPasswordRef;
    }

    public void setSftpPasswordRef(String sftpPasswordRef) {
        this.sftpPasswordRef = sftpPasswordRef;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public UUID getResultingTenantId() {
        return resultingTenantId;
    }

    public void approve(String reviewer, UUID tenantId) {
        this.status = SubmissionStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.resultingTenantId = tenantId;
    }

    public void reject(String reviewer) {
        this.status = SubmissionStatus.REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }
}
