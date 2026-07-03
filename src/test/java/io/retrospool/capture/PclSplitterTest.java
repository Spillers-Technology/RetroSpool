package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;

import io.retrospool.Fixtures;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PclSplitterTest {

    private final PclSplitter splitter = new PclSplitter();

    @Test
    void singleReportIsNotSplit() {
        byte[] single = Fixtures.load("sample.pcl");
        assertThat(splitter.split(single)).containsExactly(single);
    }

    @Test
    void concatenatedFixtureSplitsIntoTwoSegmentsPreservingAllBytes() {
        byte[] data = Fixtures.load("concatenated.pcl");
        List<byte[]> segments = splitter.split(data);

        assertThat(segments).hasSize(2);
        assertThat(new String(segments.get(0), StandardCharsets.ISO_8859_1)).contains("Report ONE");
        assertThat(new String(segments.get(1), StandardCharsets.ISO_8859_1)).contains("Report TWO");
        // No bytes lost or duplicated: segments re-concatenate to the original.
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        segments.forEach(joined::writeBytes);
        assertThat(joined.toByteArray()).isEqualTo(data);
    }

    @Test
    void escEWithoutPrecedingFormFeedIsNotABoundary() {
        // ESC E embedded mid-stream (e.g. inside a binary section) with no FF before it.
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.writeBytes(new byte[]{0x1B, 'E'});
        data.writeBytes("start of report with plenty of body text ".getBytes(StandardCharsets.ISO_8859_1));
        data.writeBytes(new byte[]{0x1B, 'E'}); // false positive: not FF-preceded
        data.writeBytes("more of the same report".getBytes(StandardCharsets.ISO_8859_1));
        assertThat(splitter.split(data.toByteArray())).hasSize(1);
    }

    @Test
    void trailingBareResetIsMergedIntoLastSegment() {
        byte[] data = Fixtures.load("concatenated.pcl");
        List<byte[]> segments = splitter.split(data);
        byte[] last = segments.get(segments.size() - 1);
        // fixture ends FF + ESC E; that terminator belongs to report two, not a third segment
        assertThat(last[last.length - 2]).isEqualTo((byte) 0x1B);
        assertThat(last[last.length - 1]).isEqualTo((byte) 'E');
    }

    @Test
    void emptyInputYieldsNoSegments() {
        assertThat(splitter.split(new byte[0])).isEmpty();
        assertThat(splitter.split(null)).isEmpty();
    }
}
