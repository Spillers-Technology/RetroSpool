package io.retrospool.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.retrospool.api.TestConnectionController;
import io.retrospool.connection.ConnectionTestService;
import io.retrospool.connection.TestConnectionResult;
import io.retrospool.persistence.SubmissionRepository;
import io.retrospool.persistence.Tenant;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Exercises the real security filter chain without starting persistence or external services. */
@WebMvcTest({MeController.class, TestConnectionController.class, SubmissionAdminController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "retrospool.admin.dev-user=")
class SecurityConfigTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConnectionTestService connectionTestService;

    @MockitoBean
    private SubmissionRepository submissions;

    @MockitoBean
    private SubmissionApprovalService approval;

    @Test
    void adminApiRejectsRequestsWithoutAuthentikIdentity() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
    }

    @Test
    void authentikHeadersBecomeTheAdminPrincipal() throws Exception {
        mvc.perform(get("/api/me")
                        .header(AuthentikHeaderAuthenticationFilter.USERNAME_HEADER, "jsmith")
                        .header(AuthentikHeaderAuthenticationFilter.EMAIL_HEADER, "jsmith@example.com")
                        .header(AuthentikHeaderAuthenticationFilter.NAME_HEADER, "Jane Smith")
                        .header(AuthentikHeaderAuthenticationFilter.GROUPS_HEADER, "operators|auditors,admins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("jsmith"))
                .andExpect(jsonPath("$.email").value("jsmith@example.com"))
                .andExpect(jsonPath("$.displayName").value("Jane Smith"))
                .andExpect(jsonPath("$.groups.length()").value(3))
                .andExpect(jsonPath("$.groups[0]").value("operators"))
                .andExpect(jsonPath("$.groups[1]").value("auditors"))
                .andExpect(jsonPath("$.groups[2]").value("admins"));
    }

    @Test
    void blankAuthentikUsernameDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/me")
                        .header(AuthentikHeaderAuthenticationFilter.USERNAME_HEADER, "   ")
                        .header(AuthentikHeaderAuthenticationFilter.EMAIL_HEADER, "ignored@example.com"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void connectionTestRemainsOnThePublicSubmissionSurface() throws Exception {
        when(connectionTestService.test(any()))
                .thenReturn(TestConnectionResult.success("Signon validated", 12));

        mvc.perform(post("/api/connection/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"host":"pub400.com","username":"QUSER",
                                 "password":"secret","useSsl":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void authenticatedAdminMutationRejectsMissingCsrfToken() throws Exception {
        UUID submissionId = UUID.randomUUID();

        mvc.perform(authenticated(post("/api/submissions/{id}/approve", submissionId)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(approval);
    }

    @Test
    void csrfCookieAndHeaderAuthorizeAdminMutationWithoutAServerSession() throws Exception {
        MvcResult bootstrap = mvc.perform(authenticated(get("/api/me")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();
        Cookie csrfCookie = bootstrap.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.isHttpOnly()).isFalse();
        assertThat(csrfCookie.getPath()).isEqualTo("/");
        assertThat(bootstrap.getRequest().getSession(false)).isNull();

        UUID submissionId = UUID.randomUUID();
        when(approval.approve(submissionId, "jsmith"))
                .thenReturn(new Tenant("Acme Reports", "pub400.com", "QUSER"));

        mvc.perform(authenticated(post("/api/submissions/{id}/approve", submissionId))
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Reports"));

        verify(approval).approve(submissionId, "jsmith");
    }

    private static MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder request) {
        return request.header(AuthentikHeaderAuthenticationFilter.USERNAME_HEADER, "jsmith")
                .header(AuthentikHeaderAuthenticationFilter.EMAIL_HEADER, "jsmith@example.com");
    }
}
