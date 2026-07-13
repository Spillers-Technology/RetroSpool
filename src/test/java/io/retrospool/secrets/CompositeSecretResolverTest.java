package io.retrospool.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompositeSecretResolverTest {

    @Mock
    private DatabaseSecretStore databaseStore;

    @Mock
    private EnvVarSecretResolver envResolver;

    @Test
    void routesDbRefsToTheDatabaseStore() {
        CompositeSecretResolver resolver = new CompositeSecretResolver(databaseStore, envResolver);
        String ref = "db:" + UUID.randomUUID();
        when(databaseStore.resolve(ref)).thenReturn("db-secret".toCharArray());

        assertThat(resolver.resolve(ref)).isEqualTo("db-secret".toCharArray());
        verifyNoInteractions(envResolver);
    }

    @Test
    void routesEnvRefsToTheEnvResolver() {
        CompositeSecretResolver resolver = new CompositeSecretResolver(databaseStore, envResolver);
        when(envResolver.resolve("IBMI_PASSWORD")).thenReturn("env-secret".toCharArray());

        assertThat(resolver.resolve("IBMI_PASSWORD")).isEqualTo("env-secret".toCharArray());
        verifyNoInteractions(databaseStore);
    }

    @Test
    void rejectsBlankRef() {
        CompositeSecretResolver resolver = new CompositeSecretResolver(databaseStore, envResolver);
        assertThatThrownBy(() -> resolver.resolve(" "))
                .isInstanceOf(SecretResolutionException.class);
    }
}
