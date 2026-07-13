package io.retrospool.web;

import java.util.List;

/**
 * Identity of a credentialed admin, resolved from the Authentik outpost's
 * forward-auth headers (D-022). This is the {@code principal} on the
 * authentication placed in the security context.
 */
public record AdminUser(String username, String email, String displayName, List<String> groups) {
}
