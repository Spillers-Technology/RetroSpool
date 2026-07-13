package io.retrospool.secrets;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The resolver the rest of the app injects (D-023). Routes by the shape of the
 * {@code secret_ref}: {@code db:<uuid>} references go to the encrypted
 * {@link DatabaseSecretStore} (submitter-entered passwords), everything else to the
 * {@link EnvVarSecretResolver} (operator-provisioned env vars, D-012). This keeps both
 * secret sources behind the single {@link SecretResolver} contract the pipeline uses.
 */
@Primary
@Component
public class CompositeSecretResolver implements SecretResolver {

    private final DatabaseSecretStore databaseStore;
    private final EnvVarSecretResolver envResolver;

    public CompositeSecretResolver(DatabaseSecretStore databaseStore, EnvVarSecretResolver envResolver) {
        this.databaseStore = databaseStore;
        this.envResolver = envResolver;
    }

    @Override
    public char[] resolve(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            throw new SecretResolutionException("secret_ref is not set");
        }
        return DatabaseSecretStore.handles(secretRef)
                ? databaseStore.resolve(secretRef)
                : envResolver.resolve(secretRef);
    }
}
