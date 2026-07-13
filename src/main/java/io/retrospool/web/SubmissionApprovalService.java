package io.retrospool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.persistence.SubmissionStatus;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * The mandatory human-review gate of the two-surface model (D-007): a pending
 * landing submission is promoted to an active {@link Tenant} only by an explicit
 * admin approval. Nothing auto-activates. The IBM i password ref collected at
 * submission rides through onto the tenant as a write-only {@code secret_ref}
 * (D-010, D-012). Every decision writes an audit event.
 */
@Service
public class SubmissionApprovalService {

    private final SubmissionRepository submissions;
    private final TenantRepository tenants;
    private final AuditEventRepository audit;
    private final ObjectMapper mapper;

    public SubmissionApprovalService(
            SubmissionRepository submissions,
            TenantRepository tenants,
            AuditEventRepository audit,
            ObjectMapper mapper) {
        this.submissions = submissions;
        this.tenants = tenants;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** Promote a PENDING submission to an active tenant. The locked read serializes decisions. */
    @Transactional
    public Tenant approve(UUID submissionId, String reviewedBy) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NoSuchElementException("submission not found: " + submissionId));
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new IllegalStateException("submission is not PENDING (is " + submission.getStatus() + ")");
        }

        JsonNode draft = readDraft(submission);
        String host = requireField(draft, "host");
        String username = requireField(draft, "username", "user");
        String name = text(draft, firstPresent(draft, "name", "sessionName", "session"), host);

        Tenant tenant = new Tenant(name, host, username);
        tenant.setUseSsl(boolField(draft, true, "useSsl", "use_ssl", "ssl"));
        tenant.setPrinterDeviceName(text(draft, firstPresent(draft, "deviceName", "printerDeviceName", "device"), null));
        if (StringUtils.hasText(submission.getIbmiPasswordRef())) {
            tenant.setSecretRef(submission.getIbmiPasswordRef());
        }
        tenant = tenants.save(tenant);

        submission.approve(reviewedBy, tenant.getId());
        submissions.save(submission);

        audit.save(new AuditEvent(tenant.getId(), "submission.approved",
                jsonPayload("submissionId", submissionId.toString(), "reviewedBy", reviewedBy)));
        return tenant;
    }

    @Transactional
    public void reject(UUID submissionId, String reviewedBy) {
        Submission submission = submissions.findByIdForUpdate(submissionId)
                .orElseThrow(() -> new NoSuchElementException("submission not found: " + submissionId));
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            throw new IllegalStateException("submission is not PENDING (is " + submission.getStatus() + ")");
        }
        submission.reject(reviewedBy);
        submissions.save(submission);
        audit.save(new AuditEvent(null, "submission.rejected",
                jsonPayload("submissionId", submissionId.toString(), "reviewedBy", reviewedBy)));
    }

    private JsonNode readDraft(Submission submission) {
        try {
            return mapper.readTree(submission.getParsedDraft());
        } catch (Exception e) {
            throw new IllegalStateException("submission draft is not valid JSON", e);
        }
    }

    private String jsonPayload(String... kv) {
        ObjectNode node = mapper.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            node.put(kv[i], kv[i + 1]);
        }
        return node.toString();
    }

    private static String firstPresent(JsonNode draft, String... keys) {
        for (String k : keys) {
            if (draft.hasNonNull(k)) {
                return k;
            }
        }
        return keys[0];
    }

    private static String requireField(JsonNode draft, String... keys) {
        for (String k : keys) {
            if (draft.hasNonNull(k) && StringUtils.hasText(draft.get(k).asText())) {
                return draft.get(k).asText().trim();
            }
        }
        throw new IllegalStateException("submission draft is missing required field: " + keys[0]);
    }

    private static String text(JsonNode draft, String key, String fallback) {
        return draft.hasNonNull(key) && StringUtils.hasText(draft.get(key).asText())
                ? draft.get(key).asText().trim()
                : fallback;
    }

    private static boolean boolField(JsonNode draft, boolean fallback, String... keys) {
        for (String k : keys) {
            if (draft.hasNonNull(k)) {
                return draft.get(k).asBoolean(fallback);
            }
        }
        return fallback;
    }
}
