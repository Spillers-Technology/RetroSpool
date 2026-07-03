package io.retrospool.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvVarSecretResolverTest {

    private final Map<String, String> fakeEnv = Map.of("IBMI_PASSWORD_ACME", "s3cret");
    private final EnvVarSecretResolver resolver = new EnvVarSecretResolver(fakeEnv::get);

    @Test
    void resolvesExistingVariable() {
        assertThat(resolver.resolve("IBMI_PASSWORD_ACME")).isEqualTo("s3cret".toCharArray());
    }

    @Test
    void missingVariableIsAHardErrorNamingTheRefOnly() {
        assertThatThrownBy(() -> resolver.resolve("NOT_SET"))
                .isInstanceOf(SecretResolutionException.class)
                .hasMessageContaining("NOT_SET")
                .hasMessageNotContaining("s3cret");
    }

    @Test
    void nullOrBlankRefIsAHardError() {
        assertThatThrownBy(() -> resolver.resolve(null))
                .isInstanceOf(SecretResolutionException.class);
        assertThatThrownBy(() -> resolver.resolve("  "))
                .isInstanceOf(SecretResolutionException.class);
    }
}
