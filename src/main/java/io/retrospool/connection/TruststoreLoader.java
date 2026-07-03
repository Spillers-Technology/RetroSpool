package io.retrospool.connection;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Applies a shared truststore for SSL signon. {@link SecureAS400} uses the standard JSSE machinery,
 * so configuring the default truststore via system properties is sufficient for a single shared
 * store. Per-tenant truststores are a later enhancement (see docs/implementation-plan.md Phase 1).
 */
@Component
public class TruststoreLoader {

    private static final Logger log = LoggerFactory.getLogger(TruststoreLoader.class);

    private final String path;
    private final String password;
    private final String type;

    public TruststoreLoader(
            @Value("${gateway.truststore.path:}") String path,
            @Value("${gateway.truststore.password:}") String password,
            @Value("${gateway.truststore.type:PKCS12}") String type) {
        this.path = path;
        this.password = password;
        this.type = type;
    }

    @PostConstruct
    void apply() {
        if (!StringUtils.hasText(path)) {
            log.info("No shared truststore configured (gateway.truststore.path empty); "
                    + "using the JVM default truststore for SSL signon.");
            return;
        }
        System.setProperty("javax.net.ssl.trustStore", path);
        System.setProperty("javax.net.ssl.trustStoreType", type);
        if (StringUtils.hasText(password)) {
            System.setProperty("javax.net.ssl.trustStorePassword", password);
        }
        log.info("Configured shared {} truststore for SSL signon from {}", type, path);
    }
}
