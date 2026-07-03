package io.retrospool.capture;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for the GhostPDL render sidecar (D-018). Deliberately never throws out of
 * {@link #render}: a render failure must never fail the capture — the original .pcl
 * still lands and the failure is recorded on the capture row for later retry.
 */
@Component
public class PclRenderClient {

    private static final Logger log = LoggerFactory.getLogger(PclRenderClient.class);
    private static final int ERROR_SNIPPET_CHARS = 500;

    private final String baseUrl;
    private final long maxBytes;
    private final RestClient restClient;

    @Autowired
    public PclRenderClient(
            @Value("${gateway.render.url:}") String baseUrl,
            @Value("${gateway.render.timeout-seconds:30}") long timeoutSeconds,
            @Value("${gateway.render.max-bytes:104857600}") long maxBytes) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.maxBytes = maxBytes;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Visible for testing — inject a prepared {@link RestClient}. */
    PclRenderClient(String baseUrl, long maxBytes, RestClient restClient) {
        this.baseUrl = baseUrl;
        this.maxBytes = maxBytes;
        this.restClient = restClient;
    }

    public boolean isConfigured() {
        return !baseUrl.isBlank();
    }

    public RenderResult render(byte[] pcl) {
        if (!isConfigured()) {
            return RenderResult.failed(
                    "Renderer not configured (gateway.render.url is empty); retry once configured.");
        }
        if (pcl.length > maxBytes) {
            return RenderResult.failed(
                    "PCL segment (" + pcl.length + " bytes) exceeds render cap (" + maxBytes + ").");
        }
        try {
            byte[] pdf = restClient.post()
                    .uri(baseUrl + "/render")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(pcl)
                    .retrieve()
                    .body(byte[].class);
            if (pdf == null || pdf.length == 0) {
                return RenderResult.failed("Renderer returned an empty response.");
            }
            return RenderResult.ok(pdf);
        } catch (RestClientResponseException e) {
            String detail = snippet(e.getResponseBodyAsString());
            log.info("Render sidecar rejected PCL ({}): {}", e.getStatusCode(), detail);
            return RenderResult.failed("Renderer error " + e.getStatusCode().value() + ": " + detail);
        } catch (ResourceAccessException e) {
            log.info("Render sidecar unreachable: {}", e.getMessage());
            return RenderResult.failed("Renderer unreachable: " + snippet(e.getMessage()));
        } catch (Exception e) {
            log.warn("Unexpected render failure", e);
            return RenderResult.failed("Unexpected render failure: " + snippet(e.getMessage()));
        }
    }

    private static String snippet(String s) {
        if (s == null || s.isBlank()) {
            return "(no detail)";
        }
        String trimmed = s.strip();
        return trimmed.length() <= ERROR_SNIPPET_CHARS
                ? trimmed
                : trimmed.substring(0, ERROR_SNIPPET_CHARS) + "…";
    }
}
