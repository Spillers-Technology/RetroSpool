package io.retrospool.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.persistence.SubmissionStatus;
import io.retrospool.persistence.Tenant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin review of landing submissions (D-007). Read the queue, then approve
 * (promote to tenant) or reject. Approval is the mandatory human gate.
 */
@RestController
@RequestMapping("/api/submissions")
public class SubmissionAdminController {

    private final SubmissionRepository submissions;
    private final SubmissionApprovalService approval;
    private final ObjectMapper mapper;

    public SubmissionAdminController(
            SubmissionRepository submissions, SubmissionApprovalService approval, ObjectMapper mapper) {
        this.submissions = submissions;
        this.approval = approval;
        this.mapper = mapper;
    }

    @GetMapping
    public List<AdminDtos.SubmissionView> list(@RequestParam(required = false) String status) {
        List<Submission> found = (status == null || status.isBlank())
                ? submissions.findAll()
                : submissions.findByStatusOrderBySubmittedAtAsc(SubmissionStatus.valueOf(status.toUpperCase()));
        return found.stream()
                .sorted(Comparator.comparing(Submission::getSubmittedAt).reversed())
                .map(s -> AdminDtos.SubmissionView.of(s, JsonUtil.parse(mapper, s.getParsedDraft())))
                .toList();
    }

    @GetMapping("/{id}")
    public AdminDtos.SubmissionView get(@PathVariable UUID id) {
        Submission s = submissions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("submission not found: " + id));
        return AdminDtos.SubmissionView.of(s, JsonUtil.parse(mapper, s.getParsedDraft()));
    }

    @PostMapping("/{id}/approve")
    public AdminDtos.TenantSummary approve(@PathVariable UUID id, Authentication authentication) {
        Tenant tenant = approval.approve(id, JsonUtil.actor(authentication));
        return new AdminDtos.TenantSummary(
                tenant.getId(),
                tenant.getName(),
                tenant.getHost(),
                tenant.getUsername(),
                tenant.isUseSsl(),
                tenant.getRetentionPolicy().name(),
                tenant.getPollIntervalSeconds(),
                tenant.getCreatedAt(),
                0, 0, 0);
    }

    @PostMapping("/{id}/reject")
    public AdminDtos.SubmissionView reject(@PathVariable UUID id, Authentication authentication) {
        approval.reject(id, JsonUtil.actor(authentication));
        Submission s = submissions.findById(id)
                .orElseThrow(() -> new NoSuchElementException("submission not found: " + id));
        return AdminDtos.SubmissionView.of(s, JsonUtil.parse(mapper, s.getParsedDraft()));
    }
}
