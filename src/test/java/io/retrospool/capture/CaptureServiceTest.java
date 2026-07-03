package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.retrospool.Fixtures;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Capture;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.DetectedFormat;
import io.retrospool.persistence.RenderStatus;
import io.retrospool.storage.ObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaptureServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID queueId = UUID.randomUUID();

    private InMemoryObjectStore store;
    private CaptureRepository captureRepo;
    private AuditEventRepository auditRepo;
    private PclRenderClient renderClient;
    private List<AuditEvent> auditEvents;
    private CaptureService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryObjectStore();
        captureRepo = mock(CaptureRepository.class);
        auditRepo = mock(AuditEventRepository.class);
        renderClient = mock(PclRenderClient.class);
        auditEvents = new ArrayList<>();

        when(captureRepo.existsByTenantIdAndSha256AndLogicalSegmentIndex(
                any(), anyString(), anyInt())).thenReturn(false);
        when(captureRepo.save(any(Capture.class))).thenAnswer(inv -> inv.getArgument(0));
        when(auditRepo.save(any(AuditEvent.class))).thenAnswer(inv -> {
            auditEvents.add(inv.getArgument(0));
            return inv.getArgument(0);
        });

        service = new CaptureService(new FormatSniffer(), new PclSplitter(),
                new ConverterDispatcher(new TextToPdfConverter()), renderClient,
                store, captureRepo, auditRepo, new ObjectMapper());
    }

    @Test
    void concatenatedPclProducesTwoCapturesWithRenderedSiblings() {
        when(renderClient.render(any())).thenReturn(
                RenderResult.ok("%PDF-fake".getBytes(StandardCharsets.US_ASCII)));

        List<Capture> created = service.capture(tenantId, queueId,
                Fixtures.load("concatenated.pcl"), SpoolMetadata.none());

        assertThat(created).hasSize(2);
        for (int i = 0; i < 2; i++) {
            Capture c = created.get(i);
            assertThat(c.getDetectedFormat()).isEqualTo(DetectedFormat.PCL);
            assertThat(c.getLogicalSegmentIndex()).isEqualTo(i);
            assertThat(c.getStorageKey())
                    .startsWith(tenantId.toString() + "/")
                    .contains(c.getId().toString())
                    .endsWith("-" + i + ".pcl");
            assertThat(c.getRenderStatus()).isEqualTo(RenderStatus.SUCCESS);
            assertThat(c.getRenderedStorageKey()).isEqualTo(c.getStorageKey() + ".pdf");
            assertThat(store.bytes(c.getStorageKey())).isNotEmpty();
            assertThat(store.bytes(c.getRenderedStorageKey())).isNotEmpty();
        }
        assertThat(auditEvents).hasSize(2);
        assertThat(auditEvents.get(0).getTenantId()).isEqualTo(tenantId);
        assertThat(auditEvents.get(0).getEventType()).isEqualTo("CAPTURE_CREATED");
    }

    @Test
    void renderFailureStillLandsThePclCapture() {
        when(renderClient.render(any())).thenReturn(RenderResult.failed("boom"));

        List<Capture> created = service.capture(tenantId, queueId,
                Fixtures.load("sample.pcl"), SpoolMetadata.none());

        assertThat(created).hasSize(1);
        Capture c = created.get(0);
        assertThat(c.getRenderStatus()).isEqualTo(RenderStatus.FAILED);
        assertThat(c.getRenderError()).isEqualTo("boom");
        assertThat(c.getRenderedStorageKey()).isNull();
        assertThat(store.bytes(c.getStorageKey())).isEqualTo(Fixtures.load("sample.pcl"));
    }

    @Test
    void textIsConvertedToPdfAndRenderIsSkipped() {
        List<Capture> created = service.capture(tenantId, queueId,
                Fixtures.load("sample.txt"), SpoolMetadata.none());

        assertThat(created).hasSize(1);
        Capture c = created.get(0);
        assertThat(c.getDetectedFormat()).isEqualTo(DetectedFormat.TEXT);
        assertThat(c.getRenderStatus()).isEqualTo(RenderStatus.SKIPPED);
        assertThat(c.getStorageKey()).endsWith(".pdf");
        assertThat(new String(store.bytes(c.getStorageKey()), 0, 5)).isEqualTo("%PDF-");
        // sha256 is over the ORIGINAL text bytes, not the converted PDF
        assertThat(c.getByteSize()).isEqualTo(Fixtures.load("sample.txt").length);
    }

    @Test
    void duplicateSegmentsAreSkipped() {
        when(captureRepo.existsByTenantIdAndSha256AndLogicalSegmentIndex(
                any(), anyString(), anyInt())).thenReturn(true);

        List<Capture> created = service.capture(tenantId, queueId,
                Fixtures.load("sample.txt"), SpoolMetadata.none());

        assertThat(created).isEmpty();
        assertThat(store.size()).isZero();
        assertThat(auditEvents).isEmpty();
    }

    @Test
    void unknownBytesLandAsBin() {
        List<Capture> created = service.capture(tenantId, queueId,
                Fixtures.load("binary.bin"), SpoolMetadata.none());

        assertThat(created).hasSize(1);
        assertThat(created.get(0).getDetectedFormat()).isEqualTo(DetectedFormat.UNKNOWN);
        assertThat(created.get(0).getStorageKey()).endsWith(".bin");
    }

    /** Minimal in-memory ObjectStore double. */
    private static final class InMemoryObjectStore implements ObjectStore {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public void put(String key, byte[] bytes, String contentType) {
            objects.put(key, bytes);
        }

        @Override
        public byte[] get(String key) {
            return objects.get(key);
        }

        @Override
        public boolean exists(String key) {
            return objects.containsKey(key);
        }

        byte[] bytes(String key) {
            return objects.get(key);
        }

        int size() {
            return objects.size();
        }
    }
}
