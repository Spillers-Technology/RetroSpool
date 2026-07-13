package io.retrospool.web;

import io.retrospool.persistence.Capture;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.storage.ObjectStore;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Per-tenant captures list and artifact download. Every read is tenant-scoped
 * (D-008): the {@code tenantId} is part of the path and every repository lookup
 * carries it, so a capture id alone can never cross a company boundary.
 */
@RestController
@RequestMapping("/api")
public class CaptureAdminController {

    private final CaptureRepository captures;
    private final ObjectStore store;

    public CaptureAdminController(CaptureRepository captures, ObjectStore store) {
        this.captures = captures;
        this.store = store;
    }

    @GetMapping("/tenants/{tenantId}/captures")
    public List<AdminDtos.CaptureView> list(@PathVariable UUID tenantId) {
        return captures.findByTenantIdOrderByCapturedAtDesc(tenantId).stream()
                .map(AdminDtos.CaptureView::of)
                .toList();
    }

    @GetMapping("/tenants/{tenantId}/captures/{captureId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable UUID tenantId,
            @PathVariable UUID captureId,
            @RequestParam(defaultValue = "original") String variant) {

        Capture capture = captures.findByTenantIdAndId(tenantId, captureId)
                .orElseThrow(() -> new NoSuchElementException("capture not found: " + captureId));

        boolean wantPdf = "pdf".equalsIgnoreCase(variant);
        String key = wantPdf ? capture.getRenderedStorageKey() : capture.getStorageKey();
        if (!StringUtils.hasText(key)) {
            throw new NoSuchElementException("no " + (wantPdf ? "rendered PDF" : "artifact") + " for capture " + captureId);
        }
        if (!store.exists(key)) {
            throw new NoSuchElementException("artifact bytes missing from object store: " + key);
        }

        byte[] bytes = store.get(key);
        MediaType contentType = contentTypeFor(key);
        String filename = filenameFor(capture, key);

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(filename).build().toString())
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private static MediaType contentTypeFor(String key) {
        String lower = key.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return MediaType.APPLICATION_PDF;
        }
        if (lower.endsWith(".txt")) {
            return MediaType.TEXT_PLAIN;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static String filenameFor(Capture capture, String key) {
        String ext = key.contains(".") ? key.substring(key.lastIndexOf('.')) : "";
        String base = StringUtils.hasText(capture.getSpoolFileName())
                ? capture.getSpoolFileName().replaceAll("[^A-Za-z0-9._-]", "_")
                : "capture-" + capture.getId();
        return base.toLowerCase().endsWith(ext.toLowerCase()) ? base : base + ext;
    }
}
