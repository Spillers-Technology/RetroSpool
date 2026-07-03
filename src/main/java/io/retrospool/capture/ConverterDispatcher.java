package io.retrospool.capture;

import io.retrospool.persistence.DetectedFormat;
import org.springframework.stereotype.Component;

/**
 * Maps a detected format to the primary stored artifact (docs/architecture.md
 * "Converter dispatcher"): PDF passthrough, TEXT rendered via PDFBox, PCL stored
 * as-is (.pcl — the rendered PDF sibling is produced separately via the GhostPDL
 * sidecar, D-018), UNKNOWN passthrough as .bin.
 */
@Component
public class ConverterDispatcher {

    private final TextToPdfConverter textToPdf;

    public ConverterDispatcher(TextToPdfConverter textToPdf) {
        this.textToPdf = textToPdf;
    }

    public ConversionResult convert(DetectedFormat format, byte[] bytes) {
        return switch (format) {
            case PDF -> new ConversionResult(bytes, "pdf");
            case TEXT -> new ConversionResult(textToPdf.convert(bytes), "pdf");
            case PCL -> new ConversionResult(bytes, "pcl");
            case UNKNOWN -> new ConversionResult(bytes, "bin");
        };
    }
}
