package io.retrospool.secrets;

/**
 * Thrown when a caller needs to persist a submitter-entered secret (D-023) but no
 * encryption key is configured, so {@link SecretWriter#isEnabled()} is false. Distinct
 * from {@link SecretResolutionException} (a runtime resolve/encrypt failure) so the API
 * layer can map it to a 503 with a "configure the key" message rather than a 500.
 */
public class SecretStorageUnavailableException extends RuntimeException {

    public SecretStorageUnavailableException(String message) {
        super(message);
    }
}
