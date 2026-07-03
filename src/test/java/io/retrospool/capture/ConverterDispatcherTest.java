package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;

import io.retrospool.Fixtures;
import io.retrospool.persistence.DetectedFormat;
import org.junit.jupiter.api.Test;

class ConverterDispatcherTest {

    private final ConverterDispatcher dispatcher =
            new ConverterDispatcher(new TextToPdfConverter());

    @Test
    void pdfPassesThroughUnchanged() {
        byte[] pdf = Fixtures.load("sample.pdf");
        ConversionResult result = dispatcher.convert(DetectedFormat.PDF, pdf);
        assertThat(result.bytes()).isEqualTo(pdf);
        assertThat(result.extension()).isEqualTo("pdf");
    }

    @Test
    void textIsRenderedToPdf() {
        ConversionResult result = dispatcher.convert(DetectedFormat.TEXT, Fixtures.load("sample.txt"));
        assertThat(new String(result.bytes(), 0, 5)).isEqualTo("%PDF-");
        assertThat(result.extension()).isEqualTo("pdf");
    }

    @Test
    void pclIsStoredAsIsWithPclExtension() {
        byte[] pcl = Fixtures.load("sample.pcl");
        ConversionResult result = dispatcher.convert(DetectedFormat.PCL, pcl);
        assertThat(result.bytes()).isEqualTo(pcl);
        assertThat(result.extension()).isEqualTo("pcl");
    }

    @Test
    void unknownPassesThroughAsBin() {
        byte[] bin = Fixtures.load("binary.bin");
        ConversionResult result = dispatcher.convert(DetectedFormat.UNKNOWN, bin);
        assertThat(result.bytes()).isEqualTo(bin);
        assertThat(result.extension()).isEqualTo("bin");
    }
}
