package io.retrospool.submission;

import java.util.List;

/**
 * Normalized connection draft extracted from an uploaded emulator session file
 * (IBM Personal Communications {@code .ws} or Host On-Demand session) by
 * {@link WsHodParser}. This is the low-trust submission surface's starting point
 * (D-007): a draft the submitter reviews and completes, never an active tenant.
 *
 * <p>Field names are the JSON contract stored in {@code submission.parsed_draft}
 * and are the keys {@code SubmissionApprovalService} reads at promotion time
 * ({@code host}, {@code username}, {@code name}, {@code useSsl}, {@code deviceName}).
 * Nothing sensitive lives here — emulator session files never carry the IBM i
 * password; the submitter adds it separately and it is stored write-only (D-012).
 *
 * @param host        IBM i host name/address, or {@code null} if the file had none.
 * @param port        telnet port declared in the file; informational for spool ops.
 * @param useSsl      whether the session negotiated TLS (defaults to {@code true}).
 * @param username    user id if the file carried one (printer sessions rarely do).
 * @param name        session description/name; falls back to the uploaded file name.
 * @param deviceName  LU / associated-printer / device name; informational (D-001).
 * @param ccsid       host code page if numeric, else {@code null}.
 * @param sessionType human-readable session kind (e.g. {@code "5250 Printer"}).
 * @param sourceFormat which parser branch matched ({@code "PComm .ws"} / {@code "HOD"}).
 * @param warnings    non-fatal notes for the submitter (missing host, defaulted SSL…).
 */
public record ParsedDraft(
        String host,
        Integer port,
        boolean useSsl,
        String username,
        String name,
        String deviceName,
        Integer ccsid,
        String sessionType,
        String sourceFormat,
        List<String> warnings) {

    public ParsedDraft {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
