package io.retrospool.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportDestinationRepository extends JpaRepository<ExportDestination, UUID> {

    List<ExportDestination> findByTenantId(UUID tenantId);

    List<ExportDestination> findByTenantIdAndEnabledTrue(UUID tenantId);

    Optional<ExportDestination> findByTenantIdAndId(UUID tenantId, UUID id);
}
