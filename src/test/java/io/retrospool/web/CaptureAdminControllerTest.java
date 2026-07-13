package io.retrospool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.retrospool.persistence.Capture;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.DetectedFormat;
import io.retrospool.storage.ObjectStore;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CaptureAdminControllerTest {

    @Mock
    private CaptureRepository captures;

    @Mock
    private ObjectStore store;

    private CaptureAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new CaptureAdminController(captures, store);
    }

    @Test
    void downloadLooksUpCaptureByTenantAndCaptureIdsTogether() {
        UUID requestedTenant = UUID.randomUUID();
        UUID captureId = UUID.randomUUID();
        when(captures.findByTenantIdAndId(requestedTenant, captureId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.download(requestedTenant, captureId, "original"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("capture not found");

        verify(captures).findByTenantIdAndId(requestedTenant, captureId);
        verify(store, never()).exists(org.mockito.ArgumentMatchers.anyString());
        verify(store, never()).get(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void originalDownloadReturnsStoredBytesAndSafeFilename() throws Exception {
        UUID tenantId = UUID.randomUUID();
        Capture capture = capture(tenantId, "tenant/original/report.pdf");
        capture.setSpoolFileName("Quarterly Report");
        byte[] bytes = "%PDF-test".getBytes(StandardCharsets.US_ASCII);
        when(captures.findByTenantIdAndId(tenantId, capture.getId())).thenReturn(Optional.of(capture));
        when(store.exists(capture.getStorageKey())).thenReturn(true);
        when(store.get(capture.getStorageKey())).thenReturn(bytes);

        ResponseEntity<Resource> response = controller.download(tenantId, capture.getId(), "original");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getContentDisposition().getFilename()).isEqualTo("Quarterly_Report.pdf");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void pdfVariantCannotFallBackToOriginalArtifact() {
        UUID tenantId = UUID.randomUUID();
        Capture capture = capture(tenantId, "tenant/original/report.pcl");
        when(captures.findByTenantIdAndId(tenantId, capture.getId())).thenReturn(Optional.of(capture));

        assertThatThrownBy(() -> controller.download(tenantId, capture.getId(), "pdf"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("no rendered PDF");

        verify(store, never()).exists(org.mockito.ArgumentMatchers.anyString());
    }

    private static Capture capture(UUID tenantId, String storageKey) {
        Capture capture = new Capture(
                tenantId, UUID.randomUUID(), DetectedFormat.PDF, 0, "abc123", 9);
        capture.assignStorageKey(storageKey);
        return capture;
    }
}
