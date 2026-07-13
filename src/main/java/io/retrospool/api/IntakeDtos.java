package io.retrospool.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Request/response shapes for the low-trust public submission surface (D-007). The
 * emulator session file is parsed into a draft the submitter reviews; on submit, the
 * draft plus optional write-only passwords (D-010/D-012) create a pending submission.
 * Nothing here activates a tenant — that is the admin approval gate.
 */
public final class IntakeDtos {

    private IntakeDtos() {
    }

    /** Reviewed/edited connection draft. Field names mirror {@code ParsedDraft}. */
    public record DraftInput(
            @NotBlank String host,
            Integer port,
            boolean useSsl,
            @NotBlank String username,
            String name,
            String deviceName,
            Integer ccsid,
            String sessionType) {
    }

    /** Optional SFTP destination to provision on approval (password auth, D-010). */
    public record SftpDestinationInput(
            @NotBlank String name,
            @NotBlank String host,
            Integer port,
            @NotBlank String username,
            @NotBlank String remotePath,
            String hostKeyFingerprint,
            String password) {
    }

    /** Full submission payload from the standalone page. */
    public record SubmissionRequest(
            @Valid DraftInput draft,
            String ibmiPassword,
            @Valid SftpDestinationInput sftpDestination) {
    }

    /** What the page shows after a successful submit (never any secret value). */
    public record SubmissionCreatedResponse(
            UUID id,
            String status,
            boolean ibmiPasswordStored,
            boolean sftpDestinationConfigured) {
    }
}
