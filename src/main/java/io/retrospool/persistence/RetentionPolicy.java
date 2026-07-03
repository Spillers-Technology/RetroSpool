package io.retrospool.persistence;

/** What to do with the host spool file after a successful capture. Default HOLD (D-009). */
public enum RetentionPolicy {
    HOLD, DELETE, LEAVE
}
