package io.retrospool.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Trusts the identity headers injected by the Authentik forward-auth outpost
 * (the ingress already terminates the OIDC flow and only forwards a request
 * after a successful signon — D-022). Reaching the app with an
 * {@code X-authentik-username} header therefore means an authenticated admin.
 *
 * <p>For local/quickstart runs with no outpost in front, {@code devUser} (from
 * {@code retrospool.admin.dev-user}) provides a stand-in identity so the console
 * is usable; it is blank in production and the filter authenticates nobody
 * without headers.
 */
public class AuthentikHeaderAuthenticationFilter extends OncePerRequestFilter {

    static final String USERNAME_HEADER = "X-authentik-username";
    static final String EMAIL_HEADER = "X-authentik-email";
    static final String NAME_HEADER = "X-authentik-name";
    static final String GROUPS_HEADER = "X-authentik-groups";

    private final String devUser;

    public AuthentikHeaderAuthenticationFilter(String devUser) {
        this.devUser = StringUtils.hasText(devUser) ? devUser.trim() : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            AbstractAuthenticationToken auth = resolve(request);
            if (auth != null) {
                auth.setDetails(request.getRemoteAddr());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private AbstractAuthenticationToken resolve(HttpServletRequest request) {
        String username = request.getHeader(USERNAME_HEADER);
        AdminUser user;
        if (StringUtils.hasText(username)) {
            user = new AdminUser(
                    username.trim(),
                    trimmedOrNull(request.getHeader(EMAIL_HEADER)),
                    trimmedOrNull(request.getHeader(NAME_HEADER)),
                    parseGroups(request.getHeader(GROUPS_HEADER)));
        } else if (devUser != null) {
            user = new AdminUser(devUser, devUser + "@local", devUser + " (dev)", List.of("retrospool-admins"));
        } else {
            return null;
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        for (String g : user.groups()) {
            authorities.add(new SimpleGrantedAuthority("GROUP_" + g));
        }
        return new PreAuthenticatedAuthenticationToken(user, "n/a", authorities);
    }

    private static List<String> parseGroups(String header) {
        if (!StringUtils.hasText(header)) {
            return List.of();
        }
        // Authentik joins groups with '|'; tolerate ',' too.
        return Arrays.stream(header.split("[|,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String trimmedOrNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
