package io.retrospool.connection;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.SecureAS400;
import java.beans.PropertyVetoException;
import org.springframework.stereotype.Component;

/**
 * Builds {@link AS400} connection objects with the settings that are non-negotiable on Linux:
 *
 * <ul>
 *   <li>{@code GuiAvailable=false} — never attempt an interactive password/SSL prompt on a
 *       headless host.</li>
 *   <li>{@code MustUseSockets=true} — critical on Linux; forces pure-Java socket I/O instead of
 *       routing through native AS400 services.</li>
 * </ul>
 *
 * <p>SSL: jt400 21.x exposes no {@code AS400.setUseSSL(...)}; a secure connection is requested by
 * using the {@link SecureAS400} subclass, which negotiates TLS through the standard JSSE
 * truststore (configured by {@link TruststoreLoader}). See docs/decisions.md D-013.
 *
 * <p>The returned object is not yet connected — JTOpen connects lazily on first service use.
 */
@Component
public class As400Factory {

    public AS400 create(ConnectionParameters params) {
        AS400 system = params.useSsl()
                ? new SecureAS400(params.host(), params.username(), params.password())
                : new AS400(params.host(), params.username(), params.password());
        try {
            system.setGuiAvailable(false);
        } catch (PropertyVetoException e) {
            // No vetoer is registered, so this is unreachable in practice.
            throw new IllegalStateException("Unable to disable GUI on AS400 connection", e);
        }
        system.setMustUseSockets(true);
        return system;
    }
}
