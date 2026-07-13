package io.retrospool.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Submissions predate tenants (two-surface model, D-007) — not tenant-scoped. */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findByStatusOrderBySubmittedAtAsc(SubmissionStatus status);

    /** Serialize approval/rejection decisions for one submission inside a transaction. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Submission s where s.id = :id")
    Optional<Submission> findByIdForUpdate(@Param("id") UUID id);
}
