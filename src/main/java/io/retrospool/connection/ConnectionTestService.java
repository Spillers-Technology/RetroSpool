package io.retrospool.connection;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Performs a synchronous IBM i signon check and maps the outcome to a {@link TestConnectionResult}.
 * This is the backend of the submission page's "Test Connection" button — the milestone-one
 * validation that JTOpen can actually reach a real host with the SSL/truststore settings correct.
 */
@Service
public class ConnectionTestService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionTestService.class);
    private static final int SIGNON_TIMEOUT_MILLIS = 15_000;

    private final As400Factory factory;
    private final SignonValidator validator;

    @Autowired
    public ConnectionTestService(As400Factory factory) {
        this(factory, defaultValidator());
    }

    /** Visible for testing — inject a fake {@link SignonValidator} to avoid hitting a real host. */
    ConnectionTestService(As400Factory factory, SignonValidator validator) {
        this.factory = factory;
        this.validator = validator;
    }

    private static SignonValidator defaultValidator() {
        return system -> {
            system.setvalidateSignonTimeOut(SIGNON_TIMEOUT_MILLIS);
            return system.validateSignon();
        };
    }

    public TestConnectionResult test(ConnectionParameters params) {
        Instant start = Instant.now();
        AS400 system = null;
        try {
            system = factory.create(params);
            boolean accepted = validator.validate(system);
            long ms = elapsed(start);
            if (accepted) {
                return TestConnectionResult.success(
                        "Signon validated for " + params.username() + "@" + params.host(), ms);
            }
            return TestConnectionResult.failure("INVALID_CREDENTIALS",
                    "Host rejected the signon (invalid user or password).", ms);
        } catch (AS400SecurityException e) {
            log.info("Test connection security failure for {}@{}: {}",
                    params.username(), params.host(), e.getMessage());
            return TestConnectionResult.failure("SECURITY_ERROR",
                    "Signon refused: " + safeMessage(e), elapsed(start));
        } catch (IOException e) {
            log.info("Test connection connectivity failure for {}@{}: {}",
                    params.username(), params.host(), e.getMessage());
            return TestConnectionResult.failure("CONNECTIVITY_ERROR",
                    "Could not reach the host (network, port, or TLS handshake): " + safeMessage(e),
                    elapsed(start));
        } catch (Exception e) {
            log.warn("Test connection unexpected failure for {}@{}",
                    params.username(), params.host(), e);
            return TestConnectionResult.failure("ERROR",
                    "Unexpected error: " + safeMessage(e), elapsed(start));
        } finally {
            if (system != null) {
                system.disconnectAllServices();
            }
            Arrays.fill(params.password(), '\0');
        }
    }

    private static long elapsed(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private static String safeMessage(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
