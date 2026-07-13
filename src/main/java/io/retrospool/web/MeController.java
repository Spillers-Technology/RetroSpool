package io.retrospool.web;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current admin identity for the SPA shell. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    public record MeResponse(String username, String email, String displayName, List<String> groups) {
    }

    @GetMapping
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AdminUser user)) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new MeResponse(
                user.username(),
                user.email(),
                user.displayName() != null ? user.displayName() : user.username(),
                user.groups()));
    }
}
