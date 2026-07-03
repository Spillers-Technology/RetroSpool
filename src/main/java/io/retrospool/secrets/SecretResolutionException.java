package io.retrospool.secrets;

/** A secret_ref could not be resolved. The message must name the ref, never a value. */
public class SecretResolutionException extends RuntimeException {

    public SecretResolutionException(String message) {
        super(message);
    }
}
