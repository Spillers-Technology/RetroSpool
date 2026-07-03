package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PclRenderClientTest {

    private static final String BASE_URL = "http://render-sidecar:8080";
    private static final byte[] PCL = {0x1B, 'E', 'h', 'i', 0x0C};

    private MockRestServiceServer server;
    private PclRenderClient client;

    private void buildClient(long maxBytes) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PclRenderClient(BASE_URL, maxBytes, builder.build());
    }

    @Test
    void successfulRenderReturnsPdfBytes() {
        buildClient(1024);
        byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);
        server.expect(requestTo(BASE_URL + "/render"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(pdf, MediaType.APPLICATION_PDF));

        RenderResult result = client.render(PCL);

        assertThat(result.success()).isTrue();
        assertThat(result.pdf()).isEqualTo(pdf);
        server.verify();
    }

    @Test
    void rendererErrorBecomesFailureWithDetailNotException() {
        buildClient(1024);
        server.expect(requestTo(BASE_URL + "/render"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("gpcl6 failed: unsupported command"));

        RenderResult result = client.render(PCL);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("422").contains("gpcl6 failed");
    }

    @Test
    void emptyResponseIsAFailure() {
        buildClient(1024);
        server.expect(requestTo(BASE_URL + "/render"))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_PDF));

        RenderResult result = client.render(PCL);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("empty");
    }

    @Test
    void unconfiguredClientFailsSoftlyWithoutCallingAnything() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unused = MockRestServiceServer.bindTo(builder).build();
        PclRenderClient unconfigured = new PclRenderClient("", 1024, builder.build());

        RenderResult result = unconfigured.render(PCL);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not configured");
        unused.verify(); // no requests were made
    }

    @Test
    void oversizedPayloadIsRejectedLocally() {
        buildClient(4); // cap below payload size
        RenderResult result = client.render(PCL);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("exceeds render cap");
        server.verify(); // no requests were made
    }
}
