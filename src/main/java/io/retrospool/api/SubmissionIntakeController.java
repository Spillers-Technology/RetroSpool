package io.retrospool.api;

import io.retrospool.api.IntakeDtos.SubmissionCreatedResponse;
import io.retrospool.api.IntakeDtos.SubmissionRequest;
import io.retrospool.submission.ParsedDraft;
import io.retrospool.submission.WsHodParser;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Public submission intake (D-007), the low-trust counterpart to the credentialed
 * {@code SubmissionAdminController}. Two anonymous steps, both permitted and
 * CSRF-exempt in {@code SecurityConfig} exactly like {@code POST /api/connection/test}:
 *
 * <ol>
 *   <li>{@code POST /api/submissions/parse} — upload a PComm {@code .ws} or HOD session
 *       file; get back a parsed draft to review. Nothing is persisted.</li>
 *   <li>{@code POST /api/submissions} — submit the reviewed draft (plus optional
 *       write-only passwords). Creates a <b>pending</b> submission, never a tenant.</li>
 * </ol>
 *
 * Emulator session files are a few KB of text; anything larger than {@link #MAX_BYTES}
 * is rejected before parsing.
 */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionIntakeController {

    static final long MAX_BYTES = 256 * 1024;

    private final WsHodParser parser;
    private final SubmissionIntakeService intake;

    public SubmissionIntakeController(WsHodParser parser, SubmissionIntakeService intake) {
        this.parser = parser;
        this.intake = intake;
    }

    @PostMapping(path = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ParsedDraft parse(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException(
                    "That file is too large to be a session profile (max " + (MAX_BYTES / 1024) + " KB).");
        }
        // ISO-8859-1 never throws on stray bytes; these config files are ASCII/Latin-1.
        String content = new String(file.getBytes(), StandardCharsets.ISO_8859_1);
        return parser.parse(content, file.getOriginalFilename());
    }

    @PostMapping
    public SubmissionCreatedResponse create(@Valid @RequestBody SubmissionRequest request) {
        return intake.create(request);
    }
}
