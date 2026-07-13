package io.retrospool.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the single-page app for client-side routes. Spring serves the built
 * bundle (index.html, /assets/**) from the classpath; a deep link or hard refresh
 * to a client route must also return index.html so the router can take over.
 * API paths ({@code /api/**}) and asset paths (which contain a dot) are excluded.
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/dashboard", "/submissions/**", "/tenants/**", "/captures/**", "/test-connection"})
    public String forward() {
        return "forward:/index.html";
    }
}
