package io.retrospool.capture;

/** Outcome of a sidecar render; {@code pdf} is null unless {@code success}. */
public record RenderResult(boolean success, byte[] pdf, String error) {

    public static RenderResult ok(byte[] pdf) {
        return new RenderResult(true, pdf, null);
    }

    public static RenderResult failed(String error) {
        return new RenderResult(false, null, error);
    }
}
