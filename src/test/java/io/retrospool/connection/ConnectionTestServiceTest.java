package io.retrospool.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.ibm.as400.access.AS400SecurityException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Exercises the outcome-to-result mapping without touching a real host, via the
 * {@link SignonValidator} seam.
 */
class ConnectionTestServiceTest {

    private final As400Factory factory = new As400Factory();

    private ConnectionParameters params() {
        return new ConnectionParameters("HOST", "USER", "pw".toCharArray(), false);
    }

    @Test
    void acceptedSignonMapsToOk() {
        ConnectionTestService svc = new ConnectionTestService(factory, system -> true);

        TestConnectionResult r = svc.test(params());

        assertThat(r.success()).isTrue();
        assertThat(r.code()).isEqualTo("OK");
    }

    @Test
    void rejectedSignonMapsToInvalidCredentials() {
        ConnectionTestService svc = new ConnectionTestService(factory, system -> false);

        TestConnectionResult r = svc.test(params());

        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void securityExceptionMapsToSecurityError() {
        // AS400SecurityException has no public constructor; mock one to throw.
        ConnectionTestService svc = new ConnectionTestService(factory, system -> {
            throw mock(AS400SecurityException.class);
        });

        TestConnectionResult r = svc.test(params());

        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo("SECURITY_ERROR");
    }

    @Test
    void ioExceptionMapsToConnectivityError() {
        ConnectionTestService svc = new ConnectionTestService(factory, system -> {
            throw new IOException("connection refused");
        });

        TestConnectionResult r = svc.test(params());

        assertThat(r.success()).isFalse();
        assertThat(r.code()).isEqualTo("CONNECTIVITY_ERROR");
    }
}
