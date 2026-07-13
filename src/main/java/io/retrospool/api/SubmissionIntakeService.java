package io.retrospool.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.retrospool.api.IntakeDtos.DraftInput;
import io.retrospool.api.IntakeDtos.SftpDestinationInput;
import io.retrospool.api.IntakeDtos.SubmissionCreatedResponse;
import io.retrospool.api.IntakeDtos.SubmissionRequest;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.secrets.SecretStorageUnavailableException;
import io.retrospool.secrets.SecretWriter;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Creates a pending {@link Submission} from a reviewed public draft (D-007). This is the
 * write half of the low-trust submission surface: it never activates a tenant, only
 * records a draft for admin review. Submitter-entered passwords are stored write-only
 * via {@link SecretWriter} (D-012/D-023) and referenced from the submission; their
 * plaintext never touches the draft JSON, a log line, or a response.
 */
@Service
public class SubmissionIntakeService {

    private final SubmissionRepository submissions;
    private final AuditEventRepository audit;
    private final SecretWriter secretWriter;
    private final ObjectMapper mapper;

    public SubmissionIntakeService(
            SubmissionRepository submissions,
            AuditEventRepository audit,
            SecretWriter secretWriter,
            ObjectMapper mapper) {
        this.submissions = submissions;
        this.audit = audit;
        this.secretWriter = secretWriter;
        this.mapper = mapper;
    }

    @Transactional
    public SubmissionCreatedResponse create(SubmissionRequest request) {
        DraftInput draft = request.draft();
        boolean wantsIbmiSecret = StringUtils.hasText(request.ibmiPassword());
        boolean wantsSftpSecret = request.sftpDestination() != null
                && StringUtils.hasText(request.sftpDestination().password());
        if ((wantsIbmiSecret || wantsSftpSecret) && !secretWriter.isEnabled()) {
            throw new SecretStorageUnavailableException(
                    "This server cannot store submitted passwords yet — the operator must set "
                            + "SECRET_ENCRYPTION_KEY. Retry without a password, or ask the operator to "
                            + "enable secret storage.");
        }

        Submission submission = new Submission(buildDraftJson(draft, request.sftpDestination()));

        if (wantsIbmiSecret) {
            submission.setIbmiPasswordRef(store(request.ibmiPassword()));
        }
        if (wantsSftpSecret) {
            submission.setSftpPasswordRef(store(request.sftpDestination().password()));
        }

        submissions.save(submission);
        audit.save(new AuditEvent(null, "submission.created", auditPayload(draft)));

        return new SubmissionCreatedResponse(
                submission.getId(),
                submission.getStatus().name(),
                submission.getIbmiPasswordRef() != null,
                request.sftpDestination() != null);
    }

    private String store(String password) {
        char[] chars = password.toCharArray();
        try {
            return secretWriter.write(chars);
        } finally {
            Arrays.fill(chars, '\0');
        }
    }

    /** Draft JSON is the {@code submission.parsed_draft} contract read by approval. */
    private String buildDraftJson(DraftInput draft, SftpDestinationInput sftp) {
        ObjectNode node = mapper.createObjectNode();
        node.put("host", draft.host().trim());
        node.put("useSsl", draft.useSsl());
        node.put("username", draft.username().trim());
        node.put("name", StringUtils.hasText(draft.name()) ? draft.name().trim() : draft.host().trim());
        putIfPresent(node, "port", draft.port());
        putIfText(node, "deviceName", draft.deviceName());
        putIfPresent(node, "ccsid", draft.ccsid());
        putIfText(node, "sessionType", draft.sessionType());

        if (sftp != null) {
            // Config only — never the password (that becomes a write-only secret_ref).
            ObjectNode dest = node.putObject("sftpDestination");
            dest.put("name", sftp.name().trim());
            dest.put("host", sftp.host().trim());
            putIfPresent(dest, "port", sftp.port());
            dest.put("username", sftp.username().trim());
            dest.put("remotePath", sftp.remotePath().trim());
            putIfText(dest, "hostKeyFingerprint", sftp.hostKeyFingerprint());
        }
        return node.toString();
    }

    private String auditPayload(DraftInput draft) {
        ObjectNode node = mapper.createObjectNode();
        node.put("host", draft.host().trim());
        node.put("name", StringUtils.hasText(draft.name()) ? draft.name().trim() : draft.host().trim());
        return node.toString();
    }

    private static void putIfText(ObjectNode node, String key, String value) {
        if (StringUtils.hasText(value)) {
            node.put(key, value.trim());
        }
    }

    private static void putIfPresent(ObjectNode node, String key, Integer value) {
        if (value != null) {
            node.put(key, value);
        }
    }
}
