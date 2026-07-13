package io.retrospool.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * CI gate for D-008: every declared query method on a repository whose entity carries
 * a {@code tenantId} column must scope by tenant. This is deliberately a compile-time
 * shape check — add new repositories to {@link #ALL_REPOSITORIES} (a fresh repo that
 * is not registered here fails the completeness check below).
 */
class TenantIsolationGateTest {

    private static final List<Class<? extends JpaRepository<?, ?>>> ALL_REPOSITORIES = List.of(
            TenantRepository.class,
            TenantOutputQueueRepository.class,
            SubmissionRepository.class,
            SpoolWatermarkRepository.class,
            CaptureRepository.class,
            ExportDestinationRepository.class,
            ExportAttemptRepository.class,
            AuditEventRepository.class,
            // Not tenant-scoped: encrypted secrets are written by the pre-tenant
            // submission surface (D-023). No tenantId field, so the shape check skips it.
            io.retrospool.secrets.StoredSecretRepository.class);

    @Test
    void everyQueryMethodOnTenantScopedEntitiesTakesTenantId() {
        List<String> violations = new ArrayList<>();
        for (Class<?> repo : ALL_REPOSITORIES) {
            Class<?> entity = entityOf(repo);
            if (!hasTenantIdField(entity)) {
                continue; // scope inherited or entity predates tenants (see repo javadoc)
            }
            for (Method m : repo.getDeclaredMethods()) {
                if (!m.getName().contains("TenantId")) {
                    violations.add(repo.getSimpleName() + "." + m.getName()
                            + " queries tenant-scoped " + entity.getSimpleName()
                            + " without a TenantId parameter");
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void gateCoversEveryRepositoryInThePersistencePackage() throws Exception {
        // Completeness: a new repository that is not registered above fails here.
        var resolver = new org.springframework.core.io.support.PathMatchingResourcePatternResolver();
        var metadataFactory = new org.springframework.core.type.classreading.CachingMetadataReaderFactory();
        List<Class<?>> found = new ArrayList<>();
        for (var resource : resolver.getResources(
                "classpath*:io/retrospool/**/*.class")) {
            String className = metadataFactory.getMetadataReader(resource)
                    .getClassMetadata().getClassName();
            Class<?> clazz = Class.forName(className);
            if (clazz.isInterface() && JpaRepository.class.isAssignableFrom(clazz)) {
                found.add(clazz);
            }
        }
        assertThat(found).containsExactlyInAnyOrderElementsOf(ALL_REPOSITORIES);
    }

    private static Class<?> entityOf(Class<?> repo) {
        return ResolvableType.forClass(repo).as(JpaRepository.class).getGeneric(0).resolve();
    }

    private static boolean hasTenantIdField(Class<?> entity) {
        for (Field f : entity.getDeclaredFields()) {
            if (f.getName().equals("tenantId")) {
                return true;
            }
        }
        return false;
    }
}
