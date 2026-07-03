package io.retrospool.connection;

/**
 * The minimal inputs needed to sign on to an IBM i host.
 *
 * <p>The password is a {@code char[]} so callers can scrub it after use; this record does not
 * override {@code equals}/{@code hashCode}, so do not use instances as map keys.
 */
public record ConnectionParameters(
        String host,
        String username,
        char[] password,
        boolean useSsl) {
}
