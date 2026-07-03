package io.retrospool.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantOutputQueueRepository extends JpaRepository<TenantOutputQueue, UUID> {

    List<TenantOutputQueue> findByTenantId(UUID tenantId);

    Optional<TenantOutputQueue> findByTenantIdAndId(UUID tenantId, UUID id);
}
