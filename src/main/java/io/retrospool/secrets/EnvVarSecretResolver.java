package io.retrospool.secrets;

import java.util.function.UnaryOperator;
import org.springframework.stereotype.Component;

/**
 * Treats a secret_ref as an environment variable name. Missing or blank env var is a
 * hard {@link SecretResolutionException} — never an empty-string fallback (D-012).
 */
@Component
public class EnvVarSecretResolver implements SecretResolver {

    private final UnaryOperator<String> env;

    public EnvVarSecretResolver() {
        this(System::getenv);
    }

    /** Visible for testing — inject a fake environment. */
    EnvVarSecretResolver(UnaryOperator<String> env) {
        this.env = env;
    }

    @Override
    public char[] resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new SecretResolutionException("secret_ref is not set");
        }
        String value = env.apply(secretRef);
        if (value == null || value.isEmpty()) {
            throw new SecretResolutionException(
                    "Environment variable '" + secretRef + "' is not set or empty");
        }
        return value.toCharArray();
    }
}
