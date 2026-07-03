package io.retrospool.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** High-water mark for a polled output queue; keeps restarts idempotent. */
@Entity
@Table(name = "spool_watermark")
public class SpoolWatermark {

    @EmbeddedId
    private SpoolWatermarkId id;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_seen_job_number")
    private String lastSeenJobNumber;

    protected SpoolWatermark() {
    }

    public SpoolWatermark(SpoolWatermarkId id) {
        this.id = id;
    }

    public SpoolWatermarkId getId() {
        return id;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public String getLastSeenJobNumber() {
        return lastSeenJobNumber;
    }

    public void advance(Instant seenAt, String jobNumber) {
        this.lastSeenAt = seenAt;
        this.lastSeenJobNumber = jobNumber;
    }
}
