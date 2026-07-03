package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class TextToPdfConverterTest {

    private final TextToPdfConverter converter = new TextToPdfConverter();

    @Test
    void shortLinesProducePortraitLetter() throws IOException {
        byte[] pdf = converter.convert("short line\nanother short line\n"
                .getBytes(StandardCharsets.US_ASCII));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDRectangle box = doc.getPage(0).getMediaBox();
            assertThat(box.getWidth()).isEqualTo(PDRectangle.LETTER.getWidth());
            assertThat(box.getHeight()).isEqualTo(PDRectangle.LETTER.getHeight());
            assertThat(new PDFTextStripper().getText(doc)).contains("short line");
        }
    }

    @Test
    void lineOver80ColumnsFlipsToLandscape() throws IOException {
        String wide = "x".repeat(TextToPdfConverter.LANDSCAPE_THRESHOLD + 1);
        byte[] pdf = converter.convert(wide.getBytes(StandardCharsets.US_ASCII));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDRectangle box = doc.getPage(0).getMediaBox();
            assertThat(box.getWidth()).isGreaterThan(box.getHeight());
        }
    }

    @Test
    void linesOver132ColumnsAreWrappedWithMarker() throws IOException {
        String longLine = "A".repeat(TextToPdfConverter.WRAP_COLUMNS + 20);
        byte[] pdf = converter.convert(longLine.getBytes(StandardCharsets.US_ASCII));
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains(TextToPdfConverter.WRAP_MARKER.strip());
            // every rendered line respects the wrap width
            for (String line : text.split("\r?\n")) {
                assertThat(line.length()).isLessThanOrEqualTo(TextToPdfConverter.WRAP_COLUMNS);
            }
        }
    }

    @Test
    void nonAsciiAndTabsAreSanitizedNotFatal() throws IOException {
        byte[] input = "col1\tcol2 café bell".getBytes(StandardCharsets.ISO_8859_1);
        byte[] pdf = converter.convert(input);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("col1").contains("col2");
        }
    }

    @Test
    void emptyInputStillProducesAOnePagePdf() throws IOException {
        byte[] pdf = converter.convert(new byte[0]);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }
}
