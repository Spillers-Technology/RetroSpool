package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;

import io.retrospool.Fixtures;
import io.retrospool.persistence.DetectedFormat;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FormatSnifferTest {

    private final FormatSniffer sniffer = new FormatSniffer();

    @Test
    void detectsPdfFixture() {
        assertThat(sniffer.sniff(Fixtures.load("sample.pdf"))).isEqualTo(DetectedFormat.PDF);
    }

    @Test
    void detectsPclFixture() {
        assertThat(sniffer.sniff(Fixtures.load("sample.pcl"))).isEqualTo(DetectedFormat.PCL);
    }

    @Test
    void detectsTextFixture() {
        assertThat(sniffer.sniff(Fixtures.load("sample.txt"))).isEqualTo(DetectedFormat.TEXT);
    }

    @Test
    void unknownForBinaryFixture() {
        assertThat(sniffer.sniff(Fixtures.load("binary.bin"))).isEqualTo(DetectedFormat.UNKNOWN);
    }

    @Test
    void stripsLeadingNullsBeforeSniffing() {
        byte[] padded = new byte[]{0, 0, 0, 0x1B, 'E', 'x'};
        assertThat(sniffer.sniff(padded)).isEqualTo(DetectedFormat.PCL);
    }

    @Test
    void detectsPclXlAndPclCommandSignatures() {
        assertThat(sniffer.sniff(new byte[]{0x1B, '%', '-', '1'})).isEqualTo(DetectedFormat.PCL);
        assertThat(sniffer.sniff(new byte[]{0x1B, '&', 'l', '0'})).isEqualTo(DetectedFormat.PCL);
    }

    @Test
    void textAllowsCommonWhitespace() {
        byte[] text = "line one\r\n\tline two\fnext".getBytes(StandardCharsets.US_ASCII);
        assertThat(sniffer.sniff(text)).isEqualTo(DetectedFormat.TEXT);
    }

    @Test
    void emptyAndAllNullInputAreUnknown() {
        assertThat(sniffer.sniff(new byte[0])).isEqualTo(DetectedFormat.UNKNOWN);
        assertThat(sniffer.sniff(new byte[]{0, 0, 0})).isEqualTo(DetectedFormat.UNKNOWN);
        assertThat(sniffer.sniff(null)).isEqualTo(DetectedFormat.UNKNOWN);
    }
}
