package io.retrospool.persistence;

/**
 * PCL->PDF sidecar rendering outcome (D-018). SKIPPED for non-PCL formats; a FAILED
 * render never fails the capture — the original .pcl still lands and is retryable.
 */
public enum RenderStatus {
    SKIPPED, SUCCESS, FAILED
}
