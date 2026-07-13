package io.retrospool.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.retrospool.api.IntakeDtos.SubmissionCreatedResponse;
import io.retrospool.submission.ParsedDraft;
import io.retrospool.submission.WsHodParser;
import io.retrospool.web.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public intake surface must be reachable anonymously and without a CSRF token
 * (D-007), exactly like Test Connection — this exercises the real filter chain to prove
 * the {@link SecurityConfig} exception is in place and correctly scoped.
 */
@WebMvcTest({SubmissionIntakeController.class, IntakeExceptionAdvice.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = "retrospool.admin.dev-user=")
class SubmissionIntakeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WsHodParser parser;

    @MockitoBean
    private SubmissionIntakeService intake;

    @Test
    void parseIsAnonymousAndCsrfExempt() throws Exception {
        when(parser.parse(any(), any())).thenReturn(new ParsedDraft(
                "ibmi.example.com", 992, true, null, "Payroll", "PRT01", 37,
                "5250 Printer", "PComm .ws", List.of()));

        MockMultipartFile file = new MockMultipartFile(
                "file", "payroll.ws", "text/plain",
                "[Telnet5250]\nHost=ibmi.example.com\n".getBytes());

        mvc.perform(multipart("/api/submissions/parse").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.host").value("ibmi.example.com"))
                .andExpect(jsonPath("$.useSsl").value(true));
    }

    @Test
    void createIsAnonymousAndCsrfExempt() throws Exception {
        when(intake.create(any())).thenReturn(
                new SubmissionCreatedResponse(UUID.randomUUID(), "PENDING", true, false));

        mvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draft":{"host":"ibmi.example.com","useSsl":true,"username":"RPTUSER"},
                                 "ibmiPassword":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.ibmiPasswordStored").value(true));
    }

    @Test
    void createRejectsMissingHostWith400() throws Exception {
        mvc.perform(post("/api/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"draft":{"host":"","useSsl":true,"username":"RPTUSER"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }
}
