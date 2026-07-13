package io.retrospool.web;

import com.fasterxml.jackson.databind.JsonNode;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.Capture;
import io.retrospool.persistence.ExportDestination;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantOutputQueue;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response shapes for the admin console API. Kept as a nested set to avoid a file per record. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record Stats(long tenants, long pendingSubmissions, long captures, long outputQueues) {
    }

    public record SubmissionView(
            UUID id,
            String status,
            JsonNode draft,
            boolean hasIbmiPassword,
            boolean hasSftpPassword,
            Instant submittedAt,
            String reviewedBy,
            Instant reviewedAt,
            UUID resultingTenantId) {

        static SubmissionView of(Submission s, JsonNode draft) {
            return new SubmissionView(
                    s.getId(),
                    s.getStatus().name(),
                    draft,
                    s.getIbmiPasswordRef() != null,
                    s.getSftpPasswordRef() != null,
                    s.getSubmittedAt(),
                    s.getReviewedBy(),
                    s.getReviewedAt(),
                    s.getResultingTenantId());
        }
    }

    public record TenantSummary(
            UUID id,
            String name,
            String host,
            String username,
            boolean useSsl,
            String retentionPolicy,
            int pollIntervalSeconds,
            Instant createdAt,
            long outputQueues,
            long exportDestinations,
            long captures) {
    }

    public record OutputQueueView(UUID id, String library, String queueName, String retentionPolicy) {
        static OutputQueueView of(TenantOutputQueue q) {
            return new OutputQueueView(
                    q.getId(),
                    q.getLibrary(),
                    q.getQueueName(),
                    q.getRetentionPolicy() != null ? q.getRetentionPolicy().name() : null);
        }
    }

    public record ExportDestinationView(
            UUID id, String type, String name, JsonNode config, boolean secretSet, boolean enabled) {
        static ExportDestinationView of(ExportDestination d, JsonNode config) {
            return new ExportDestinationView(
                    d.getId(), d.getType().name(), d.getName(), config, d.getSecretRef() != null, d.isEnabled());
        }
    }

    public record CaptureView(
            UUID id,
            String spoolFileName,
            String spoolJobName,
            String spoolJobUser,
            String detectedFormat,
            int logicalSegmentIndex,
            String sha256,
            long byteSize,
            String renderStatus,
            boolean hasRenderedPdf,
            Instant createdAt,
            Instant capturedAt) {

        static CaptureView of(Capture c) {
            return new CaptureView(
                    c.getId(),
                    c.getSpoolFileName(),
                    c.getSpoolJobName(),
                    c.getSpoolJobUser(),
                    c.getDetectedFormat().name(),
                    c.getLogicalSegmentIndex(),
                    c.getSha256(),
                    c.getByteSize(),
                    c.getRenderStatus().name(),
                    c.getRenderedStorageKey() != null,
                    c.getCreatedAt(),
                    c.getCapturedAt());
        }
    }

    public record AuditEventView(Long id, String eventType, JsonNode payload, Instant createdAt) {
    }

    public record TenantDetail(
            UUID id,
            String name,
            String host,
            int port,
            String username,
            boolean useSsl,
            boolean ibmiPasswordSet,
            String printerDeviceName,
            Integer ccsid,
            List<String> libraryList,
            String retentionPolicy,
            int pollIntervalSeconds,
            Instant createdAt,
            Instant updatedAt,
            List<OutputQueueView> outputQueues,
            List<ExportDestinationView> exportDestinations,
            List<CaptureView> recentCaptures,
            List<AuditEventView> recentAudit) {

        static TenantDetail of(
                Tenant t,
                List<OutputQueueView> queues,
                List<ExportDestinationView> destinations,
                List<CaptureView> captures,
                List<AuditEventView> audit) {
            return new TenantDetail(
                    t.getId(),
                    t.getName(),
                    t.getHost(),
                    t.getPort(),
                    t.getUsername(),
                    t.isUseSsl(),
                    t.getSecretRef() != null,
                    t.getPrinterDeviceName(),
                    t.getCcsid(),
                    t.getLibraryList(),
                    t.getRetentionPolicy().name(),
                    t.getPollIntervalSeconds(),
                    t.getCreatedAt(),
                    t.getUpdatedAt(),
                    queues,
                    destinations,
                    captures,
                    audit);
        }
    }

    public record ReviewRequest(String note) {
    }

    static AuditEventView auditOf(AuditEvent e, JsonNode payload) {
        return new AuditEventView(e.getId(), e.getEventType(), payload, e.getCreatedAt());
    }
}
