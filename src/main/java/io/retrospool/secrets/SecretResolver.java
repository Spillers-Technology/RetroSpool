package io.retrospool.secrets;

/**
 * Resolves a write-only {@code secret_ref} indirection to its value (D-012).
 * Implementations must never log or expose resolved values; no API returns them.
 * Env-var-backed first; a Vault-backed impl is an additive later step.
 */
public interface SecretResolver {

    /**
     * @throws SecretResolutionException if the ref cannot be resolved — never returns
     *         null or empty-string fallbacks; a missing secret is a hard error.
     */
    char[] resolve(String secretRef);
}
