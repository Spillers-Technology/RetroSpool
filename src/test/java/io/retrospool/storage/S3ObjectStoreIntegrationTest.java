package io.retrospool.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** S3ObjectStore against a real MinIO (endpoint override + path-style, as in prod-with-MinIO). */
@Tag("integration")
@Testcontainers
class S3ObjectStoreIntegrationTest {

    private static final String BUCKET = "spool-landing-test";

    @Container
    static final MinIOContainer minio = new MinIOContainer("minio/minio:latest");

    static S3ObjectStore store;

    @BeforeAll
    static void setUp() {
        S3Client client = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .build();
        client.createBucket(b -> b.bucket(BUCKET));
        store = new S3ObjectStore(client, BUCKET);
    }

    @Test
    void putGetExistsRoundTrip() {
        String key = "tenant-x/2026/07/02/cap-0.pcl";
        byte[] payload = {0x1B, 'E', 'h', 'e', 'l', 'l', 'o', 0x0C};

        assertThat(store.exists(key)).isFalse();
        store.put(key, payload, "application/vnd.hp-pcl");
        assertThat(store.exists(key)).isTrue();
        assertThat(store.get(key)).isEqualTo(payload);
    }
}
