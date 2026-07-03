package io.retrospool.capture;

import io.retrospool.persistence.DetectedFormat;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Detects the spool payload format from the first 16 bytes after stripping leading
 * nulls (docs/architecture.md "Format sniffing"). DEVTYPE(*USERASCII) streams are
 * opaque; this is deliberately a cheap signature check, not a parser.
 */
@Component
public class FormatSniffer {

    private static final int WINDOW = 16;
    private static final byte ESC = 0x1B;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public DetectedFormat sniff(byte[] data) {
        if (data == null) {
            return DetectedFormat.UNKNOWN;
        }
        int start = 0;
        while (start < data.length && data[start] == 0x00) {
            start++;
        }
        int len = Math.min(WINDOW, data.length - start);
        if (len <= 0) {
            return DetectedFormat.UNKNOWN;
        }

        if (startsWith(data, start, len, PDF_MAGIC)) {
            return DetectedFormat.PDF;
        }
        if (len >= 2 && data[start] == ESC) {
            byte second = data[start + 1];
            // ESC E (printer reset), ESC % (PCL XL / PJL escape), ESC & (PCL command)
            if (second == 'E' || second == '%' || second == '&') {
                return DetectedFormat.PCL;
            }
        }
        if (allPrintable(data, start, len)) {
            return DetectedFormat.TEXT;
        }
        return DetectedFormat.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, int offset, int available, byte[] prefix) {
        if (available < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean allPrintable(byte[] data, int offset, int len) {
        for (int i = offset; i < offset + len; i++) {
            byte b = data[i];
            boolean printable = b >= 0x20 && b <= 0x7E;
            boolean whitespace = b == '\t' || b == '\r' || b == '\n' || b == 0x0C;
            if (!printable && !whitespace) {
                return false;
            }
        }
        return true;
    }
}
