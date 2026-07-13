package io.retrospool.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static String base64Key() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) {
            key[i] = (byte) i;
        }
        return Base64.getEncoder().encodeToString(key);
    }

    @Test
    void roundTripsThroughEncryptDecrypt() {
        SecretCipher cipher = new SecretCipher(base64Key());
        byte[] secret = "Passw0rd!".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = cipher.encrypt(secret);
        assertThat(cipher.decrypt(envelope)).isEqualTo(secret);
    }

    @Test
    void producesDistinctCiphertextPerCall() {
        SecretCipher cipher = new SecretCipher(base64Key());
        byte[] secret = "same-input".getBytes(StandardCharsets.UTF_8);
        // Random nonce per message → identical plaintext encrypts differently.
        assertThat(cipher.encrypt(secret)).isNotEqualTo(cipher.encrypt(secret));
    }

    @Test
    void rejectsTamperedCiphertext() {
        SecretCipher cipher = new SecretCipher(base64Key());
        byte[] envelope = cipher.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        envelope[envelope.length - 1] ^= 0x01; // flip a tag bit

        assertThatThrownBy(() -> cipher.decrypt(envelope))
                .isInstanceOf(SecretResolutionException.class);
    }

    @Test
    void differentKeyCannotDecrypt() {
        byte[] envelope = new SecretCipher(base64Key()).encrypt("s".getBytes(StandardCharsets.UTF_8));
        SecretCipher other = new SecretCipher("a-totally-different-passphrase");

        assertThatThrownBy(() -> other.decrypt(envelope))
                .isInstanceOf(SecretResolutionException.class);
    }

    @Test
    void passphraseIsAcceptedViaSha256Derivation() {
        SecretCipher cipher = new SecretCipher("just a human passphrase");
        assertThat(cipher.isConfigured()).isTrue();
        byte[] secret = "x".getBytes(StandardCharsets.UTF_8);
        assertThat(cipher.decrypt(cipher.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void blankKeyLeavesCipherUnconfigured() {
        SecretCipher cipher = new SecretCipher("");
        assertThat(cipher.isConfigured()).isFalse();
        assertThatThrownBy(() -> cipher.encrypt(new byte[]{1}))
                .isInstanceOf(SecretResolutionException.class);
    }
}
