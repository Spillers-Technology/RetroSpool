package io.retrospool.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.retrospool.secrets.StoredSecret;
import io.retrospool.secrets.StoredSecretRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Flyway (V1+V2) against real Postgres 16 + entity round-trips for the exotic
 * mappings (text[] arrays, jsonb) + the load-bearing dedup constraint (D-008).
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PersistenceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TenantRepository tenants;
    @Autowired
    TenantOutputQueueRepository queues;
    @Autowired
    SubmissionRepository submissions;
    @Autowired
    CaptureRepository captures;
    @Autowired
    StoredSecretRepository secrets;

    @Test
    void secretEnvelopeRoundTripsAsBytea() {
        // V3 (D-023): the encrypted-secret envelope persists as bytea and reloads intact.
        byte[] material = {0, 1, 2, (byte) 0xFF, 0x10, 0x42};
        StoredSecret saved = secrets.saveAndFlush(new StoredSecret(material));

        StoredSecret reloaded = secrets.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getMaterial()).containsExactly(0, 1, 2, 0xFF, 0x10, 0x42);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void tenantRoundTripsLibraryListArray() {
        Tenant tenant = new Tenant("Acme Motors", "ibmi.example.com", "SPOOLUSR");
        tenant.setLibraryList(List.of("QGPL", "ACMELIB"));
        tenants.saveAndFlush(tenant);

        Tenant reloaded = tenants.findById(tenant.getId()).orElseThrow();
        assertThat(reloaded.getLibraryList()).containsExactly("QGPL", "ACMELIB");
        assertThat(reloaded.getRetentionPolicy()).isEqualTo(RetentionPolicy.HOLD);
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void submissionRoundTripsJsonbDraft() {
        Submission submission = new Submission(
                "{\"host\":\"ibmi.example.com\",\"useSsl\":true,\"username\":\"SPOOLUSR\"}");
        submissions.saveAndFlush(submission);

        Submission reloaded = submissions.findById(submission.getId()).orElseThrow();
        assertThat(reloaded.getParsedDraft()).contains("ibmi.example.com");
        assertThat(reloaded.getStatus()).isEqualTo(SubmissionStatus.PENDING);
        assertThat(submissions.findByStatusOrderBySubmittedAtAsc(SubmissionStatus.PENDING))
                .extracting(Submission::getId).contains(submission.getId());
    }

    @Test
    void dedupConstraintBlocksReinsertWithinTenant() {
        Tenant tenant = tenants.saveAndFlush(new Tenant("A", "h", "u"));
        TenantOutputQueue queue = queues.saveAndFlush(
                new TenantOutputQueue(tenant.getId(), "QGPL", "PRTQ"));

        String sha = "b".repeat(64);
        Capture first = new Capture(tenant.getId(), queue.getId(),
                DetectedFormat.PCL, 0, sha, 10);
        first.assignStorageKey("a/k1.pcl");
        captures.saveAndFlush(first);

        // same tenant + sha + index -> constraint fires (this aborts the test tx,
        // so it must be the last statement in this test — see the cross-tenant test)
        Capture duplicate = new Capture(tenant.getId(), queue.getId(),
                DetectedFormat.PCL, 0, sha, 10);
        duplicate.assignStorageKey("a/k2.pcl");
        assertThatThrownBy(() -> captures.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void identicalBytesAcrossTenantsAreDistinctCaptures() {
        Tenant tenantA = tenants.saveAndFlush(new Tenant("A", "h", "u"));
        Tenant tenantB = tenants.saveAndFlush(new Tenant("B", "h", "u"));
        TenantOutputQueue queueA = queues.saveAndFlush(
                new TenantOutputQueue(tenantA.getId(), "QGPL", "PRTQ"));
        TenantOutputQueue queueB = queues.saveAndFlush(
                new TenantOutputQueue(tenantB.getId(), "QGPL", "PRTQ"));

        String sha = "d".repeat(64);
        Capture forA = new Capture(tenantA.getId(), queueA.getId(),
                DetectedFormat.PCL, 0, sha, 10);
        forA.assignStorageKey("a/k1.pcl");
        captures.saveAndFlush(forA);

        // same bytes for a DIFFERENT tenant -> distinct capture, never merged (D-008)
        Capture forB = new Capture(tenantB.getId(), queueB.getId(),
                DetectedFormat.PCL, 0, sha, 10);
        forB.assignStorageKey("b/k1.pcl");
        captures.saveAndFlush(forB);
        assertThat(captures.existsByTenantIdAndSha256AndLogicalSegmentIndex(
                tenantB.getId(), sha, 0)).isTrue();
    }

    @Test
    void renderColumnsPersist() {
        Tenant tenant = tenants.saveAndFlush(new Tenant("C", "h", "u"));
        TenantOutputQueue queue = queues.saveAndFlush(
                new TenantOutputQueue(tenant.getId(), "QGPL", "PRTQ"));

        Capture capture = new Capture(tenant.getId(), queue.getId(),
                DetectedFormat.PCL, 0, "c".repeat(64), 10);
        capture.assignStorageKey("c/k1.pcl");
        capture.renderSucceeded("c/k1.pcl.pdf");
        captures.saveAndFlush(capture);

        Capture reloaded = captures.findByTenantIdAndId(tenant.getId(), capture.getId())
                .orElseThrow();
        assertThat(reloaded.getRenderStatus()).isEqualTo(RenderStatus.SUCCESS);
        assertThat(reloaded.getRenderedStorageKey()).isEqualTo("c/k1.pcl.pdf");
    }
}
