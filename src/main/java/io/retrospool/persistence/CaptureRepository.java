package io.retrospool.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Every query is tenant-scoped (D-008); the isolation test gate enforces this shape. */
public interface CaptureRepository extends JpaRepository<Capture, UUID> {

    boolean existsByTenantIdAndSha256AndLogicalSegmentIndex(
            UUID tenantId, String sha256, int logicalSegmentIndex);

    Optional<Capture> findByTenantIdAndSha256AndLogicalSegmentIndex(
            UUID tenantId, String sha256, int logicalSegmentIndex);

    List<Capture> findByTenantIdOrderByCapturedAtDesc(UUID tenantId);

    Optional<Capture> findByTenantIdAndId(UUID tenantId, UUID id);
}
