package io.retrospool.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.SecureAS400;
import org.junit.jupiter.api.Test;

class As400FactoryTest {

    private final As400Factory factory = new As400Factory();

    @Test
    void nonSslConnectionUsesPlainAs400WithLinuxSafeFlags() {
        AS400 system = factory.create(
                new ConnectionParameters("HOST1", "USER1", "pw".toCharArray(), false));

        assertThat(system).isNotInstanceOf(SecureAS400.class);
        assertThat(system.isSecure()).isFalse();
        assertThat(system.isMustUseSockets()).isTrue();
        assertThat(system.isGuiAvailable()).isFalse();
    }

    @Test
    void sslConnectionUsesSecureAs400() {
        AS400 system = factory.create(
                new ConnectionParameters("HOST1", "USER1", "pw".toCharArray(), true));

        assertThat(system).isInstanceOf(SecureAS400.class);
        assertThat(system.isSecure()).isTrue();
        assertThat(system.isMustUseSockets()).isTrue();
        assertThat(system.isGuiAvailable()).isFalse();
    }
}
