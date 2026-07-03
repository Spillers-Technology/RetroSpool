package io.retrospool.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * No tenant_id column here — scope is inherited: capture ids are only ever obtained
 * through tenant-scoped {@link CaptureRepository} queries.
 */
public interface ExportAttemptRepository extends JpaRepository<ExportAttempt, UUID> {

    List<ExportAttempt> findByCaptureId(UUID captureId);
}
