package io.retrospool.api;

import io.retrospool.connection.ConnectionParameters;
import io.retrospool.connection.ConnectionTestService;
import io.retrospool.connection.TestConnectionResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backend for the submission page's "Test Connection" action. Lives on the low-trust submission
 * surface, so it takes raw credentials in the request (never persisted) and returns only a
 * pass/fail + message. See docs/architecture.md (two-surface model).
 */
@RestController
@RequestMapping("/api/connection")
public class TestConnectionController {

    private final ConnectionTestService service;

    public TestConnectionController(ConnectionTestService service) {
        this.service = service;
    }

    @PostMapping("/test")
    public TestConnectionResult test(@Valid @RequestBody TestConnectionRequest req) {
        ConnectionParameters params = new ConnectionParameters(
                req.host(), req.username(), req.password().toCharArray(), req.useSsl());
        // ConnectionTestService scrubs the password array before returning.
        return service.test(params);
    }

    public record TestConnectionRequest(
            @NotBlank String host,
            @NotBlank String username,
            @NotBlank String password,
            boolean useSsl) {
    }
}
