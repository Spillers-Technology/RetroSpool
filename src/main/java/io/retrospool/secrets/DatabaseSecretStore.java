package io.retrospool.secrets;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed encrypted secret store (D-023), the write path the env-var resolver
 * (D-012) lacks. Secrets are addressed as {@code db:<uuid>} and stored as AES-256-GCM
 * envelopes ({@link SecretCipher}). This class handles only {@code db:} references;
 * {@link CompositeSecretResolver} routes env-var references to
 * {@link EnvVarSecretResolver}. Nothing here logs or returns plaintext (D-012).
 */
@Component
public class DatabaseSecretStore implements SecretWriter {

    static final String PREFIX = "db:";

    private final SecretCipher cipher;
    private final StoredSecretRepository repository;

    public DatabaseSecretStore(SecretCipher cipher, StoredSecretRepository repository) {
        this.cipher = cipher;
        this.repository = repository;
    }

    static boolean handles(String secretRef) {
        return secretRef != null && secretRef.startsWith(PREFIX);
    }

    @Override
    public boolean isEnabled() {
        return cipher.isConfigured();
    }

    @Override
    @Transactional
    public String write(char[] secret) {
        if (secret == null || secret.length == 0) {
            throw new SecretResolutionException("cannot store an empty secret");
        }
        byte[] plaintext = toUtf8(secret);
        try {
            StoredSecret saved = repository.save(new StoredSecret(cipher.encrypt(plaintext)));
            return PREFIX + saved.getId();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /** Resolve a {@code db:<uuid>} reference. Package-private; called via the composite. */
    @Transactional(readOnly = true)
    char[] resolve(String secretRef) {
        UUID id = parseId(secretRef);
        StoredSecret stored = repository.findById(id)
                .orElseThrow(() -> new SecretResolutionException("no stored secret for ref " + secretRef));
        byte[] plaintext = cipher.decrypt(stored.getMaterial());
        try {
            return toChars(plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static UUID parseId(String secretRef) {
        try {
            return UUID.fromString(secretRef.substring(PREFIX.length()));
        } catch (RuntimeException e) {
            throw new SecretResolutionException("malformed db secret ref: " + secretRef);
        }
    }

    private static byte[] toUtf8(char[] chars) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOfRange(encoded.array(), encoded.position(), encoded.limit());
        Arrays.fill(encoded.array(), (byte) 0);
        return bytes;
    }

    private static char[] toChars(byte[] bytes) {
        CharBuffer decoded = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = Arrays.copyOfRange(decoded.array(), decoded.position(), decoded.limit());
        Arrays.fill(decoded.array(), '\0');
        return chars;
    }
}
