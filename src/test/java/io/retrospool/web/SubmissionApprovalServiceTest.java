package io.retrospool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.persistence.SubmissionStatus;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class SubmissionApprovalServiceTest {

    @Mock
    private SubmissionRepository submissions;

    @Mock
    private TenantRepository tenants;

    @Mock
    private AuditEventRepository audit;

    private ObjectMapper mapper;
    private SubmissionApprovalService service;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new SubmissionApprovalService(submissions, tenants, audit, mapper);
    }

    @Test
    void approvePromotesDraftCarriesSecretAndWritesTenantAudit() throws Exception {
        Submission submission = new Submission("""
                {"host":"pub400.com","user":"QUSER","sessionName":"Acme Reports",
                 "use_ssl":false,"deviceName":"PRT01"}
                """);
        submission.setIbmiPasswordRef("env:ACME_IBMI_PASSWORD");
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(audit.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Tenant tenant = service.approve(submission.getId(), "reviewer@example.com");

        verify(submissions).findByIdForUpdate(submission.getId());
        assertThat(tenant.getName()).isEqualTo("Acme Reports");
        assertThat(tenant.getHost()).isEqualTo("pub400.com");
        assertThat(tenant.getUsername()).isEqualTo("QUSER");
        assertThat(tenant.isUseSsl()).isFalse();
        assertThat(tenant.getPrinterDeviceName()).isEqualTo("PRT01");
        assertThat(tenant.getSecretRef()).isEqualTo("env:ACME_IBMI_PASSWORD");
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.APPROVED);
        assertThat(submission.getReviewedBy()).isEqualTo("reviewer@example.com");
        assertThat(submission.getReviewedAt()).isNotNull();
        assertThat(submission.getResultingTenantId()).isEqualTo(tenant.getId());

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getTenantId()).isEqualTo(tenant.getId());
        assertThat(event.getEventType()).isEqualTo("submission.approved");
        assertThat(mapper.readTree(event.getPayload()).get("submissionId").asText())
                .isEqualTo(submission.getId().toString());
        assertThat(mapper.readTree(event.getPayload()).get("reviewedBy").asText())
                .isEqualTo("reviewer@example.com");
    }

    @Test
    void rejectDoesNotCreateTenantAndWritesSystemAudit() throws Exception {
        Submission submission = new Submission("{\"host\":\"pub400.com\",\"username\":\"QUSER\"}");
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(audit.save(any(AuditEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reject(submission.getId(), "reviewer@example.com");

        verify(submissions).findByIdForUpdate(submission.getId());
        assertThat(submission.getStatus()).isEqualTo(SubmissionStatus.REJECTED);
        assertThat(submission.getReviewedBy()).isEqualTo("reviewer@example.com");
        assertThat(submission.getReviewedAt()).isNotNull();
        assertThat(submission.getResultingTenantId()).isNull();
        verify(tenants, never()).save(any());

        ArgumentCaptor<AuditEvent> eventCaptor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).save(eventCaptor.capture());
        AuditEvent event = eventCaptor.getValue();
        assertThat(event.getTenantId()).isNull();
        assertThat(event.getEventType()).isEqualTo("submission.rejected");
        assertThat(mapper.readTree(event.getPayload()).get("submissionId").asText())
                .isEqualTo(submission.getId().toString());
    }

    @Test
    void reviewedSubmissionCannotBeApprovedAgain() {
        Submission submission = new Submission("{\"host\":\"pub400.com\",\"username\":\"QUSER\"}");
        submission.reject("first-reviewer");
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.approve(submission.getId(), "second-reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not PENDING");

        verify(tenants, never()).save(any());
        verify(audit, never()).save(any());
    }

    @Test
    void malformedDraftNeverCreatesTenant() {
        Submission submission = new Submission("not-json");
        when(submissions.findByIdForUpdate(submission.getId())).thenReturn(Optional.of(submission));

        assertThatThrownBy(() -> service.approve(submission.getId(), "reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid JSON");

        verify(tenants, never()).save(any());
        verify(audit, never()).save(any());
    }
}
