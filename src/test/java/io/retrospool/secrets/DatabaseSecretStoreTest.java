package io.retrospool.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class DatabaseSecretStoreTest {

    @Mock
    private StoredSecretRepository repository;

    private final SecretCipher cipher =
            new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void writeThenResolveRoundTripsThePassword() {
        DatabaseSecretStore store = new DatabaseSecretStore(cipher, repository);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String ref = store.write("hunter2".toCharArray());
        assertThat(ref).startsWith("db:");

        ArgumentCaptor<StoredSecret> captor = ArgumentCaptor.forClass(StoredSecret.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        StoredSecret persisted = captor.getValue();
        // The stored envelope is not the plaintext.
        assertThat(new String(persisted.getMaterial())).doesNotContain("hunter2");

        when(repository.findById(persisted.getId())).thenReturn(Optional.of(persisted));
        assertThat(store.resolve(ref)).isEqualTo("hunter2".toCharArray());
    }

    @Test
    void rejectsEmptySecret() {
        DatabaseSecretStore store = new DatabaseSecretStore(cipher, repository);
        assertThatThrownBy(() -> store.write(new char[0]))
                .isInstanceOf(SecretResolutionException.class);
    }

    @Test
    void resolveFailsForUnknownRef() {
        DatabaseSecretStore store = new DatabaseSecretStore(cipher, repository);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store.resolve("db:" + id))
                .isInstanceOf(SecretResolutionException.class);
    }

    @Test
    void disabledWhenCipherHasNoKey() {
        DatabaseSecretStore store = new DatabaseSecretStore(new SecretCipher(""), repository);
        assertThat(store.isEnabled()).isFalse();
    }

    @Test
    void handlesOnlyDbPrefixedRefs() {
        assertThat(DatabaseSecretStore.handles("db:" + UUID.randomUUID())).isTrue();
        assertThat(DatabaseSecretStore.handles("IBMI_PASSWORD")).isFalse();
        assertThat(DatabaseSecretStore.handles(null)).isFalse();
    }
}
