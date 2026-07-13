package io.retrospool.web;

import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.persistence.SubmissionStatus;
import io.retrospool.persistence.TenantOutputQueueRepository;
import io.retrospool.persistence.TenantRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard headline counts. */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final TenantRepository tenants;
    private final SubmissionRepository submissions;
    private final CaptureRepository captures;
    private final TenantOutputQueueRepository queues;

    public StatsController(
            TenantRepository tenants,
            SubmissionRepository submissions,
            CaptureRepository captures,
            TenantOutputQueueRepository queues) {
        this.tenants = tenants;
        this.submissions = submissions;
        this.captures = captures;
        this.queues = queues;
    }

    @GetMapping
    public AdminDtos.Stats stats() {
        return new AdminDtos.Stats(
                tenants.count(),
                submissions.findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING).size(),
                captures.count(),
                queues.count());
    }
}
