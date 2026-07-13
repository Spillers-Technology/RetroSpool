package io.retrospool.secrets;

/**
 * Writes secret material and returns a write-only {@code secret_ref} (D-012, D-023).
 * The counterpart to {@link SecretResolver}: intake collects a password, this persists
 * it (encrypted), and the resolver reads it back later in the pipeline. No API ever
 * returns the value — only the reference and a "set / not set" flag.
 */
public interface SecretWriter {

    /** True when a storage backend is configured and {@link #write} can succeed. */
    boolean isEnabled();

    /**
     * Persist {@code secret} and return its reference. The caller still owns and should
     * scrub {@code secret}; implementations must not retain it.
     *
     * @throws SecretResolutionException if no backend is configured.
     */
    String write(char[] secret);
}
