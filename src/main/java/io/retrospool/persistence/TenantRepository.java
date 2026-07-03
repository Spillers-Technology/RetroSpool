package io.retrospool.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Tenant is the scoping root itself — unscoped access is expected here only. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
