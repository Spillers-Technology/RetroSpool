package io.retrospool.capture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders plain-text spool data to PDF (docs/architecture.md): Courier, US Letter,
 * landscape when the longest line exceeds {@value #LANDSCAPE_THRESHOLD} columns else
 * portrait; lines are hard-wrapped at {@value #WRAP_COLUMNS} columns with a leading
 * {@value #WRAP_MARKER} marker on continuation lines.
 */
@Component
public class TextToPdfConverter {

    static final int LANDSCAPE_THRESHOLD = 80;
    static final int WRAP_COLUMNS = 132;
    // ASCII-only: the standard-14 Courier encoder rejects non-ASCII glyphs.
    static final String WRAP_MARKER = "+ ";

    private static final float FONT_SIZE = 8f;
    private static final float LEADING = 9.6f;
    private static final float MARGIN = 36f;

    public byte[] convert(byte[] textBytes) {
        List<String> rawLines = readLines(textBytes);
        boolean landscape = maxLineLength(rawLines) > LANDSCAPE_THRESHOLD;
        List<String> lines = wrap(rawLines);

        PDRectangle pageSize = landscape
                ? new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth())
                : PDRectangle.LETTER;

        try (PDDocument doc = new PDDocument()) {
            PDType1Font courier = new PDType1Font(Standard14Fonts.FontName.COURIER);
            int linesPerPage = (int) ((pageSize.getHeight() - 2 * MARGIN) / LEADING);
            for (int offset = 0; offset < Math.max(lines.size(), 1); offset += linesPerPage) {
                PDPage page = new PDPage(pageSize);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(courier, FONT_SIZE);
                    cs.setLeading(LEADING);
                    cs.newLineAtOffset(MARGIN, pageSize.getHeight() - MARGIN);
                    for (int i = offset; i < Math.min(offset + linesPerPage, lines.size()); i++) {
                        cs.showText(lines.get(i));
                        cs.newLine();
                    }
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Text->PDF conversion failed", e);
        }
    }

    private static List<String> readLines(byte[] textBytes) {
        // ISO-8859-1 decodes any byte 1:1; sanitize keeps Courier's WinAnsi encoder happy.
        String text = new String(textBytes, StandardCharsets.ISO_8859_1);
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\r\n|\r|\n|\f", -1)) {
            lines.add(sanitize(line));
        }
        return lines;
    }

    private static String sanitize(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        for (char c : line.toCharArray()) {
            if (c == '\t') {
                sb.append("        ".substring(0, 8 - (sb.length() % 8)));
            } else if (c >= 0x20 && c <= 0x7E) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static int maxLineLength(List<String> lines) {
        return lines.stream().mapToInt(String::length).max().orElse(0);
    }

    private static List<String> wrap(List<String> lines) {
        List<String> wrapped = new ArrayList<>(lines.size());
        for (String line : lines) {
            if (line.length() <= WRAP_COLUMNS) {
                wrapped.add(line);
                continue;
            }
            wrapped.add(line.substring(0, WRAP_COLUMNS));
            int continuationWidth = WRAP_COLUMNS - WRAP_MARKER.length();
            for (int i = WRAP_COLUMNS; i < line.length(); i += continuationWidth) {
                wrapped.add(WRAP_MARKER
                        + line.substring(i, Math.min(i + continuationWidth, line.length())));
            }
        }
        return wrapped;
    }
}
