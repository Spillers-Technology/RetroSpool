package io.retrospool.capture;

/** Bytes to store as the primary artifact plus the extension they should carry. */
public record ConversionResult(byte[] bytes, String extension) {
}
