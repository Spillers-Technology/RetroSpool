package io.retrospool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.retrospool.persistence.AuditEvent;
import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.DestinationType;
import io.retrospool.persistence.ExportAttemptRepository;
import io.retrospool.persistence.ExportDestination;
import io.retrospool.persistence.ExportDestinationRepository;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantRepository;
import io.retrospool.secrets.SecretStorageUnavailableException;
import io.retrospool.secrets.SecretWriter;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Covers the export-destination CRUD service: config-shape validation, write-only
 * secret handling (D-012/D-023), and cross-tenant isolation (D-008) — the same shape
 * as {@link SubmissionApprovalServiceTest}, mocking every repository dependency.
 */
@ExtendWith(MockitoExtension.class)
class ExportDestinationServiceTest {

    @Mock
    private ExportDestinationRepository destinations;

    @Mock
    private ExportAttemptRepository attempts;

    @Mock
    private TenantRepository tenants;

    @Mock
    private AuditEventRepository audit;

    @Mock
    private SecretWriter secretWriter;

    private ObjectMapper mapper;
    private ExportDestinationService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        service = new ExportDestinationService(destinations, attempts, tenants, audit, secretWriter, mapper);
        tenantId = UUID.randomUUID();
        when(destinations.save(any(ExportDestination.class))).thenAnswer(inv -> inv.getArgument(0));
        when(audit.save(any(AuditEvent.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- create -------------------------------------------------------------------

    @Test
    void createsS3DestinationWithoutPassword() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        JsonNode config = mapper.readTree("""
                {"bucket":"acme-reports","region":"us-east-1"}
                """);

        ExportDestination destination = service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("s3", "Nightly S3", config, null), "reviewer");

        assertThat(destination.getTenantId()).isEqualTo(tenantId);
        assertThat(destination.getType()).isEqualTo(DestinationType.S3);
        assertThat(destination.getName()).isEqualTo("Nightly S3");
        assertThat(destination.getSecretRef()).isNull();
        assertThat(mapper.readTree(destination.getConfig()).get("bucket").asText()).isEqualTo("acme-reports");
        verify(secretWriter, never()).write(any());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).save(events.capture());
        assertThat(events.getValue().getEventType()).isEqualTo("destination.created");
        assertThat(events.getValue().getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void createsSftpDestinationAndWritesPasswordThroughSecretWriterOnly() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(secretWriter.isEnabled()).thenReturn(true);
        AtomicReference<String> valueSeenByWriter = new AtomicReference<>();
        when(secretWriter.write(any())).thenAnswer(invocation -> {
            valueSeenByWriter.set(new String(invocation.getArgument(0, char[].class)));
            return "db:11111111-1111-1111-1111-111111111111";
        });
        JsonNode config = mapper.readTree("""
                {"host":"sftp.example.com","port":22,"username":"svc",
                 "remote_path":"/in/reports","host_key_fingerprint":"SHA256:abc"}
                """);

        ExportDestination destination = service.create(
                tenantId,
                new AdminDtos.CreateDestinationRequest("SFTP", "Downstream SFTP", config, "hunter2"),
                "reviewer");

        assertThat(destination.getSecretRef()).isEqualTo("db:11111111-1111-1111-1111-111111111111");
        assertThat(destination.getConfig()).doesNotContain("hunter2");

        ArgumentCaptor<char[]> secretCaptor = ArgumentCaptor.forClass(char[].class);
        verify(secretWriter).write(secretCaptor.capture());
        assertThat(valueSeenByWriter.get()).isEqualTo("hunter2");
        assertThat(secretCaptor.getValue()).containsOnly('\0');
    }

    @Test
    void createRejectsUnknownTenant() {
        when(tenants.findById(tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                tenantId,
                new AdminDtos.CreateDestinationRequest("S3", "x", mapper.createObjectNode(), null),
                "reviewer"))
                .isInstanceOf(NoSuchElementException.class);

        verify(destinations, never()).save(any());
    }

    @Test
    void createRejectsS3ConfigShapeOnSftpType() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        // Otherwise-complete SFTP config, but carrying an S3-only field (bucket) — must
        // be rejected as a cross-type mismatch, not silently accepted.
        JsonNode config = mapper.readTree("""
                {"host":"sftp.example.com","username":"svc","remote_path":"/in","bucket":"acme-reports"}
                """);

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("SFTP", "Bad", config, null), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid for type SFTP");

        verify(destinations, never()).save(any());
    }

    @Test
    void createRejectsLegacyCamelCaseWherePersistedConfigRequiresCanonicalKeys() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        JsonNode config = mapper.readTree("""
                {"host":"sftp.example.com","username":"svc","remotePath":"/in"}
                """);

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("SFTP", "Bad", config, null), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("remote_path");

