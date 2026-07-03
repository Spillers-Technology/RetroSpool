package io.retrospool.capture;

import java.time.Instant;

/**
 * Host-side identity of a spool file, carried onto its captures. Everything is
 * optional here — Phase 4's poller supplies it; fixture-driven tests may not.
 */
public record SpoolMetadata(
        String fileName,
        Integer fileNumber,
        String jobName,
        String jobUser,
        String jobNumber,
        Instant hostCreatedAt) {

    public static SpoolMetadata none() {
        return new SpoolMetadata(null, null, null, null, null, null);
    }
}
