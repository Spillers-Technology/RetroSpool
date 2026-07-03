package io.retrospool.connection;

import com.ibm.as400.access.AS400;

/**
 * Seam over the actual JTOpen signon call so {@link ConnectionTestService} can be unit-tested
 * without a live host. The production implementation delegates to {@link AS400#validateSignon()}.
 */
@FunctionalInterface
public interface SignonValidator {

    /**
     * @return {@code true} if the host accepted the signon, {@code false} if it rejected it.
     * @throws Exception JTOpen throws {@code AS400SecurityException} / {@code IOException}.
     */
    boolean validate(AS400 system) throws Exception;
}
