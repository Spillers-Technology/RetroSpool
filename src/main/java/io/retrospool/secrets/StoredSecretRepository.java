package io.retrospool.secrets;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Storage for encrypted secret envelopes (D-023). Not tenant-scoped — secrets are
 * written by the pre-tenant submission surface, so this repository is exempt from the
 * D-008 tenant-isolation gate (registered there explicitly).
 */
public interface StoredSecretRepository extends JpaRepository<StoredSecret, UUID> {
}
