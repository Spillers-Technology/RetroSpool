package io.retrospool.persistence;

/** Export attempt state; FAILED is terminal after max retries (D-004). */
public enum ExportStatus {
    PENDING, SUCCESS, FAILED
}
