package io.retrospool.secrets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Authenticated symmetric encryption for the secret store (D-023). AES-256-GCM with a
 * random 96-bit nonce per message; the on-disk envelope is {@code nonce || ciphertext
 * || 128-bit tag}. Tampering is detected on decrypt (GCM auth tag), which surfaces as a
 * {@link SecretResolutionException} rather than returning garbage.
 *
 * <p>The key comes from {@code gateway.secrets.encryption-key}. A base64 value decoding
 * to 16/24/32 bytes is used directly as an AES key; any other non-blank value is hashed
 * with SHA-256 into a 256-bit key so an operator can supply a passphrase. A blank value
 * leaves the cipher <b>unconfigured</b> ({@link #isConfigured()} is false) — the app
 * still boots, but the secret store refuses writes until a key is set.
 */
@Component
public class SecretCipher {

    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${gateway.secrets.encryption-key:}") String configured) {
        this.key = deriveKey(configured);
    }

    public boolean isConfigured() {
        return key != null;
    }

    /** @return {@code nonce || ciphertext || tag}; caller owns scrubbing {@code plaintext}. */
    public byte[] encrypt(byte[] plaintext) {
        requireConfigured();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plaintext);
            byte[] out = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, out, 0, nonce.length);
            System.arraycopy(sealed, 0, out, nonce.length, sealed.length);
            return out;
        } catch (Exception e) {
            throw new SecretResolutionException("failed to encrypt secret");
        }
    }

    public byte[] decrypt(byte[] envelope) {
        requireConfigured();
        if (envelope == null || envelope.length <= NONCE_BYTES) {
            throw new SecretResolutionException("stored secret is malformed");
        }
        try {
            byte[] nonce = Arrays.copyOfRange(envelope, 0, NONCE_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(envelope, NONCE_BYTES, envelope.length - NONCE_BYTES);
        } catch (Exception e) {
            // Wrong key or tampered ciphertext both land here; never leak specifics.
            throw new SecretResolutionException("failed to decrypt secret (wrong key or corrupt data)");
        }
    }

    private void requireConfigured() {
        if (key == null) {
            throw new SecretResolutionException(
                    "secret encryption key is not configured (set gateway.secrets.encryption-key)");
        }
    }

    private static SecretKeySpec deriveKey(String configured) {
        if (configured == null || configured.isBlank()) {
            return null;
        }
        byte[] material = tryDecodeBase64(configured.trim());
        if (material != null && (material.length == 16 || material.length == 24 || material.length == 32)) {
            return new SecretKeySpec(material, "AES");
        }
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(configured.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hashed, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable to derive secret key", e);
        }
    }

    private static byte[] tryDecodeBase64(String value) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
