package io.retrospool.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.api.IntakeDtos.DraftInput;
import io.retrospool.api.IntakeDtos.SftpDestinationInput;
import io.retrospool.api.IntakeDtos.SubmissionCreatedResponse;
import io.retrospool.api.IntakeDtos.SubmissionRequest;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.Submission;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.secrets.SecretStorageUnavailableException;
import io.retrospool.secrets.SecretWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmissionIntakeServiceTest {

    @Mock
    private SubmissionRepository submissions;

    @Mock
    private AuditEventRepository audit;

    @Mock
    private SecretWriter secretWriter;

    private SubmissionIntakeService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new SubmissionIntakeService(submissions, audit, secretWriter, mapper);
    }

    private static DraftInput draft() {
        return new DraftInput("ibmi.example.com", 992, true, "RPTUSER",
                "Payroll", "PRT01", 37, "5250 Printer");
    }

    @Test
    void createsPendingSubmissionWithDraftJsonAndAudits() throws Exception {
        SubmissionCreatedResponse response =
                service.create(new SubmissionRequest(draft(), null, null));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.ibmiPasswordStored()).isFalse();
        assertThat(response.sftpDestinationConfigured()).isFalse();

        Submission saved = capturePersisted();
        JsonNode json = mapper.readTree(saved.getParsedDraft());
        assertThat(json.get("host").asText()).isEqualTo("ibmi.example.com");
        assertThat(json.get("username").asText()).isEqualTo("RPTUSER");
        assertThat(json.get("useSsl").asBoolean()).isTrue();
        assertThat(json.get("deviceName").asText()).isEqualTo("PRT01");
        assertThat(json.get("ccsid").asInt()).isEqualTo(37);
        assertThat(saved.getIbmiPasswordRef()).isNull();

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("submission.created");
    }

    @Test
    void storesIbmiPasswordAsWriteOnlyRefAndKeepsItOutOfTheDraft() throws Exception {
        when(secretWriter.isEnabled()).thenReturn(true);
        when(secretWriter.write(any())).thenReturn("db:11111111-1111-1111-1111-111111111111");

        SubmissionCreatedResponse response =
                service.create(new SubmissionRequest(draft(), "sup3rsecret", null));

        assertThat(response.ibmiPasswordStored()).isTrue();
        Submission saved = capturePersisted();
        assertThat(saved.getIbmiPasswordRef()).startsWith("db:");
        assertThat(saved.getParsedDraft()).doesNotContain("sup3rsecret");
    }

    @Test
    void storesSftpDestinationConfigWithoutItsPassword() throws Exception {
        when(secretWriter.isEnabled()).thenReturn(true);
        when(secretWriter.write(any())).thenReturn("db:22222222-2222-2222-2222-222222222222");

        SftpDestinationInput sftp = new SftpDestinationInput(
                "Downstream", "sftp.example.com", 22, "svc", "/in/reports", "SHA256:abc", "sftp-pass");

        SubmissionCreatedResponse response =
                service.create(new SubmissionRequest(draft(), null, sftp));

        assertThat(response.sftpDestinationConfigured()).isTrue();
        Submission saved = capturePersisted();
        assertThat(saved.getSftpPasswordRef()).startsWith("db:");

        JsonNode dest = mapper.readTree(saved.getParsedDraft()).get("sftpDestination");
        assertThat(dest.get("host").asText()).isEqualTo("sftp.example.com");
        assertThat(dest.get("remotePath").asText()).isEqualTo("/in/reports");
        assertThat(dest.has("password")).isFalse();
        assertThat(saved.getParsedDraft()).doesNotContain("sftp-pass");
    }

    @Test
    void rejectsPasswordSubmissionWhenSecretStorageDisabled() {
        when(secretWriter.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.create(new SubmissionRequest(draft(), "pw", null)))
                .isInstanceOf(SecretStorageUnavailableException.class);

        verify(submissions, never()).save(any());
    }

    @Test
    void allowsDraftOnlySubmissionWithoutSecretStorage() {
        // No password anywhere: secret storage need not be enabled.
        SubmissionCreatedResponse response =
                service.create(new SubmissionRequest(draft(), null, null));
        assertThat(response.status()).isEqualTo("PENDING");
        verify(secretWriter, never()).write(any());
    }

    private Submission capturePersisted() {
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(submissions).save(captor.capture());
        return captor.getValue();
    }
}