        verify(destinations, never()).save(any());
    }

    @Test
    void createRejectsMissingRequiredField() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        JsonNode config = mapper.readTree("""
                {"bucket":"acme-reports"}
                """); // missing region

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("S3", "Bad", config, null), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing required field");
    }

    @Test
    void createFailsClearlyWhenSecretStorageIsDisabled() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(secretWriter.isEnabled()).thenReturn(false);
        JsonNode config = mapper.readTree("""
                {"host":"sftp.example.com","username":"svc","remote_path":"/in"}
                """);

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("SFTP", "x", config, "pw"), "reviewer"))
                .isInstanceOf(SecretStorageUnavailableException.class);

        verify(destinations, never()).save(any());
    }

    @Test
    void createRejectsPasswordForS3() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        JsonNode config = mapper.readTree("""
                {"bucket":"acme-reports","region":"us-east-1"}
                """);

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("S3", "S3", config, "not-applicable"), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only valid for SFTP or FTPS");

        verify(secretWriter, never()).write(any());
        verify(destinations, never()).save(any());
    }

    @Test
    void createRejectsWrongConfigValueTypesAndOutOfRangePorts() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        JsonNode badBoolean = mapper.readTree("""
                {"bucket":"acme-reports","region":"us-east-1","pathStyleAccess":"yes"}
                """);
        JsonNode badPort = mapper.readTree("""
                {"host":"sftp.example.com","username":"svc","remote_path":"/in","port":70000}
                """);

        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("S3", "S3", badBoolean, null), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pathStyleAccess must be a boolean");
        assertThatThrownBy(() -> service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("SFTP", "SFTP", badPort, null), "reviewer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port must be an integer from 1 to 65535");
    }

    // --- update -----------------------------------------------------------------

    @Test
    void updateRenamesRotatesConfigAndTogglesEnabled() throws Exception {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "Old name",
                "{\"bucket\":\"old\",\"region\":\"us-east-1\"}");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        JsonNode newConfig = mapper.readTree("""
                {"bucket":"new-bucket","region":"us-west-2"}
                """);

        ExportDestination updated = service.update(
                tenantId, existing.getId(),
                new AdminDtos.UpdateDestinationRequest("New name", newConfig, false, null),
                "reviewer");

        assertThat(updated.getName()).isEqualTo("New name");
        assertThat(updated.isEnabled()).isFalse();
        assertThat(mapper.readTree(updated.getConfig()).get("bucket").asText()).isEqualTo("new-bucket");
    }

    @Test
    void updateRotatesPasswordByOverwritingSecretRef() throws Exception {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.SFTP, "SFTP",
                "{\"host\":\"h\",\"username\":\"u\",\"remote_path\":\"/p\"}");
        existing.setSecretRef("db:old-ref");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(secretWriter.isEnabled()).thenReturn(true);
        when(secretWriter.write(any())).thenReturn("db:new-ref");

        ExportDestination updated = service.update(
                tenantId, existing.getId(),
                new AdminDtos.UpdateDestinationRequest(null, null, null, "new-password"),
                "reviewer");

        assertThat(updated.getSecretRef()).isEqualTo("db:new-ref");
    }

    @Test
    void updateRejectsCrossTenantId() {
        UUID otherTenantId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(destinations.findByTenantIdAndId(otherTenantId, destinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(
                otherTenantId, destinationId,
                new AdminDtos.UpdateDestinationRequest("x", null, null, null),
                "reviewer"))
                .isInstanceOf(NoSuchElementException.class);

        verify(destinations, never()).save(any());
        verify(destinations).findByTenantIdAndId(otherTenantId, destinationId);
    }

    // --- enable/disable -----------------------------------------------------------

    @Test
    void setEnabledTogglesFlagAndAudits() {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "S3",
                "{\"bucket\":\"b\",\"region\":\"r\"}");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));

        ExportDestination result = service.setEnabled(tenantId, existing.getId(), false, "reviewer");

        assertThat(result.isEnabled()).isFalse();
        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit).save(events.capture());
        assertThat(events.getValue().getEventType()).isEqualTo("destination.disabled");
    }

    // --- delete ---------------------------------------------------------------------

    @Test
    void deleteHardDeletesWhenNoAttemptHistory() {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "S3",
                "{\"bucket\":\"b\",\"region\":\"r\"}");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(attempts.existsByExportDestinationId(existing.getId())).thenReturn(false);

        service.delete(tenantId, existing.getId(), "reviewer");

        verify(destinations).delete(existing);
        verify(destinations).flush();
        verify(audit).save(any(AuditEvent.class));
    }

    @Test
    void deleteTurnsDatabaseHistoryRaceIntoConflict() {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "S3",
                "{\"bucket\":\"b\",\"region\":\"r\"}");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(attempts.existsByExportDestinationId(existing.getId())).thenReturn(false);
        doThrow(new DataIntegrityViolationException("restricted by export attempt"))
                .when(destinations).flush();

        assertThatThrownBy(() -> service.delete(tenantId, existing.getId(), "reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disable it instead");

        verify(audit, never()).save(any());
    }

    @Test
    void deleteBlockedWhenAttemptHistoryExists() {
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "S3",
                "{\"bucket\":\"b\",\"region\":\"r\"}");
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(attempts.existsByExportDestinationId(existing.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.delete(tenantId, existing.getId(), "reviewer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disable it instead");

        verify(destinations, never()).delete(any());
    }

    @Test
    void deleteRejectsCrossTenantId() {
        UUID otherTenantId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();
        when(destinations.findByTenantIdAndId(otherTenantId, destinationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(otherTenantId, destinationId, "reviewer"))
                .isInstanceOf(NoSuchElementException.class);

        verify(destinations, never()).delete(any());
        verify(attempts, never()).existsByExportDestinationId(any());
    }

    // --- response shape never carries the secret -----------------------------------

    @Test
    void viewDtoNeverExposesSecretRefOrPlaintext() throws Exception {
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(secretWriter.isEnabled()).thenReturn(true);
        when(secretWriter.write(any())).thenReturn("db:22222222-2222-2222-2222-222222222222");
        JsonNode config = mapper.readTree("""
                {"host":"sftp.example.com","username":"svc","remote_path":"/in"}
                """);

        ExportDestination destination = service.create(
                tenantId, new AdminDtos.CreateDestinationRequest("SFTP", "SFTP", config, "s3cr3t!"), "reviewer");
        AdminDtos.ExportDestinationView view =
                AdminDtos.ExportDestinationView.of(destination, mapper.readTree(destination.getConfig()));

        String serialized = mapper.writeValueAsString(view);
        assertThat(view.secretSet()).isTrue();
        assertThat(serialized).doesNotContain("s3cr3t!").doesNotContain("secretRef").doesNotContain("22222222");
    }
}
