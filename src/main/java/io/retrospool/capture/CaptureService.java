package io.retrospool.capture;

import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Capture;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.DetectedFormat;
import io.retrospool.storage.ObjectStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Capture pipeline orchestration (docs/architecture.md): sniff -> split (PCL) ->
 * convert -> store -> render (PCL, via sidecar; D-018) -> persist with within-tenant
 * dedup (D-008). Duplicate segments are skipped, never errors — the DB constraint
 * unique(tenant_id, sha256, logical_segment_index) is the authority; the exists()
 * pre-check is only an optimization. A render failure never fails the capture.
 */
@Service
public class CaptureService {

    private static final Logger log = LoggerFactory.getLogger(CaptureService.class);
    private static final DateTimeFormatter KEY_DATE =
            DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneOffset.UTC);

    private final FormatSniffer sniffer;
    private final PclSplitter splitter;
    private final ConverterDispatcher dispatcher;
    private final PclRenderClient renderClient;
    private final ObjectStore objectStore;
    private final CaptureRepository captures;
    private final AuditEventRepository auditEvents;
    private final ObjectMapper objectMapper;

    public CaptureService(FormatSniffer sniffer, PclSplitter splitter,
                          ConverterDispatcher dispatcher, PclRenderClient renderClient,
                          ObjectStore objectStore, CaptureRepository captures,
                          AuditEventRepository auditEvents, ObjectMapper objectMapper) {
        this.sniffer = sniffer;
        this.splitter = splitter;
        this.dispatcher = dispatcher;
        this.renderClient = renderClient;
        this.objectStore = objectStore;
        this.captures = captures;
        this.auditEvents = auditEvents;
        this.objectMapper = objectMapper;
    }

    /** @return the captures created by this call (duplicates are skipped, not returned). */
    public List<Capture> capture(UUID tenantId, UUID outputQueueId, byte[] payload,
                                 SpoolMetadata meta) {
        DetectedFormat format = sniffer.sniff(payload);
        List<byte[]> segments = format == DetectedFormat.PCL
                ? splitter.split(payload)
                : List.of(payload);

        List<Capture> created = new ArrayList<>();
        for (int index = 0; index < segments.size(); index++) {
            byte[] segment = segments.get(index);
            String sha256 = sha256Hex(segment);
            if (captures.existsByTenantIdAndSha256AndLogicalSegmentIndex(tenantId, sha256, index)) {
                log.debug("Skipping duplicate segment tenant={} sha256={} index={}",
                        tenantId, sha256, index);
                continue;
            }
            Capture capture = storeSegment(tenantId, outputQueueId, format, index,
                    segment, sha256, meta);
            if (capture != null) {
                created.add(capture);
            }
        }
        return created;
    }

    private Capture storeSegment(UUID tenantId, UUID outputQueueId, DetectedFormat format,
                                 int index, byte[] segment, String sha256, SpoolMetadata meta) {
        ConversionResult converted = dispatcher.convert(format, segment);
        Capture capture = new Capture(tenantId, outputQueueId, format, index, sha256,
                segment.length);
        capture.assignStorageKey(
                storageKey(tenantId, capture.getId(), index, converted.extension()));
        applyMetadata(capture, meta);

        objectStore.put(capture.getStorageKey(), converted.bytes(),
                contentType(converted.extension()));

        if (format == DetectedFormat.PCL) {
            RenderResult render = renderClient.render(segment);
            if (render.success()) {
                String pdfKey = capture.getStorageKey() + ".pdf";
                objectStore.put(pdfKey, render.pdf(), "application/pdf");
                capture.renderSucceeded(pdfKey);
            } else {
                capture.renderFailed(render.error());
                log.info("PCL render failed for tenant={} sha256={} index={}: {}",
                        tenantId, sha256, index, render.error());
            }
        }

        try {
            capture = captures.save(capture);
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another insert of the same (tenant, sha256, index):
            // the constraint is the dedup authority — treat as duplicate, not failure.
            log.debug("Duplicate segment (constraint) tenant={} sha256={} index={}",
                    tenantId, sha256, index);
            return null;
        }
        audit(capture);
        return capture;
    }

    private void applyMetadata(Capture capture, SpoolMetadata meta) {
        if (meta == null) {
            return;
        }
        capture.setSpoolFileName(meta.fileName());
        capture.setSpoolFileNumber(meta.fileNumber());
        capture.setSpoolJobName(meta.jobName());
        capture.setSpoolJobUser(meta.jobUser());
        capture.setSpoolJobNumber(meta.jobNumber());
        capture.setCreatedAt(meta.hostCreatedAt());
    }

    private void audit(Capture capture) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("captureId", capture.getId().toString());
            payload.put("format", capture.getDetectedFormat().name());
            payload.put("segmentIndex", capture.getLogicalSegmentIndex());
            payload.put("byteSize", capture.getByteSize());
            payload.put("storageKey", capture.getStorageKey());
            payload.put("renderStatus", capture.getRenderStatus().name());
            auditEvents.save(new AuditEvent(capture.getTenantId(), "CAPTURE_CREATED",
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            // Audit must never break capture; the capture row itself is the record.
            log.warn("Failed to write audit event for capture {}", capture.getId(), e);
        }
    }

    /** D-016: {tenantId}/{yyyy}/{MM}/{dd}/{captureId}-{segmentIndex}.{ext} (UTC). */
    private static String storageKey(UUID tenantId, UUID captureId, int index, String ext) {
        return tenantId + "/" + KEY_DATE.format(Instant.now()) + "/" + captureId
                + "-" + index + "." + ext;
    }

    private static String contentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "pcl" -> "application/vnd.hp-pcl";
            default -> "application/octet-stream";
        };
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
