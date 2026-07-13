package io.retrospool.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;

/** Small helpers shared by the admin controllers. */
final class JsonUtil {

    private JsonUtil() {
    }

    /** Parse a stored JSON string column into a node, tolerating null/blank/garbage. */
    static JsonNode parse(ObjectMapper mapper, String json) {
        if (!StringUtils.hasText(json)) {
            return NullNode.getInstance();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return NullNode.getInstance();
        }
    }

    /** The signed-in admin's username, for audit attribution. */
    static String actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AdminUser user) {
            return user.username();
        }
        return "unknown";
    }
}
