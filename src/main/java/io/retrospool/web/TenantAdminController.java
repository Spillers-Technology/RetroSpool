package io.retrospool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.ExportDestinationRepository;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantOutputQueueRepository;
import io.retrospool.persistence.TenantRepository;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tenant list and per-tenant detail (Connection / Queues / Destinations / Captures / Audit). */
@RestController
@RequestMapping("/api/tenants")
public class TenantAdminController {

    private static final int RECENT_LIMIT = 50;

    private final TenantRepository tenants;
    private final TenantOutputQueueRepository queues;
    private final ExportDestinationRepository destinations;
    private final CaptureRepository captures;
    private final AuditEventRepository audit;
    private final ObjectMapper mapper;

    public TenantAdminController(
            TenantRepository tenants,
            TenantOutputQueueRepository queues,
            ExportDestinationRepository destinations,
            CaptureRepository captures,
            AuditEventRepository audit,
            ObjectMapper mapper) {
        this.tenants = tenants;
        this.queues = queues;
        this.destinations = destinations;
        this.captures = captures;
        this.audit = audit;
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
}
