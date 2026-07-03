package io.retrospool.persistence;

/** Result of {@code FormatSniffer} over the first bytes of a spool file. */
public enum DetectedFormat {
    PDF, PCL, TEXT, UNKNOWN
}
