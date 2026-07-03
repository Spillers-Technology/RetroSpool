package io.retrospool.connection;

/**
 * Outcome of a synchronous Test Connection check, returned to the submission UI.
 *
 * @param code one of {@code OK}, {@code INVALID_CREDENTIALS}, {@code SECURITY_ERROR},
 *             {@code CONNECTIVITY_ERROR}, {@code ERROR}.
 */
public record TestConnectionResult(
        boolean success,
        String code,
        String message,
        long elapsedMillis) {

    public static TestConnectionResult success(String message, long elapsedMillis) {
        return new TestConnectionResult(true, "OK", message, elapsedMillis);
    }

    public static TestConnectionResult failure(String code, String message, long elapsedMillis) {
        return new TestConnectionResult(false, code, message, elapsedMillis);
    }
}
