package io.retrospool.capture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Splits concatenated PCL on logical report boundaries: ESC E (0x1B 0x45, printer
 * reset) counts as a boundary only at offset 0 or when immediately preceded by a
 * form feed (0x0C) — the guard against false positives inside binary sections
 * (docs/architecture.md "PCL splitting").
 */
@Component
public class PclSplitter {

    private static final byte ESC = 0x1B;
    private static final byte RESET = 0x45; // 'E'
    private static final byte FORM_FEED = 0x0C;

    /**
     * Jobs commonly end with a bare trailing reset (FF + ESC E); a "segment" shorter
     * than this is that terminator, not a report — it is merged into the previous
     * segment so no bytes are lost and no junk capture is created.
     */
    private static final int MIN_TAIL_SEGMENT_BYTES = 8;

    /** Each element is one logical report; index in the list == logical_segment_index. */
    public List<byte[]> split(byte[] data) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);
        for (int i = 1; i < data.length - 1; i++) {
            if (data[i] == ESC && data[i + 1] == RESET && data[i - 1] == FORM_FEED) {
                boundaries.add(i);
            }
        }
        if (boundaries.size() == 1) {
            return List.of(data);
        }
        if (data.length - boundaries.get(boundaries.size() - 1) < MIN_TAIL_SEGMENT_BYTES) {
            boundaries.remove(boundaries.size() - 1);
        }
        if (boundaries.size() == 1) {
            return List.of(data);
        }
        List<byte[]> segments = new ArrayList<>(boundaries.size());
        for (int s = 0; s < boundaries.size(); s++) {
            int from = boundaries.get(s);
            int to = s + 1 < boundaries.size() ? boundaries.get(s + 1) : data.length;
            segments.add(Arrays.copyOfRange(data, from, to));
        }
        return segments;
    }
}
