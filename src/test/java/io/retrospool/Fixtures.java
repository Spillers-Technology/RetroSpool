package io.retrospool;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads the committed spool fixtures from src/test/resources/fixtures. */
public final class Fixtures {

    private Fixtures() {
    }

    public static byte[] load(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing fixture: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
