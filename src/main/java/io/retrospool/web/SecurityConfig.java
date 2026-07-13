package io.retrospool.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Admin-surface security (D-022). The Authentik outpost in front of the ingress
 * performs the OIDC flow; this app trusts the forwarded identity headers via
 * {@link AuthentikHeaderAuthenticationFilter}. The API is stateless — no server
 * session or login form — while admin mutations use a cookie-backed CSRF token
 * because the upstream Authentik flow can still rely on a browser session cookie.
 *
 * <ul>
 *   <li>{@code /api/health}, {@code /actuator/health} — open, for k8s probes that
 *       hit the pod directly and never carry outpost headers.</li>
 *   <li>{@code POST /api/connection/test}, {@code POST /api/submissions/parse}, and
 *       {@code POST /api/submissions} — open as the low-trust public submission surface
 *       (D-007). Matched by exact path so the admin approve/reject routes under
 *       {@code /api/submissions/{id}/…} stay authenticated.</li>
 *   <li>all other {@code /api/**} routes — require an authenticated admin and
 *       return 401 (not a redirect) so the SPA can react.</li>
 *   <li>everything else (the built SPA: index.html, assets) — served freely; the
 *       outpost already gates these at the ingress.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, @Value("${retrospool.admin.dev-user:}") String devUser)
            throws Exception {
        AuthentikHeaderAuthenticationFilter headerFilter = new AuthentikHeaderAuthenticationFilter(devUser);
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        RequestMatcher publicSubmissionSurface = SecurityConfig::isPublicSubmissionSurface;

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        // The SPA reads the raw XSRF-TOKEN cookie and echoes it in
                        // X-XSRF-TOKEN; no BREACH-masked form parameter is involved.
                        .csrfTokenRequestHandler(csrfRequestHandler)
                        // D-007: retain the standalone submission page's curl/browser
                        // contract without opening any authenticated admin mutation.
                        .ignoringRequestMatchers(publicSubmissionSurface))
                .cors(cors -> cors.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health", "/actuator/health/**").permitAll()
                        // D-007: the public submission surface (upload/parse, submit,
                        // Test Connection) is anonymous. Kept method- and path-specific so
                        // no admin read or approval endpoint becomes reachable without an
                        // Authentik identity.
                        .requestMatchers(publicSubmissionSurface).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(headerFilter, AnonymousAuthenticationFilter.class)
                // Force the deferred token to be materialized as a cookie on the
                // initial SPA GET (normally /api/me), without creating a session.
                .addFilterAfter(new CsrfCookieFilter(), AuthentikHeaderAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthenticated\",\"message\":\"admin sign-in required\"}");
                }))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable());

        return http.build();
    }

    // Exactly the low-trust submission surface (D-007). Path-exact and POST-only: the
    // admin routes at /api/submissions/{id}/approve|reject have longer paths and never
    // match here, so they still require an Authentik identity.
    private static final java.util.Set<String> PUBLIC_POST_PATHS = java.util.Set.of(
            "/api/connection/test",
            "/api/submissions",
            "/api/submissions/parse");

    private static boolean isPublicSubmissionSurface(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return PUBLIC_POST_PATHS.contains(path);
    }
}
