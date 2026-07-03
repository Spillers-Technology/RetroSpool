package io.retrospool.capture;

import static org.assertj.core.api.Assertions.assertThat;

import io.retrospool.Fixtures;
import io.retrospool.persistence.Capture;
import io.retrospool.persistence.CaptureRepository;
import io.retrospool.persistence.DetectedFormat;
import io.retrospool.persistence.RenderStatus;
import io.retrospool.persistence.Tenant;
import io.retrospool.persistence.TenantOutputQueue;
import io.retrospool.persistence.TenantOutputQueueRepository;
import io.retrospool.persistence.TenantRepository;
import io.retrospool.storage.ObjectStore;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * The whole pipeline against real infrastructure: Postgres 16, MinIO, and the actual
 * GhostPDL render sidecar built from render-sidecar/Dockerfile. Concatenated PCL in ->
 * two .pcl objects + two rendered .pdf siblings + two capture rows; re-feeding the same
 * bytes creates nothing (dedup); text in -> converted PDF with render SKIPPED.
 *
 * NOTE: first run compiles GhostPDL from source in the image build (several minutes);
 * Docker layer cache makes later runs cheap.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest
class CaptureEndToEndIntegrationTest {

    private static final String BUCKET = "spool-landing-e2e";

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static final MinIOContainer minio = new MinIOContainer("minio/minio:latest");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> renderSidecar = new GenericContainer<>(
            new ImageFromDockerfile("spool-render-sidecar-test", false)
                    .withFileFromPath(".", Paths.get("render-sidecar")))
            .withExposedPorts(8080);

    @DynamicPropertySource
    static void wire(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("gateway.storage.bucket", () -> BUCKET);
        registry.add("gateway.storage.endpoint", minio::getS3URL);
        registry.add("gateway.storage.path-style-access", () -> "true");
        registry.add("gateway.storage.access-key", minio::getUserName);
        registry.add("gateway.storage.secret-key", minio::getPassword);
        registry.add("gateway.render.url", () -> "http://" + renderSidecar.getHost()
                + ":" + renderSidecar.getMappedPort(8080));
    }

    @BeforeAll
    static void createBucket() {
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build()) {
            client.createBucket(b -> b.bucket(BUCKET));
        }
    }

    @Autowired
    CaptureService captureService;
    @Autowired
    CaptureRepository captures;
    @Autowired
    TenantRepository tenants;
    @Autowired
    TenantOutputQueueRepository queues;
    @Autowired
    ObjectStore objectStore;

    @Test
    void concatenatedPclEndToEndWithDedupOnRefeed() {
        Tenant tenant = tenants.save(new Tenant("E2E Acme", "host", "USER"));
        TenantOutputQueue queue = queues.save(
                new TenantOutputQueue(tenant.getId(), "QGPL", "PRTQ"));
        byte[] payload = Fixtures.load("concatenated.pcl");

        List<Capture> created = captureService.capture(
                tenant.getId(), queue.getId(), payload, SpoolMetadata.none());

        assertThat(created).hasSize(2);
        for (Capture c : created) {
            assertThat(c.getDetectedFormat()).isEqualTo(DetectedFormat.PCL);
            assertThat(c.getRenderStatus())
                    .as("render via real gpcl6 sidecar (error: %s)", c.getRenderError())
                    .isEqualTo(RenderStatus.SUCCESS);
            assertThat(objectStore.exists(c.getStorageKey())).isTrue();
            byte[] pdf = objectStore.get(c.getRenderedStorageKey());
            assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        }

        // idempotency: same bytes again -> nothing new
        assertThat(captureService.capture(
                tenant.getId(), queue.getId(), payload, SpoolMetadata.none())).isEmpty();
        assertThat(captures.findByTenantIdOrderByCapturedAtDesc(tenant.getId())).hasSize(2);
    }

    @Test
    void textSpoolBecomesPdfWithRenderSkipped() {
        Tenant tenant = tenants.save(new Tenant("E2E Acme 2", "host", "USER"));
        TenantOutputQueue queue = queues.save(
                new TenantOutputQueue(tenant.getId(), "QGPL", "PRTQ2"));

        List<Capture> created = captureService.capture(
                tenant.getId(), queue.getId(), Fixtures.load("sample.txt"), SpoolMetadata.none());

        assertThat(created).hasSize(1);
        Capture c = created.get(0);
        assertThat(c.getDetectedFormat()).isEqualTo(DetectedFormat.TEXT);
        assertThat(c.getRenderStatus()).isEqualTo(RenderStatus.SKIPPED);
        byte[] stored = objectStore.get(c.getStorageKey());
        assertThat(new String(stored, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
