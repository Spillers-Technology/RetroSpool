package io.retrospool.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Keyed by (tenant_id, output_queue_id) — the composite id carries the tenant scope. */
public interface SpoolWatermarkRepository extends JpaRepository<SpoolWatermark, SpoolWatermarkId> {
}
