package io.retrospool.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Submissions predate tenants (two-surface model, D-007) — not tenant-scoped. */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);
}
