package io.retrospool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.ExportDestination;
import io.retrospool.persistence.ExportDestinationRepository;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantOutputQueueRepository;
import io.retrospool.persistence.TenantRepository;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant list and per-tenant detail (Connection / Queues / Destinations / Captures / Audit),
 * plus export-destination CRUD (Phase 6/7). Every destination write is scoped by the path's
 * {@code tenantId} via {@link ExportDestinationService}, which follows the same
 * find-by-tenant-and-id-or-404 shape {@link CaptureAdminController} already uses (D-008).
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantAdminController {

    private static final int RECENT_LIMIT = 50;

    private final TenantRepository tenants;
    private final TenantOutputQueueRepository queues;
    private final ExportDestinationRepository destinations;
    private final CaptureRepository captures;
    private final AuditEventRepository audit;
    private final ExportDestinationService destinationService;
    private final ObjectMapper mapper;

    public TenantAdminController(
            TenantRepository tenants,
            TenantOutputQueueRepository queues,
            ExportDestinationRepository destinations,
            CaptureRepository captures,
            AuditEventRepository audit,
            ExportDestinationService destinationService,
            ObjectMapper mapper) {
        this.tenants = tenants;
        this.queues = queues;
        this.destinations = destinations;
        this.captures = captures;
        this.audit = audit;
        this.destinationService = destinationService;
        this.mapper = mapper;
    }

    @GetMapping
    public List<AdminDtos.TenantSummary> list() {
        return tenants.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
                .map(t -> new AdminDtos.TenantSummary(
                        t.getId(),
                        t.getName(),
                        t.getHost(),
                        t.getUsername(),
                        t.isUseSsl(),
                        t.getRetentionPolicy().name(),
                        t.getPollIntervalSeconds(),
                        t.getCreatedAt(),
                        queues.findByTenantId(t.getId()).size(),
                        destinations.findByTenantId(t.getId()).size(),
                        captures.findByTenantIdOrderByCapturedAtDesc(t.getId()).size()))
                .toList();
    }

    @GetMapping("/{id}")
    public AdminDtos.TenantDetail detail(@PathVariable UUID id) {
        Tenant t = tenants.findById(id).orElseThrow(() -> new NoSuchElementException("tenant not found: " + id));

        List<AdminDtos.OutputQueueView> queueViews = queues.findByTenantId(id).stream()
                .map(AdminDtos.OutputQueueView::of)
                .toList();

        List<AdminDtos.ExportDestinationView> destinationViews = destinations.findByTenantId(id).stream()
                .map(d -> AdminDtos.ExportDestinationView.of(d, JsonUtil.parse(mapper, d.getConfig())))
                .toList();

        List<AdminDtos.CaptureView> captureViews = captures.findByTenantIdOrderByCapturedAtDesc(id).stream()
                .limit(RECENT_LIMIT)
                .map(AdminDtos.CaptureView::of)
                .toList();

        List<AdminDtos.AuditEventView> auditViews = audit.findByTenantIdOrderByCreatedAtDesc(id).stream()
                .limit(RECENT_LIMIT)
                .map(e -> AdminDtos.auditOf(e, JsonUtil.parse(mapper, e.getPayload())))
                .toList();

        return AdminDtos.TenantDetail.of(t, queueViews, destinationViews, captureViews, auditViews);
    }

    @PostMapping("/{tenantId}/destinations")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminDtos.ExportDestinationView createDestination(
            @PathVariable UUID tenantId,
            @Valid @RequestBody AdminDtos.CreateDestinationRequest request,
            Authentication authentication) {
        ExportDestination destination = destinationService.create(tenantId, request, JsonUtil.actor(authentication));
        return AdminDtos.ExportDestinationView.of(destination, JsonUtil.parse(mapper, destination.getConfig()));
    }

    @PutMapping("/{tenantId}/destinations/{id}")
    public AdminDtos.ExportDestinationView updateDestination(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @RequestBody AdminDtos.UpdateDestinationRequest request,
            Authentication authentication) {
        ExportDestination destination =
                destinationService.update(tenantId, id, request, JsonUtil.actor(authentication));
        return AdminDtos.ExportDestinationView.of(destination, JsonUtil.parse(mapper, destination.getConfig()));
    }

    @PatchMapping("/{tenantId}/destinations/{id}/enabled")
    public AdminDtos.ExportDestinationView setDestinationEnabled(
            @PathVariable UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody AdminDtos.EnabledRequest request,
            Authentication authentication) {
        ExportDestination destination =
                destinationService.setEnabled(tenantId, id, request.enabled(), JsonUtil.actor(authentication));
        return AdminDtos.ExportDestinationView.of(destination, JsonUtil.parse(mapper, destination.getConfig()));
    }

    @DeleteMapping("/{tenantId}/destinations/{id}")
    public ResponseEntity<Void> deleteDestination(
            @PathVariable UUID tenantId, @PathVariable UUID id, Authentication authentication) {
        destinationService.delete(tenantId, id, JsonUtil.actor(authentication));
        return ResponseEntity.noContent().build();
    }
}
