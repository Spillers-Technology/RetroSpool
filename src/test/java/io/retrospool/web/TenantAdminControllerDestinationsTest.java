package io.retrospool.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.retrospool.persistence.AuditEventRepository;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.DestinationType;
import io.retrospool.persistence.ExportAttemptRepository;
import io.retrospool.persistence.ExportDestination;
import io.retrospool.persistence.ExportDestinationRepository;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantOutputQueueRepository;
import io.retrospool.persistence.TenantRepository;
import io.retrospool.secrets.SecretWriter;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * HTTP-level coverage for the export-destination CRUD endpoints: the real security
 * filter chain (auth + CSRF, D-022) plus the real {@link ExportDestinationService}
 * wired to mocked repositories, so this exercises the same tenant-isolation path
 * production traffic hits (D-008) and confirms the wire format never leaks a secret
 * (D-012/D-023).
 */
@WebMvcTest({TenantAdminController.class, AdminExceptionAdvice.class})
@Import({SecurityConfig.class, ExportDestinationService.class})
@TestPropertySource(properties = "retrospool.admin.dev-user=")
class TenantAdminControllerDestinationsTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TenantRepository tenants;

    @MockitoBean
    private TenantOutputQueueRepository queues;

    @MockitoBean
    private ExportDestinationRepository destinations;

    @MockitoBean
    private ExportAttemptRepository attempts;

    @MockitoBean
    private CaptureRepository captures;

    @MockitoBean
    private AuditEventRepository audit;

    @MockitoBean
    private SecretWriter secretWriter;

    @Test
    void createDestinationReturns201AndNeverEchoesThePassword() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(secretWriter.isEnabled()).thenReturn(true);
        when(secretWriter.write(any())).thenReturn("db:33333333-3333-3333-3333-333333333333");
        when(destinations.save(any(ExportDestination.class))).thenAnswer(inv -> inv.getArgument(0));
        Cookie csrf = bootstrapCsrf(tenantId);

        MvcResult result = mvc.perform(authenticated(post("/api/tenants/{tenantId}/destinations", tenantId))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SFTP","name":"Downstream",
                                 "config":{"host":"sftp.example.com","username":"svc","remote_path":"/in"},
                                 "password":"hunter2-supersecret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Downstream"))
                .andExpect(jsonPath("$.secretSet").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("hunter2-supersecret")
                .doesNotContain("secretRef")
                .doesNotContain("33333333");
    }

    @Test
    void createDestinationRejectsConfigShapeMismatch() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        Cookie csrf = bootstrapCsrf(tenantId);

        mvc.perform(authenticated(post("/api/tenants/{tenantId}/destinations", tenantId))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"SFTP","name":"Bad","config":{"bucket":"acme","region":"us-east-1"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }

    @Test
    void updatingAnotherTenantsDestinationReturns404NotForbidden() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantsDestinationId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(destinations.findByTenantIdAndId(tenantId, otherTenantsDestinationId)).thenReturn(Optional.empty());
        Cookie csrf = bootstrapCsrf(tenantId);

        mvc.perform(authenticated(put("/api/tenants/{tenantId}/destinations/{id}", tenantId, otherTenantsDestinationId))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    @Test
    void deletingAnotherTenantsDestinationReturns404() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantsDestinationId = UUID.randomUUID();
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(destinations.findByTenantIdAndId(tenantId, otherTenantsDestinationId)).thenReturn(Optional.empty());
        Cookie csrf = bootstrapCsrf(tenantId);

        mvc.perform(authenticated(delete("/api/tenants/{tenantId}/destinations/{id}", tenantId, otherTenantsDestinationId))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void enabledToggleRequiresCsrfLikeAnyOtherAdminMutation() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID destinationId = UUID.randomUUID();

        mvc.perform(authenticated(patch("/api/tenants/{tenantId}/destinations/{id}/enabled", tenantId, destinationId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteBlockedWhenExportAttemptHistoryExistsReturns409() throws Exception {
        UUID tenantId = UUID.randomUUID();
        ExportDestination existing = new ExportDestination(tenantId, DestinationType.S3, "S3",
                "{\"bucket\":\"b\",\"region\":\"r\"}");
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant("Acme", "pub400.com", "QUSER")));
        when(destinations.findByTenantIdAndId(tenantId, existing.getId())).thenReturn(Optional.of(existing));
        when(attempts.existsByExportDestinationId(existing.getId())).thenReturn(true);
        Cookie csrf = bootstrapCsrf(tenantId);

        mvc.perform(authenticated(delete("/api/tenants/{tenantId}/destinations/{id}", tenantId, existing.getId()))
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isConflict());
    }

    private Cookie bootstrapCsrf(UUID tenantId) throws Exception {
        MvcResult bootstrap = mvc.perform(authenticated(get("/api/tenants/{tenantId}", tenantId)))
                .andReturn();
        Cookie csrf = bootstrap.getResponse().getCookie("XSRF-TOKEN");
        org.assertj.core.api.Assertions.assertThat(csrf).isNotNull();
        return csrf;
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(AuthentikHeaderAuthenticationFilter.USERNAME_HEADER, "jsmith")
                .header(AuthentikHeaderAuthenticationFilter.EMAIL_HEADER, "jsmith@example.com");
    }
}
